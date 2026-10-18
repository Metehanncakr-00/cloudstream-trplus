"""
models.py

Standardized data structures for Scrapling-based provider monitoring and research.
"""

from enum import Enum
from dataclasses import dataclass, field
from typing import Optional, List, Dict, Any

class FetchMode(str, Enum):
    HTTP = "HTTP"
    DYNAMIC = "DYNAMIC"
    STEALTH = "STEALTH"

class FetchStatus(str, Enum):
    SUCCESS = "SUCCESS"
    BLOCKED = "BLOCKED"
    CLOUDFLARE = "CLOUDFLARE"
    TIMEOUT = "TIMEOUT"
    NETWORK_ERROR = "NETWORK_ERROR"
    UNTRUSTED_REDIRECT = "UNTRUSTED_REDIRECT"
    CONFIG_ERROR = "CONFIG_ERROR"
    CONTENT_MARKER_MISMATCH = "CONTENT_MARKER_MISMATCH"
    SCRAPLING_UNAVAILABLE = "SCRAPLING_UNAVAILABLE"

@dataclass
class CapturedXhr:
    url: str
    method: str
    resourceType: str
    headers: Dict[str, str] = field(default_factory=dict)
    postData: Optional[str] = None
    status: Optional[int] = None
    responseHeaders: Dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "url": self.url,
            "method": self.method,
            "resourceType": self.resourceType,
            "headers": self.headers,
            "postData": self.postData,
            "status": self.status,
            "responseHeaders": self.responseHeaders
        }

@dataclass
class FetchResult:
    requestedUrl: str
    finalUrl: str
    statusCode: Optional[int]
    fetchMode: FetchMode
    status: FetchStatus
    blocked: bool = False
    cloudflare: bool = False
    elapsedMs: int = 0
    browserUsed: bool = False
    body: str = ""
    error: Optional[str] = None
    redirectChain: List[str] = field(default_factory=list)
    candidateHost: Optional[str] = None
    capturedXhr: List[Dict[str, Any]] = field(default_factory=list)
    _selector: Optional[Any] = None

    def get_selector(self, adaptive: bool = False, storage_args: Optional[Dict[str, Any]] = None):
        """Lazy-constructs a Scrapling Selector from the body."""
        if self._selector is not None:
            return self._selector
        if not self.body:
            return None
        try:
            from scrapling import Selector
            self._selector = Selector(
                content=self.body,
                url=self.finalUrl,
                adaptive=adaptive,
                storage_args=storage_args
            )
            return self._selector
        except Exception:
            return None

    def to_summary_dict(self) -> Dict[str, Any]:
        return {
            "requestedUrl": self.requestedUrl,
            "finalUrl": self.finalUrl,
            "statusCode": self.statusCode,
            "fetchMode": self.fetchMode.value,
            "status": self.status.value,
            "blocked": self.blocked,
            "cloudflare": self.cloudflare,
            "elapsedMs": self.elapsedMs,
            "browserUsed": self.browserUsed,
            "error": self.error,
            "candidateHost": self.candidateHost,
            "capturedXhrCount": len(self.capturedXhr)
        }
