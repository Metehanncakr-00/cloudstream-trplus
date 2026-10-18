#!/usr/bin/env python3
"""
verify_version_bumps.py

CI Invariant Guard:
- If `core/**` is modified: ALL active provider plugins must have bumped versions.
- If `<Provider>/**` is modified: that specific provider's `build.gradle.kts` must have a bumped version.
- Prevents the upstream CloudStream update failure where code is updated but clients never receive it.
"""

import os
import sys
import re
import argparse
import subprocess
from typing import Dict, List, Set, Tuple

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSION_REGEX = re.compile(r'version\s*=\s*(\d+)')

def get_active_providers() -> List[str]:
    providers = []
    for item in sorted(os.listdir(REPO_ROOT)):
        full = os.path.join(REPO_ROOT, item)
        if os.path.isdir(full) and os.path.isfile(os.path.join(full, "build.gradle.kts")):
            providers.append(item)
    return providers

def get_file_version_from_git(ref: str, provider: str) -> int:
    try:
        cmd = ["git", "show", f"{ref}:{provider}/build.gradle.kts"]
        p = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True)
        if p.returncode == 0:
            m = VERSION_REGEX.search(p.stdout)
            if m:
                return int(m.group(1))
    except Exception:
        pass
    return -1

def get_current_provider_version(provider: str) -> int:
    path = os.path.join(REPO_ROOT, provider, "build.gradle.kts")
    if os.path.isfile(path):
        with open(path, "r", encoding="utf-8-sig") as f:
            m = VERSION_REGEX.search(f.read())
            if m:
                return int(m.group(1))
    return -1

def get_changed_files(base_ref: str) -> List[str]:
    try:
        # First check unstaged/staged vs base_ref
        cmd = ["git", "diff", "--name-only", base_ref]
        p = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True)
        if p.returncode == 0:
            return [line.strip().replace("\\", "/") for line in p.stdout.splitlines() if line.strip()]
    except Exception:
        pass
    return []

def verify_version_bumps(base_ref: str = None) -> Tuple[bool, List[str]]:
    # Resolve base_ref (handle GitHub zero-SHA "0000000000000000000000000000000000000000")
    if base_ref and set(base_ref) == {'0'}:
        base_ref = None

    if base_ref:
        # Verify if passed base_ref exists in git
        p = subprocess.run(["git", "rev-parse", "--verify", base_ref], cwd=REPO_ROOT, capture_output=True)
        if p.returncode != 0:
            print(f"[WARN] Requested base '{base_ref}' not found in git history, falling back to candidates.")
            base_ref = None

    if not base_ref:
        for candidate in ["HEAD^", "HEAD~1", "origin/main", "HEAD"]:
            p = subprocess.run(["git", "rev-parse", "--verify", candidate], cwd=REPO_ROOT, capture_output=True)
            if p.returncode == 0:
                base_ref = candidate
                break

    if not base_ref:
        return True, ["No git history found to compare versions against; skipping bump check."]

    changed_files = get_changed_files(base_ref)
    if not changed_files:
        return True, [f"No changes detected compared to {base_ref}."]

    providers = get_active_providers()
    core_changed = any(f.startswith("core/") for f in changed_files)
    
    errors = []
    details = []

    if core_changed:
        details.append(f"core/** was modified against {base_ref}. Enforcing version bump across all {len(providers)} providers.")
        for p in providers:
            base_ver = get_file_version_from_git(base_ref, p)
            curr_ver = get_current_provider_version(p)
            if base_ver > 0 and curr_ver <= base_ver:
                errors.append(f"Provider '{p}' MUST bump version because core/ changed (base: {base_ver}, current: {curr_ver})")
    else:
        for p in providers:
            provider_changed = any(f.startswith(f"{p}/") and not f.endswith("build.gradle.kts") for f in changed_files)
            if provider_changed:
                base_ver = get_file_version_from_git(base_ref, p)
                curr_ver = get_current_provider_version(p)
                if base_ver > 0 and curr_ver <= base_ver:
                    errors.append(f"Provider '{p}' code changed but version was not bumped (base: {base_ver}, current: {curr_ver})")

    if errors:
        return False, errors
    return True, details + ["All affected provider versions were successfully bumped!"]

def main():
    parser = argparse.ArgumentParser(description="Verify that plugin versions are bumped when code changes.")
    parser.add_argument("--base", help="Git reference to compare against (default: origin/main or HEAD~1)")
    args = parser.parse_args()

    success, messages = verify_version_bumps(args.base)
    for msg in messages:
        print(f"[{'ERROR' if not success else 'INFO'}] {msg}")

    if not success:
        sys.exit(1)

if __name__ == "__main__":
    main()
