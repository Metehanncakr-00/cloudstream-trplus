"""
discovery.py

Shared semantic discovery helpers for CloudStreamHub health monitoring, live smoke testing, and probe tooling.
Ensures unified logic across provider_health.py, live_provider_smoke.py, and provider_probe.py:
- Content Item Discovery (Cards/posters): Strict count > 0 for PASS.
- Soft 404 validation: Checks HTTP status, title, and body tokens.
- Player Discovery with Chaining:
  * Movie: Detail page -> Player discovery.
  * TvSeries / Anime: Detail page -> Discover first episode link -> Fetch episode page -> Player discovery.
  * Strong player evidence: iframe[src], video[src], .m3u8 URLs, JWPlayer / VideoJS initializations.
- Subtitle Classification: TRUTHFUL distinction between VTT/SRT, DUBBED, HARDSUB, or NONE.
"""

import re
from typing import Dict, Any, List, Optional, Tuple
from urllib.parse import urljoin, urlparse
from bs4 import BeautifulSoup

from .models import FetchMode, FetchStatus, FetchResult
from .detection import is_soft_404
from .redaction import XhrRedactor

KNOWN_CARD_SELECTORS = [
    "a.mcard", "a.dcard", "a.poster", "a.item", "a[href*='/belgesel/']",
    "div.poster a", ".listmovie a", ".panel a", "article a", ".film-content a",
    "div.film-box a", "div.menulink a", "div.kutu a", "a.card", "div.title-card a"
]

STRONG_PLAYER_HOSTS = [
    "vidpapi", "closeload", "rapidrame", "streamtape", "doodstream",
    "vidsrc", "superembed", "player", "embed", "upstream"
]

# Navigation and Category indicators to exclude from content items
NAVIGATION_TOKENS = {
    ".#0-9", "#0-9", "0-9", "#", "tüm liste", "tum liste", "sayfa", "page",
    "kategori", "kategoriler", "kategori seç", "türler", "turler", "filmler", "diziler", "anime", "animeler",
    "aksiyon", "komedi", "korku", "dram", "romantik", "animasyon", "belgesel", "bilim kurgu",
    "macera", "suç", "suc", "fantastik", "gerilim", "gizem", "savaş", "savas", "western"
}

GENERIC_EXCLUDE_URL_PATTERNS = [
    "login", "register", "giris", "kayit", "facebook", "twitter", "instagram",
    "youtube", "iletisim", "hakkinda", "dmca", "sikayet", "privacy", "terms",
    "/kategori/", "/category/", "/genre/", "/tur/", "/etiket/", "/tag/",
    "/harf/", "/harf-", "/takvim", "/siralama", "/top-", "/sayfa/", "/page/"
]

def discover_homepage_cards(
    html_body: str,
    base_url: str,
    selector_override: Optional[str] = None,
    homepage_cfg: Optional[Dict[str, Any]] = None
) -> List[Dict[str, Any]]:
    """
    Extracts valid content cards from homepage HTML with strict navigation/category filtering.
    Invariant: navigation link != content item, category link != content item.
    """
    if not html_body:
        return []

    soup = BeautifulSoup(html_body, "html.parser")

    cfg = homepage_cfg or {}
    selectors_to_check = cfg.get("selectors")
    if not selectors_to_check:
        if selector_override:
            selectors_to_check = [s.strip() for s in selector_override.split(",")]
        else:
            selectors_to_check = KNOWN_CARD_SELECTORS

    required_patterns = cfg.get("requiredUrlPatterns", [])
    excluded_patterns = cfg.get("excludedUrlPatterns", [])

    cards = []
    seen_urls = set()

    for sel in selectors_to_check:
        for tag in soup.select(sel):
            href = tag.get("href")
            title = tag.get("title") or tag.text.strip()
            img = tag.find("img")
            poster = (img.get("src") or img.get("data-src") or img.get("data-lazy-src")) if img else None

            if not href:
                continue

            abs_href = urljoin(base_url, href)

            # 1. Deduplication
            if abs_href in seen_urls or abs_href.rstrip("/") == base_url.rstrip("/"):
                continue

            # 2. Strict Navigation Token check (title)
            normalized_title = title.lower().strip()
            if normalized_title in NAVIGATION_TOKENS or (len(normalized_title) <= 2 and not normalized_title.isalnum()):
                continue

            # 3. Excluded URL patterns check (generic + config-specific)
            all_excluded = GENERIC_EXCLUDE_URL_PATTERNS + [p.lower() for p in excluded_patterns]
            if any(p in abs_href.lower() for p in all_excluded):
                continue

            # 4. Required URL patterns check (if configured)
            if required_patterns:
                if not any(req.lower() in abs_href.lower() for req in required_patterns):
                    continue

            # 5. Content item must have a real title or poster
            if not title and not poster:
                continue

            seen_urls.add(abs_href)
            cards.append({
                "title": (title or "Discovered Title")[:80].strip(),
                "url": abs_href,
                "poster": urljoin(base_url, poster) if poster else None,
                "selector": sel
            })
            if len(cards) >= 15:
                break
        if cards:
            break

    return cards

def parse_detail_page(
    html_body: str,
    status_code: Optional[int],
    base_url: str,
    episode_selector: Optional[str] = None,
    fetcher: Optional[Any] = None
) -> Dict[str, Any]:
    """
    Validates and extracts detail page metadata with soft-404 rejection and truthful episode discovery:
    - Primary: monitoring.playerProbe.episodeSelector (STRICT_CONFIG)
    - Fallback: Generic episode selectors (GENERIC_FALLBACK)
    """
    result = {
        "status": "FAIL",
        "title": None,
        "isSoft404": False,
        "episodeLinks": [],
        "episodeDiscoveryMode": "NONE",
        "reason": None
    }

    if not html_body or status_code == 404:
        result["status"] = "FAIL"
        result["reason"] = f"HTTP {status_code}"
        result["isSoft404"] = (status_code == 404)
        return result

    trimmed_body = html_body.strip()
    if trimmed_body.startswith("{") or trimmed_body.startswith("["):
        try:
            import json
            jdata = json.loads(trimmed_body)
            title = None
            if isinstance(jdata, dict):
                movie_obj = jdata.get("data", {}).get("movie") or jdata.get("data") or jdata
                if isinstance(movie_obj, dict):
                    title = movie_obj.get("title") or movie_obj.get("name")
                if not title:
                    title = jdata.get("title") or jdata.get("status_message")
            result["status"] = "PASS"
            result["title"] = (str(title) if title else "JSON API Content")[:80]
            return result
        except Exception:
            pass

    soup = BeautifulSoup(html_body, "html.parser")
    title_tag = soup.select_one("h1, h2, meta[property='og:title'], title")
    title = title_tag.text.strip() if title_tag else "Unknown"

    if is_soft_404(status_code, html_body, title):
        result["status"] = "FAIL"
        result["title"] = title[:50]
        result["isSoft404"] = True
        result["reason"] = "SOFT_404_PAGE"
        return result

    result["status"] = "PASS"
    result["title"] = title[:80]

    seen_eps = set()

    # Helper to validate and clean episode URL
    def is_valid_episode_url(url_str: str) -> bool:
        low = url_str.lower()
        # Exclude non-episode pages that contain words like 'sezon' or 'bolum'
        if any(bad in low for bad in [
            "yakinda", "yeni-sezon-animeleri", "sezon-animeleri", "kategori",
            "category", "genre", "tur/", "etiket", "tag", "facebook", "twitter", "#"
        ]):
            return False
        return True

    # 1. PRIMARY: Configured episodeSelector or TurkAnime AJAX bolumler
    # Check for TurkAnime AJAX bolumler button first
    bolum_btn = soup.select_one("a[data-url*='ajax/bolumler']")
    if bolum_btn and fetcher:
        data_url = bolum_btn.get("data-url")
        if data_url:
            parsed_u = urlparse(base_url)
            root_u = f"{parsed_u.scheme}://{parsed_u.netloc}/"
            ajax_url = urljoin(root_u, data_url.lstrip("/"))
            token_el = soup.select_one("meta[name='_token']")
            token = token_el.get("content") if token_el else ""
            headers = {
                "X-Requested-With": "XMLHttpRequest",
                "token": token,
                "Referer": base_url
            }
            try:
                ajax_res = fetcher.fetch(ajax_url, headers=headers)
                if ajax_res.statusCode == 200 and ajax_res.body:
                    ajax_soup = BeautifulSoup(ajax_res.body, "html.parser")
                    for a in ajax_soup.select("a[href*='/video/']"):
                        ep_href = a.get("href")
                        if ep_href:
                            abs_ep = urljoin(base_url, ep_href)
                            if abs_ep not in seen_eps and is_valid_episode_url(abs_ep):
                                seen_eps.add(abs_ep)
                                result["episodeLinks"].append(abs_ep)
                    if result["episodeLinks"]:
                        result["episodeDiscoveryMode"] = "STRICT_CONFIG"
            except Exception:
                pass

    if not result["episodeLinks"] and episode_selector:
        selectors_list = [s.strip() for s in episode_selector.split(",") if s.strip()]
        for sel in selectors_list:
            if "ajax/bolumler" in sel:
                continue
            for tag in soup.select(sel):
                href = tag.get("href") or tag.get("data-href")
                if not href and tag.get("onclick"):
                    # Extract from onclick (e.g., IndexIcerik('...'))
                    m = re.search(r"'(ajax/[^']+)'", tag.get("onclick"))
                    if m:
                        href = m.group(1)

                if href and "ajax/bolumler" not in href:
                    abs_href = urljoin(base_url, href)
                    if abs_href not in seen_eps and abs_href != base_url and is_valid_episode_url(abs_href):
                        seen_eps.add(abs_href)
                        result["episodeLinks"].append(abs_href)

        if result["episodeLinks"]:
            result["episodeDiscoveryMode"] = "STRICT_CONFIG"

    # 3. FALLBACK: Generic episode selectors
    if not result["episodeLinks"]:
        ep_selectors = [
            "a[href*='bolum']", "a[href*='episode']", "a[href*='sezon']",
            ".episode-item a", ".season-item a", "a.episode", "#bolumler a",
            "a[href*='/video/']", "a[href*='/titles/']"
        ]
        for sel in ep_selectors:
            for tag in soup.select(sel):
                href = tag.get("href")
                if href:
                    abs_href = urljoin(base_url, href)
                    if abs_href not in seen_eps and abs_href != base_url and is_valid_episode_url(abs_href):
                        seen_eps.add(abs_href)
                        result["episodeLinks"].append(abs_href)
            if result["episodeLinks"]:
                result["episodeDiscoveryMode"] = "GENERIC_FALLBACK"
                break

    return result

def evaluate_player_discovery(html_body: str, base_url: str) -> Tuple[str, Dict[str, Any]]:
    """
    Evaluates presence of real player containers without generic 'player' substring false positives.
    Reports: 'PLAYER_DISCOVERED' or 'PLAYER_NOT_FOUND'.
    Strong Evidence:
    - Real iframes with src or data-src pointing to video/embed hosts
    - HTML5 <video> with <source src="...">
    - Embedded m3u8 or mp4 URLs in script initializations
    - JWPlayer or VideoJS initialization with file/source property
    """
    info = {
        "iframes": [],
        "videos": [],
        "mediaUrls": [],
        "hasPlayerScript": False
    }

    if not html_body:
        return "PLAYER_NOT_FOUND", info

    soup = BeautifulSoup(html_body, "html.parser")

    # 1. Inspect Iframes
    for ifr in soup.find_all("iframe"):
        src = ifr.get("src") or ifr.get("data-src") or ifr.get("data-lazy-src")
        if src:
            abs_src = urljoin(base_url, src)
            # Filter out non-video ads/analytics and trailer iframes
            if not any(ign in abs_src.lower() for ign in ["google", "recaptcha", "analytics", "disqus", "ads", "youtube.com", "youtu.be"]):
                info["iframes"].append(XhrRedactor.sanitize_url(abs_src))

    # 2. Inspect HTML5 Video elements
    for v in soup.find_all("video"):
        src = v.get("src")
        if src:
            info["videos"].append(XhrRedactor.sanitize_url(urljoin(base_url, src)))
        for source in v.find_all("source"):
            ssrc = source.get("src")
            if ssrc:
                info["videos"].append(XhrRedactor.sanitize_url(urljoin(base_url, ssrc)))

    # 3. Inspect Script player initializations (m3u8, closeload, vidpapi, jwplayer sources)
    body_str = html_body
    m3u8_matches = re.findall(r'https?://[^"\'\s<>]+\.(?:m3u8|mp4|mpd)[^"\'\s<>]*', body_str, re.IGNORECASE)
    for m in m3u8_matches[:5]:
        info["mediaUrls"].append(XhrRedactor.sanitize_url(m))

    body_without_scx = re.sub(r'var\s+scx\s*=\s*(\{.+?\});', '', body_str, flags=re.DOTALL | re.IGNORECASE)
    has_player_code = bool(
        re.search(r'(?:jwplayer|videojs|Playerjs)\s*\(', body_without_scx, re.IGNORECASE) or
        any(host in body_without_scx.lower() for host in ["vidpapi", "closeload", "rapidrame", "streamtape", "doodstream", "vidsrc", "superembed"]) or
        re.search(r'(?:file|source)\s*:\s*["\']https?://[^"\']+\.(?:m3u8|mp4)', body_without_scx, re.IGNORECASE)
    )
    # Star TV mounts its public Dyg player after hydration. A container alone
    # or an SDK preload alone does not establish player discovery.
    if soup.select_one("#dyg-player") and soup.select_one(
        'link[rel="preload"][as="script"][href="https://player-assets.dygdigital.com/prod/js/bundle.js"]'
    ) and re.search(r'"referenceId"\s*:\s*"[a-f0-9]{24}"', body_str.replace('\\"', '"')):
        has_player_code = True
    info["hasPlayerScript"] = has_player_code

    import base64
    for b64_match in re.finditer(r'const\s+encodedContent\s*=\s*[\'"]([A-Za-z0-9+/=]+)[\'"]', body_str):
        try:
            decoded_snippet = base64.b64decode(b64_match.group(1)).decode("utf-8", errors="ignore")
            ifr_m = re.search(r'src=[\'"]([^\'"]+)[\'"]', decoded_snippet)
            if ifr_m:
                abs_src = urljoin(base_url, ifr_m.group(1))
                info["iframes"].append(XhrRedactor.sanitize_url(abs_src))
                info["hasPlayerScript"] = True
        except Exception:
            pass

    # 5. Inspect SCX sources
    scx_match = re.search(r'var\s+scx\s*=\s*(\{.+?\});', body_str, re.DOTALL | re.IGNORECASE)
    if scx_match:
        import codecs
        raw_json = scx_match.group(1)
        for token_m in re.finditer(r'"([a-zA-Z0-9+/=_-]{10,})"', raw_json):
            token = token_m.group(1)
            if len(token) < 15:
                continue
            try:
                rtt = codecs.decode(token, 'rot_13')
                pad_len = (4 - len(rtt) % 4) % 4
                padded = rtt + ("=" * pad_len)
                decoded = base64.b64decode(padded).decode("utf-8")
                if decoded.startswith("http://") or decoded.startswith("https://") or decoded.startswith("//"):
                    full_url = "https:" + decoded if decoded.startswith("//") else decoded
                    info["iframes"].append(XhrRedactor.sanitize_url(full_url))
                    info["hasPlayerScript"] = True
            except Exception:
                pass

    if info["iframes"] or info["videos"] or info["mediaUrls"] or has_player_code:
        return "PLAYER_DISCOVERED", info

    # Check for Torrent / Magnet links
    if "magnet:?" in body_str or ".torrent" in body_str.lower() or '"torrents":' in body_str:
        info["mediaUrls"].append("torrent_stream_source")
        return "PLAYER_DISCOVERED", info

    return "PLAYER_NOT_FOUND", info

def classify_subtitles(html_body: str, title: Optional[str] = None) -> str:
    """
    Accurately classifies subtitle availability:
    - 'VTT/SRT': WebVTT or SRT track discovered
    - 'HARDSUB': Explicitly hardsubbed (gömülü altyazı)
    - 'DUBBED': Audio dubbing without separate subtitles
    - 'NONE': No subtitle tracks or markers discovered
    """
    if not html_body:
        return "NONE"

    body_lower = html_body.lower()
    title_lower = (title or "").lower()

    if ".vtt" in body_lower or ".srt" in body_lower or "<track" in body_lower:
        return "VTT/SRT"

    if "altyazı" in title_lower or "altyazılı" in title_lower or "gömülü altyazı" in body_lower:
        return "HARDSUB"

    if "dublaj" in title_lower or "türkçe dublaj" in body_lower:
        return "DUBBED"

    return "NONE"
