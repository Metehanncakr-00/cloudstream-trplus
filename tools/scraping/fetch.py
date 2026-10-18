"""
fetch.py

3-Tier ProviderFetcher abstraction implementing HTTP -> DYNAMIC -> STEALTH strategy.
Integrated with Scrapling (FetcherSession, DynamicSession, StealthySession), session reuse,
explicit solve_cloudflare=True escalation, redirect safety checks, XHR interception, and security redaction.
"""

import os
import time
import json
import logging
from typing import Optional, Set, List, Dict, Any, Callable
from urllib.parse import urlparse

from .models import FetchMode, FetchStatus, FetchResult, CapturedXhr
from .detection import (
    is_cloudflare_challenge,
    is_bot_blocked,
    verify_redirect_safety,
    verify_content_markers
)
from .redaction import XhrRedactor

logger = logging.getLogger("ProviderFetcher")

def extract_response_text(response: Any) -> str:
    """
    Safely extracts response body as text without raising UnicodeDecodeError or altering JSON.
    Supports:
    - Empty body / None
    - Raw bytes with valid UTF-8
    - Declared charset in response or headers
    - Invalid UTF-8 with fallback (windows-1254, windows-1252, iso-8859-9, latin-1)
    - Replacement fallback (errors='replace')
    - String pass-through or safe text conversion
    """
    if response is None:
        return ""
    if isinstance(response, str):
        return response

    raw_bytes = None
    if isinstance(response, (bytes, bytearray)):
        raw_bytes = bytes(response)
    elif hasattr(response, "body") and isinstance(response.body, (bytes, bytearray)):
        raw_bytes = bytes(response.body)
    elif hasattr(response, "content") and isinstance(response.content, (bytes, bytearray)):
        raw_bytes = bytes(response.content)

    if raw_bytes is None:
        # Fallback to text if body is not available as bytes
        if hasattr(response, "text"):
            t = response.text
            if isinstance(t, str):
                return t
            return str(t or "")
        return ""

    if not raw_bytes:
        return ""

    # Check declared charset
    declared_encoding = getattr(response, "encoding", None)
    if not declared_encoding and hasattr(response, "headers"):
        headers = getattr(response, "headers", {}) or {}
        ct = headers.get("content-type", "") if isinstance(headers, dict) else getattr(headers, "get", lambda k, d="": "")("content-type", "")
        if "charset=" in ct.lower():
            declared_encoding = ct.lower().split("charset=")[-1].split(";")[0].strip()

    # 1. Try UTF-8 first (standard web)
    try:
        return raw_bytes.decode("utf-8")
    except UnicodeDecodeError:
        pass

    # 2. Try declared encoding if different from utf-8
    if declared_encoding and declared_encoding.lower() not in ("utf-8", "utf8"):
        try:
            return raw_bytes.decode(declared_encoding)
        except (UnicodeDecodeError, LookupError):
            pass

    # 3. Windows/Latin fallbacks (e.g. Turkish Windows 1254, Latin 1)
    for enc in ("windows-1254", "windows-1252", "iso-8859-9", "latin-1"):
        try:
            return raw_bytes.decode(enc)
        except (UnicodeDecodeError, LookupError):
            continue

    # 4. Final safety fallback: replacement (never fail-closed with UnicodeDecodeError)
    return raw_bytes.decode("utf-8", errors="replace")

class ProviderFetcher:
    """
    Central fetch engine for CloudStreamHub monitoring and research.
    
    Session Reuse:
    - HTTP Session: Reuses Scrapling FetcherSession across multiple requests.
    - Dynamic Session: Reuses Scrapling DynamicSession (Playwright browser context).
    - Stealth Session: Reuses Scrapling StealthySession (Anti-detect browser context).
    
    Tiers:
    - HTTP (Tier 1): High speed, curl_cffi with browser impersonation via FetcherSession.
    - DYNAMIC (Tier 2): Headless Playwright browser via DynamicSession for JS hydration.
    - STEALTH (Tier 3): Anti-detect Playwright browser via StealthySession with solve_cloudflare=True.
    """

    def __init__(
        self,
        allowed_hosts: Optional[Set[str]] = None,
        canonical: Optional[str] = None,
        timeout: int = 15,
        user_agent: Optional[str] = None,
        allow_subdomains: bool = False,
        is_probe_mode: bool = False
    ):
        self.allowed_hosts = allowed_hosts or set()
        self.canonical = canonical
        self.timeout = timeout
        self.user_agent = user_agent
        self.allow_subdomains = allow_subdomains
        self.is_probe_mode = is_probe_mode

        # Session instances for reuse
        self._http_session_manager = None
        self._http_client = None

        self._dynamic_session_manager = None
        self._dynamic_client = None

        self._stealth_session_manager = None
        self._stealth_client = None

    def _get_http_client(self):
        if self._http_client is None:
            try:
                from scrapling.fetchers import FetcherSession
                self._http_session_manager = FetcherSession(timeout=self.timeout)
                self._http_client = self._http_session_manager.__enter__()
            except Exception as e:
                logger.warning(f"Could not initialize FetcherSession: {e}")
                self._http_client = None
        return self._http_client

    def _get_dynamic_client(self):
        if self._dynamic_client is None:
            try:
                from scrapling.fetchers import DynamicSession
                self._dynamic_session_manager = DynamicSession(timeout=self.timeout * 1000)
                self._dynamic_client = self._dynamic_session_manager.__enter__()
            except Exception as e:
                logger.warning(f"Could not initialize DynamicSession: {e}")
                self._dynamic_client = None
        return self._dynamic_client

    def _get_stealth_client(self):
        if self._stealth_client is None:
            try:
                from scrapling.fetchers import StealthySession
                self._stealth_session_manager = StealthySession(timeout=self.timeout * 1000)
                self._stealth_client = self._stealth_session_manager.__enter__()
            except Exception as e:
                logger.warning(f"Could not initialize StealthySession: {e}")
                self._stealth_client = None
        return self._stealth_client

    def close(self):
        """Releases active browser sessions and network resources."""
        if self._http_session_manager and self._http_client:
            try:
                self._http_session_manager.__exit__(None, None, None)
            except Exception:
                pass
            self._http_session_manager = None
            self._http_client = None

        if self._dynamic_session_manager and self._dynamic_client:
            try:
                self._dynamic_session_manager.__exit__(None, None, None)
            except Exception:
                pass
            self._dynamic_session_manager = None
            self._dynamic_client = None

        if self._stealth_session_manager and self._stealth_client:
            try:
                self._stealth_session_manager.__exit__(None, None, None)
            except Exception:
                pass
            self._stealth_session_manager = None
            self._stealth_client = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

    def fetch(
        self,
        url: str,
        preferred_mode: FetchMode = FetchMode.HTTP,
        allow_dynamic_fallback: bool = True,
        allow_stealth_fallback: bool = True,
        capture_xhr: bool = False,
        expected_markers: Optional[List[str]] = None,
        method: str = "GET",
        data: Optional[Any] = None,
        headers: Optional[Dict[str, str]] = None
    ) -> FetchResult:
        """
        Executes request following preferred mode, fallback rules, and Cloudflare/XHR requirements.
        """
        # RULE: If capture_xhr=True, HTTP cannot capture background requests; must run in browser
        if capture_xhr and preferred_mode == FetchMode.HTTP:
            preferred_mode = FetchMode.DYNAMIC

        # Escalation path
        current_mode = preferred_mode
        last_result: Optional[FetchResult] = None

        while True:
            res = self._fetch_single(
                url=url,
                mode=current_mode,
                capture_xhr=capture_xhr,
                method=method,
                data=data,
                headers=headers
            )
            last_result = res

            # Untrusted redirect or config error: stop immediately (fail-closed)
            if res.status in (FetchStatus.UNTRUSTED_REDIRECT, FetchStatus.CONFIG_ERROR):
                return res

            # Cloudflare Challenge Detected
            if res.cloudflare or res.status == FetchStatus.CLOUDFLARE:
                if current_mode != FetchMode.STEALTH and allow_stealth_fallback:
                    # ESCALATE DIRECTLY TO STEALTH with solve_cloudflare=True (skip useless dynamic round)
                    current_mode = FetchMode.STEALTH
                    continue
                else:
                    return res

            # General WAF / Anti-Bot Blocked (403/429)
            if res.blocked or res.status == FetchStatus.BLOCKED:
                if current_mode != FetchMode.STEALTH and allow_stealth_fallback:
                    current_mode = FetchMode.STEALTH
                    continue
                elif current_mode == FetchMode.HTTP and allow_dynamic_fallback:
                    current_mode = FetchMode.DYNAMIC
                    continue
                else:
                    return res

            # Content Markers Validation (Fail-closed)
            if res.status == FetchStatus.SUCCESS and expected_markers:
                if not verify_content_markers(res.body, expected_markers):
                    # Missing markers on HTTP might be a JS shell: try DYNAMIC then STEALTH
                    if current_mode == FetchMode.HTTP and allow_dynamic_fallback:
                        current_mode = FetchMode.DYNAMIC
                        continue
                    elif current_mode == FetchMode.DYNAMIC and allow_stealth_fallback:
                        current_mode = FetchMode.STEALTH
                        continue
                    else:
                        # All tiers exhausted and markers still missing -> fail-closed!
                        res.status = FetchStatus.CONTENT_MARKER_MISMATCH
                        res.error = f"Expected markers missing: {expected_markers}"
                        return res

            # Normal success or terminal state
            break

        return last_result if last_result else FetchResult(
            requestedUrl=url,
            finalUrl=url,
            statusCode=None,
            fetchMode=preferred_mode,
            status=FetchStatus.NETWORK_ERROR,
            error="No fetch attempts succeeded"
        )

    def _fetch_single(
        self,
        url: str,
        mode: FetchMode,
        capture_xhr: bool = False,
        method: str = "GET",
        data: Optional[Any] = None,
        headers: Optional[Dict[str, str]] = None
    ) -> FetchResult:
        t0 = time.time()

        try:
            if mode == FetchMode.HTTP:
                return self._fetch_http(url, t0, method=method, data=data, headers=headers)
            elif mode == FetchMode.DYNAMIC:
                return self._fetch_browser(url, t0, stealth=False, capture_xhr=capture_xhr, headers=headers)
            elif mode == FetchMode.STEALTH:
                return self._fetch_browser(url, t0, stealth=True, capture_xhr=capture_xhr, headers=headers)
            else:
                raise ValueError(f"Unknown fetch mode: {mode}")
        except Exception as e:
            elapsed = int((time.time() - t0) * 1000)
            err_str = str(e)
            status = FetchStatus.NETWORK_ERROR
            if "timeout" in err_str.lower():
                status = FetchStatus.TIMEOUT
            return FetchResult(
                requestedUrl=url,
                finalUrl=url,
                statusCode=None,
                fetchMode=mode,
                status=status,
                elapsedMs=elapsed,
                error=err_str
            )

    def _fetch_http(
        self,
        url: str,
        t0: float,
        method: str = "GET",
        data: Optional[Any] = None,
        headers: Optional[Dict[str, str]] = None
    ) -> FetchResult:
        req_headers = headers or {}

        # Use reused FetcherSession client if available, else static Fetcher
        client = self._get_http_client()
        if client is not None:
            if method.upper() == "POST":
                resp = client.post(url, data=data, headers=req_headers)
            else:
                resp = client.get(url, headers=req_headers)
        else:
            from scrapling import Fetcher
            if method.upper() == "POST":
                resp = Fetcher.post(url, data=data, headers=req_headers, timeout=self.timeout)
            else:
                resp = Fetcher.get(url, headers=req_headers, timeout=self.timeout)

        elapsed = int((time.time() - t0) * 1000)
        final_url = getattr(resp, "url", url)
        status_code = getattr(resp, "status", None)

        body = extract_response_text(resp)

        resp_headers = getattr(resp, "headers", {}) or {}

        # 1. Verify redirect safety (strict exact match by default)
        safe, candidate_host = verify_redirect_safety(
            final_url,
            self.allowed_hosts,
            self.canonical,
            allow_subdomains=self.allow_subdomains,
            is_probe_mode=self.is_probe_mode
        )
        if not safe:
            if candidate_host == "CONFIG_EMPTY_ALLOWLIST":
                return FetchResult(
                    requestedUrl=url,
                    finalUrl=final_url,
                    statusCode=status_code,
                    fetchMode=FetchMode.HTTP,
                    status=FetchStatus.CONFIG_ERROR,
                    elapsedMs=elapsed,
                    error="Allowed hosts configuration is empty"
                )
            return FetchResult(
                requestedUrl=url,
                finalUrl=final_url,
                statusCode=status_code,
                fetchMode=FetchMode.HTTP,
                status=FetchStatus.UNTRUSTED_REDIRECT,
                elapsedMs=elapsed,
                candidateHost=candidate_host,
                error=f"Redirected to untrusted host: {candidate_host}"
            )

        # 2. Check Cloudflare Challenge
        is_cf = is_cloudflare_challenge(status_code, body, resp_headers)
        if is_cf:
            return FetchResult(
                requestedUrl=url,
                finalUrl=final_url,
                statusCode=status_code,
                fetchMode=FetchMode.HTTP,
                status=FetchStatus.CLOUDFLARE,
                cloudflare=True,
                blocked=True,
                elapsedMs=elapsed,
                body=body
            )

        # 3. Check General Anti-Bot Block
        is_blocked = is_bot_blocked(status_code, body)
        if is_blocked:
            return FetchResult(
                requestedUrl=url,
                finalUrl=final_url,
                statusCode=status_code,
                fetchMode=FetchMode.HTTP,
                status=FetchStatus.BLOCKED,
                blocked=True,
                elapsedMs=elapsed,
                body=body
            )

        # 4. Standard HTTP status check
        fetch_status = FetchStatus.SUCCESS if (status_code and 200 <= status_code < 400) else FetchStatus.NETWORK_ERROR

        return FetchResult(
            requestedUrl=url,
            finalUrl=final_url,
            statusCode=status_code,
            fetchMode=FetchMode.HTTP,
            status=fetch_status,
            elapsedMs=elapsed,
            body=body
        )

    def _fetch_browser(
        self,
        url: str,
        t0: float,
        stealth: bool = False,
        capture_xhr: bool = False,
        headers: Optional[Dict[str, str]] = None
    ) -> FetchResult:
        mode = FetchMode.STEALTH if stealth else FetchMode.DYNAMIC
        captured_raw: List[Dict[str, Any]] = []

        def page_setup_hook(page):
            if headers:
                safe_browser_headers = {k: v for k, v in headers.items() if k.lower() != "referer"}
                if safe_browser_headers:
                    try:
                        page.set_extra_http_headers(safe_browser_headers)
                    except Exception:
                        pass

            if capture_xhr:
                def on_request(req):
                    try:
                        res_type = req.resource_type
                        if res_type in ("xhr", "fetch", "websocket", "media", "script"):
                            captured_raw.append({
                                "url": req.url,
                                "method": req.method,
                                "resourceType": res_type,
                                "headers": req.headers,
                                "postData": req.post_data
                            })
                    except Exception:
                        pass

                def on_response(resp):
                    try:
                        resp_url = resp.url
                        for item in reversed(captured_raw):
                            if item["url"] == resp_url:
                                item["status"] = resp.status
                                item["responseHeaders"] = resp.headers
                                break
                    except Exception:
                        pass

                page.on("request", on_request)
                page.on("response", on_response)

        fetch_kwargs: Dict[str, Any] = {
            "page_setup": page_setup_hook,
            "timeout": self.timeout * 1000,
            "network_idle": True
        }
        if stealth:
            fetch_kwargs["solve_cloudflare"] = True

        # Use reused browser session client if available
        client = self._get_stealth_client() if stealth else self._get_dynamic_client()
        if client is not None:
            resp = client.fetch(url, **fetch_kwargs)
        else:
            from scrapling import DynamicFetcher, StealthyFetcher
            fetcher_cls = StealthyFetcher if stealth else DynamicFetcher
            resp = fetcher_cls.fetch(url, **fetch_kwargs)

        elapsed = int((time.time() - t0) * 1000)

        final_url = getattr(resp, "url", url)
        status_code = getattr(resp, "status", 200)

        body = extract_response_text(resp)

        resp_headers = getattr(resp, "headers", {}) or {}
        sanitized_xhrs = [XhrRedactor.sanitize_captured_xhr(x) for x in captured_raw]

        # 1. Verify redirect safety
        safe, candidate_host = verify_redirect_safety(
            final_url,
            self.allowed_hosts,
            self.canonical,
            allow_subdomains=self.allow_subdomains,
            is_probe_mode=self.is_probe_mode
        )
        if not safe:
            if candidate_host == "CONFIG_EMPTY_ALLOWLIST":
                return FetchResult(
                    requestedUrl=url,
                    finalUrl=final_url,
                    statusCode=status_code,
                    fetchMode=mode,
                    status=FetchStatus.CONFIG_ERROR,
                    browserUsed=True,
                    elapsedMs=elapsed,
                    capturedXhr=sanitized_xhrs,
                    error="Allowed hosts configuration is empty"
                )
            return FetchResult(
                requestedUrl=url,
                finalUrl=final_url,
                statusCode=status_code,
                fetchMode=mode,
                status=FetchStatus.UNTRUSTED_REDIRECT,
                browserUsed=True,
                elapsedMs=elapsed,
                candidateHost=candidate_host,
                capturedXhr=sanitized_xhrs,
                error=f"Redirected to untrusted host: {candidate_host}"
            )

        # 2. Check Cloudflare
        is_cf = is_cloudflare_challenge(status_code, body, resp_headers)
        if is_cf:
            return FetchResult(
                requestedUrl=url,
                finalUrl=final_url,
                statusCode=status_code,
                fetchMode=mode,
                status=FetchStatus.CLOUDFLARE,
                cloudflare=True,
                blocked=True,
                browserUsed=True,
                elapsedMs=elapsed,
                body=body,
                capturedXhr=sanitized_xhrs
            )

        # 3. Check General Anti-Bot Block
        is_blocked = is_bot_blocked(status_code, body)
        if is_blocked:
            return FetchResult(
                requestedUrl=url,
                finalUrl=final_url,
                statusCode=status_code,
                fetchMode=mode,
                status=FetchStatus.BLOCKED,
                blocked=True,
                browserUsed=True,
                elapsedMs=elapsed,
                body=body,
                capturedXhr=sanitized_xhrs
            )

        fetch_status = FetchStatus.SUCCESS if (status_code and 200 <= status_code < 400) else FetchStatus.NETWORK_ERROR

        return FetchResult(
            requestedUrl=url,
            finalUrl=final_url,
            statusCode=status_code,
            fetchMode=mode,
            status=fetch_status,
            browserUsed=True,
            elapsedMs=elapsed,
            body=body,
            capturedXhr=sanitized_xhrs
        )
