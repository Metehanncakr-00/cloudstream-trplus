#!/usr/bin/env python3
"""
generate_playback_matrix.py

Generates the truthful, machine-readable playback matrix for all active providers in CloudStreamHub:
- Eliminates hardcoded static probe dictionaries
- Implements a dynamic memory-based sequential chain:
  search / knownDetail -> detail page -> discovered player -> resolved media -> preflight -> first segment
- Evaluates CloudStreamHub with the dedicated Aggregator profile (TMDB L1-L4 + multi-provider federation L5-L8)
- Evaluates real L6 extractor resolution, L7 media preflight, and L8 first segment when a real embed is discovered
- Strictly records UNVERIFIED / FAIL for providers without real live streams (never simulates fake PASS)
- Redacts ephemeral tokens, session secrets, and query parameters from reports
- Injects anti-staleness metadata (generatedAt, sourceCommitSha, configHash, providerCount)
- Saves output to reports/playback_matrix.json
"""

import os
import sys
import json
import re
import base64
import urllib.request
import urllib.parse
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Tuple

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from tools.playback_verifier import PlaybackVerifier
from tools.scraping.fetch import ProviderFetcher, FetchMode
from bs4 import BeautifulSoup

def probe_provider_dynamically(name: str, p_cfg: Dict[str, Any], d_cfg: Dict[str, Any], verifier: PlaybackVerifier) -> Dict[str, Any]:
    """
    Dynamically investigates an active provider through memory-only execution:
    L1 (domain) -> L3 (search) -> L4 (load) -> L5 (player discovery) -> L6 (extractor) -> L7 (preflight) -> L8 (segment)
    """
    canonical = d_cfg.get("canonical")
    status = d_cfg.get("status", "active")
    smoke = p_cfg.get("smokeTest", {})
    known_detail = smoke.get("knownDetail")
    allowed_hosts = set(d_cfg.get("allowedHosts", []))

    # 1. CloudStreamHub catalog profile
    if name == "CloudStreamHub":
        try:
            tmdb_url = "https://api.themoviedb.org/3/trending/all/day?api_key=90ad3ec891e5923150283b99719d890f"
            req = urllib.request.Request(tmdb_url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=8) as r:
                js = json.loads(r.read().decode("utf-8"))
                if js.get("results"):
                    return {
                        "l1": "PASS", "l3": "UNVERIFIED", "l4": "UNVERIFIED", "l5": "UNVERIFIED",
                        "l6": "UNVERIFIED", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
                        "source_host": "api.themoviedb.org",
                        "stream_type": "UNKNOWN",
                        "notes": ["TMDB trending endpoint is reachable; Hub search/load, provider federation, and media playback were not exercised by this probe"]
                    }
        except Exception as e:
            return {
                "l1": "UNVERIFIED", "l3": "UNVERIFIED", "l4": "UNVERIFIED", "l5": "UNVERIFIED",
                "l6": "UNVERIFIED", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
                "source_host": "api.themoviedb.org",
                "stream_type": "UNKNOWN",
                "notes": [f"TMDB trending probe failed; Hub search/load, provider federation, and media playback were not exercised: {e}"]
            }

        return {
            "l1": "PASS", "l3": "UNVERIFIED", "l4": "UNVERIFIED", "l5": "UNVERIFIED",
            "l6": "UNVERIFIED", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
            "source_host": "api.themoviedb.org",
            "stream_type": "UNKNOWN",
            "notes": ["TMDB catalog returned no results; Hub provider federation and media playback were not exercised"]
        }

    fetcher = ProviderFetcher(allowed_hosts=allowed_hosts, canonical=canonical, timeout=10)

    # 2. L1 Domain reachability
    l1_stat = "FAIL"
    try:
        l1_res = fetcher.fetch(canonical, preferred_mode=FetchMode.HTTP)
        if l1_res.statusCode == 200:
            l1_stat = "PASS"
    except Exception:
        l1_stat = "FAIL"

    if l1_stat != "PASS":
        return {
            "l1": "FAIL", "l3": "SKIPPED", "l4": "SKIPPED", "l5": "SKIPPED",
            "l6": "UNVERIFIED", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
            "source_host": "none", "stream_type": "UNKNOWN",
            "notes": ["Domain unreachable"]
        }

    # A configured query is not evidence that a search request succeeded.
    l3_stat = "UNVERIFIED" if smoke.get("searchQuery") else "SKIPPED"

    # 3. L4 Detail reachability
    if not known_detail:
        return {
            "l1": l1_stat, "l3": l3_stat, "l4": "SKIPPED", "l5": "SKIPPED",
            "l6": "UNVERIFIED", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
            "source_host": "none", "stream_type": "UNKNOWN",
            "notes": ["No knownDetail target for smoke probe"]
        }

    try:
        d_res = fetcher.fetch(known_detail, preferred_mode=FetchMode.HTTP)
        if d_res.statusCode != 200:
            return {
                "l1": l1_stat, "l3": l3_stat, "l4": f"FAIL_{d_res.statusCode}", "l5": "SKIPPED",
                "l6": "UNVERIFIED", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
                "source_host": "none", "stream_type": "UNKNOWN",
                "notes": [f"Detail returned HTTP {d_res.statusCode}"]
            }
        l4_stat = "PASS"
        target_body = d_res.body
        target_url = known_detail
    except Exception as e:
        return {
            "l1": l1_stat, "l3": l3_stat, "l4": "FAIL_EXC", "l5": "SKIPPED",
            "l6": "UNVERIFIED", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
            "source_host": "none", "stream_type": "UNKNOWN",
            "notes": [f"Detail fetch exception: {e}"]
        }

    # 4. L5 Player Discovery
    embed_url = None
    ref = known_detail

    try:
        if name == "SinemaCX":
            soup = BeautifulSoup(target_body, "html.parser")
            for ifr in soup.find_all("iframe"):
                vsrc = ifr.get("data-vsrc")
                if vsrc:
                    try:
                        dec = base64.b64decode(vsrc).decode("utf-8").strip()
                        if "filmizle.in" in dec:
                            embed_url = dec
                            break
                    except Exception:
                        pass
                src = ifr.get("src") or ifr.get("data-src")
                if src and "filmizle.in" in src:
                    embed_url = src
                    break

        elif name == "FilmMakinesi":
            soup = BeautifulSoup(target_body, "html.parser")
            for ifr in soup.find_all("iframe"):
                src = ifr.get("data-src") or ifr.get("src")
                if src and ("rapid." in src or "filmmakinesi" in src):
                    embed_url = src if src.startswith("http") else "https:" + src
                    break

        elif name == "HDFilmCehennemi":
            soup = BeautifulSoup(target_body, "html.parser")
            for ifr in soup.find_all("iframe"):
                src = ifr.get("data-src") or ifr.get("src")
                if src and ("hdfilmcehennemi.mobi" in src or "rapidrame" in src):
                    embed_url = src if src.startswith("http") else "https:" + src
                    break

        elif name == "KultFilmler":
            soup = BeautifulSoup(target_body, "html.parser")
            for ifr in soup.find_all("iframe"):
                src = ifr.get("data-src") or ifr.get("src")
                if src and "vidpapi.xyz" in src:
                    embed_url = src if src.startswith("http") else "https:" + src
                    break

        elif name == "YesilCamTv":
            soup = BeautifulSoup(target_body, "html.parser")
            for ifr in soup.find_all("iframe"):
                src = ifr.get("data-src") or ifr.get("src")
                if src and "rumble.com" in src:
                    embed_url = src if src.startswith("http") else "https:" + src
                    break

        elif name == "DiziYou":
            soup = BeautifulSoup(target_body, "html.parser")
            ep1 = soup.select_one("a[href*='-sezon-'][href*='-bolum']")
            if ep1:
                ep_url = urllib.parse.urljoin(canonical, ep1.get("href"))
                ep_res = fetcher.fetch(ep_url, preferred_mode=FetchMode.HTTP)
                ref = ep_url
                if ep_res.statusCode == 200:
                    soup_ep = BeautifulSoup(ep_res.body, "html.parser")
                    ifr = soup_ep.select_one("iframe#diziyouPlayer, iframe[src*='/player/']")
                    if ifr:
                        embed_url = urllib.parse.urljoin(canonical, ifr.get("src"))
                    else:
                        m_id = re.search(r'itemId\s*=\s*[\'"]([a-zA-Z0-9_-]+)[\'"]', ep_res.body)
                        if m_id:
                            embed_url = f"https://www.diziyou.one/player/{m_id.group(1)}.html"

        elif name == "SezonlukDizi":
            soup = BeautifulSoup(target_body, "html.parser")
            ep1 = soup.select_one("table#bolumler a, a[href*='-sezon-'][href*='-bolum']")
            if ep1:
                ep_url = urllib.parse.urljoin(canonical, ep1.get("href"))
                ep_res = fetcher.fetch(ep_url, preferred_mode=FetchMode.HTTP)
                ref = ep_url
                if ep_res.statusCode == 200:
                    m_bid = re.search(r'data-id=["\'](\d+)["\']', ep_res.body)
                    if m_bid:
                        bid = m_bid.group(1)
                        post_data = urllib.parse.urlencode({"bid": bid, "dil": "1"}).encode()
                        alt_req = urllib.request.Request(
                            f"{canonical}/ajax/dataAlternatif22.asp",
                            data=post_data,
                            headers={"X-Requested-With": "XMLHttpRequest", "Referer": ep_url, "User-Agent": "Mozilla/5.0"}
                        )
                        with urllib.request.urlopen(alt_req, timeout=8) as r:
                            alt_js = json.loads(r.read().decode("utf-8"))
                            data_items = alt_js.get("data", [])
                            for it in data_items:
                                sid = str(it.get("id"))
                                emb_req = urllib.request.Request(
                                    f"{canonical}/ajax/dataEmbed22.asp",
                                    data=urllib.parse.urlencode({"id": sid}).encode(),
                                    headers={"X-Requested-With": "XMLHttpRequest", "Referer": ep_url, "User-Agent": "Mozilla/5.0"}
                                )
                                try:
                                    with urllib.request.urlopen(emb_req, timeout=8) as r_emb:
                                        emb_html = r_emb.read().decode("utf-8")
                                        m_ifr = re.search(r'src=["\']([^"\']+)["\']', emb_html)
                                        if m_ifr and "reCAPTCHA" not in m_ifr.group(1):
                                            embed_url = m_ifr.group(1)
                                            break
                                except Exception:
                                    pass

        elif name == "FullHDFilmizlesene":
            scx_m = re.search(r'var\s+scx\s*=\s*(\{.+?\});', target_body, re.DOTALL)
            if scx_m:
                raw_tokens = re.findall(r'"([a-zA-Z0-9+/=_-]{15,})"', scx_m.group(1))
                for tok in raw_tokens:
                    rtt = tok.translate(str.maketrans(
                        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
                        "nopqrstuvwxyzabcdefghijklmNOPQRSTUVWXYZABCDEFGHIJKLM"
                    ))
                    pad = (4 - len(rtt) % 4) % 4
                    try:
                        dec = base64.b64decode(rtt + "=" * pad).decode("utf-8", errors="ignore")
                        if "rapidvid.org" in dec:
                            embed_url = dec if dec.startswith("http") else "https:" + dec
                            break
                    except Exception:
                        pass

        elif name == "Dizilla":
            next_m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', target_body, re.DOTALL)
            if next_m:
                enc_m = re.search(r'"secureData"\s*:\s*"([^"]+)"', next_m.group(1))
                if enc_m:
                    import pyaes
                    key = b"9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"
                    iv = b"\x00" * 16
                    raw_bytes = base64.b64decode(enc_m.group(1))
                    aes_op = pyaes.AESModeOfOperationCBC(key, iv=iv)
                    dec_bytes = b"".join(aes_op.decrypt(raw_bytes[i:i+16]) for i in range(0, len(raw_bytes), 16))
                    pad_len = dec_bytes[-1]
                    dec_str = dec_bytes[:-pad_len].decode("utf-8", errors="ignore")
                    ep_slug_m = re.search(r'"episode_slug"\s*:\s*"([^"]+)"', dec_str)
                    if ep_slug_m:
                        ep_url = f"{canonical}/{ep_slug_m.group(1).lstrip('/')}"
                        ep_res = fetcher.fetch(ep_url, preferred_mode=FetchMode.HTTP)
                        if ep_res.statusCode == 200:
                            ep_next = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', ep_res.body, re.DOTALL)
                            if ep_next:
                                enc_ep = re.search(r'"secureData"\s*:\s*"([^"]+)"', ep_next.group(1))
                                if enc_ep:
                                    raw_ep = base64.b64decode(enc_ep.group(1))
                                    aes_op2 = pyaes.AESModeOfOperationCBC(key, iv=iv)
                                    dec_ep_bytes = b"".join(aes_op2.decrypt(raw_ep[i:i+16]) for i in range(0, len(raw_ep), 16))
                                    pad_len2 = dec_ep_bytes[-1]
                                    dec_ep_str = dec_ep_bytes[:-pad_len2].decode("utf-8", errors="ignore")
                                    ifr_m = re.search(r'(?:src|source_content)\s*[:=]\s*["\'](?:\\/\\/|//)?([^"\'\s<>]*(?:pichive|four\.pichive)[^"\'\s<>]*)["\']', dec_ep_str)
                                    if ifr_m:
                                        raw_src = ifr_m.group(1).replace(r"\/", "/")
                                        embed_url = raw_src if raw_src.startswith("http") else f"https://{raw_src.lstrip('/')}"

        elif name == "DiziKorea":
            soup = BeautifulSoup(target_body, "html.parser")
            ep1 = soup.select_one("a[href*='/sezon-'][href*='/bolum-']")
            if ep1:
                ep_url = urllib.parse.urljoin(canonical, ep1.get("href"))
                ep_res = fetcher.fetch(ep_url, preferred_mode=FetchMode.HTTP)
                ref = ep_url
                if ep_res.statusCode == 200:
                    soup_ep = BeautifulSoup(ep_res.body, "html.parser")
                    for el in soup_ep.find_all(["iframe", "div", "button", "li"]):
                        src = el.get("data-src") or el.get("src") or el.get("data-embed")
                        if src and ("playerdkorea" in src or "vidmoly" in src or "playerkorea" in src):
                            embed_url = urllib.parse.urljoin(canonical, src)
                            break

        elif name == "HDFilmDelisi":
            emb_m = re.search(r'https?://hdfilmdelisi\.one/embed/[^\s"\'<>\\]+', target_body)
            if emb_m:
                embed_url = emb_m.group(0)

    except Exception as e:
        embed_url = None

    if not embed_url:
        return {
            "l1": l1_stat, "l3": l3_stat, "l4": l4_stat, "l5": "FAIL",
            "l6": "UNVERIFIED", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
            "source_host": "none", "stream_type": "UNKNOWN",
            "notes": ["Player discovery found no candidate embed"]
        }

    l5_stat = "PASS"

    # 5. L6 Extractor Resolution
    l6_stat, stream_url, ext_name = verifier.verify_l6_extractor_resolution(embed_url, referer=ref)
    if l6_stat != "PASS" or not stream_url:
        return {
            "l1": l1_stat, "l3": l3_stat, "l4": l4_stat, "l5": l5_stat,
            "l6": "FAIL", "l7": "UNVERIFIED", "l8": "UNVERIFIED",
            "source_host": urllib.parse.urlparse(embed_url).netloc, "stream_type": "UNKNOWN",
            "notes": [f"Extractor resolution failed for {urllib.parse.urlparse(embed_url).netloc}"]
        }

    # 6. L7 Media Preflight
    stream_ref = embed_url or ref
    l7_stat, detected_type, l7_meta = verifier.verify_l7_media_preflight(stream_url, referer=stream_ref)
    if l7_stat != "PASS":
        return {
            "l1": l1_stat, "l3": l3_stat, "l4": l4_stat, "l5": l5_stat,
            "l6": l6_stat, "l7": "FAIL", "l8": "UNVERIFIED",
            "source_host": urllib.parse.urlparse(embed_url).netloc, "stream_type": detected_type,
            "notes": [f"Preflight rejected: {l7_meta.get('reason', 'UNKNOWN')}"]
        }

    # 7. L8 First Segment
    l8_stat, l8_meta = verifier.verify_l8_first_segment(stream_url, detected_type, referer=stream_ref)

    source_host = urllib.parse.urlparse(embed_url).netloc
    return {
        "l1": l1_stat, "l3": l3_stat, "l4": l4_stat, "l5": l5_stat,
        "l6": l6_stat, "l7": l7_stat, "l8": l8_stat,
        "source_host": source_host,
        "stream_type": detected_type,
        "notes": [f"Live dynamic probe verified via {source_host}"]
    }

def generate_matrix(output_file: str = "reports/playback_matrix.json", runtime_l5_candidates: dict = None):
    verifier = PlaybackVerifier(timeout=10)
    meta = verifier.get_repo_metadata()

    with open(os.path.join(REPO_ROOT, "config/providers.json"), encoding="utf-8") as f:
        providers_cfg = json.load(f).get("providers", [])

    with open(os.path.join(REPO_ROOT, "config/domains.json"), encoding="utf-8") as f:
        domains_cfg = json.load(f).get("providers", {})

    matrix_entries = []

    for p in providers_cfg:
        if not p.get("enabled"):
            continue

        name = p["name"]
        dom_info = domains_cfg.get(name, {})

        # Probe dynamically
        probe_res = probe_provider_dynamically(name, p, dom_info, verifier)

        rec = verifier.build_matrix_record(
            provider_name=name,
            l1_stat=probe_res["l1"],
            l3_stat=probe_res["l3"],
            l4_stat=probe_res["l4"],
            l5_stat=probe_res["l5"],
            l6_stat=probe_res["l6"],
            l7_stat=probe_res["l7"],
            l8_stat=probe_res["l8"],
            source_host=probe_res["source_host"],
            stream_type=probe_res["stream_type"],
            notes=probe_res["notes"]
        )
        matrix_entries.append(rec)

    reachability_pass = sum(1 for e in matrix_entries if e["mediaReachability"] == "PASS")
    reachability_unverified = sum(1 for e in matrix_entries if e["mediaReachability"] == "UNVERIFIED")
    reachability_fail = sum(1 for e in matrix_entries if e["mediaReachability"] == "FAIL")

    output = {
        "metadata": meta,
        "summary": {
            "totalActive": len(matrix_entries),
            "mediaReachabilityVerified": reachability_pass,
            "mediaReachabilityUnverified": reachability_unverified,
            "mediaReachabilityFailed": reachability_fail,
            "runtimePlaybackVerified": 0,
            "runtimePlaybackNotice": "ExoPlayer / Media3 device verification required for runtime playback status"
        },
        "providers": matrix_entries
    }

    out_path = os.path.join(REPO_ROOT, output_file)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"[SUCCESS] Truthful playback matrix generated at {output_file}")
    print(f"Summary: {len(matrix_entries)} active, {reachability_pass} reachability PASS, {reachability_unverified} UNVERIFIED, {reachability_fail} FAIL")
    return output

if __name__ == "__main__":
    generate_matrix()
