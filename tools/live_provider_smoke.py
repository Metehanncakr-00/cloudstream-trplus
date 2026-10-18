#!/usr/bin/env python3
"""
live_provider_smoke.py

Comprehensive live smoke test verifying active CloudStreamHub providers powered by Scrapling:
- Homepage: PASS IFF >= 1 valid content item discovered; 0 items -> FAIL (or DRIFT_DETECTED if candidate found)
- Detail: Rejects soft 404 pages (HTTP 200 with 404/not found in title/body)
- Player Discovery: Evaluates real player containers with episode chaining for TV/Anime
  * Reports PLAYER_DISCOVERED or PLAYER_NOT_FOUND
  * Marks runtimePlayback as UNVERIFIED_BY_AUTOMATION
- Subtitle: Distinguishes VTT/SRT, DUBBED, HARDSUB, or NONE truthfully
- Shared semantic engine with provider_health.py (tools.scraping.discovery)
"""

import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

import re
import json
import time
import argparse
from urllib.parse import urljoin, urlparse
from datetime import datetime, timezone
from bs4 import BeautifulSoup

from tools.scraping import (
    ProviderFetcher,
    FetchMode,
    FetchStatus,
    AdaptiveManager,
    XhrRedactor,
    discover_homepage_cards,
    parse_detail_page,
    evaluate_player_discovery,
    classify_subtitles
)

def test_provider(provider, domains_config, adaptive_mgr):
    name = provider["name"]
    domain_key = provider.get("domainKey", name)
    domain_info = domains_config.get("providers", {}).get(domain_key, {})
    canonical = domain_info.get("canonical")
    allowed_hosts = set(domain_info.get("allowedHosts", []))
    smoke = provider.get("smokeTest", {})
    monitoring_cfg = provider.get("monitoring", {})
    known_detail = smoke.get("knownDetail")

    pref_fetch = FetchMode(monitoring_cfg.get("preferredFetch", "HTTP"))
    dyn_fallback = monitoring_cfg.get("dynamicFallback", True)
    stealth_fallback = monitoring_cfg.get("stealthFallback", True)

    res = {
        "provider": name,
        "module": provider["module"],
        "homepage": {"status": "UNTESTED"},
        "detail": {"status": "UNTESTED"},
        "playerDiscovery": {"status": "UNTESTED"},
        "runtimePlayback": "UNVERIFIED_BY_AUTOMATION",
        "subtitle": "NONE",
        "overall": "UNKNOWN",
        "details": []
    }

    if not canonical:
        res["overall"] = "FAIL"
        res["details"].append("No canonical URL configured")
        return res

    with ProviderFetcher(allowed_hosts=allowed_hosts, canonical=canonical, timeout=15) as fetcher:
        # 1. Homepage Test
        home_res = fetcher.fetch(
            canonical,
            preferred_mode=pref_fetch,
            allow_dynamic_fallback=dyn_fallback,
            allow_stealth_fallback=stealth_fallback
        )
        if home_res.status == FetchStatus.CONFIG_ERROR:
            res["homepage"] = {"status": "CONFIG_ERROR", "error": XhrRedactor.redact_string(str(home_res.error))}
            res["overall"] = "CRITICAL"
            return res
        elif home_res.status == FetchStatus.UNTRUSTED_REDIRECT:
            res["homepage"] = {"status": "UNTRUSTED_REDIRECT", "candidate": XhrRedactor.redact_string(home_res.candidateHost or "")}
            res["overall"] = "CRITICAL"
            return res
        elif home_res.status == FetchStatus.CLOUDFLARE:
            res["homepage"] = {"status": "AUTOMATION_BLOCKED", "reason": "Cloudflare challenge"}
        elif home_res.status == FetchStatus.BLOCKED:
            res["homepage"] = {"status": "AUTOMATION_BLOCKED", "reason": "WAF / 403 Forbidden"}
        elif home_res.statusCode == 200:
            homepage_cfg = monitoring_cfg.get("homepage", {})
            cards = discover_homepage_cards(home_res.body, canonical, homepage_cfg=homepage_cfg)
            if len(cards) > 0:
                res["homepage"] = {"status": "PASS", "itemCount": len(cards), "sample": cards[0]["title"]}
            else:
                # 0 cards: Check adaptive drift
                stat, count, _ = adaptive_mgr.check_css_selector(
                    html_content=home_res.body,
                    selector_str="a.poster, a.mcard",
                    identifier=f"{name}_smoke_home",
                    base_url=canonical
                )
                if stat == "DRIFT_DETECTED" and count > 0:
                    res["homepage"] = {"status": "DRIFT_DETECTED", "itemCount": count}
                else:
                    # ZERO ITEMS IS STRICTLY FAIL (NOT PASS!)
                    res["homepage"] = {"status": "FAIL", "itemCount": 0, "reason": "0 valid content items discovered"}
        else:
            res["homepage"] = {"status": "FAIL", "code": home_res.statusCode}

        # 2. Detail & Player Discovery Test
        if known_detail:
            det_res = fetcher.fetch(
                known_detail,
                preferred_mode=pref_fetch,
                allow_dynamic_fallback=dyn_fallback,
                allow_stealth_fallback=stealth_fallback
            )
            if det_res.status in (FetchStatus.CLOUDFLARE, FetchStatus.BLOCKED):
                res["detail"] = {"status": "AUTOMATION_BLOCKED", "reason": "Cloudflare/WAF challenge"}
                res["playerDiscovery"] = {"status": "AUTOMATION_BLOCKED"}
            else:
                ep_selector = monitoring_cfg.get("playerProbe", {}).get("episodeSelector")
                detail_info = parse_detail_page(det_res.body, det_res.statusCode, known_detail, episode_selector=ep_selector, fetcher=fetcher)
                if detail_info["status"] == "FAIL":
                    if detail_info.get("isSoft404"):
                        res["detail"] = {"status": "FAIL", "title": detail_info.get("title"), "reason": "SOFT_404_PAGE"}
                    else:
                        res["detail"] = {"status": "FAIL", "reason": XhrRedactor.redact_string(str(detail_info.get("reason")))}
                    res["playerDiscovery"] = {"status": "PLAYER_NOT_FOUND"}
                else:
                    res["detail"] = {
                        "status": "PASS",
                        "title": detail_info["title"][:50],
                        "episodeDiscoveryMode": detail_info.get("episodeDiscoveryMode", "NONE"),
                        "episodesCount": len(detail_info.get("episodeLinks", []))
                    }
                    res["subtitle"] = classify_subtitles(det_res.body, detail_info["title"])

                    # Target chaining for player discovery
                    player_probe_cfg = monitoring_cfg.get("playerProbe", {})
                    probe_mode = player_probe_cfg.get("mode", "detail")
                    target_body = det_res.body
                    target_url = known_detail

                    if probe_mode == "episode" and detail_info.get("episodeLinks"):
                        ep_url = detail_info["episodeLinks"][0]
                        try:
                            ep_res = fetcher.fetch(ep_url, preferred_mode=pref_fetch, allow_dynamic_fallback=dyn_fallback)
                            if ep_res.statusCode == 200:
                                target_body = ep_res.body
                                target_url = ep_url
                                ep_sub = classify_subtitles(target_body)
                                if ep_sub != "NONE":
                                    res["subtitle"] = ep_sub
                        except Exception:
                            pass

                    p_stat, p_info = evaluate_player_discovery(target_body, target_url)
                    if p_stat != "PLAYER_DISCOVERED" and fetcher:
                        soup = BeautifulSoup(target_body, "html.parser")
                        btn = soup.select_one("button[onclick*='videosec'], a[onclick*='videosec']")
                        if btn:
                            m = re.search(r"'(ajax/videosec[^']+)'", btn.get("onclick", ""))
                            if m:
                                parsed_t = urlparse(target_url)
                                root_t = f"{parsed_t.scheme}://{parsed_t.netloc}/"
                                videosec_url = urljoin(root_t, m.group(1))
                                try:
                                    v_res = fetcher.fetch(
                                        videosec_url,
                                        preferred_mode=FetchMode.HTTP,
                                        allow_dynamic_fallback=False,
                                        headers={"X-Requested-With": "XMLHttpRequest", "Referer": target_url}
                                    )
                                    if v_res.statusCode == 200 and v_res.body:
                                        p_stat, p_info = evaluate_player_discovery(v_res.body, videosec_url)
                                        if p_stat == "PLAYER_DISCOVERED":
                                            target_url = videosec_url
                                except Exception:
                                    pass

                    if p_stat == "PLAYER_DISCOVERED":
                        res["playerDiscovery"] = {
                            "status": "PLAYER_DISCOVERED",
                            "iframes": len(p_info["iframes"]),
                            "hasPlayer": p_info["hasPlayerScript"],
                            "targetUrl": XhrRedactor.redact_string(target_url)
                        }
                    else:
                        res["playerDiscovery"] = {"status": "PLAYER_NOT_FOUND", "targetUrl": XhrRedactor.redact_string(target_url)}

    statuses = [res["homepage"]["status"], res["detail"]["status"]]
    if any(s in ("UNTRUSTED_REDIRECT", "CONFIG_ERROR") for s in statuses):
        res["overall"] = "CRITICAL"
    elif any(s == "AUTOMATION_BLOCKED" for s in statuses):
        res["overall"] = "AUTOMATION_BLOCKED"
    elif any(s == "DRIFT_DETECTED" for s in statuses):
        res["overall"] = "DRIFT_DETECTED"
    elif all(s == "PASS" for s in statuses) and res["playerDiscovery"].get("status") == "PLAYER_DISCOVERED":
        res["overall"] = "PASS"
    elif any(s == "PASS" for s in statuses):
        res["overall"] = "PARTIAL_PASS"
    else:
        res["overall"] = "FAIL"

    return res

def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    providers_file = os.path.join(repo_root, "config", "providers.json")
    domains_file = os.path.join(repo_root, "config", "domains.json")

    with open(providers_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    with open(domains_file, "r", encoding="utf-8") as f:
        domains_config = json.load(f)

    providers = [p for p in data.get("providers", []) if p.get("enabled")]
    adaptive_mgr = AdaptiveManager()

    print(f"=== Live Provider Smoke Test (Scrapling) ({len(providers)} providers) ===\n")
    results = []

    for p in providers:
        print(f"[*] Testing {p['name']}...", end=" ", flush=True)
        res = test_provider(p, domains_config, adaptive_mgr)
        results.append(res)
        print(f"{res['overall']} (Home: {res['homepage']['status']}, Detail: {res['detail']['status']}, PlayerDiscovery: {res['playerDiscovery']['status']}, Sub: {res['subtitle']})")

    out_path = os.path.join(repo_root, "reports", "live_provider_smoke.json")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({
            "testedAt": datetime.now(timezone.utc).isoformat(),
            "providers": results
        }, f, indent=2, ensure_ascii=False)

    print(f"\n[+] Smoke report saved to {out_path}")

if __name__ == "__main__":
    main()
