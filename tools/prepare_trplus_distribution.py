"""Validate and stage a local, unpublished, branch-ready distribution.

No builds, Git commands, network requests, or generated metadata edits occur here.
Run after the normal Gradle build has produced build/plugins.json and module CS3s.
"""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

if __package__:
    from .verify_release_safety import ReleaseSafetyError, verify_release_safety
else:
    from verify_release_safety import ReleaseSafetyError, verify_release_safety


class DistributionError(ValueError):
    """An unsafe or inconsistent distribution input."""


EXCLUDED_DIRS = {
    ".git", ".gradle", ".kotlin", "build", "builds", "__pycache__",
    ".pytest_cache", ".mypy_cache", ".ruff_cache", ".scrapling", "cache",
    "caches", "backups", "backup", ".codex", ".agents", ".ai-workflow",
    ".opencode", ".venv", "venv", "node_modules", "logs", "reports",
}
SOURCE_DIRS = {"core", "gradle", "config", "tools", "tests", ".github", "docs", "site", "legacy"}
PROMPT_FILES = {"agents.md", "codex_migration.md"}
SAFE_NAME = re.compile(r"[A-Za-z_][A-Za-z0-9_-]*\Z")
SENSITIVE = re.compile(
    r"cookie|authorization|bearer\s|(?:api[_-]?key|access[_-]?token|password|secret)\s*[:=]",
    re.IGNORECASE,
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise DistributionError(message)


def is_link(path):
    return path.is_symlink() or getattr(path, "is_junction", lambda: False)()


def checked_file(root, relative):
    path = root / relative
    require(not any(is_link(p) for p in (path, *path.parents)), f"Symlink input: {relative}")
    require(path.is_file(), f"Missing input: {relative}")
    return path


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as exc:
        raise DistributionError(f"Invalid JSON input {path}: {exc}") from exc


def source_plugin_class(root, module):
    """Resolve the unique annotated plugin declaration without following links."""
    source = root / module / "src/main"
    require(source.is_dir() and not any(is_link(p) for p in (source, *source.parents)),
            f"Missing/unsafe plugin source: {module}")
    identities = []
    annotation = re.compile(
        r"@(?:[A-Za-z_][A-Za-z0-9_]*\.)*CloudstreamPlugin\b\s*"
        r"(?:\([^)]*\)\s*)?(?:(?:public|internal|private|open|final|abstract)\s+)*"
        r"class\s+([A-Za-z_][A-Za-z0-9_]*)\b"
    )
    for current, dirs, files in os.walk(source, followlinks=False):
        current = Path(current)
        dirs[:] = sorted(d for d in dirs if not is_link(current / d))
        for filename in sorted(files):
            path = current / filename
            if path.suffix != ".kt" or is_link(path):
                continue
            text = checked_file(root, path.relative_to(root)).read_text(encoding="utf-8-sig")
            # Comments and string literals cannot supply a package or annotation.
            text = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|/\*[\s\S]*?\*/|//[^\n]*',
                          lambda match: re.sub(r"[^\n]", " ", match.group()), text)
            classes = annotation.findall(text)
            if not classes:
                continue
            packages = re.findall(r"^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)\s*;?\s*$",
                                  text, re.MULTILINE)
            require(len(packages) == 1, f"Ambiguous plugin source package: {module}/{filename}")
            identities.extend(f"{packages[0]}.{name}" for name in classes)
    require(len(identities) == 1, f"Expected one annotated source plugin: {module}")
    return identities[0]


def validate(root, repository):
    require(re.fullmatch(r"[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+", repository) is not None,
            "--repository must be an owner/repo, not a URL")
    require(repository.split("/")[1] not in {".", ".."}, "Invalid repository name")
    raw = f"https://raw.githubusercontent.com/{repository}/builds"
    repo_path = checked_file(root, "repo.json")
    repo = read_json(repo_path)
    require(isinstance(repo, dict) and repo.get("pluginLists") == [f"{raw}/plugins.json"],
            "repo.json pluginLists must exactly match the target builds/plugins.json URL")
    registry = read_json(checked_file(root, "config/providers.json"))
    require(isinstance(registry, dict) and isinstance(registry.get("providers"), list),
            "Invalid providers registry")
    enabled = set()
    modules = set()
    for provider in registry["providers"]:
        require(isinstance(provider, dict), "Invalid provider entry")
        module = provider.get("module")
        require(isinstance(module, str) and SAFE_NAME.fullmatch(module), "Invalid provider module")
        require(module not in modules, f"Duplicate provider module: {module}")
        modules.add(module)
        require(type(provider.get("enabled")) is bool, f"Missing enabled flag: {module}")
        if provider["enabled"]:
            checked_file(root, f"{module}/build.gradle.kts")
            enabled.add(module)
    require(enabled, "No enabled providers")
    metadata_path = checked_file(root, "build/plugins.json")
    plugins = read_json(metadata_path)
    require(isinstance(plugins, list), "plugins.json must be an array")
    artifacts = {}
    seen = set()
    for plugin in plugins:
        require(isinstance(plugin, dict), "Invalid plugin entry")
        name = plugin.get("internalName")
        require(isinstance(name, str) and SAFE_NAME.fullmatch(name), "Invalid internalName")
        require(name not in seen, f"Duplicate internalName: {name}")
        seen.add(name)
        require(name in enabled, f"Unexpected/disabled plugin: {name}")
        require(plugin.get("repositoryUrl") == f"https://github.com/{repository}",
                f"Wrong repositoryUrl: {name}")
        require(plugin.get("url") == f"{raw}/{name}.cs3", f"Wrong artifact URL: {name}")
        version = plugin.get("version")
        require(type(version) is int and version > 0, f"Invalid version: {name}")
        gradle = checked_file(root, f"{name}/build.gradle.kts").read_text(encoding="utf-8-sig")
        versions = re.findall(r"^\s*version\s*=\s*(\d+)\s*(?://[^\n]*)?$", gradle, re.MULTILINE)
        require(versions == [str(version)], f"Gradle version mismatch: {name}")
        artifact = checked_file(root, f"{name}/build/{name}.cs3")
        data = artifact.read_bytes()
        require(type(plugin.get("fileSize")) is int and plugin["fileSize"] == len(data),
                f"fileSize mismatch: {name}")
        require(plugin.get("fileHash") == f"sha256-{digest(data)}", f"fileHash mismatch: {name}")
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                require(archive.namelist().count("manifest.json") == 1,
                        f"Missing/duplicate ZIP manifest: {name}")
                require(archive.getinfo("manifest.json").file_size <= 1024 * 1024,
                        f"Oversized ZIP manifest: {name}")
                require(archive.testzip() is None, f"Corrupt ZIP: {name}")
                require("classes.dex" in archive.namelist(), f"Missing classes.dex: {name}")
                manifest = json.loads(archive.read("manifest.json"))
        except (zipfile.BadZipFile, ValueError, RuntimeError, OSError) as exc:
            raise DistributionError(f"Invalid CS3 ZIP {name}: {exc}") from exc
        require(isinstance(manifest, dict), f"Invalid ZIP manifest: {name}")
        require(manifest.get("name") == name, f"Manifest name mismatch: {name}")
        require(type(manifest.get("version")) is int and manifest["version"] == version,
                f"Manifest version mismatch: {name}")
        class_name = manifest.get("pluginClassName")
        require(isinstance(class_name, str) and re.fullmatch(
            r"(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*", class_name),
            f"Invalid manifest pluginClassName: {name}")
        require(class_name == source_plugin_class(root, name),
                f"Manifest pluginClassName does not match annotated source: {name}")
        require(type(manifest.get("requiresResources")) is bool,
                f"Invalid manifest requiresResources: {name}")
        for key in ("pluginClassName", "requiresResources", "apiVersion"):
            if key in manifest and key in plugin:
                require(manifest[key] == plugin[key], f"Manifest {key} mismatch: {name}")
        artifacts[f"{name}.cs3"] = data
    require(seen == enabled, f"Enabled inventory mismatch; missing: {sorted(enabled - seen)}")
    artifacts["repo.json"] = repo_path.read_bytes()
    artifacts["plugins.json"] = metadata_path.read_bytes()
    return artifacts, modules, raw


def safe_source_file(path):
    name = path.name.lower()
    if name in PROMPT_FILES or name in {"local.properties", "manifest.sha256", "sha256sums"}:
        return False
    if name.startswith((".env", ".secret")) or re.search(r"secret|cookie|credential|private[_-]?key|keystore|signing", name):
        return False
    if name.endswith((".zip", ".cs3", ".log", ".bak", ".tmp", ".pyc", ".pyo", ".pem",
                      ".key", ".p12", ".pfx", ".jks", ".keystore", ".crt", ".cer")):
        return False
    return True


def safe_directory(name):
    lowered = name.lower()
    return (lowered not in EXCLUDED_DIRS
            and lowered not in {"keys", "secrets", "credentials"}
            and not lowered.startswith((".env", ".secret")))


def source_payload(root, modules, output):
    """Copy source trees, never local tooling state or arbitrary root archives."""
    payload = {}
    # Auto-discovered modules include disabled/unregistered source modules too.
    source_dirs = SOURCE_DIRS | modules | {
        p.name for p in root.iterdir()
        if p.is_dir() and not is_link(p) and (p / "build.gradle.kts").is_file()
    }
    for current, dirs, files in os.walk(root, followlinks=False):
        current = Path(current)
        dirs[:] = sorted(d for d in dirs if (
            safe_directory(d)
            and not is_link(current / d)
            and (current / d) != output
            and not d.lower().startswith(("cloudstream-trplus-final", "finaloutput"))
            and (current != root or d in source_dirs)
        ))
        for filename in sorted(files):
            path = current / filename
            if is_link(path) or not safe_source_file(path):
                continue
            if current == root and not (
                filename in {".gitignore", ".gitattributes", "gradlew", "gradlew.bat", "LICENSE"}
                or filename.upper().startswith(("LICENSE", "NOTICE", "COPYING", "AUTHORS", "ATTRIBUTION"))
                or path.suffix.lower() in {".md", ".kts", ".properties", ".lockfile", ".json"}
            ):
                continue
            payload[path.relative_to(root).as_posix()] = path.read_bytes()
    for required in ("README.md", "LICENSE", "gradlew", "gradlew.bat", "settings.gradle.kts",
                     "build.gradle.kts", "gradle/wrapper/gradle-wrapper.jar"):
        require(required in payload, f"Missing source distribution input: {required}")
    return payload


def report_payload(root):
    """Only bounded human summaries; XML is reduced to test counts, never raw logs."""
    payload = {}
    reports = root / "reports"
    if not reports.is_dir() or is_link(reports):
        return payload
    for current, dirs, files in os.walk(reports, followlinks=False):
        current = Path(current)
        dirs[:] = sorted(d for d in dirs if safe_directory(d)
                         and not is_link(current / d))
        for filename in sorted(files):
            path = current / filename
            if is_link(path) or not safe_source_file(path) or path.stat().st_size > 256 * 1024:
                continue
            relative = path.relative_to(reports).as_posix()
            if re.search(r"(?:^|[_-])(?:raw|cookies?|headers?|network)(?:[_-]|\.)", filename, re.I):
                continue
            if path.suffix.lower() == ".md" or (
                path.suffix.lower() == ".txt" and "summary" in filename.lower()
            ):
                lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
                # Reports are evidence, not an excuse to distribute captured credentials.
                lines = ["[sensitive line omitted]" if SENSITIVE.search(line) else
                         re.sub(r"(https?://[^\s?]+)\?[^\s)]+", r"\1?[query omitted]", line)
                         for line in lines]
                payload[relative] = ("\n".join(lines) + "\n").encode("utf-8")
            elif path.suffix.lower() == ".xml" and (
                filename.startswith("TEST-") or "junit" in filename.lower() or "pytest" in filename.lower()
            ):
                try:
                    document = ET.fromstring(path.read_bytes())
                    suites = [node for node in document.iter() if node.tag == "testsuite"]
                    counts = [{key: int(node.get(key, "0")) if node.get(key, "0").isdigit() else 0 for key in
                               ("tests", "failures", "errors", "skipped")} for node in suites]
                    payload[relative + ".summary.json"] = json_bytes({"testSuites": counts})
                except ET.ParseError:
                    continue
    return payload


def json_bytes(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def sums(payload):
    return "".join(f"{digest(data)}  {name}\n" for name, data in sorted(payload.items())).encode("utf-8")


def prepare_distribution(root, repository, output="cloudstream-trplus-final", published=None,
                         allow_missing=(), allow_new=()):
    root = Path(root).absolute()
    require(root.is_dir() and not any(is_link(p) for p in (root, *root.parents)),
            "Repository root must be a real directory, not a symlink")
    target = Path(output)
    if not target.is_absolute():
        target = root / target
    require(".." not in target.parts, "Output must not contain parent traversal")
    require(target.is_relative_to(root) and target != root, "Output must be underneath repository root")
    require(not any(is_link(p) for p in (target, *target.parents)), "Symlink output path")
    require(not target.exists(), f"Refusing existing output: {target}")
    require(target.parent.is_dir(), "Output parent must already exist")
    require(target.parts[len(root.parts)].lower() not in EXCLUDED_DIRS | SOURCE_DIRS,
            "Output must not be inside source/build directories")
    artifacts, modules, raw = validate(root, repository)
    release_report = None
    if published is not None:
        try:
            release_report = verify_release_safety(root / "build/plugins.json", published,
                                                   "test", allow_missing, allow_new)
        except ReleaseSafetyError as exc:
            raise DistributionError(str(exc)) from exc
        require(release_report["passed"], "; ".join(release_report["errors"]))
    else:
        require(not allow_missing and not allow_new, "Inventory exceptions require a published baseline")
    require(target.parts[len(root.parts)] not in modules, "Output must not be inside a provider module")
    sources = source_payload(root, modules, target)
    reports = report_payload(root)
    if release_report is not None:
        reports["release-safety.json"] = json_bytes(release_report)
    reports["distribution-status.json"] = json_bytes({
        "published": False, "manualPlayback": "NOT_TESTED",
        "repository": repository, "networkChecked": False,
        "scope": "Local packaging integrity only; not all-contract or playback certification.",
        "plannedPublicUrls": {"repo": f"{raw}/repo.json", "plugins": f"{raw}/plugins.json",
                              "artifacts": [f"{raw}/{n}" for n in sorted(artifacts) if n.endswith(".cs3")]},
        "artifactCount": sum(n.endswith(".cs3") for n in artifacts),
        "releaseSafetyChecked": release_report is not None,
    })
    # Keep branch-relative README links usable, using only the same filtered evidence.
    sources.update({f"reports/{name}": data for name, data in reports.items()})
    packages = {"main": sources, "builds": artifacts, "reports": reports}
    # Everything above is read-only: bad inputs never leave a partial output directory.
    target.mkdir()
    root_payload = {}
    for directory, payload in packages.items():
        payload["SHA256SUMS"] = sums(payload)
        for name, data in sorted(payload.items()):
            destination = target / directory / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
            root_payload[f"{directory}/{name}"] = data
        if directory in {"main", "builds"}:
            zip_name = f"cloudstream-trplus-{directory}.zip"
            with zipfile.ZipFile(target / zip_name, "x", compression=zipfile.ZIP_DEFLATED) as archive:
                for name, data in sorted(payload.items()):
                    info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
                    mode = 0o755 if name == "gradlew" else 0o644
                    info.external_attr = (0o100000 | mode) << 16
                    info.compress_type = zipfile.ZIP_DEFLATED
                    archive.writestr(info, data)
            root_payload[zip_name] = (target / zip_name).read_bytes()
    (target / "SHA256SUMS").write_bytes(sums(root_payload))
    return target


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--repository", required=True, help="Exact planned owner/repo; no publication occurs")
    parser.add_argument("--output", default="cloudstream-trplus-final")
    parser.add_argument("--published", type=Path, required=True,
                        help="Frozen published plugins.json baseline (read-only; required)")
    parser.add_argument("--allow-missing", nargs="+", default=[],
                        help="Explicit missing IDs for this unpublished test candidate only")
    parser.add_argument("--allow-new", nargs="+", default=[])
    args = parser.parse_args(argv)
    try:
        output = prepare_distribution(args.root, args.repository, args.output, args.published,
                                      args.allow_missing, args.allow_new)
    except (DistributionError, OSError) as exc:
        print(f"Distribution refused: {exc}", file=sys.stderr)
        return 1
    print(f"Local distribution prepared: {output} (published=false; manualPlayback=NOT_TESTED)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
