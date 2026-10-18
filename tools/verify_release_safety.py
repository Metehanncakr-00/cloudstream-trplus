"""Read-only release checks against an explicit, frozen published plugins.json.

No network or source/artifact edits. Test-only inventory exceptions never waive
version/hash checks and are recorded in the JSON report.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


class ReleaseSafetyError(ValueError):
    """Invalid baseline, candidate, or exception policy."""


SAFE_NAME = re.compile(r"[A-Za-z_][A-Za-z0-9_-]*\Z")
HASH = re.compile(r"sha256-[0-9a-f]{64}\Z")


def manifest(path):
    data = Path(path).read_bytes()
    try:
        entries = json.loads(data)
    except (ValueError, UnicodeError) as exc:
        raise ReleaseSafetyError(f"Invalid JSON: {path}") from exc
    if not isinstance(entries, list) or not entries:
        raise ReleaseSafetyError(f"Expected nonempty plugin array: {path}")
    indexed = {}
    for entry in entries:
        if not isinstance(entry, dict):
            raise ReleaseSafetyError(f"Invalid plugin entry: {path}")
        name = entry.get("internalName")
        if not isinstance(name, str) or not SAFE_NAME.fullmatch(name):
            raise ReleaseSafetyError(f"Invalid internalName: {path}")
        if name in indexed:
            raise ReleaseSafetyError(f"Duplicate internalName: {name} in {path}")
        if type(entry.get("version")) is not int or entry["version"] <= 0:
            raise ReleaseSafetyError(f"Invalid version: {name} in {path}")
        if not isinstance(entry.get("fileHash"), str) or not HASH.fullmatch(entry["fileHash"]):
            raise ReleaseSafetyError(f"Invalid fileHash: {name} in {path}")
        indexed[name] = entry
    return indexed, hashlib.sha256(data).hexdigest()


def verify_release_safety(local, published, mode="release", allow_missing=(), allow_new=()):
    if mode not in {"release", "test"}:
        raise ReleaseSafetyError("Mode must be release or test")
    if allow_missing and mode != "test":
        raise ReleaseSafetyError("--allow-missing requires --mode test; release omissions are forbidden")
    for name in (*allow_missing, *allow_new):
        if not isinstance(name, str) or not SAFE_NAME.fullmatch(name):
            raise ReleaseSafetyError("Invalid inventory exception internalName")
    candidate, local_hash = manifest(local)
    baseline, published_hash = manifest(published)
    missing = sorted(baseline.keys() - candidate.keys())
    added = sorted(candidate.keys() - baseline.keys())
    errors = []
    for name in missing:
        if name not in allow_missing:
            errors.append(f"Removed published ID: {name}")
    for name in added:
        if name not in allow_new:
            errors.append(f"New ID requires --allow-new: {name}")
    for name in sorted(candidate.keys() & baseline.keys()):
        current, previous = candidate[name], baseline[name]
        if current["version"] < previous["version"]:
            errors.append(f"Version regression: {name} {current['version']} < {previous['version']}")
        elif current["version"] == previous["version"] and current["fileHash"] != previous["fileHash"]:
            errors.append(f"Changed bytes require version bump: {name} version {current['version']}")
    return {
        "passed": not errors, "mode": mode, "published": False,
        "localManifestSha256": local_hash, "publishedManifestSha256": published_hash,
        "localCount": len(candidate), "publishedCount": len(baseline),
        "missingIds": missing, "newIds": added,
        "allowedMissingIds": sorted(set(allow_missing)), "allowedNewIds": sorted(set(allow_new)),
        "releaseInventoryComplete": not missing, "errors": errors,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--local", required=True, type=Path)
    parser.add_argument("--published", required=True, type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--mode", choices=("release", "test"), default="release")
    parser.add_argument("--allow-missing", nargs="+", default=[])
    parser.add_argument("--allow-new", nargs="+", default=[])
    args = parser.parse_args(argv)
    try:
        if args.report and args.report.resolve() in {args.local.resolve(), args.published.resolve()}:
            raise ReleaseSafetyError("Report must not overwrite an input manifest")
        report = verify_release_safety(args.local, args.published, args.mode,
                                       args.allow_missing, args.allow_new)
        text = json.dumps(report, indent=2) + "\n"
        if args.report:
            args.report.write_text(text, encoding="utf-8")
        print(text, end="")
        return 0 if report["passed"] else 1
    except (ReleaseSafetyError, OSError) as exc:
        print(f"Release safety refused: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
