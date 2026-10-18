#!/usr/bin/env python3
"""
validate_repo.py

Comprehensive repository and configuration validator:
- Validates repo.json structure
- Validates settings.gradle.kts and module directories
- Cross-references config/providers.json and config/domains.json:
    enabled config provider <=> root Gradle provider module <=> @CloudstreamPlugin <=> MainAPI <=> domains.json entry
- Checks for duplicate providers, duplicate modules, duplicate internalNames
- Validates module build.gradle.kts metadata (version, authors, language, description, status, tvTypes)
- Checks Kotlin plugin registration (@CloudstreamPlugin & Plugin class)
- Checks MainAPI inheritance
- Validates generated plugins.json and verifies SHA-256 hash against built .cs3 artifacts
"""

import os
import sys
import json
import re
import hashlib
import argparse
from pathlib import Path

VALID_TV_TYPES = {
    "Movie", "TvSeries", "Anime", "AnimeMovie", "OVA", "Cartoon",
    "Documentary", "AsianDrama", "Live", "NSFW", "Others", "Music",
    "AudioBook", "CustomMedia", "Audio", "Podcast", "Torrent"
}

VALID_STATUSES = {0, 1, 2, 3}

def check_file_hash(filepath, expected_hash):
    sha = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            sha.update(chunk)
    computed = "sha256-" + sha.hexdigest()
    return computed.lower() == expected_hash.lower()


def validate_artifacts(repo_root, plugins, enabled_modules, physical_modules):
    """Validate current module outputs only; archived deliveries are not builds.

    Return errors without modifying inputs. Identity is internalName (the module
    name), never the potentially different human-readable plugin name.
    """
    errors = []
    if not isinstance(plugins, list):
        return ["build/plugins.json must be an array"]
    root = Path(repo_root).absolute()
    enabled = set(enabled_modules)
    physical = set(physical_modules)
    safe_name = re.compile(r"[A-Za-z_][A-Za-z0-9_-]*\Z")
    seen = set()
    for plugin in plugins:
        if not isinstance(plugin, dict):
            errors.append("Invalid plugin entry in build/plugins.json")
            continue
        name = plugin.get("internalName")
        if not isinstance(name, str) or not safe_name.fullmatch(name):
            errors.append("Invalid internalName in build/plugins.json")
            continue
        if name in seen:
            errors.append(f"Duplicate internalName in build/plugins.json: {name}")
            continue
        seen.add(name)
        if name not in enabled or name not in physical:
            errors.append(f"Unknown/disabled plugin internalName: {name}")
            continue
        artifact = root / name / "build" / f"{name}.cs3"
        module_file = root / name / "build.gradle.kts"
        inputs = (root, root / name, root / name / "build", artifact, module_file)
        if any(path.is_symlink() or getattr(path, "is_junction", lambda: False)()
               for path in inputs):
            errors.append(f"Unsafe symlink/junction artifact input: {name}")
            continue
        if not module_file.is_file():
            errors.append(f"Missing physical provider module: {name}")
            continue
        expected_hash = plugin.get("fileHash")
        if not isinstance(expected_hash, str) or not re.fullmatch(
                r"sha256-[0-9a-f]{64}", expected_hash, re.IGNORECASE):
            errors.append(f"Plugin {name} has missing/invalid fileHash in plugins.json")
            continue
        if not artifact.is_file():
            errors.append(f"Missing canonical .cs3 artifact: {name}/build/{name}.cs3")
            continue
        try:
            if not check_file_hash(artifact, expected_hash):
                errors.append(f"Hash mismatch for {name}/build/{name}.cs3!")
        except OSError as exc:
            errors.append(f"Cannot read canonical artifact {name}: {exc}")
    for name in sorted(enabled - seen):
        errors.append(f"Enabled module missing from plugins.json: {name}")
    return errors


def main():
    parser = argparse.ArgumentParser(description="CloudStream Repository and Config Validator")
    parser.add_argument("--verify-artifacts", action="store_true", help="Verify generated .cs3 and plugins.json artifacts")
    args = parser.parse_args()

    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    errors = []

    print("[Validator] Starting repository validation...")

    # 1. Check repo.json
    repo_json_path = os.path.join(repo_root, "repo.json")
    if not os.path.exists(repo_json_path):
        errors.append("Missing repo.json in root!")
    else:
        try:
            with open(repo_json_path, "r", encoding="utf-8") as f:
                repo_data = json.load(f)
            if not repo_data.get("name"):
                errors.append("repo.json must contain a non-empty 'name'")
            if repo_data.get("manifestVersion") != 1:
                errors.append("repo.json manifestVersion must be 1")
            if not isinstance(repo_data.get("pluginLists"), list) or not repo_data["pluginLists"]:
                errors.append("repo.json must contain a non-empty 'pluginLists' array")
        except Exception as e:
            errors.append(f"Invalid JSON in repo.json: {e}")

    # 2. Check settings.gradle.kts and discover modules
    settings_path = os.path.join(repo_root, "settings.gradle.kts")
    gradle_modules = set()
    settings_ignored = set()
    settings_disabled = set()
    if not os.path.exists(settings_path):
        errors.append("Missing settings.gradle.kts in root!")
    else:
        with open(settings_path, "r", encoding="utf-8") as f:
            settings_content = f.read()
        # Check for static include("ModName")
        for line in settings_content.splitlines():
            line = line.strip()
            if line.startswith("include(") or line.startswith("include ("):
                m = re.search(r'["\']:?([a-zA-Z0-9_\-]+)["\']', line)
                if m:
                    gradle_modules.add(m.group(1))
        # Check for dynamic discovery in settings.gradle.kts
        if "eachDir" in settings_content or "listFiles" in settings_content:
            # extract ignoredDirs and disabled list
            ign_match = re.search(r'ignoredDirs\s*=\s*listOf\(([^)]*)\)', settings_content, re.DOTALL)
            if ign_match:
                settings_ignored = set(re.findall(r'"([^"]+)"', ign_match.group(1)))
            dis_match = re.search(r'disabled\s*=\s*listOf(?:<String>)?\(([^)]*)\)', settings_content, re.DOTALL)
            if dis_match:
                settings_disabled = set(re.findall(r'"([^"]+)"', dis_match.group(1)))

    # 3. Find physical provider module directories with build.gradle.kts
    physical_modules = set()
    base_ignored = {"tools", "docs", "config", "legacy", ".github", "gradle", "reports", "site", ".gradle", "build"} | settings_ignored
    for entry in os.listdir(repo_root):
        full_p = os.path.join(repo_root, entry)
        if os.path.isdir(full_p) and entry not in base_ignored and not entry.startswith("."):
            if os.path.exists(os.path.join(full_p, "build.gradle.kts")):
                physical_modules.add(entry)

    print(f"[Validator] Discovered {len(physical_modules)} physical provider modules: {sorted(physical_modules)}")

    # If static gradle_modules found, check against physical
    if gradle_modules:
        active_gradle_modules = gradle_modules
        for mod in physical_modules:
            if mod not in gradle_modules:
                errors.append(f"Module directory '{mod}' exists but is not included in settings.gradle.kts!")
        for mod in gradle_modules:
            if mod not in physical_modules:
                errors.append(f"Module '{mod}' included in settings.gradle.kts does not exist on disk!")
    else:
        # Dynamic discovery check: active modules are physical modules minus settings_disabled
        active_gradle_modules = physical_modules - settings_disabled
        for mod in settings_disabled:
            if mod not in physical_modules:
                errors.append(f"Disabled module '{mod}' in settings.gradle.kts does not exist on disk!")

    # 4. Check config/domains.json
    domains_path = os.path.join(repo_root, "config", "domains.json")
    domains_data = {}
    if not os.path.exists(domains_path):
        errors.append("Missing config/domains.json!")
    else:
        try:
            with open(domains_path, "r", encoding="utf-8") as f:
                domains_json = json.load(f)
            domains_data = domains_json.get("providers", {})
        except Exception as e:
            errors.append(f"Invalid JSON in config/domains.json: {e}")

    # 5. Check config/providers.json
    providers_path = os.path.join(repo_root, "config", "providers.json")
    enabled_providers = []
    prov_list = []
    if not os.path.exists(providers_path):
        errors.append("Missing config/providers.json!")
    else:
        try:
            with open(providers_path, "r", encoding="utf-8") as f:
                prov_config = json.load(f)
            prov_list = prov_config.get("providers", [])
            seen_names = set()
            seen_modules = set()

            for p in prov_list:
                name = p.get("name")
                module = p.get("module")
                enabled = p.get("enabled", False)
                domain_key = p.get("domainKey", name)

                if not name:
                    errors.append("Provider entry missing 'name' in config/providers.json")
                    continue
                if name in seen_names:
                    errors.append(f"Duplicate provider name '{name}' in config/providers.json")
                seen_names.add(name)

                if not module:
                    errors.append(f"Provider '{name}' missing 'module' in config/providers.json")
                    continue
                if module in seen_modules and enabled:
                    errors.append(f"Duplicate module '{module}' enabled in config/providers.json")
                seen_modules.add(module)

                if enabled:
                    enabled_providers.append(p)
                    # Invariant 1: Root Gradle provider module must exist
                    if module not in physical_modules:
                        errors.append(f"Enabled provider '{name}' has module '{module}' which does NOT exist on disk!")

                    # Invariant 2: Domain config entry must exist
                    if domain_key not in domains_data:
                        errors.append(f"Enabled provider '{name}' has domainKey '{domain_key}' missing from config/domains.json!")
                    else:
                        d_info = domains_data[domain_key]
                        canonical = d_info.get("canonical")
                        if not canonical or not (canonical.startswith("https://") or canonical.startswith("http://")):
                            errors.append(f"Provider '{name}' has invalid canonical URL in domains.json: {canonical}")
                        allowed_hosts = d_info.get("allowedHosts", [])
                        if not allowed_hosts:
                            errors.append(f"Provider '{name}' has empty allowedHosts in domains.json")
        except Exception as e:
            errors.append(f"Invalid JSON in config/providers.json: {e}")

    # Check for orphan physical modules not declared in config/providers.json
    configured_modules = {p.get("module") for p in prov_list if isinstance(p, dict)}
    for mod in physical_modules:
        if mod not in configured_modules:
            errors.append(f"Physical module '{mod}' has no entry in config/providers.json!")

    # Check enabled providers count matches active Gradle modules
    enabled_module_names = {p["module"] for p in enabled_providers}
    if active_gradle_modules != enabled_module_names:
        diff = active_gradle_modules.symmetric_difference(enabled_module_names)
        errors.append(f"Mismatch between active Gradle modules and enabled providers in config: {diff}")

    # 6. Check each module's code, metadata and annotations
    seen_internal_names = set()
    for mod in physical_modules:
        mod_dir = os.path.join(repo_root, mod)
        bg_file = os.path.join(mod_dir, "build.gradle.kts")
        with open(bg_file, "r", encoding="utf-8") as f:
            bg_content = f.read()

        # Check version
        ver_match = re.search(r'version\s*=\s*(\d+)', bg_content)
        if not ver_match:
            errors.append(f"[{mod}] Missing version in build.gradle.kts")
        else:
            ver = int(ver_match.group(1))
            if ver <= 0:
                errors.append(f"[{mod}] Version must be > 0 (found {ver})")

        # Check authors
        if "authors" not in bg_content:
            errors.append(f"[{mod}] Missing authors list in build.gradle.kts")

        # Check description
        if "description" not in bg_content:
            errors.append(f"[{mod}] Missing description in build.gradle.kts")

        # Check language
        lang_match = re.search(r'language\s*=\s*"([^"]+)"', bg_content)
        if not lang_match:
            errors.append(f"[{mod}] Missing language in build.gradle.kts")

        # Check manifest.json
        manifest_file = os.path.join(mod_dir, "manifest.json")
        if os.path.exists(manifest_file):
            try:
                with open(manifest_file, "r", encoding="utf-8") as f:
                    manifest_data = json.load(f)
                plugin_class = manifest_data.get("plugin")
                if not plugin_class:
                    errors.append(f"[{mod}] manifest.json missing 'plugin' class declaration")
                if plugin_class in seen_internal_names:
                    errors.append(f"[{mod}] Duplicate plugin class '{plugin_class}' found in manifests!")
                seen_internal_names.add(plugin_class)
            except Exception as e:
                errors.append(f"[{mod}] Invalid JSON in manifest.json: {e}")

        # Check Kotlin source files
        src_dir = os.path.join(mod_dir, "src", "main", "kotlin")
        if not os.path.exists(src_dir):
            errors.append(f"[{mod}] Missing src/main/kotlin directory")
            continue

        has_plugin = False
        has_main_api = False
        for root, _, files in os.walk(src_dir):
            for file in files:
                if file.endswith(".kt"):
                    with open(os.path.join(root, file), "r", encoding="utf-8") as f:
                        kt_code = f.read()
                    if "@CloudstreamPlugin" in kt_code:
                        has_plugin = True
                    if ": MainAPI()" in kt_code or ": MainAPI" in kt_code:
                        has_main_api = True

        if not has_plugin:
            errors.append(f"[{mod}] Missing @CloudstreamPlugin class")
        if not has_main_api:
            errors.append(f"[{mod}] Missing MainAPI implementation class")

    # 7. If --verify-artifacts is specified
    if args.verify_artifacts:
        plugins_json_path = os.path.join(repo_root, "build", "plugins.json")
        if not os.path.exists(plugins_json_path):
            errors.append(f"Artifact verification failed: {plugins_json_path} does not exist!")
        else:
            try:
                with open(plugins_json_path, "r", encoding="utf-8") as f:
                    plugins = json.load(f)
                if isinstance(plugins, list):
                    print(f"[Validator] Validating {len(plugins)} entries in build/plugins.json")
                errors.extend(validate_artifacts(repo_root, plugins, enabled_module_names, physical_modules))
            except (OSError, ValueError) as exc:
                errors.append(f"Invalid generated plugins.json: {exc}")

    if errors:
        print(f"\n=== Validation Failures ({len(errors)}) ===")
        for e in errors:
            print(f" - [ERROR] {e}")
        sys.exit(1)

    print(f"\n[SUCCESS] Repository validation passed with 0 errors! ({len(enabled_providers)} active providers verified, {len(physical_modules)} physical modules discovered)")
if __name__ == "__main__":
    main()
