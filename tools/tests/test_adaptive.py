import os
import tempfile
import pytest
from tools.scraping.adaptive import AdaptiveManager

def test_adaptive_drift_lifecycle():
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmpdir:
        db_path = os.path.join(tmpdir, "adaptive_test.db")
        mgr = AdaptiveManager(db_path=db_path)

        # Baseline HTML
        html_v1 = '<div class="main"><a class="target-card" href="/movie1">Movie Title One</a></div>'
        status, count, candidates = mgr.check_css_selector(
            html_content=html_v1,
            selector_str="a.target-card",
            identifier="test_card",
            base_url="https://testsite.com"
        )
        assert status == "PASS"
        assert count == 1

        # Mutated HTML (class changed from target-card to new-card-style)
        html_v2 = '<div class="main"><a class="new-card-style" href="/movie1">Movie Title One</a></div>'

        # Strict check would fail, but adaptive should detect drift
        status2, count2, candidates2 = mgr.check_css_selector(
            html_content=html_v2,
            selector_str="a.target-card",
            identifier="test_card",
            base_url="https://testsite.com"
        )
        assert status2 == "DRIFT_DETECTED"
        assert count2 >= 1
        assert candidates2 is not None
        assert len(candidates2) > 0
