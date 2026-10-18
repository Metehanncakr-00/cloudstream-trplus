#!/usr/bin/env python3
"""Report upstream/fork patch candidates without changing the target repository."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import tempfile
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any

DEFAULT_UPSTREAM = "https://github.com/Kraptor123/cs-kraptor.git"
DEFAULT_REFERENCE = "https://github.com/etumen/cs-kraptor.git"
DEFAULT_FORK_REPOSITORY = "Kraptor123/cs-kraptor"


def git(repo: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *args], text=True, capture_output=True, check=False
    )
    if check and result.returncode:
        raise RuntimeError(result.stderr.strip() or f"git {' '.join(args)} failed")
    return result.stdout.strip()


def patch_id(repo: Path, commit: str) -> str | None:
    patch = subprocess.run(
        ["git", "-C", str(repo), "show", "--pretty=format:", "--no-ext-diff", commit],
        text=True,
        capture_output=True,
        check=True,
    ).stdout
    if not patch.strip():
        return None
    result = subprocess.run(
        ["git", "patch-id", "--stable"], input=patch, text=True, capture_output=True, check=True
    ).stdout.strip()
    return result.split()[0] if result else None


def commit_paths(repo: Path, commit: str) -> list[str]:
    output = git(repo, "diff-tree", "--no-commit-id", "--name-only", "-r", commit)
    return sorted(line for line in output.splitlines() if line)


def compare_source(target: Path, source: Path, name: str, max_commits: int) -> dict[str, Any]:
    target_head = git(target, "rev-parse", "HEAD")
    source_head = git(source, "rev-parse", "HEAD")
    target_commits = git(target, "rev-list", "--no-merges", target_head, "--max-count=5000").splitlines()
    target_history = set(git(target, "rev-list", target_head, "--max-count=5000").splitlines())
    source_history = git(source, "rev-list", source_head).splitlines()
    # The checkouts are independent clones, so identify the nearest shared commit by history
    # intersection instead of requiring the target object to exist in the source clone.
    merge_base = next((commit for commit in source_history if commit in target_history), None)
    target_ids: dict[str, str] = {}
    for commit in target_commits:
        identifier = patch_id(target, commit)
        if identifier:
            target_ids.setdefault(identifier, commit)

    if merge_base:
        candidates = git(
            source, "rev-list", "--no-merges", source_head, f"^{merge_base}", f"--max-count={max_commits}"
        ).splitlines()
    else:
        candidates = git(
            source, "rev-list", "--no-merges", source_head, f"--max-count={max_commits}"
        ).splitlines()

    commits = []
    for commit in candidates:
        identifier = patch_id(source, commit)
        commits.append({
            "sha": commit,
            "patchId": identifier,
            "subject": git(source, "show", "-s", "--format=%s", commit),
            "paths": commit_paths(source, commit),
            "presentInTarget": bool(identifier and identifier in target_ids),
            "matchingTargetCommit": target_ids.get(identifier) if identifier else None,
        })
    return {
        "name": name,
        "sourceHead": source_head,
        "targetHead": target_head,
        "mergeBase": merge_base,
        "comparison": "merge-base" if merge_base else "patch-id-only-no-common-base",
        "commits": commits,
    }


def discover_forks(repository: str) -> tuple[list[tuple[str, str]], str | None]:
    forks: list[tuple[str, str]] = []
    page = 1
    try:
        while True:
            url = f"https://api.github.com/repos/{repository}/forks?per_page=100&page={page}"
            request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json", "User-Agent": "CloudStreamHub-upstream-report"})
            with urllib.request.urlopen(request, timeout=20) as response:
                entries = json.load(response)
            if not entries:
                break
            forks.extend((entry["full_name"], entry["clone_url"]) for entry in entries if entry.get("clone_url"))
            if len(entries) < 100:
                break
            page += 1
    except (OSError, urllib.error.URLError, json.JSONDecodeError) as error:
        return forks, f"Fork discovery failed for {repository}: {error}"
    return forks, None


def analyze(target: Path, sources: list[tuple[str, Path | None, str | None]], max_commits: int) -> dict[str, Any]:
    results = []
    for name, path, error in sources:
        if error or path is None:
            results.append({"name": name, "error": error or "Source unavailable"})
            continue
        try:
            results.append(compare_source(target, path, name, max_commits))
        except (RuntimeError, subprocess.CalledProcessError) as failure:
            results.append({"name": name, "error": str(failure)})

    patch_sources: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for source in results:
        for commit in source.get("commits", []):
            if commit.get("patchId"):
                patch_sources[commit["patchId"]].append((source["name"], commit["sha"]))
    duplicate_patches = [
        {"patchId": patch, "candidates": [{"source": name, "sha": sha} for name, sha in candidates]}
        for patch, candidates in sorted(patch_sources.items()) if len(candidates) > 1
    ]
    return {"targetHead": git(target, "rev-parse", "HEAD"), "sources": results, "duplicatePatches": duplicate_patches}


def render_markdown(report: dict[str, Any]) -> str:
    lines = ["# Upstream and fork patch intelligence", "", f"Target SHA: `{report['targetHead']}`", ""]
    for warning in report.get("warnings", []):
        lines.append(f"Discovery warning: {warning}")
    if report.get("warnings"):
        lines.append("")
    for source in report["sources"]:
        lines.extend([f"## {source['name']}", ""])
        if source.get("error"):
            lines.extend([f"Unavailable: {source['error']}", ""])
            continue
        lines.extend([
            f"Source SHA: `{source['sourceHead']}`  ",
            f"Merge base: `{source['mergeBase'] or 'none'}`  ",
            f"Comparison: `{source['comparison']}`", "",
        ])
        if not source["commits"]:
            lines.extend(["No unique non-merge commits found in the comparison window.", ""])
        for commit in source["commits"]:
            status = "already present" if commit["presentInTarget"] else "candidate"
            paths = ", ".join(f"`{path}`" for path in commit["paths"]) or "(no changed paths)"
            lines.append(f"- `{commit['sha'][:12]}` ({status}; patch `{commit['patchId'] or 'none'}`) — {commit['subject']}; paths: {paths}")
        lines.append("")
    lines.extend(["## Equivalent patch candidates", ""])
    if report["duplicatePatches"]:
        for duplicate in report["duplicatePatches"]:
            candidates = ", ".join(f"{item['source']}@{item['sha'][:12]}" for item in duplicate["candidates"])
            lines.append(f"- `{duplicate['patchId']}`: {candidates}")
    else:
        lines.append("No equivalent patch IDs found among reported source candidates.")
    lines.append("")
    return "\n".join(lines)


def clone_source(name: str, url: str, destination: Path) -> tuple[str, Path | None, str | None]:
    result = subprocess.run(["git", "clone", "--bare", "--filter=blob:none", url, str(destination)], text=True, capture_output=True)
    if result.returncode:
        return name, None, result.stderr.strip() or "git clone failed"
    # Bare repositories support -C and HEAD just like worktrees.
    return name, destination, None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", type=Path, default=Path("."), help="target checkout with complete history")
    parser.add_argument("--upstream", default=DEFAULT_UPSTREAM)
    parser.add_argument("--reference", default=DEFAULT_REFERENCE)
    parser.add_argument("--discover-forks", default=DEFAULT_FORK_REPOSITORY)
    parser.add_argument("--max-commits", type=int, default=200)
    parser.add_argument("--output-dir", type=Path, default=Path("reports/upstream-intelligence"))
    args = parser.parse_args()
    target = args.target.resolve()
    sources: list[tuple[str, Path | None, str | None]] = []
    warnings = []
    with tempfile.TemporaryDirectory(prefix="cloudstreamhub-upstream-") as temp:
        temp_path = Path(temp)
        urls = [("Kraptor123/cs-kraptor (upstream)", args.upstream), ("etumen/cs-kraptor (reference)", args.reference)]
        forks, warning = discover_forks(args.discover_forks)
        if warning:
            warnings.append(warning)
        urls.extend((f"fork:{name}", url) for name, url in forks)
        for index, (name, url) in enumerate(urls):
            sources.append(clone_source(name, url, temp_path / f"source-{index}.git"))
        report = analyze(target, sources, max(1, args.max_commits))
        report["warnings"] = warnings

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output_dir / "report.md").write_text(render_markdown(report), encoding="utf-8")
    print(f"Wrote upstream intelligence reports to {args.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
