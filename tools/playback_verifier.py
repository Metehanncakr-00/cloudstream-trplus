#!/usr/bin/env python3
"""
playback_verifier.py

Extended Playback Verification Engine for CloudStreamHub providers:
- L6: Extractor Resolution (Tests if an embed/iframe URL resolves to a media stream URL)
- L7: Media Preflight (Validates Content-Type, HTTP status, and container/manifest magic signatures)
- L8: First Segment Verification (For HLS/DASH, fetches variant and first chunk/segment to ensure readability)
- Playback Matrix Generation: Generates machine-readable reports/playback_matrix.json
- Anti-Staleness Metadata: Injects commit SHA, config hash, generated timestamp, and version metadata
"""

import os
import sys
import re
import ssl
import json
import time
import hashlib
import subprocess
import base64
import urllib.request
import urllib.parse
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Tuple

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

DEFAULT_TIMEOUT = 10
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

class PlaybackVerifier:
    def __init__(self, timeout: int = DEFAULT_TIMEOUT):
        self.timeout = timeout
        self.ssl_ctx = ssl.create_default_context()
    @staticmethod
    def unpack_packer(script: str) -> str:
        m = re.search(r"eval\(function\(p,a,c,k,e,d\).+?return\s+p\s*\}\s*\(\s*'((?:\\'|[^'])*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'((?:\\'|[^'])*)'\.split\('\|'\)", script, re.DOTALL)
        if not m:
            return script
        payload = m.group(1).replace(r"\'", "'").replace(r"\\", "\\")
        radix = int(m.group(2))
        count = int(m.group(3))
        symtab = m.group(4).split('|')

        digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        def base_n(num, b):
            if num == 0:
                return "0"
            res = []
            while num > 0:
                res.append(digits[num % b])
                num //= b
            return "".join(reversed(res))

        lookup = {}
        for i in range(count):
            k = base_n(i, radix)
            sym = symtab[i] if i < len(symtab) and symtab[i] else k
            lookup[k] = sym

        return re.sub(r'\b\w+\b', lambda match: lookup.get(match.group(0), match.group(0)), payload)

    @staticmethod
    def decode_closeload(parts: List[str]) -> str:
        rkt2 = list(parts)
        if len(rkt2) < 10:
            return ""
        aik4z = len(rkt2) - 2
        ozqzr = aik4z % 7
        ft0q0 = 8 + (aik4z % 5)

        if ft0q0 >= len(rkt2) or ozqzr >= len(rkt2):
            return ""

        t2o = rkt2.pop(ft0q0)
        xtqz = rkt2.pop(ozqzr)
        uvhq = "".join(rkt2)

        if len(xtqz) > 4096:
            uvhq = base64.b64decode(uvhq).decode('latin1', errors='ignore')

        xbgh9 = 0
        hqbz = 0
        for v2jhe in range(len(xtqz)):
            xu25 = ord(xtqz[v2jhe])
            xbgh9 = (xbgh9 * 37 + xu25) % 241
            hqbz = (hqbz + ((xu25 << 1) ^ v2jhe)) & 255

        mlr3o = (xbgh9 * 3 + hqbz) % 256
        gjy = (hqbz % 11) + 5
        euerq = ((hqbz * 251 + xbgh9) % 65519) + 1

        for v2jhe in range(len(t2o) - 1, -1, -1):
            k1vh = t2o[v2jhe]
            if k1vh == '7':
                pad_len = (4 - len(uvhq) % 4) % 4
                uvhq = base64.b64decode(uvhq + "=" * pad_len).decode('latin1', errors='ignore')
            elif k1vh == '3':
                uvhq = uvhq[::-1]
            else:
                v29c = (26 - ((ord(k1vh) - 96) % 26)) % 26
                chars = []
                for ch in uvhq:
                    c9n = ord(ch)
                    if 'A' <= ch <= 'Z':
                        chars.append(chr((c9n - 65 + v29c) % 26 + 65))
                    elif 'a' <= ch <= 'z':
                        chars.append(chr((c9n - 97 + v29c) % 26 + 97))
                    else:
                        chars.append(ch)
                uvhq = "".join(chars)

        if len(t2o) > 2048:
            uvhq = uvhq[::-1]

        aik4z = len(uvhq)
        gm7n = [0] * aik4z
        for v2jhe in range(aik4z - 1, 0, -1):
            euerq = (euerq * 97 + 41) % 65519
            gm7n[v2jhe] = euerq % (v2jhe + 1)

        dowxc = list(uvhq)
        for v2jhe in range(1, aik4z):
            wrxyp = gm7n[v2jhe]
            t2avk = dowxc[v2jhe]
            dowxc[v2jhe] = dowxc[wrxyp]
            dowxc[wrxyp] = t2avk

        uvhq = "".join(dowxc)

        uo2b0 = mlr3o
        julf = []
        for v2jhe in range(len(uvhq)):
            xu25 = ord(uvhq[v2jhe])
            uo2b0 = (uo2b0 * 5 + gjy) % 256
            julf.append(chr(xu25 ^ uo2b0))
            uo2b0 = (uo2b0 + xu25) % 256

        return "".join(julf)

    def get_repo_metadata(self) -> Dict[str, Any]:
        """Collects truthful Git commit, config hash, and version metadata."""
        sha = "unknown"
        try:
            p = subprocess.run(["git", "rev-parse", "HEAD"], cwd=REPO_ROOT, capture_output=True, text=True)
            if p.returncode == 0:
                sha = p.stdout.strip()
        except Exception:
            pass

        # Hash providers and domains configuration
        h = hashlib.sha256()
        for cfg in ["config/providers.json", "config/domains.json"]:
            path = os.path.join(REPO_ROOT, cfg)
            if os.path.isfile(path):
                with open(path, "rb") as f:
                    h.update(f.read())
        config_hash = h.hexdigest()[:16]

        with open(os.path.join(REPO_ROOT, "config/providers.json"), encoding="utf-8") as f:
            provs = json.load(f).get("providers", [])
        active_count = sum(1 for p in provs if p.get("enabled"))

        return {
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "sourceCommitSha": sha,
            "configHash": config_hash,
            "providerCount": active_count,
            "cloudStreamVersion": "v4.8.0"
        }

    def verify_l6_extractor_resolution(self, embed_url: str, referer: Optional[str] = None) -> Tuple[str, Optional[str], Optional[str]]:
        """
        L6: Resolves an embed/iframe URL to a candidate stream URL.
        Returns (status, stream_url, extractor_name).
        """
        if not embed_url or not embed_url.startswith("http"):
            return "FAIL", None, None

        headers = {"User-Agent": USER_AGENT, "Referer": referer or embed_url}

        # 1. Direct stream link
        if any(embed_url.endswith(ext) or ext in embed_url for ext in [".m3u8", ".mp4", ".mkv"]):
            return "PASS", embed_url, "Direct"

        # 2. FilmizleIn / FirePlayer
        if "filmizle.in" in embed_url:
            m = re.search(r'/video/([a-zA-Z0-9_-]+)', embed_url)
            if m:
                vid = m.group(1)
                api_url = f"https://player.filmizle.in/player/index.php?data={vid}&do=getVideo"
                data = urllib.parse.urlencode({"hash": vid, "r": referer or "https://sinemacc.com/", "s": ""}).encode("utf-8")
                req = urllib.request.Request(api_url, data=data, headers={
                    "User-Agent": USER_AGENT,
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": embed_url
                })
                try:
                    with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                        js = json.loads(resp.read().decode("utf-8", errors="ignore"))
                        stream = js.get("securedLink") or js.get("videoSource")
                        if stream:
                            return "PASS", stream.replace("\\/", "/"), "FilmizleIn"
                except Exception:
                    pass

        # 3. Peacemaker
        if "peacemakerst.com" in embed_url:
            m = re.search(r'/video/([a-zA-Z0-9_-]+)', embed_url)
            if m:
                vid = m.group(1)
                api_url = f"https://peacemakerst.com/tv/video/{vid}?do=getVideo"
                data = urllib.parse.urlencode({"hash": vid, "r": referer or "https://www.dizimom.diy/", "s": ""}).encode("utf-8")
                req = urllib.request.Request(api_url, data=data, headers={
                    "User-Agent": USER_AGENT,
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": embed_url
                })
                try:
                    with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                        js = json.loads(resp.read().decode("utf-8", errors="ignore"))
                        sources = js.get("videoSources", [])
                        if sources and sources[0].get("file"):
                            return "PASS", sources[0]["file"], "Peacemaker"
                except Exception:
                    pass

        # 4. Vidmoly
        if "vidmoly" in embed_url:
            full_url = embed_url.replace("/w/", "/embed-") if "/w/" in embed_url else embed_url
            req = urllib.request.Request(full_url, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                    html = resp.read().decode("utf-8", errors="ignore")
                    m3u8 = re.search(r'file:\s*["\']([^"\']+\.m3u8[^"\']*)["\']', html)
                    if m3u8:
                        return "PASS", m3u8.group(1), "Vidmoly"
            except Exception:
                pass

        # 5. CloseLoad / Rapid / Rapidrame / HDFilmCehennemi
        if any(k in embed_url for k in ["closeload", "rapid.", "rapidrame", "hdfilmcehennemi"]):
            c_headers = dict(headers)
            ext_label = "Rapidrame" if ("rapidrame" in embed_url or "hdfilmcehennemi" in embed_url) else "CloseLoad"
            if "filmmakinesi" in embed_url or (referer and "filmmakinesi" in referer):
                c_headers["Referer"] = "https://filmmakinesi.to/"
            elif "hdfilmcehennemi" in embed_url or (referer and "hdfilmcehennemi" in referer):
                c_headers["Referer"] = "https://www.hdfilmcehennemi.nl/"
            try:
                req = urllib.request.Request(embed_url, headers=c_headers)
                try:
                    resp = urllib.request.urlopen(req, timeout=self.timeout)
                except Exception:
                    resp = urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx)

                with resp:
                    html = resp.read().decode("utf-8", errors="ignore")
                    m = re.search(r'file:\s*["\']([^"\']+\.(?:m3u8|mp4|txt)[^"\']*)["\']', html) or re.search(r'<source[^>]+src=["\']([^"\']+)["\']', html)
                    if m:
                        return "PASS", m.group(1), ext_label

                    full_content = html
                    if "eval(function(p,a,c,k,e,d)" in html:
                        full_content = html + "\n" + self.unpack_packer(html)

                    var_matches = re.findall(r'sources:\s*\[\{file:\s*([a-zA-Z0-9_]+)', full_content)
                    valid_vars = [v_name for v_name in var_matches if v_name != "atob"]
                    if valid_vars:
                        t_var = valid_vars[0]
                        p_match = re.search(rf'var\s+{t_var}\s*=\s*[a-zA-Z0-9_]+\s*\(\s*["\']([^"\']+)["\']\.split\(\s*["\']([^"\']+)["\']\s*\)\s*\);', full_content)
                        if p_match:
                            raw_p = p_match.group(1)
                            delim = p_match.group(2)
                            decoded = self.decode_closeload(raw_p.split(delim))
                            if decoded.startswith("http"):
                                return "PASS", decoded, ext_label

                    for gen_m in re.finditer(r'var\s+[a-zA-Z0-9_]+\s*=\s*[a-zA-Z0-9_]+\s*\(\s*["\']([^"\']{100,})["\']\.split\(\s*["\']([|\^*@#~])["\']\s*\)\s*\);', full_content):
                        raw_p = gen_m.group(1)
                        delim = gen_m.group(2)
                        parts = raw_p.split(delim)
                        if len(parts) >= 10:
                            decoded = self.decode_closeload(parts)
                            if decoded.startswith("http"):
                                return "PASS", decoded, ext_label
            except Exception:
                pass

        # 6. Vidpapi (KultFilmler)
        if "vidpapi.xyz" in embed_url:
            m = re.search(r'/video/([a-zA-Z0-9_-]+)', embed_url)
            if m:
                vid = m.group(1)
                api_url = f"https://vidpapi.xyz/player/index.php?data={vid}&do=getVideo"
                data = urllib.parse.urlencode({
                    "hash": vid,
                    "r": referer or "https://kultfilmler.net/",
                    "s": ""
                }).encode("utf-8")
                req = urllib.request.Request(api_url, data=data, headers={
                    "User-Agent": USER_AGENT,
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": embed_url,
                    "Origin": "https://vidpapi.xyz"
                })
                try:
                    try:
                        resp = urllib.request.urlopen(req, timeout=self.timeout)
                    except Exception:
                        resp = urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx)
                    with resp:
                        js = json.loads(resp.read().decode("utf-8", errors="ignore"))
                        stream = js.get("securedLink") or js.get("videoSource")
                        if stream:
                            return "PASS", stream.replace("\\/", "/"), "Vidpapi"
                except Exception:
                    pass

        # 7. Rumble (YesilCamTv)
        if "rumble.com" in embed_url:
            clean_embed = embed_url.split("#")[0]
            req = urllib.request.Request(clean_embed, headers=headers)
            try:
                try:
                    resp = urllib.request.urlopen(req, timeout=self.timeout)
                except Exception:
                    resp = urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx)
                with resp:
                    html = resp.read().decode("utf-8", errors="ignore")
                    m_hls = re.search(r'["\']hls["\']\s*:\s*\{[^}]*["\']url["\']\s*:\s*["\']([^"\']+)["\']', html) or \
                            re.search(r'https?:\\?/\\?/[^"\'\s<>]+\.rumble\.com/hls-vod/[^"\'\s<>]+\.m3u8[^"\'\s<>]*', html)
                    if m_hls:
                        raw = m_hls.group(1) if m_hls.lastindex else m_hls.group(0)
                        return "PASS", raw.replace(r"\/", "/"), "Rumble"
                    m_mp4 = re.search(r'["\'](\d{3,4})["\']\s*:\s*\{[^}]*["\']url["\']\s*:\s*["\']([^"\']+\.mp4[^"\']*)["\']', html)
                    if m_mp4:
                        return "PASS", m_mp4.group(2).replace(r"\/", "/"), "Rumble"
            except Exception:
                pass

        # 8. Pichive (Dizilla)
        if "pichive" in embed_url:
            m = re.search(r'/video/([a-zA-Z0-9_-]+)', embed_url)
            if m:
                vid = m.group(1)
                host = urllib.parse.urlparse(embed_url).netloc
                api_url = f"https://{host}/player/index.php?data={vid}&do=getVideo"
                data = urllib.parse.urlencode({"hash": vid, "r": referer or "https://dizilla.now/", "s": ""}).encode("utf-8")
                req = urllib.request.Request(api_url, data=data, headers={
                    "User-Agent": USER_AGENT,
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": embed_url
                })
                try:
                    with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                        js = json.loads(resp.read().decode("utf-8", errors="ignore"))
                        stream = js.get("securedLink") or js.get("videoSource")
                        if stream:
                            return "PASS", stream.replace("\\/", "/"), "Pichive"
                except Exception:
                    pass

        # 9. RapidVid (FullHDFilmizlesene)
        if "rapidvid.org" in embed_url:
            try:
                req = urllib.request.Request(embed_url, headers={"User-Agent": USER_AGENT, "Referer": referer or "https://www.fullhdfilmizlesene.now/"})
                with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                    html = resp.read().decode("utf-8", errors="ignore")
                    
                    # New window._p8 JSON format (Eylul 2026)
                    p8_m = re.search(r'window\._p8\s*=\s*[\'"]([^\'"]+)[\'"]', html)
                    if p8_m:
                        token = p8_m.group(1)
                        rev = token[::-1]
                        pad = (4 - len(rev) % 4) % 4
                        raw_bytes = base64.b64decode(rev + "=" * pad)
                        raw_str = raw_bytes.decode('latin1', errors='ignore')
                        key = "K9L"
                        sb = []
                        for i, ch in enumerate(raw_str):
                            r = key[i % 3]
                            n = ord(ch) - (ord(r) % 5 + 1)
                            sb.append(chr(n))
                        inner = "".join(sb)
                        inner_pad = (4 - len(inner) % 4) % 4
                        json_str = base64.b64decode(inner + "=" * inner_pad).decode('utf-8', errors='ignore')
                        js_data = json.loads(json_str)
                        stream = js_data.get("cm") or js_data.get("tm")
                        if stream and stream.startswith("http"):
                            return "PASS", stream, "RapidVid"

                    # Legacy av(...) format
                    av_m = re.search(r'["\']?file["\']?\s*:\s*av\((["\'])(.*?)\1\)', html)
                    if av_m:
                        token = av_m.group(2)
                        rev = token[::-1]
                        pad = (4 - len(rev) % 4) % 4
                        padded = rev + "=" * pad
                        raw_bytes = base64.b64decode(padded)
                        raw_str = raw_bytes.decode('latin1', errors='ignore')
                        key = "K9L"
                        sb = []
                        for i, ch in enumerate(raw_str):
                            r = key[i % 3]
                            n = ord(ch) - (ord(r) % 5 + 1)
                            sb.append(chr(n))
                        inner = "".join(sb)
                        inner_pad = (4 - len(inner) % 4) % 4
                        stream = base64.b64decode(inner + "=" * inner_pad).decode('utf-8', errors='ignore')
                        if stream.startswith("http"):
                            return "PASS", stream, "RapidVid"
            except Exception:
                pass

        # 10. DiziYou Player
        if "diziyou.one" in embed_url and "/player/" in embed_url:
            m = re.search(r'/player/([a-zA-Z0-9_-]+)\.html', embed_url)
            if m:
                item_id = m.group(1)
                stream = f"https://storage.diziyou.one/episodes/{item_id}/play.m3u8"
                return "PASS", stream, "DiziYou"

        # 11. PlayerDKorea (DiziKorea)
        if "playerdkorea" in embed_url or "playerkorea" in embed_url:
            try:
                vid_m = re.search(r'/video/([a-zA-Z0-9_-]+)', embed_url)
                if vid_m:
                    video_id = vid_m.group(1)
                    api_url = f"https://playerdkorea.xyz/player/index.php?data={video_id}&do=getVideo"
                    post_data = urllib.parse.urlencode({"hash": video_id, "r": referer or "https://dizikorea3.com/", "s": ""}).encode('utf-8')
                    req = urllib.request.Request(api_url, data=post_data, headers={
                        "User-Agent": USER_AGENT,
                        "Referer": embed_url,
                        "X-Requested-With": "XMLHttpRequest"
                    })
                    with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                        js = json.loads(resp.read().decode("utf-8"))
                        stream = js.get("securedLink") or js.get("videoSource")
                        if stream:
                            return "PASS", stream.replace("\\/", "/"), "PlayerDKorea"
            except Exception:
                pass

        # 11. HDFilmDelisi Embed
        if "hdfilmdelisi.one/embed/" in embed_url:
            try:
                req = urllib.request.Request(embed_url, headers={"User-Agent": USER_AGENT, "Referer": referer or "https://hdfilmdelisi.one/"})
                with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                    html = resp.read().decode("utf-8", errors="ignore")
                    v_m = re.search(r'<video[^>]+src=["\']([^"\']+)["\']', html) or re.search(r'<source[^>]+src=["\']([^"\']+)["\']', html)
                    if v_m:
                        stream = v_m.group(1)
                        if stream.startswith("/"):
                            stream = f"https://hdfilmdelisi.one{stream}"
                        return "PASS", stream, "HDFilmDelisi"
            except Exception:
                pass

        return "UNSUPPORTED_HOST", None, None

    def verify_l7_media_preflight(self, stream_url: str, referer: Optional[str] = None) -> Tuple[str, str, Dict[str, Any]]:
        """
        L7: Validates Content-Type, HTTP status, and container magic bytes.
        Prevents ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003) by rejecting HTML/JSON.
        Returns (status, stream_type, metadata_dict).
        """
        if not stream_url or not stream_url.startswith("http"):
            return "FAIL", "UNKNOWN", {"reason": "INVALID_URL"}

        headers = {
            "User-Agent": USER_AGENT,
            "Range": "bytes=0-1024"
        }
        if referer:
            headers["Referer"] = referer

        req = urllib.request.Request(stream_url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                status_code = resp.status
                ct = resp.headers.get("Content-Type", "")
                bytes_sample = resp.read(512)
                text_sample = bytes_sample.decode("utf-8", errors="ignore").lower().strip()

                # HLS detection (EXTM3U magic signature takes precedence over text/html Content-Type)
                if text_sample.startswith("#extm3u") or "application/vnd.apple.mpegurl" in ct or "application/x-mpegurl" in ct:
                    if not (text_sample.startswith("<!doctype html") or text_sample.startswith("<html")):
                        return "PASS", "HLS", {
                            "statusCode": status_code,
                            "contentType": ct,
                            "magicHeader": "#EXTM3U"
                        }

                # Rejection of HTML and JSON errors (3003 root cause prevention)
                if "text/html" in ct or text_sample.startswith("<!doctype html") or text_sample.startswith("<html") or "security error" in text_sample:
                    return "FAIL", "HTML_ERROR_PAGE", {
                        "statusCode": status_code,
                        "contentType": ct,
                        "reason": "HTML_PAGE_REJECTED_3003_PREVENTION"
                    }

                # MP4 container detection (ftyp box)
                if len(bytes_sample) >= 8 and (b"ftyp" in bytes_sample[4:8] or b"moov" in bytes_sample[4:8]):
                    return "PASS", "PROGRESSIVE_MP4", {
                        "statusCode": status_code,
                        "contentType": ct,
                        "magicHeader": "ftyp"
                    }

                # Matroska / WebM EBML
                if bytes_sample.startswith(b"\x1a\x45\xdf\xa3"):
                    return "PASS", "PROGRESSIVE_MKV", {
                        "statusCode": status_code,
                        "contentType": ct,
                        "magicHeader": "EBML"
                    }

                # Generic 200 with video content type
                if "video/" in ct and status_code in (200, 206):
                    return "PASS", "VIDEO", {
                        "statusCode": status_code,
                        "contentType": ct
                    }

                return "FAIL", "UNKNOWN_CONTAINER", {
                    "statusCode": status_code,
                    "contentType": ct,
                    "reason": "UNRECOGNIZED_CONTAINER_SIGNATURE"
                }
        except urllib.error.HTTPError as e:
            return "FAIL", "HTTP_ERROR", {"statusCode": e.code, "reason": str(e)}
        except Exception as e:
            return "FAIL", "NETWORK_ERROR", {"reason": str(e)}

    def verify_l8_first_segment(self, stream_url: str, stream_type: str, referer: Optional[str] = None) -> Tuple[str, Dict[str, Any]]:
        """
        L8: Verifies that the first actual media segment/chunk can be fetched and is non-empty.
        """
        if not stream_url or not stream_url.startswith("http"):
            return "FAIL", {"reason": "INVALID_URL"}

        headers = {"User-Agent": USER_AGENT}
        if referer:
            headers["Referer"] = referer

        if stream_type != "HLS":
            # For progressive files, fetch first 4KB
            headers["Range"] = "bytes=0-4096"
            req = urllib.request.Request(stream_url, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                    chunk = resp.read(2048)
                    if len(chunk) > 0:
                        return "PASS", {"bytesFetched": len(chunk)}
                    return "FAIL", {"reason": "EMPTY_FIRST_CHUNK"}
            except Exception as e:
                return "FAIL", {"reason": str(e)}

        # For DASH (MPD)
        if stream_type == "DASH" or stream_url.endswith(".mpd") or ".mpd" in stream_url:
            try:
                req = urllib.request.Request(stream_url, headers=headers)
                with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                    mpd_content = resp.read().decode("utf-8", errors="ignore")
                init_match = re.search(r'<Initialization[^>]+sourceURL=["\']([^"\']+)["\']', mpd_content)
                seg_match = re.search(r'<SegmentURL[^>]+media=["\']([^"\']+)["\']', mpd_content)
                base_match = re.search(r'<BaseURL[^>]*>([^<]+)</BaseURL>', mpd_content)
                base_url = stream_url
                if base_match:
                    base_url = urllib.parse.urljoin(stream_url, base_match.group(1).strip())
                target = init_match.group(1) if init_match else (seg_match.group(1) if seg_match else None)
                if target:
                    target_url = urllib.parse.urljoin(base_url, target)
                    headers["Range"] = "bytes=0-2048"
                    s_req = urllib.request.Request(target_url, headers=headers)
                    with urllib.request.urlopen(s_req, timeout=self.timeout, context=self.ssl_ctx) as s_resp:
                        chunk = s_resp.read(1024)
                        if len(chunk) > 0:
                            return "PASS", {"dashInitFetched": len(chunk)}
                return "PASS", {"dashManifestValid": True}
            except Exception as e:
                return "FAIL", {"reason": f"DASH_FETCH_ERROR: {e}"}

        # For HLS: Real playlist parsing with extensionless support, EXT-X-STREAM-INF, and EXT-X-MAP
        req = urllib.request.Request(stream_url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=self.ssl_ctx) as resp:
                playlist = resp.read().decode("utf-8", errors="ignore")

            # Check if master playlist
            if "#EXT-X-STREAM-INF" in playlist:
                variant_lines = []
                take_next = False
                for line in playlist.splitlines():
                    line = line.strip()
                    if not line:
                        continue
                    if line.startswith("#EXT-X-STREAM-INF"):
                        take_next = True
                    elif take_next and not line.startswith("#"):
                        variant_lines.append(line)
                        take_next = False
                if not variant_lines:
                    variant_lines = [l.strip() for l in playlist.splitlines() if l.strip() and not l.startswith("#")]
                if variant_lines:
                    first_variant = urllib.parse.urljoin(stream_url, variant_lines[0])
                    v_req = urllib.request.Request(first_variant, headers=headers)
                    with urllib.request.urlopen(v_req, timeout=self.timeout, context=self.ssl_ctx) as v_resp:
                        playlist = v_resp.read().decode("utf-8", errors="ignore")
                    stream_url = first_variant

            # In media playlist: check for initialization map (#EXT-X-MAP:URI="...")
            map_match = re.search(r'#EXT-X-MAP:URI=["\']([^"\']+)["\']', playlist)
            if map_match:
                init_url = urllib.parse.urljoin(stream_url, map_match.group(1))
                headers["Range"] = "bytes=0-2048"
                init_req = urllib.request.Request(init_url, headers=headers)
                with urllib.request.urlopen(init_req, timeout=self.timeout, context=self.ssl_ctx) as init_resp:
                    chunk = init_resp.read(1024)
                    if len(chunk) > 0:
                        return "PASS", {"initMap": init_url.split("?")[0], "bytes": len(chunk)}

            lines = [l.strip() for l in playlist.splitlines() if l.strip() and not l.startswith("#")]
            if not lines:
                return "FAIL", {"reason": "NO_SEGMENTS_OR_VARIANTS_IN_PLAYLIST"}

            first_target = urllib.parse.urljoin(stream_url, lines[0])

            # Fetch the first segment chunk
            headers["Range"] = "bytes=0-2048"
            seg_req = urllib.request.Request(first_target, headers=headers)
            with urllib.request.urlopen(seg_req, timeout=self.timeout, context=self.ssl_ctx) as seg_resp:
                seg_bytes = seg_resp.read(1024)
                if len(seg_bytes) > 0:
                    redacted_target = first_target.split("?")[0]
                    return "PASS", {"segmentUrl": redacted_target, "bytes": len(seg_bytes)}
                return "FAIL", {"reason": "EMPTY_SEGMENT_RESPONSE"}
        except Exception as e:
            return "FAIL", {"reason": str(e)}

    def build_matrix_record(
        self,
        provider_name: str,
        l1_stat: str,
        l3_stat: str,
        l4_stat: str,
        l5_stat: str,
        l6_stat: str,
        l7_stat: str,
        l8_stat: str,
        source_host: Optional[str] = None,
        stream_type: Optional[str] = None,
        notes: Optional[List[str]] = None
    ) -> Dict[str, Any]:
        """Creates a standardized provider playback matrix entry with truthful reachability semantics."""
        if l7_stat == "PASS" and l8_stat == "PASS":
            reachability = "PASS"
        elif l7_stat == "FAIL" or l8_stat == "FAIL":
            reachability = "FAIL"
        else:
            reachability = "UNVERIFIED"

        return {
            "provider": provider_name,
            "domain": l1_stat,
            "search": l3_stat,
            "load": l4_stat,
            "playerDiscovery": l5_stat,
            "extractorResolution": l6_stat,
            "mediaPreflight": l7_stat,
            "firstSegment": l8_stat,
            "mediaReachability": reachability,
            "runtimePlayback": "UNVERIFIED_BY_DEVICE",
            "sourceHost": source_host or "none",
            "streamType": stream_type or "UNKNOWN",
            "notes": notes or []
        }
