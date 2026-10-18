"""
adaptive.py

Adaptive selector drift detection engine using Scrapling's SQLiteStorageSystem.
Enables detecting upstream DOM mutations without modifying Kotlin provider implementations.
"""

import os
import sqlite3
from typing import Dict, Any, Optional, Tuple, List
from scrapling import Selector
from scrapling.core.storage import SQLiteStorageSystem

DEFAULT_CACHE_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "cache"
)
DEFAULT_DB_FILE = os.path.join(DEFAULT_CACHE_DIR, "adaptive_selectors.db")

class AdaptiveManager:
    """
    Manages fingerprint storage and drift detection for CSS/XPath selectors.
    
    Workflows:
    1. STRICT SEARCH: Runs the selector as defined.
       - If element(s) found -> Saves/updates fingerprint baseline with auto_save=True, returns status "PASS".
    2. ADAPTIVE SEARCH: If strict search returns 0 elements:
       - Runs selector with adaptive=True against stored fingerprint.
       - If element(s) found -> Status "DRIFT_DETECTED", includes candidate element info.
       - If element(s) not found -> Status "FAIL".
       
    Strict Rule:
    DO NOT automatically edit Kotlin source code.
    Drift reports are diagnostics for maintainers and issue tracking.
    """

    def __init__(self, db_path: Optional[str] = None):
        self.db_path = db_path or DEFAULT_DB_FILE
        os.makedirs(os.path.dirname(os.path.abspath(self.db_path)), exist_ok=True)

    def check_css_selector(
        self,
        html_content: str,
        selector_str: str,
        identifier: str,
        base_url: str = "https://localhost"
    ) -> Tuple[str, int, Optional[List[Dict[str, Any]]]]:
        """
        Evaluates a CSS selector against HTML content using strict first, then adaptive fallback.
        
        Returns:
            (status, match_count, candidate_info_list)
            status can be: "PASS", "DRIFT_DETECTED", "FAIL"
        """
        if not html_content or not selector_str or not identifier:
            return "FAIL", 0, None

        storage_args = {
            "storage_file": self.db_path,
            "url": base_url
        }

        try:
            # Instantiate Selector with adaptive storage enabled
            sel = Selector(
                content=html_content,
                url=base_url,
                adaptive=True,
                storage_args=storage_args
            )

            # 1. Strict Query first (adaptive=False)
            strict_elements = sel.css(selector_str, identifier=identifier, adaptive=False)
            if strict_elements and len(strict_elements) > 0:
                # Save baseline fingerprint
                try:
                    sel.css(selector_str, identifier=identifier, auto_save=True)
                except Exception:
                    pass
                return "PASS", len(strict_elements), None

            # 2. Strict query failed (0 matches). Try Adaptive lookup (adaptive=True)
            adaptive_elements = sel.css(selector_str, identifier=identifier, adaptive=True)
            if adaptive_elements and len(adaptive_elements) > 0:
                candidate_details = []
                for elem in adaptive_elements[:5]:
                    candidate_details.append({
                        "tag": getattr(elem, "tag", None),
                        "text": (getattr(elem, "text", "") or "")[:100].strip(),
                        "attrib": getattr(elem, "attrib", {})
                    })
                return "DRIFT_DETECTED", len(adaptive_elements), candidate_details

            # 3. Both strict and adaptive found 0 elements
            return "FAIL", 0, None

        except Exception as e:
            return "FAIL", 0, [{"error": str(e)}]

    def check_xpath_selector(
        self,
        html_content: str,
        xpath_str: str,
        identifier: str,
        base_url: str = "https://localhost"
    ) -> Tuple[str, int, Optional[List[Dict[str, Any]]]]:
        """Evaluates an XPath selector with strict first, then adaptive fallback."""
        if not html_content or not xpath_str or not identifier:
            return "FAIL", 0, None

        storage_args = {
            "storage_file": self.db_path,
            "url": base_url
        }

        try:
            sel = Selector(
                content=html_content,
                url=base_url,
                adaptive=True,
                storage_args=storage_args
            )

            strict_elements = sel.xpath(xpath_str, identifier=identifier, adaptive=False)
            if strict_elements and len(strict_elements) > 0:
                try:
                    sel.xpath(xpath_str, identifier=identifier, auto_save=True)
                except Exception:
                    pass
                return "PASS", len(strict_elements), None

            adaptive_elements = sel.xpath(xpath_str, identifier=identifier, adaptive=True)
            if adaptive_elements and len(adaptive_elements) > 0:
                candidate_details = []
                for elem in adaptive_elements[:5]:
                    candidate_details.append({
                        "tag": getattr(elem, "tag", None),
                        "text": (getattr(elem, "text", "") or "")[:100].strip(),
                        "attrib": getattr(elem, "attrib", {})
                    })
                return "DRIFT_DETECTED", len(adaptive_elements), candidate_details

            return "FAIL", 0, None

        except Exception as e:
            return "FAIL", 0, [{"error": str(e)}]
