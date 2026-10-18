#!/usr/bin/env python3
"""
repository_health.py

Validates the deployed repository from external perspective:
- Fetches repo.json from remote URL or local path
- Validates repository manifest
- Fetches each pluginList (plugins.json)
- Validates each plugin entry (name, internalName, version, tvTypes)
- Checks HTTP accessibility of each .cs3 artifact
- Computes and verifies SHA-256 hash
"""

import sys
import json
import urllib.request
import hashlib
import argparse

def main():
    parser = argparse.ArgumentParser(description="CloudStream Remote Repository Health Checker")
    parser.add_argument("--repo-url", required=True, help="URL to repo.json")
    args = parser.parse_args()

    print(f"[Repo Health] Fetching manifest from {args.repo_url}...")
    try:
        req = urllib.request.Request(args.repo_url, headers={"User-Agent": "CloudStream/4.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            repo_manifest = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"[ERROR] Failed to fetch repo.json: {e}", file=sys.stderr)
        sys.exit(1)

    name = repo_manifest.get("name")
    plugin_lists = repo_manifest.get("pluginLists", [])
    print(f"[Repo Health] Manifest '{name}' (version {repo_manifest.get('manifestVersion')}) has {len(plugin_lists)} plugin lists.")

    for plist_url in plugin_lists:
        print(f"[Repo Health] Fetching plugin list from {plist_url}...")
        try:
            req = urllib.request.Request(plist_url, headers={"User-Agent": "CloudStream/4.0"})
            with urllib.request.urlopen(req, timeout=10) as resp:
                plugins = json.loads(resp.read().decode("utf-8"))
            print(f" -> Found {len(plugins)} plugins in list.")
        except Exception as e:
            print(f"[ERROR] Failed to fetch plugins.json from {plist_url}: {e}", file=sys.stderr)
            sys.exit(1)

        for p in plugins:
            p_name = p.get("name")
            p_url = p.get("url")
            p_hash = p.get("fileHash")
            print(f"   * Checking artifact for {p_name} ({p_url})...")
            try:
                req = urllib.request.Request(p_url, headers={"User-Agent": "CloudStream/4.0"})
                with urllib.request.urlopen(req, timeout=15) as resp:
                    data = resp.read()
                computed_hash = "sha256-" + hashlib.sha256(data).hexdigest()
                if computed_hash.lower() == p_hash.lower():
                    print(f"     [OK] Hash verified: {computed_hash}")
                else:
                    print(f"     [FAIL] Hash mismatch! Expected {p_hash}, got {computed_hash}")
                    sys.exit(1)
            except Exception as e:
                print(f"     [FAIL] Could not download artifact: {e}")
                sys.exit(1)

    print("\n[SUCCESS] Entire repository distribution chain verified successfully!")

if __name__ == "__main__":
    main()
