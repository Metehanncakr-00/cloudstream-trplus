#!/usr/bin/env python3
"""
manage_health_issues.py

Automates GitHub Issue creation, deduplication, lifecycle management, and closing:
- Checks reports/provider-health.json, reports/domain-health.json, and config/
- Interacts with GitHub REST API using $GITHUB_TOKEN and $GITHUB_REPOSITORY
- Dedup: Identifies issues by provider and problem type, across monitor sources
- Inactive providers: Suppresses churn without claiming recovery
- Recovery: Closes only the specific automated health tier proven recovered
- Updates: Comments only when material evidence changes
"""

import os
import sys
import json
import hashlib
import re
import urllib.request
import urllib.error
from datetime import datetime, timezone

GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
GITHUB_REPOSITORY = os.environ.get("GITHUB_REPOSITORY")
SERVER_URL = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
RUN_ID = os.environ.get("GITHUB_RUN_ID", "")

def github_api(endpoint, method="GET", data=None):
    if not GITHUB_TOKEN or not GITHUB_REPOSITORY:
        return None
    url = f"https://api.github.com/repos/{GITHUB_REPOSITORY}{endpoint}"
    headers = {
        "Authorization": f"token {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "CloudStreamHub-Health-Bot"
    }
    encoded_data = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=encoded_data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print(f"[API Error] {method} {url} -> HTTP {e.code}: {e.read().decode('utf-8', errors='ignore')}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"[API Error] {method} {url} -> {e}", file=sys.stderr)
        return None

def get_open_issues():
    issues = []
    page = 1
    while True:
        batch = github_api(f"/issues?state=open&per_page=100&page={page}")
        if batch is None:
            return None
        issues.extend(i for i in batch if "pull_request" not in i)
        if len(batch) < 100:
            return issues
        page += 1

def create_issue(title, body, labels):
    print(f"[Issue] Creating issue: '{title}' with labels: {labels}")
    data = {"title": title, "body": body, "labels": labels}
    return github_api("/issues", method="POST", data=data)

def add_comment(issue_number, comment):
    print(f"[Issue] Adding comment to #{issue_number}")
    data = {"body": comment}
    return github_api(f"/issues/{issue_number}/comments", method="POST", data=data)

def close_issue(issue_number, reason="Provider health checks passed"):
    print(f"[Issue] Closing issue #{issue_number}: {reason}")
    data = {"state": "closed"}
    result = github_api(f"/issues/{issue_number}", method="PATCH", data=data)
    if result is not None:
        add_comment(issue_number, f"### Resolution Update\n\n{reason}\n\nAuto-closed issue at {datetime.now(timezone.utc).isoformat()}.")
    return result

def find_provider_for_issue(issue, provider_names):
    labels = [lbl.get("name", "") for lbl in issue.get("labels", [])]
    for p in provider_names:
        if p in labels:
            return p
    title = issue.get("title", "")
    for p in provider_names:
        if f" {p} " in f" {title} " or f"[{p}]" in title or f"{p}—" in title:
            return p
    return None

def issue_labels(issue):
    return {label.get("name", "") for label in issue.get("labels", [])}


def issue_metadata(issue):
    match = re.search(r"<!-- health-state: (.*?) -->", issue.get("body") or "")
    if match:
        try:
            value = json.loads(match.group(1))
            return value if isinstance(value, dict) else {}
        except (ValueError, TypeError):
            pass
    return {}


def problem_type(issue):
    labels = issue_labels(issue)
    # Restrict legacy recognition to this monitor's issues; never match user playback bugs.
    if not labels.intersection({"domain-watch", "provider-health", "untrusted-redirect", "selector-drift"}):
        return None
    metadata = issue_metadata(issue)
    if metadata.get("problem_type"):
        return metadata["problem_type"]
    title = issue.get("title", "")
    if "untrusted-redirect" in labels or title.startswith("[Security Alert]") or title.startswith("[Domain Watch]"):
        return "untrusted_redirect"
    for tier in ("L0_config", "L1_domain"):
        if f"{tier} regression" in title:
            return tier
    if "selector-drift" in labels:
        return "selector_drift"
    return None


def latest_issue_metadata(issue, kind):
    metadata = issue_metadata(issue)
    page = 1
    while True:
        comments = github_api(f"/issues/{issue['number']}/comments?per_page=100&page={page}")
        if comments is None:
            return None
        for comment in comments:
            state = issue_metadata(comment)
            if state.get("problem_type") == kind and state.get("fingerprint"):
                metadata = state
        if len(comments) < 100:
            return metadata
        page += 1


def recorded_tiers(issue, metadata):
    if metadata.get("tiers"):
        return metadata["tiers"]
    kind = problem_type(issue)
    if kind in ("L0_config", "L1_domain"):
        return [kind]
    if kind == "selector_drift":
        # Legacy issues record drifted tiers on this specific line.
        match = re.search(r"\*\*Drifted Tier\(s\):\*\* ([^\n]+)", issue.get("body") or "")
        return re.findall(r"L[0-5]_[a-z_]+", match.group(1)) if match else []
    return []


def update_problem(issues, provider, kind, title, body, labels, evidence, tiers=None):
    fingerprint = hashlib.sha256(json.dumps(evidence, sort_keys=True, ensure_ascii=True).encode()).hexdigest()
    existing = next((i for i in issues if find_provider_for_issue(i, [provider]) == provider and problem_type(i) == kind), None)
    latest = {}
    pending_tiers = set(tiers or [])
    if existing is not None:
        # Read all comments: an earlier matching state must not suppress a later A -> B -> A change.
        latest = latest_issue_metadata(existing, kind)
        if latest is None:
            return  # Fail closed when we cannot establish the previous state.
        current = evidence.get("provider", {}).get("tierResults", {})
        pending_tiers.update(t for t in recorded_tiers(existing, latest)
                             if current.get(t) not in ("pass", "player_discovered"))
    metadata = {"provider": provider, "problem_type": kind, "fingerprint": fingerprint, "tiers": sorted(pending_tiers)}
    marker = "<!-- health-state: " + json.dumps(metadata, sort_keys=True) + " -->"
    if existing is None:
        created = create_issue(title, body + "\n\n" + marker, labels)
        if created:
            issues.append(created)
        return
    if latest.get("fingerprint") != fingerprint or latest.get("tiers", []) != metadata["tiers"]:
        add_comment(existing["number"], body + "\n\n" + marker)


def main():
    if not GITHUB_TOKEN or not GITHUB_REPOSITORY:
        print("[Issue Manager] GITHUB_TOKEN or GITHUB_REPOSITORY not set. Running in dry-run mode.")
        return

    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    def load(relative, default):
        path = os.path.join(repo_root, relative)
        if not os.path.exists(path):
            return default
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)

    providers_cfg = {p["name"]: p for p in load("config/providers.json", {}).get("providers", [])}
    domains_cfg = load("config/domains.json", {}).get("providers", {})
    prov_reports = load("reports/provider-health.json", [])
    dom_reports = load("reports/domain-health.json", [])
    names = sorted(set(providers_cfg) | set(domains_cfg))
    workflow_url = f"{SERVER_URL}/{GITHUB_REPOSITORY}/actions/runs/{RUN_ID}" if RUN_ID else "N/A"
    issues = get_open_issues()
    if issues is None:
        return  # Do not create duplicates when fetching existing issues failed.

    def inactive(name):
        return not providers_cfg.get(name, {}).get("enabled", True) or domains_cfg.get(name, {}).get("status") == "dead"

    reports = {p.get("provider"): p for p in prov_reports}
    domains = {d.get("provider"): d for d in dom_reports}
    closed = set()
    for issue in issues:
        name = find_provider_for_issue(issue, names)
        if not name or inactive(name):
            continue
        # L0-L5 evidence cannot resolve security, playback or unrecognized issues.
        kind = problem_type(issue)
        if kind not in ("L0_config", "L1_domain", "selector_drift") or issue_labels(issue).intersection({"security", "playback"}):
            continue
        metadata = latest_issue_metadata(issue, kind)
        if metadata is None:
            continue
        tiers = recorded_tiers(issue, metadata)
        current = reports.get(name, {}).get("tierResults", {})
        if tiers and all(current.get(t) in ("pass", "player_discovered") for t in tiers):
            # A conflicting domain report is not recovery evidence for L1.
            if "L1_domain" in tiers and name in domains and domains[name].get("status") != "healthy":
                continue
            if close_issue(issue["number"], f"Provider `{name}` recovered the originally failing tier(s): {', '.join(tiers)}. Playback remains unverified.") is not None:
                closed.add(issue["number"])
    issues = [i for i in issues if i["number"] not in closed]

    for name in sorted(set(reports) | set(domains)):
        if not name or inactive(name):
            continue
        report = reports.get(name, {})
        domain = domains.get(name, {})
        tiers = report.get("tierResults", {})
        # Keep only stable evidence; checkedAt, durationMs and workflow run IDs are presentation data.
        fields = ("canonical", "finalUrl", "candidateHost", "tierResults", "diagnostic", "status", "httpsValid", "hostAllowed", "markerFound", "redirectChain", "error")
        evidence = {source: {k: data[k] for k in fields if k in data} for source, data in (("provider", report), ("domain", domain))}
        details = json.dumps(evidence, indent=2, sort_keys=True, ensure_ascii=True)
        body = f"**Provider:** {name}\n\n```json\n{details}\n```\n\nWorkflow Run: {workflow_url}"
        labels = ["provider-health", "automated", name]
        if tiers.get("L1_domain") == "untrusted_redirect" or domain.get("status") == "untrusted_redirect" or domain.get("candidateHost"):
            candidate = report.get("candidateHost") or domain.get("candidateHost")
            update_problem(issues, name, "untrusted_redirect", f"[Security Alert] {name} — untrusted redirect to {candidate}", body + "\n\nManual review required before changing allowed domains.", labels + ["domain-watch", "untrusted-redirect", "security"], evidence)
        for tier in ("L0_config", "L1_domain"):
            status = tiers.get(tier, "")
            if status.startswith("fail") or status in ("config_error", "content_marker_mismatch"):
                update_problem(issues, name, tier, f"[Provider Health] {name} — {tier} regression", body, labels + ["critical"], evidence, [tier])
        drift_tiers = sorted(k for k, v in tiers.items() if v == "drift_detected")
        if drift_tiers:
            update_problem(issues, name, "selector_drift", f"[Selector Drift] {name} — upstream DOM mutation detected", body, labels + ["selector-drift"], evidence, drift_tiers)


if __name__ == "__main__":
    main()
