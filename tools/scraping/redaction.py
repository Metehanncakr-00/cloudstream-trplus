"""
redaction.py

Security and privacy redaction layer for captured XHRs, requests, headers,
query parameters, tokens, and media URLs.
"""

import re
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse

SAFE_HEADER_KEYS = {
    "user-agent",
    "accept",
    "accept-encoding",
    "accept-language",
    "accept-charset",
    "content-type",
    "content-length",
    "origin",
    "referer",
    "host",
    "connection",
    "cache-control",
    "upgrade-insecure-requests",
    "sec-ch-ua",
    "sec-ch-ua-mobile",
    "sec-ch-ua-platform",
    "sec-fetch-dest",
    "sec-fetch-mode",
    "sec-fetch-site",
    "sec-fetch-user",
    "x-requested-with",
    "pragma",
    "dnt",
}

SENSITIVE_HEADER_KEYS = {
    "authorization",
    "cookie",
    "set-cookie",
    "proxy-authorization",
    "x-auth-token",
    "x-api-key",
    "cf-access-token",
    "token",
    "x-e-h",
    "api-key",
    "apikey",
}

# Matches x-*-token, x-*-auth, x-*-key, x-e-h, api-key, authorization-like custom keys
AUTH_LIKE_HEADER_PATTERN = re.compile(
    r"^(x-.*-(token|auth|key)|x-e-h|.*api[-_]?key.*|.*authorization.*|.*cookie.*|.*token.*)$",
    re.IGNORECASE
)

SENSITIVE_PARAM_PATTERNS = [
    re.compile(r"^(token|auth|sig|signature|key|expires|secret|pass|passwd|password|jwt)$", re.IGNORECASE),
    re.compile(r".*(token|signature|secret|auth).*", re.IGNORECASE)
]

# Body JSON field key matcher: field name contains token, secret, password, credential, authorization, apikey/api_key, or key
SENSITIVE_BODY_KEY_PATTERN = re.compile(
    r'("(?=[^"]*(?:token|secret|password|credential|authorization|api[-_]?key|(?<![a-zA-Z0-9])key(?![a-zA-Z0-9])))[^"]*"\s*:\s*)"[^"]*"',
    re.IGNORECASE
)

JWT_REGEX = re.compile(r"eyJ[a-zA-Z0-9_-]{10,}\.eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}")
BEARER_REGEX = re.compile(r"Bearer\s+[a-zA-Z0-9_\-\.=]+", re.IGNORECASE)

class XhrRedactor:
    """Sanitizes sensitive security headers, parameters, tokens and ephemeral media URLs."""

    @staticmethod
    def sanitize_header_value(key: str, value: str) -> str:
        key_lower = key.lower().strip()
        # Safe headers are never redacted by key name
        if key_lower in SAFE_HEADER_KEYS:
            if JWT_REGEX.search(value) or BEARER_REGEX.search(value):
                return "<REDACTED_TOKEN>"
            return value

        # Explicit sensitive keys or auth-like pattern match
        if (key_lower in SENSITIVE_HEADER_KEYS or
            AUTH_LIKE_HEADER_PATTERN.match(key_lower) or
            any(s in key_lower for s in ("auth", "token", "cookie", "key"))):
            return "<REDACTED>"

        # Also redact if value itself looks like a Bearer token or JWT
        if JWT_REGEX.search(value) or BEARER_REGEX.search(value):
            return "<REDACTED_TOKEN>"
        return value

    @staticmethod
    def sanitize_headers(headers: Dict[str, str]) -> Dict[str, str]:
        if not headers:
            return {}
        sanitized = {}
        for k, v in headers.items():
            sanitized[k] = XhrRedactor.sanitize_header_value(k, str(v))
        return sanitized

    @staticmethod
    def sanitize_url(url: str) -> str:
        if not url:
            return ""
        try:
            parsed = urlparse(url)
            # Check for media streams with query strings (e.g. .m3u8, .mpd, .ts)
            path_lower = parsed.path.lower()
            query_str = parsed.query
            path_str = parsed.path
            is_pseudo_query = False

            if "&" in path_str and "=" in path_str:
                parts = path_str.split("&", 1)
                path_str = parts[0]
                query_str = parts[1] + ("&" + parsed.query if parsed.query else "")
                is_pseudo_query = True

            is_media_or_player = any(path_lower.endswith(ext) for ext in (".m3u8", ".mpd", ".ts", ".mp4", ".m4s")) or "ajax/videosec" in path_lower
            if is_media_or_player and query_str:
                redacted_query = "<REDACTED_QUERY>"
                if is_pseudo_query and not parsed.query:
                    new_path = path_str + "&" + redacted_query
                    return urlunparse((parsed.scheme, parsed.netloc, new_path, parsed.params, "", parsed.fragment))
                else:
                    return urlunparse((parsed.scheme, parsed.netloc, path_str, parsed.params, redacted_query, parsed.fragment))

            if not query_str:
                return url

            query_dict = parse_qs(query_str, keep_blank_values=True)
            new_query = {}
            for param, values in query_dict.items():
                if any(pat.match(param) for pat in SENSITIVE_PARAM_PATTERNS):
                    new_query[param] = ["<REDACTED>"]
                else:
                    new_query[param] = [
                        "<REDACTED_TOKEN>" if (JWT_REGEX.search(val) or len(val) > 128) else val
                        for val in values
                    ]

            redacted_query = urlencode(new_query, doseq=True)

            if is_pseudo_query and not parsed.query:
                new_path = path_str + "&" + redacted_query
                return urlunparse((parsed.scheme, parsed.netloc, new_path, parsed.params, "", parsed.fragment))
            else:
                return urlunparse((parsed.scheme, parsed.netloc, path_str, parsed.params, redacted_query, parsed.fragment))
        except Exception:
            return url

    @staticmethod
    def sanitize_body(body: Optional[str]) -> Optional[str]:
        if not body:
            return body
        # Redact JWTs and Bearer tokens in raw string payloads
        sanitized = JWT_REGEX.sub("<REDACTED_JWT>", body)
        sanitized = BEARER_REGEX.sub("Bearer <REDACTED>", sanitized)
        # Redact passwords, secrets, tokens, credentials, keys in JSON-like fields
        sanitized = SENSITIVE_BODY_KEY_PATTERN.sub(r'\1"<REDACTED>"', sanitized)
        return sanitized

    @staticmethod
    def sanitize_captured_xhr(xhr_item: Dict[str, Any]) -> Dict[str, Any]:
        """Deep sanitization of a captured network request/response record."""
        sanitized = dict(xhr_item)
        if "url" in sanitized:
            sanitized["url"] = XhrRedactor.sanitize_url(sanitized["url"])
        if "headers" in sanitized and isinstance(sanitized["headers"], dict):
            sanitized["headers"] = XhrRedactor.sanitize_headers(sanitized["headers"])
        if "responseHeaders" in sanitized and isinstance(sanitized["responseHeaders"], dict):
            sanitized["responseHeaders"] = XhrRedactor.sanitize_headers(sanitized["responseHeaders"])
        if "postData" in sanitized and sanitized["postData"]:
            sanitized["postData"] = XhrRedactor.sanitize_body(str(sanitized["postData"]))
        return sanitized

    @staticmethod
    def redact_string(text: Optional[str]) -> str:
        """Sanitizes arbitrary text, stripping JWTs, auth patterns, and query params from URLs."""
        if not text:
            return ""
        sanitized = XhrRedactor.sanitize_body(str(text)) or ""
        urls = re.findall(r'https?://[^\s<>"\']+', sanitized)
        for u in urls:
            redacted_u = XhrRedactor.sanitize_url(u)
            if redacted_u != u:
                sanitized = sanitized.replace(u, redacted_u)

        # Redact player endpoint parameters regardless of ? syntax
        sanitized = re.sub(r'(ajax/videosec)[?&][^\s<>"\']+', r'\1&<REDACTED_QUERY>', sanitized)
        return sanitized
