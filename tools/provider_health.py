#!/usr/bin/env python3
"""
provider_health.py

Truthful multi-tier provider health inspection powered by Scrapling:
L0: Configuration (Config schema, root module, gradle build, manifest, Kotlin classes)
L1: Domain Reachable (DNS/HTTPS, HTTP status, redirect chain, allowedHosts, markers)
    - Status: PASS, CLOUDFLARE, UNTRUSTED_REDIRECT, CONFIG_ERROR, CONTENT_MARKER_MISMATCH, FAIL
L2: Homepage (DOM smoke probe with Scrapling + adaptive selector drift detection)
    - Status: PASS, DRIFT_DETECTED, FAIL, AUTOMATION_BLOCKED
L3: Search (Config-driven search probe: GET / POST_JSON / POST_FORM)
    - Status: PASS, FAIL, AUTOMATION_BLOCKED, SKIPPED
L4: Load (Detail page probe with soft 404 detection & adaptive title checking)
    - Status: PASS, DRIFT_DETECTED, FAIL, AUTOMATION_BLOCKED, SKIPPED
L5: Player Discovery (Evaluates player container discovery with episode chaining)
    - Movie: Detail -> Player discovery
    - TvSeries / Anime: Detail -> Episode discovery -> Player discovery
    - Status: PLAYER_DISCOVERED, PLAYER_NOT_FOUND, AUTOMATION_BLOCKED, SKIPPED
    - NOTE: Discovery of player containers NEVER implies CloudStream Android PLAYBACK_PASS!

Tiers that are not executed or not configured report 'skipped' or 'untested', NEVER fake 'pass'.
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
import urllib.parse
from urllib.parse import urljoin, urlparse
from typing import Dict, Any, List, Optional, Tuple
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
    evaluate_player_discovery
)

def check_l0_config(provider, repo_root, domains_config):
    name = provider.get("name")
    module = provider.get("module")
    domain_key = provider.get("domainKey", name)

    if not name or not module:
        return "fail", "MISSING_NAME_OR_MODULE"

    mod_dir = os.path.join(repo_root, module)
    if not os.path.isdir(mod_dir):
        return "fail", f"MODULE_DIR_NOT_FOUND: {module}"

    bg_file = os.path.join(mod_dir, "build.gradle.kts")
    if not os.path.isfile(bg_file):
        return "fail", f"BUILD_GRADLE_NOT_FOUND: {module}"

    domain_info = domains_config.get("providers", {}).get(domain_key)
    if not domain_info or not domain_info.get("canonical"):
        return "fail", f"DOMAIN_CONFIG_NOT_FOUND: {domain_key}"

    return "pass", None

REQUIRED_HEALTH_TIERS = ["L0_config", "L1_domain", "L2_homepage", "L3_search", "L4_load", "L5_player_discovery"]

def evaluate_overall_health(tier_results: Dict[str, str], required_tiers: Optional[List[str]] = None) -> str:
    """
    Central truthful overall-health evaluator.
    Invariant: No provider may be HEALTHY when any configured required tier is FAIL.

    Outcomes:
    - critical: Domain security/configuration failure (untrusted_redirect, config_error)
    - failed: Fatal build/module failure (L0 fail, L1 HTTP error / content marker mismatch)
    - degraded: Any functional tier failure (L2 fail, L3 fail, L4 fail, L5 player_not_found, blocked/challenge)
    - warning: Selector drift detected
    - healthy: ALL required tiers passed (or skipped if optional)
    """
    reqs = required_tiers if required_tiers is not None else REQUIRED_HEALTH_TIERS

    # 1. Critical configuration/security failures
    l1 = tier_results.get("L1_domain", "")
    if l1 in ("untrusted_redirect", "config_error"):
        return "critical"

    # 2. Fatal build/manifest/root domain failures
    l0 = tier_results.get("L0_config", "")
    if l0 == "fail" or l1.startswith("fail") or l1 == "content_marker_mismatch":
        return "failed"

    # 3. Degraded functionality checks
    # Any required tier failing means provider is at best DEGRADED, NEVER HEALTHY!
    for tier in reqs:
        val = tier_results.get(tier, "")
        if val == "fail" or (val.startswith("fail") and tier not in ("L0_config", "L1_domain")):
            return "degraded"
        if tier == "L5_player_discovery" and val == "player_not_found":
            return "degraded"
        if val in ("cloudflare_challenge", "automation_blocked"):
            return "degraded"

    # 4. Warnings (Drift detected)
    for tier in reqs:
        if tier_results.get(tier) == "drift_detected":
            return "warning"

    # 5. Only healthy if all required passed
    for tier in reqs:
        val = tier_results.get(tier, "")
        if tier == "L5_player_discovery":
            if val != "player_discovered" and val != "skipped":
                return "degraded"
        elif val not in ("pass", "skipped"):
            return "degraded"

    return "healthy"

def derive_health_score(overall_status: str, tier_results: Dict[str, str]) -> int:
    """
    Derives an informational health score (0-100) for tool reporting.
    This score is reporting-only and is NEVER persisted in playback payloads or
    used for runtime source ordering or suppression.

    Grading:
    - critical: 0
    - failed: 10
    - degraded: 35-50 (based on number of failed tiers)
    - warning: 75
    - healthy: 100
    """
    if overall_status == "critical":
        return 0
    if overall_status == "failed":
        return 10
    if overall_status == "degraded":
        degraded_count = sum(1 for k, v in tier_results.items() if v not in ("pass", "skipped", "player_discovered"))
        return max(35, 50 - (max(0, degraded_count - 1) * 5))
    if overall_status == "warning":
        return 75
    if overall_status == "healthy":
        return 100
    return 0

def check_l2_homepage(name, html_body, canonical, adaptive_mgr: AdaptiveManager, monitoring_cfg: Dict[str, Any]):
    if not html_body:
        return "fail", "EMPTY_BODY", None

    homepage_cfg = monitoring_cfg.get("homepage", {})
    # Use shared discovery helper with provider-specific homepage filtering
    cards = discover_homepage_cards(html_body, canonical, homepage_cfg=homepage_cfg)
    if len(cards) > 0:
        # Register baseline with adaptive manager
        try:
            adaptive_mgr.check_css_selector(
                html_content=html_body,
                selector_str=cards[0].get("selector", "a.poster"),
                identifier=f"{name}_homepage_card",
                base_url=canonical
            )
        except Exception:
            pass
        return "pass", f"Found {len(cards)} items. Top: {cards[0]['title']}", None

    # If 0 cards discovered, run adaptive selector drift check
    primary_selector = "a.poster, a.mcard, a.dcard, article a"
    try:
        drift_stat, drift_count, candidates = adaptive_mgr.check_css_selector(
            html_content=html_body,
            selector_str=primary_selector.split(",")[0].strip(),
            identifier=f"{name}_homepage_card",
            base_url=canonical
        )
        if drift_stat == "DRIFT_DETECTED" and drift_count > 0:
            return "drift_detected", f"Selector drift detected: {drift_count} candidate elements found", candidates
    except Exception:
        pass

    return "fail", "NO_HOMEPAGE_ITEMS_PARSED", None

def check_l3_search(name, canonical, smoke_test, monitoring_cfg, fetcher: ProviderFetcher):
    q = smoke_test.get("searchQuery")
    if not q:
        return "skipped", "NO_SEARCH_QUERY_CONFIGURED"

    search_cfg = monitoring_cfg.get("search", {})
    method = search_cfg.get("method", "GET").upper()
    path = search_cfg.get("path", "/?s={query}")
    endpoint = urljoin(canonical, path.replace("{query}", urllib.parse.quote(q)))
    custom_headers = search_cfg.get("headers", {})

    try:
        if method == "POST_JSON":
            payload_template = search_cfg.get("payload", {"query": "{query}"})
            payload_str = json.dumps(payload_template).replace("{query}", q)
            headers = {"Content-Type": "application/json"}
            headers.update(custom_headers)
            res = fetcher.fetch(endpoint, preferred_mode=FetchMode.HTTP, method="POST", data=payload_str, headers=headers)
            if res.status in (FetchStatus.CLOUDFLARE, FetchStatus.BLOCKED):
                return "automation_blocked", f"Search endpoint protected ({res.status.value})"
            if res.statusCode == 200 and res.body:
                try:
                    data = json.loads(res.body)
                    json_key = search_cfg.get("jsonKey", "results")
                    results = data.get(json_key, [])
                    if results and len(results) > 0:
                        first = results[0]
                        first_title = first.get("title") or first.get("name") or str(first)
                        return "pass", f"Found {len(results)} results. Top: {str(first_title)[:40]}"
                except Exception:
                    pass
            return "fail", f"Search HTTP {res.statusCode} or 0 valid results"

        elif method == "POST_FORM":
            payload_template = search_cfg.get("payload", {"arama": "{query}"})
            data = {k: v.replace("{query}", q) for k, v in payload_template.items()}
            form_data = urllib.parse.urlencode(data)
            headers = {"Content-Type": "application/x-www-form-urlencoded"}
            headers.update(custom_headers)
            res = fetcher.fetch(endpoint, preferred_mode=FetchMode.HTTP, method="POST", data=form_data, headers=headers)
            if res.status in (FetchStatus.CLOUDFLARE, FetchStatus.BLOCKED):
                return "automation_blocked", f"Search AJAX protected ({res.status.value})"
            if res.statusCode == 200 and res.body:
                if "jsonKey" in search_cfg or res.body.strip().startswith("{"):
                    try:
                        data = json.loads(res.body)
                        json_key = search_cfg.get("jsonKey")
                        results = data
                        if json_key:
                            for k in json_key.split("."):
                                if isinstance(results, dict):
                                    results = results.get(k, [])
                        if isinstance(results, dict) and "results" in results:
                            results = results["results"]
                        if results and len(results) > 0:
                            first = results[0] if isinstance(results, list) else results
                            first_title = first.get("title") or first.get("name") if isinstance(first, dict) else str(first)
                            return "pass", f"Found {len(results)} results. Top: {str(first_title)[:40]}"
                    except Exception:
                        pass

                soup = BeautifulSoup(res.body, "html.parser")
                selector = search_cfg.get("expectedSelector", "div.panel, article a, a[href*='/anime/']")
                results = soup.select(selector)
                valid_items = [r for r in results if r.text.strip() and len(r.text.strip()) > 2]
                if valid_items:
                    first_title = valid_items[0].text.strip()[:40]
                    return "pass", f"Found {len(valid_items)} search matches. Top: {first_title}"
            return "fail", f"Search HTTP {res.statusCode} or 0 valid matches found"

        else:
            # Standard GET search (HTML or JSON API)
            res = fetcher.fetch(endpoint, preferred_mode=FetchMode.HTTP, headers=custom_headers)
            if res.status in (FetchStatus.CLOUDFLARE, FetchStatus.BLOCKED):
                return "automation_blocked", f"Search protected ({res.status.value})"
            if res.statusCode == 200 and res.body:
                trimmed = res.body.strip()
                # If response is JSON API (e.g. DiziPal api/search.php or AnimeciX secure/search or HDFilmCehennemi search)
                if trimmed.startswith("{") or trimmed.startswith("["):
                    try:
                        data = json.loads(trimmed)
                        json_key = search_cfg.get("jsonKey", "results")
                        results = data
                        if json_key and isinstance(results, dict):
                            for k in json_key.split("."):
                                if isinstance(results, dict):
                                    results = results.get(k, [])
                        if results and len(results) > 0:
                            first = results[0] if isinstance(results, list) else results
                            first_title = first.get("title") or first.get("name") if isinstance(first, dict) else str(first)
                            return "pass", f"Found {len(results)} API results. Top: {str(first_title)[:40]}"
                    except Exception:
                        pass

                # HTML search results
                soup = BeautifulSoup(res.body, "html.parser")
                selector = search_cfg.get("expectedSelector", "article a, a.item, .movie-box a, .film-box a, .video-item a, a.poster, a.mcard")
                results = soup.select(selector)
                valid_cards = []
                for r in results:
                    href = r.get("href") or (r.find("a").get("href") if r.find("a") else None)
                    text = r.text.strip() or r.get("title") or ""
                    if href and len(text) > 1 and not any(bad in href.lower() for bad in ["sayfa", "page", "kategori", "genre"]):
                        valid_cards.append({"title": text[:40], "href": href})
                if valid_cards:
                    return "pass", f"Found {len(valid_cards)} content results. Top: {valid_cards[0]['title']}"

            return "fail", f"Search returned HTTP {res.statusCode if 'res' in locals() else 'None'} or no valid content matches"

    except Exception as e:
        return "fail", str(e)

def check_l4_load(canonical, smoke_test, fetcher: ProviderFetcher, adaptive_mgr: AdaptiveManager, monitoring_cfg: Dict[str, Any]):
    detail_url = smoke_test.get("knownDetail")
    if not detail_url:
        return "skipped", "NO_DETAIL_URL_CONFIGURED", None, [], "NONE"

    episode_selector = monitoring_cfg.get("playerProbe", {}).get("episodeSelector")

    try:
        res = fetcher.fetch(detail_url, preferred_mode=FetchMode.HTTP, allow_dynamic_fallback=True)
        if res.status in (FetchStatus.CLOUDFLARE, FetchStatus.BLOCKED):
            return "automation_blocked", f"Detail page protected ({res.status.value})", None, [], "NONE"

        detail_info = parse_detail_page(
            html_body=res.body,
            status_code=res.statusCode,
            base_url=detail_url,
            episode_selector=episode_selector,
            fetcher=fetcher
        )
        if detail_info["status"] == "FAIL":
            if detail_info.get("isSoft404"):
                return "fail", f"SOFT_404_PAGE: {detail_info.get('title')}", None, [], "NONE"
            return "fail", f"Detail load failed ({detail_info.get('reason')})", None, [], "NONE"

        ep_mode = detail_info.get("episodeDiscoveryMode", "NONE")
        ep_links = detail_info.get("episodeLinks", [])
        return "pass", f"Loaded: {detail_info['title'][:40]} (Episodes: {len(ep_links)}, Mode: {ep_mode})", res.body, ep_links, ep_mode

    except Exception as e:
        return "fail", str(e), None, [], "NONE"

def check_l5_player_discovery(
    detail_body: str,
    detail_url: str,
    episode_links: List[str],
    episode_discovery_mode: str,
    monitoring_cfg: Dict[str, Any],
    fetcher: ProviderFetcher
):
    """
    Evaluates player discovery with target chaining:
    - If mode == 'episode' and episode_links exist: fetch first episode page and probe player!
    - Otherwise probe detail page.
    """
    player_probe_cfg = monitoring_cfg.get("playerProbe", {})
    probe_mode = player_probe_cfg.get("mode", "detail")

    target_body = detail_body
    target_url = detail_url

    if probe_mode == "episode" and episode_links:
        ep_url = episode_links[0]
        try:
            ep_res = fetcher.fetch(ep_url, preferred_mode=FetchMode.HTTP, allow_dynamic_fallback=True)
            if ep_res.statusCode == 200 and ep_res.body:
                target_body = ep_res.body
                target_url = ep_url
        except Exception:
            pass

    if not target_body:
        return "player_not_found", f"[{episode_discovery_mode}] No target content body available to inspect at {target_url}"

    stat, info = evaluate_player_discovery(target_body, target_url)
    if stat != "PLAYER_DISCOVERED" and fetcher:
        # Check for TurkAnime-style videosec player button
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
                        stat, info = evaluate_player_discovery(v_res.body, videosec_url)
                        if stat == "PLAYER_DISCOVERED":
                            target_url = videosec_url
                except Exception:
                    pass

        # Check for SezonlukDizi-style dataAlternatif AJAX
        if stat != "PLAYER_DISCOVERED":
            dilsec = soup.select_one("#dilsec, div[data-id]")
            bid = dilsec.get("data-id") if dilsec else None
            if not bid:
                m = re.search(r'data-id=["\'](\d+)["\']', target_body)
                bid = m.group(1) if m else None
            if bid:
                parsed_t = urlparse(target_url)
                root_t = f"{parsed_t.scheme}://{parsed_t.netloc}/"
                alt_url = urljoin(root_t, "ajax/dataAlternatif22.asp")
                try:
                    alt_res = fetcher.fetch(
                        alt_url,
                        preferred_mode=FetchMode.HTTP,
                        allow_dynamic_fallback=False,
                        method="POST",
                        data={"bid": bid, "dil": "1"},
                        headers={"X-Requested-With": "XMLHttpRequest", "Referer": target_url}
                    )
                    if alt_res.statusCode == 200 and alt_res.body:
                        alt_data = json.loads(alt_res.body).get("data", [])
                        for item in alt_data:
                            sid = item.get("id")
                            if not sid:
                                continue
                            embed_url = urljoin(root_t, "ajax/dataEmbed22.asp")
                            emb_res = fetcher.fetch(
                                embed_url,
                                preferred_mode=FetchMode.HTTP,
                                allow_dynamic_fallback=False,
                                method="POST",
                                data={"id": str(sid)},
                                headers={"X-Requested-With": "XMLHttpRequest", "Referer": target_url}
                            )
                            if emb_res.statusCode == 200 and emb_res.body:
                                emb_stat, emb_info = evaluate_player_discovery(emb_res.body, embed_url)
                                if emb_stat == "PLAYER_DISCOVERED":
                                    stat = "PLAYER_DISCOVERED"
                                    info = emb_info
                                    target_url = embed_url
                                    break
                except Exception:
                    pass

    if stat == "PLAYER_DISCOVERED":
        diag = f"[{episode_discovery_mode}] Discovered {len(info['iframes'])} iframes, {len(info['videos'])} videos at {target_url}"
        return "player_discovered", diag
    return "player_not_found", f"[{episode_discovery_mode}] No player iframe, video tag, or player script discovered at {target_url}"

def check_json_api_profile(name, canonical, smoke_test, monitoring_cfg, fetcher, report):
    """Probe catalog APIs without treating metadata as player discovery."""
    tiers = report["tierResults"]
    diagnostics = report["diagnostic"]
    search_cfg = monitoring_cfg.get("search", {})
    search_path = search_cfg.get("path", "")
    if name == "CloudStreamHub":
        key = urllib.parse.parse_qs(urlparse(search_path).query).get("api_key", [None])[0]
        if not key:
            tiers["L1_domain"] = "config_error"
            diagnostics["L1"] = "Missing configured catalog API credential"
            return
        catalog_url = urljoin(canonical, "/3/trending/all/day") + "?" + urllib.parse.urlencode({"api_key": key, "language": "tr-TR"})
    else:
        catalog_url = urljoin(canonical, "/api/v2/list_movies.json?limit=20")

    def request_json(url, headers=None):
        response = fetcher.fetch(url, preferred_mode=FetchMode.HTTP,
                                 allow_dynamic_fallback=False, allow_stealth_fallback=False,
                                 headers=headers or {})
        if response.status == FetchStatus.CONFIG_ERROR:
            return "config_error", "API fetch configuration rejected", None, response
        if response.status == FetchStatus.UNTRUSTED_REDIRECT:
            return "untrusted_redirect", "API redirect rejected", None, response
        if response.status in (FetchStatus.BLOCKED, FetchStatus.CLOUDFLARE) or response.cloudflare:
            return "automation_blocked", "API request blocked", None, response
        if response.status != FetchStatus.SUCCESS or response.statusCode != 200:
            return "fail", f"API HTTP {response.statusCode}", None, response
        try:
            payload = json.loads(response.body)
        except (ValueError, TypeError):
            return "fail", "API returned non-JSON body", None, response
        if not isinstance(payload, dict) or payload.get("success") is False:
            return "fail", "API returned invalid/error object", None, response
        if name == "YTS" and (payload.get("status") != "ok" or not isinstance(payload.get("data"), dict)):
            return "fail", "YTS API status/data invalid", None, response
        return "pass", None, payload, response

    def valid_item(item):
        return (isinstance(item, dict) and type(item.get("id")) is int and item["id"] > 0
                and isinstance(item.get("title") or item.get("name"), str)
                and bool((item.get("title") or item.get("name")).strip()))

    def catalog_items(payload):
        return payload.get("results") if name == "CloudStreamHub" else payload["data"].get("movies")

    status, diagnostic, payload, response = request_json(catalog_url)
    tiers["L1_domain"] = status
    report["finalUrl"] = urlparse(response.finalUrl or "")._replace(query="", fragment="").geturl()
    report["candidateHost"] = response.candidateHost
    if diagnostic:
        diagnostics["L1"] = diagnostic
    if status != "pass":
        return
    items = catalog_items(payload)
    tiers["L2_homepage"] = "pass" if isinstance(items, list) and any(valid_item(item) for item in items) else "fail"
    diagnostics["L2"] = "Catalog JSON items checked; no playback evidence"

    if search_path and smoke_test.get("searchQuery"):
        search_url = urljoin(canonical, search_path.replace("{query}", urllib.parse.quote(smoke_test["searchQuery"], safe="")))
        status, diagnostic, payload, _ = request_json(search_url, search_cfg.get("headers"))
        if status == "pass":
            items = catalog_items(payload)
            status = "pass" if isinstance(items, list) and any(valid_item(item) for item in items) else "fail"
        tiers["L3_search"] = status
        diagnostics["L3"] = diagnostic or "Search JSON items checked"
    detail_url = smoke_test.get("knownDetail")
    if detail_url:
        status, diagnostic, payload, _ = request_json(detail_url)
        if status == "pass":
            movie = payload if name == "CloudStreamHub" else payload["data"].get("movie")
            if name == "CloudStreamHub":
                expected_id = urlparse(detail_url).path.rstrip("/").split("/")[-1]
            else:
                expected_id = urllib.parse.parse_qs(urlparse(detail_url).query).get("movie_id", [None])[0]
            status = "pass" if valid_item(movie) and str(movie["id"]) == expected_id else "fail"
        tiers["L4_load"] = status
        diagnostics["L4"] = diagnostic or "Detail JSON identity/title checked"
    tiers["L5_player_discovery"] = "untested"
    diagnostics["L5"] = ("Hub federation/player not executed" if name == "CloudStreamHub"
                         else "Torrent metadata is not peer/file/player verification")


def inspect_provider(provider, repo_root, domains_config, adaptive_mgr: AdaptiveManager):
    name = provider["name"]
    domain_key = provider.get("domainKey", name)
    domain_info = domains_config.get("providers", {}).get(domain_key, {})
    canonical = domain_info.get("canonical")
    allowed_hosts = set(domain_info.get("allowedHosts", []))
    expected_markers = domain_info.get("expectedMarkers", [])
    smoke_test = provider.get("smokeTest", {})
    monitoring_cfg = provider.get("monitoring", {})

    pref_fetch = FetchMode(monitoring_cfg.get("preferredFetch", "HTTP"))
    dyn_fallback = monitoring_cfg.get("dynamicFallback", True)
    stealth_fallback = monitoring_cfg.get("stealthFallback", True)

    report = {
        "schemaVersion": 1,
        "provider": name,
        "checkedAt": datetime.now(timezone.utc).isoformat(),
        "canonical": canonical,
        "finalUrl": None,
        "tierResults": {
            "L0_config": "untested",
            "L1_domain": "untested",
            "L2_homepage": "untested",
            "L3_search": "untested",
            "L4_load": "untested",
            "L5_player_discovery": "untested"
        },
        "overallStatus": "untested",
        "healthScore": 0,
        "playbackVerification": "UNVERIFIED_BY_AUTOMATION",
        "diagnostic": {},
        "durationMs": 0,
        "candidateHost": None
    }

    start_time = time.time()

    def finalize_and_return(rep: Dict[str, Any]) -> Dict[str, Any]:
        rep["overallStatus"] = evaluate_overall_health(rep["tierResults"])
        rep["healthScore"] = derive_health_score(rep["overallStatus"], rep["tierResults"])
        for k, v in list(rep["diagnostic"].items()):
            rep["diagnostic"][k] = XhrRedactor.redact_string(str(v))
        rep["durationMs"] = int((time.time() - start_time) * 1000)
        return rep

    # L0
    l0_stat, l0_diag = check_l0_config(provider, repo_root, domains_config)
    report["tierResults"]["L0_config"] = l0_stat
    if l0_diag:
        report["diagnostic"]["L0"] = l0_diag
    if l0_stat != "pass":
        return finalize_and_return(report)

    # Fetcher session reuse context
    with ProviderFetcher(allowed_hosts=allowed_hosts, canonical=canonical, timeout=15) as fetcher:
        if name in ("CloudStreamHub", "YTS"):
            check_json_api_profile(name, canonical, smoke_test, monitoring_cfg, fetcher, report)
            return finalize_and_return(report)
        # L1: Domain
        l1_res = fetcher.fetch(
            url=canonical,
            preferred_mode=pref_fetch,
            allow_dynamic_fallback=dyn_fallback,
            allow_stealth_fallback=stealth_fallback,
            expected_markers=expected_markers
        )

        report["finalUrl"] = l1_res.finalUrl
        report["candidateHost"] = l1_res.candidateHost

        if l1_res.status == FetchStatus.CONFIG_ERROR:
            report["tierResults"]["L1_domain"] = "config_error"
            report["overallStatus"] = "critical"
            report["diagnostic"]["L1"] = l1_res.error
            for t in ["L2_homepage", "L3_search", "L4_load", "L5_player_discovery"]:
                report["tierResults"][t] = "skipped"
            return finalize_and_return(report)

        if l1_res.status == FetchStatus.UNTRUSTED_REDIRECT:
            report["tierResults"]["L1_domain"] = "untrusted_redirect"
            report["overallStatus"] = "critical"
            report["diagnostic"]["L1"] = f"Untrusted redirect candidate: {l1_res.candidateHost}"
            for t in ["L2_homepage", "L3_search", "L4_load", "L5_player_discovery"]:
                report["tierResults"][t] = "skipped"
            return finalize_and_return(report)

        if l1_res.status == FetchStatus.CONTENT_MARKER_MISMATCH:
            report["tierResults"]["L1_domain"] = "content_marker_mismatch"
            report["overallStatus"] = "failed"
            report["diagnostic"]["L1"] = l1_res.error
            for t in ["L2_homepage", "L3_search", "L4_load", "L5_player_discovery"]:
                report["tierResults"][t] = "skipped"
            return finalize_and_return(report)

        if l1_res.status == FetchStatus.CLOUDFLARE or l1_res.cloudflare:
            report["tierResults"]["L1_domain"] = "cloudflare_challenge"
            report["diagnostic"]["L1"] = "Cloudflare Challenge Active"
        elif l1_res.status == FetchStatus.SUCCESS:
            report["tierResults"]["L1_domain"] = "pass"
        else:
            report["tierResults"]["L1_domain"] = f"fail (HTTP_{l1_res.statusCode})"
            report["overallStatus"] = "failed"
            report["diagnostic"]["L1"] = l1_res.error or f"HTTP status: {l1_res.statusCode}"
            for t in ["L2_homepage", "L3_search", "L4_load", "L5_player_discovery"]:
                report["tierResults"][t] = "skipped"
            report["durationMs"] = int((time.time() - start_time) * 1000)
            return report

        # L2: Homepage
        l2_stat, l2_diag, l2_candidates = check_l2_homepage(name, l1_res.body, canonical, adaptive_mgr, monitoring_cfg)
        report["tierResults"]["L2_homepage"] = l2_stat
        if l2_diag:
            report["diagnostic"]["L2"] = l2_diag
        if l2_candidates:
            report["diagnostic"]["L2_candidates"] = l2_candidates

        # L3: Search
        l3_stat, l3_diag = check_l3_search(name, canonical, smoke_test, monitoring_cfg, fetcher)
        report["tierResults"]["L3_search"] = l3_stat
        if l3_diag:
            report["diagnostic"]["L3"] = l3_diag

        # L4: Detail Load
        l4_stat, l4_diag, detail_body, episode_links, ep_mode = check_l4_load(canonical, smoke_test, fetcher, adaptive_mgr, monitoring_cfg)
        report["tierResults"]["L4_load"] = l4_stat
        if l4_diag:
            report["diagnostic"]["L4"] = l4_diag

        # L5: Player Discovery with Chaining
        known_detail_url = smoke_test.get("knownDetail")
        l5_stat, l5_diag = check_l5_player_discovery(detail_body, known_detail_url, episode_links, ep_mode, monitoring_cfg, fetcher)
        report["tierResults"]["L5_player_discovery"] = l5_stat
        if l5_diag:
            report["diagnostic"]["L5"] = l5_diag

    return finalize_and_return(report)

def main():
    parser = argparse.ArgumentParser(description="Multi-tier Truthful Provider Health Checker (Scrapling)")
    parser.add_argument("--report-path", default="reports/provider-health.json", help="Path to write health report")
    parser.add_argument("--provider", help="Check only specific provider")
    args = parser.parse_args()

    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    providers_file = os.path.join(repo_root, "config", "providers.json")
    domains_file = os.path.join(repo_root, "config", "domains.json")

    with open(providers_file, "r", encoding="utf-8") as f:
        providers = [p for p in json.load(f).get("providers", []) if p.get("enabled")]

    with open(domains_file, "r", encoding="utf-8") as f:
        domains_config = json.load(f)

    adaptive_mgr = AdaptiveManager()
    results = []

    print(f"[Provider Health] Inspecting {len(providers)} enabled providers across L0-L5 (Scrapling)...")

    for p in providers:
        if args.provider and p["name"].lower() != args.provider.lower():
            continue
        print(f"\n> Inspecting {p['name']}...")
        rep = inspect_provider(p, repo_root, domains_config, adaptive_mgr)
        results.append(rep)
        t = rep["tierResults"]
        print(f"  L0: {t['L0_config']} | L1: {t['L1_domain']} | L2: {t['L2_homepage']} | L3: {t['L3_search']} | L4: {t['L4_load']} | L5: {t['L5_player_discovery']}")
        print(f"  Overall: {rep['overallStatus']} ({rep['durationMs']}ms)")

    os.makedirs(os.path.dirname(os.path.join(repo_root, args.report_path)), exist_ok=True)
    with open(os.path.join(repo_root, args.report_path), "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)

    print(f"\n[Provider Health] Saved comprehensive report to {args.report_path}")

if __name__ == "__main__":
    main()
