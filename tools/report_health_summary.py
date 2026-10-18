#!/usr/bin/env python3
"""
report_health_summary.py

Generates Markdown summary table of provider health results:
- Formats L0-L5 status matrix
- Writes to GitHub Step Summary if $GITHUB_STEP_SUMMARY is set
- Prints table to stdout
"""

import os
import sys
import json
from datetime import datetime, timezone

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

def format_cell(val):
    if not val:
        return "UNTESTED"
    val_str = str(val).lower()
    if val_str == "pass":
        return "PASS"
    elif val_str in ("skipped", "untested"):
        return "SKIPPED"
    elif "player_discovered" in val_str:
        return "PLAYER_DISCOVERED"
    elif "player_not_found" in val_str:
        return "PLAYER_NOT_FOUND"
    elif "drift_detected" in val_str:
        return "DRIFT_DETECTED"
    elif "cloudflare" in val_str:
        return "CLOUDFLARE"
    elif "automation_blocked" in val_str:
        return "BLOCKED"
    elif "untrusted_redirect" in val_str:
        return "UNTRUSTED_REDIRECT"
    elif "fail" in val_str:
        return "FAIL"
    else:
        return str(val).upper()

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    report_file = os.path.join(repo_root, "reports", "provider-health.json")

    if not os.path.exists(report_file):
        print("Error: reports/provider-health.json not found!", file=sys.stderr)
        sys.exit(1)

    with open(report_file, "r", encoding="utf-8") as f:
        results = json.load(f)

    now_str = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    md_lines = [
        "# CloudStreamHub — Provider Health Status Matrix",
        f"Checked at: {now_str}\n",
        "| Provider | L0 Config | L1 Domain | L2 Homepage | L3 Search | L4 Load | L5 Player Discovery | Overall Status |",
        "| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |"
    ]

    for r in results:
        p_name = r["provider"]
        tiers = r["tierResults"]
        l0 = format_cell(tiers.get("L0_config"))
        l1 = format_cell(tiers.get("L1_domain"))
        l2 = format_cell(tiers.get("L2_homepage"))
        l3 = format_cell(tiers.get("L3_search"))
        l4 = format_cell(tiers.get("L4_load"))
        l5 = format_cell(tiers.get("L5_player_discovery") or tiers.get("L5_playback"))

        overall = r.get("overallStatus", "unknown").upper()
        md_lines.append(f"| **{p_name}** | {l0} | {l1} | {l2} | {l3} | {l4} | {l5} | {overall} |")

    md_text = "\n".join(md_lines) + "\n"
    print(md_text)

    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as f:
            f.write("\n" + md_text + "\n")
        print(f"Appended health matrix to GITHUB_STEP_SUMMARY ({step_summary})")

if __name__ == "__main__":
    main()
