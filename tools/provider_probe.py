#!/usr/bin/env python3
"""
provider_probe.py

CLI reverse-engineering and site diagnostics tool powered by Scrapling.
Features:
- Structured discovery pipeline: Homepage -> Detail -> Episode/Player.
- Deep evidence extraction: Forms, search actions, inputs, pagination, XHR endpoints, iframes, media URLs.
- When --capture-xhr is requested, forces browser execution with network_idle stability.
- All captured traffic and URLs are sanitized through XhrRedactor.
- Permissive allowlist for arbitrary research URLs (is_probe_mode=True).

Usage:
  python tools/provider_probe.py <url> [--mode auto|http|dynamic|stealth] [--capture-xhr] [--probe-detail] [--output <path>]

Note: Probe JSON files are generated diagnostic artifacts and should not be committed.
"""

import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

import re
import json
import argparse
import logging
from typing import Dict, Any, List, Optional
from urllib.parse import urljoin, urlparse

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

logging.basicConfig(level=logging.INFO, format="[%(levelname)s] %(message)s")
logger = logging.getLogger("provider_probe")

def extract_form_heuristics(soup: BeautifulSoup, base_url: str) -> List[Dict[str, Any]]:
    forms_info = []
    for form in soup.find_all("form"):
        action = form.get("action") or ""
        method = (form.get("method") or "GET").upper()
        inputs = []
        for inp in form.find_all(["input", "select"]):
            name = inp.get("name")
            itype = inp.get("type", "text")
            if name:
                inputs.append({"name": name, "type": itype})
        abs_action = urljoin(base_url, action)
        forms_info.append({
            "action": abs_action,
            "method": method,
            "inputs": inputs,
            "likelySearch": any(k in abs_action.lower() or k in str(inputs).lower() for k in ["search", "ara", "find", "query", "q"])
        })
    return forms_info

def extract_pagination_heuristics(soup: BeautifulSoup, base_url: str) -> List[str]:
    pagination_links = []
    for a in soup.select("a[href*='sayfa'], a[href*='page'], .pagination a, .pages a, .nav-links a"):
        href = a.get("href")
        if href:
            pagination_links.append(urljoin(base_url, href))
    return list(dict.fromkeys(pagination_links))[:5]

def probe_page(
    url: str,
    mode: str = "auto",
    capture_xhr: bool = False,
    probe_detail: bool = True,
    timeout: int = 20
) -> Dict[str, Any]:
    fetch_mode_map = {
        "auto": FetchMode.HTTP,
        "http": FetchMode.HTTP,
        "dynamic": FetchMode.DYNAMIC,
        "stealth": FetchMode.STEALTH
    }
    preferred = fetch_mode_map.get(mode.lower(), FetchMode.HTTP)
    allow_dyn = mode in ("auto", "dynamic", "stealth")
    allow_stealth = mode in ("auto", "stealth")

    logger.info(f"Probing {url} (mode={preferred.value}, capture_xhr={capture_xhr})...")

    # In probe mode, is_probe_mode=True allows probing arbitrary domains
    with ProviderFetcher(timeout=timeout, is_probe_mode=True) as fetcher:
        home_res = fetcher.fetch(
            url=url,
            preferred_mode=preferred,
            allow_dynamic_fallback=allow_dyn,
            allow_stealth_fallback=allow_stealth,
            capture_xhr=capture_xhr
        )

        probe_data: Dict[str, Any] = {
            "inputUrl": url,
            "finalUrl": home_res.finalUrl,
            "statusCode": home_res.statusCode,
            "fetchModeUsed": home_res.fetchMode.value,
            "status": home_res.status.value,
            "browserUsed": home_res.browserUsed,
            "elapsedMs": home_res.elapsedMs,
            "cloudflare": home_res.cloudflare,
            "blocked": home_res.blocked,
            "error": home_res.error,
            "homepageAnalysis": {
                "cardsFound": 0,
                "cards": [],
                "forms": [],
                "pagination": []
            },
            "detailAnalysis": None,
            "playerAnalysis": None,
            "capturedXhrCount": len(home_res.capturedXhr),
            "capturedXhr": home_res.capturedXhr if capture_xhr else []
        }

        if not home_res.body:
            return probe_data

        home_soup = BeautifulSoup(home_res.body, "html.parser")
        cards = discover_homepage_cards(home_res.body, home_res.finalUrl)
        forms = extract_form_heuristics(home_soup, home_res.finalUrl)
        pagination = extract_pagination_heuristics(home_soup, home_res.finalUrl)

        probe_data["homepageAnalysis"] = {
            "cardsFound": len(cards),
            "cards": cards[:10],
            "forms": forms,
            "pagination": pagination
        }

        # 2. Detail Analysis: Follow the first valid card if probe_detail is requested
        detail_url = None
        if probe_detail and cards:
            detail_url = cards[0]["url"]
        elif probe_detail and ("/film/" in url or "/dizi/" in url or "/anime/" in url or "/titles/" in url or "/belgesel/" in url):
            detail_url = url

        if detail_url:
            logger.info(f"Probing detail page: {detail_url}...")
            det_res = fetcher.fetch(
                url=detail_url,
                preferred_mode=preferred,
                allow_dynamic_fallback=allow_dyn,
                allow_stealth_fallback=allow_stealth,
                capture_xhr=capture_xhr
            )
            det_info = parse_detail_page(det_res.body, det_res.statusCode, detail_url)
            sub_class = classify_subtitles(det_res.body, det_info.get("title"))

            probe_data["detailAnalysis"] = {
                "targetUrl": detail_url,
                "statusCode": det_res.statusCode,
                "status": det_info["status"],
                "title": det_info.get("title"),
                "isSoft404": det_info.get("isSoft404", False),
                "episodeLinksCount": len(det_info.get("episodeLinks", [])),
                "episodeLinks": det_info.get("episodeLinks", [])[:5],
                "subtitleClassification": sub_class
            }

            # 3. Player Analysis: Check detail or episode page
            target_player_url = detail_url
            target_player_body = det_res.body

            # If it has episode links, follow the first episode for player inspection
            if det_info.get("episodeLinks"):
                ep_url = det_info["episodeLinks"][0]
                logger.info(f"Probing episode page for player: {ep_url}...")
                ep_res = fetcher.fetch(
                    url=ep_url,
                    preferred_mode=preferred,
                    allow_dynamic_fallback=allow_dyn,
                    allow_stealth_fallback=allow_stealth,
                    capture_xhr=capture_xhr
                )
                if ep_res.statusCode == 200:
                    target_player_url = ep_url
                    target_player_body = ep_res.body

            p_stat, p_info = evaluate_player_discovery(target_player_body, target_player_url)
            probe_data["playerAnalysis"] = {
                "targetUrl": target_player_url,
                "status": p_stat,
                "iframesCount": len(p_info["iframes"]),
                "iframes": p_info["iframes"],
                "videosCount": len(p_info["videos"]),
                "videos": p_info["videos"],
                "mediaUrls": p_info["mediaUrls"],
                "hasPlayerScript": p_info["hasPlayerScript"]
            }

        captured = probe_data["capturedXhr"]
        probe_data["networkSummary"] = {
            "xhr": sum(1 for x in captured if x.get("resourceType") == "xhr"),
            "fetch": sum(1 for x in captured if x.get("resourceType") == "fetch"),
            "script": sum(1 for x in captured if x.get("resourceType") == "script"),
            "media": sum(1 for x in captured if x.get("resourceType") == "media"),
            "websocket": sum(1 for x in captured if x.get("resourceType") == "websocket"),
            "total": len(captured)
        }

    return probe_data

def main():
    parser = argparse.ArgumentParser(description="CloudStreamHub Provider Reverse Engineering Probe (Scrapling)")
    parser.add_argument("url", help="Target URL to probe")
    parser.add_argument("--mode", default="auto", choices=["auto", "http", "dynamic", "stealth"], help="Fetch strategy tier")
    parser.add_argument("--capture-xhr", action="store_true", help="Intercept and sanitize background XHR/fetch requests (forces browser)")
    parser.add_argument("--no-detail", action="store_true", help="Do not follow first content card to detail page")
    parser.add_argument("--timeout", type=int, default=20, help="Request timeout in seconds")
    parser.add_argument("--output", help="Optional path to write JSON report (Warning: probe JSON files are generated diagnostic artifacts and should not be committed)")
    args = parser.parse_args()

    probe_result = probe_page(
        url=args.url,
        mode=args.mode,
        capture_xhr=args.capture_xhr,
        probe_detail=not args.no_detail,
        timeout=args.timeout
    )

    print("\n" + "=" * 65)
    print(" PROVIDER PROBE RESULTS (Scrapling)")
    print("=" * 65)
    print(f" Input URL:        {probe_result['inputUrl']}")
    print(f" Final URL:        {probe_result['finalUrl']}")
    print(f" Status Code:      {probe_result['statusCode']}")
    print(f" Fetch Mode Used:  {probe_result['fetchModeUsed']} (Browser: {probe_result['browserUsed']})")
    print(f" Status:           {probe_result['status']}")
    print(f" Elapsed:          {probe_result['elapsedMs']} ms")
    if probe_result['fetchModeUsed'] == "STEALTH":
        if probe_result['cloudflare']:
            print(" Cloudflare:       Active challenge detected and solved via StealthySession")
        else:
            print(" Cloudflare:       Stealth browser probe executed; no challenge observed.")
    else:
        print(f" Cloudflare:       {probe_result['cloudflare']}")
    print(f" Anti-Bot Blocked: {probe_result['blocked']}")
    if probe_result['error']:
        print(f" Error:            {probe_result['error']}")

    print("\n--- Homepage Analysis ---")
    home = probe_result["homepageAnalysis"]
    print(f" Cards Found:      {home['cardsFound']}")
    for i, c in enumerate(home["cards"][:3]):
        print(f"  [{i+1}] {c['title']} -> {c['url']}")
    print(f" Forms Found:      {len(home['forms'])}")
    for f in home["forms"]:
        print(f"  - Action: {f['action']} [{f['method']}] (Search: {f['likelySearch']}, Inputs: {[i['name'] for i in f['inputs']]})")
    print(f" Pagination Links: {len(home['pagination'])}")
    for p in home["pagination"]:
        print(f"  - {p}")

    if probe_result.get("detailAnalysis"):
        print("\n--- Detail Analysis ---")
        det = probe_result["detailAnalysis"]
        print(f" Detail URL:       {det['targetUrl']}")
        print(f" Status:           {det['status']} (HTTP {det['statusCode']})")
        print(f" Title:            {det['title']}")
        print(f" Soft 404:         {det['isSoft404']}")
        print(f" Episodes Found:   {det['episodeLinksCount']}")
        print(f" Subtitles:        {det['subtitleClassification']}")

    if probe_result.get("playerAnalysis"):
        print("\n--- Player & Stream Analysis ---")
        ply = probe_result["playerAnalysis"]
        print(f" Target URL:       {ply['targetUrl']}")
        print(f" Player Status:    {ply['status']}")
        print(f" Iframes Found:    {ply['iframesCount']}")
        for ifr in ply["iframes"]:
            print(f"  - {ifr}")
        print(f" Videos Found:     {ply['videosCount']}")
        for v in ply["videos"]:
            print(f"  - {v}")
        print(f" Media Streams:    {len(ply['mediaUrls'])}")
        for m in ply["mediaUrls"]:
            print(f"  - {m}")
        print(f" Script Player:    {ply['hasPlayerScript']}")

    if args.capture_xhr or probe_result.get("networkSummary", {}).get("total", 0) > 0:
        net = probe_result.get("networkSummary", {})
        print("\n--- Network Capture Summary ---")
        print(f" XHR:       {net.get('xhr', 0)}")
        print(f" Fetch:     {net.get('fetch', 0)}")
        print(f" Script:    {net.get('script', 0)}")
        print(f" Media:     {net.get('media', 0)}")
        print(f" WebSocket: {net.get('websocket', 0)}")
        print(f" Total captured network requests: {net.get('total', 0)}")

    print("=" * 65)

    if args.output:
        os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(probe_result, f, indent=2, ensure_ascii=False)
        print(f"\nSaved structured JSON probe report to: {args.output}")
        print("[WARNING] Probe JSON files are generated diagnostic artifacts and should not be committed.")

if __name__ == "__main__":
    main()
