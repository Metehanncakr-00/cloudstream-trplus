"""
detection.py

Anti-bot, Cloudflare challenge, redirect security, soft 404, and content marker detection.
"""

import re
from typing import Set, Tuple, Optional, List, Dict
from urllib.parse import urlparse

CLOUDFLARE_INDICATORS = [
    "cloudflare",
    "cf-ray",
    "cf-chl-bypass",
    "just a moment...",
    "attention required! | cloudflare",
    "challenge-running",
    "turnstile",
    "cf-mitigated",
    "_cf_chl"
]

BLOCKED_STATUS_CODES = {403, 429, 451, 503}

SOFT_404_INDICATORS = [
    "404",
    "not found",
    "page not found",
    "sayfa bulunamadı",
    "sayfa bulunamadi",
    "içerik bulunamadı",
    "icerik bulunamadi",
    "bulunamadı",
    "bulunamadi",
    "böyle bir sayfa yok",
    "arama sonucu bulunamadı"
]

def is_cloudflare_challenge(status: Optional[int], body: str, headers: Optional[Dict[str, str]] = None) -> bool:
    """Detects whether a response is an active Cloudflare Turnstile, Managed Challenge, or interstitial."""
    if not status:
        return False

    body_lower = body.lower() if body else ""
    hdr_str = " ".join(f"{k}:{v}" for k, v in (headers or {}).items()).lower()

    has_cf_marker = any(ind in body_lower or ind in hdr_str for ind in CLOUDFLARE_INDICATORS)

    if status in (403, 503, 429) and has_cf_marker:
        return True

    # Sometimes 200 is returned for the interstitial HTML page itself
    if status == 200 and ("just a moment..." in body_lower or "challenge-running" in body_lower):
        return True

    return False

def is_bot_blocked(status: Optional[int], body: str) -> bool:
    """Detects general anti-bot blocking (WAF, 403 Forbidden, rate limit)."""
    if status in BLOCKED_STATUS_CODES:
        return True
    body_lower = body.lower() if body else ""
    if any(phrase in body_lower for phrase in ("access denied", "bot detected", "ddos protection by")):
        return True
    if re.search(r"\b(waf|web application firewall)\b", body_lower) and status != 200:
        return True
    return False

def verify_redirect_safety(
    final_url: str,
    allowed_hosts: Set[str],
    canonical: Optional[str] = None,
    allow_subdomains: bool = False,
    is_probe_mode: bool = False
) -> Tuple[bool, Optional[str]]:
    """
    Ensures that a request's final redirected URL remains within allowed destination hosts.
    
    Fail-closed security rules:
    - If is_probe_mode=False and allowed_hosts is empty -> (False, "CONFIG_EMPTY_ALLOWLIST")
    - If allow_subdomains=False -> Host must match exactly in allowed_hosts (or canonical host).
    - If allow_subdomains=True -> Host may be a subdomain of an allowed host.
    - If is_probe_mode=True and allowed_hosts is empty -> Permissive for arbitrary CLI exploration.
    
    Returns (is_allowed, candidate_host).
    """
    if not final_url:
        return False, None

    parsed = urlparse(final_url)
    host = parsed.netloc.lower().split(":")[0]

    normalized_allowed = {h.lower().strip() for h in allowed_hosts if h}
    if canonical:
        can_host = urlparse(canonical).netloc.lower().split(":")[0]
        if can_host:
            normalized_allowed.add(can_host)

    if not normalized_allowed:
        if is_probe_mode:
            return True, None
        return False, "CONFIG_EMPTY_ALLOWLIST"

    # Exact host match
    if host in normalized_allowed:
        return True, None

    # Opt-in subdomain matching
    if allow_subdomains:
        for allowed in normalized_allowed:
            if host.endswith("." + allowed):
                return True, None

    return False, host

def verify_content_markers(body: str, expected_markers: List[str]) -> bool:
    """Verifies that at least one of the expected content markers is present in the HTML body."""
    if not expected_markers:
        return True
    if not body:
        return False
    body_lower = body.lower()
    return any(marker.lower() in body_lower for marker in expected_markers)

def is_soft_404(status: Optional[int], body: str, title: Optional[str] = None) -> bool:
    """
    Detects soft 404 pages (HTTP 200 with 'Page Not Found' or Turkish equivalents in title/h1/body).
    """
    if status == 404:
        return True

    title_lower = (title or "").lower().strip()
    if title_lower:
        for ind in SOFT_404_INDICATORS:
            if ind in title_lower:
                return True

    if not body:
        return False

    # Remove script and style tags to avoid false positives on embedded JS translation tables
    body_no_script = re.sub(r'<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>', '', body[:15000], flags=re.IGNORECASE)
    body_no_script = re.sub(r'<style\b[^<]*(?:(?!<\/style>)<[^<]*)*<\/style>', '', body_no_script, flags=re.IGNORECASE)
    body_sample = body_no_script.lower()

    for ind in SOFT_404_INDICATORS:
        if ind in body_sample:
            return True

    return False
