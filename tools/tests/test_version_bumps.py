import os
import sys
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

import pytest
from tools.verify_version_bumps import (
    get_active_providers,
    get_current_provider_version,
    verify_version_bumps
)

def test_active_providers_count():
    providers = get_active_providers()
    assert len(providers) >= 30
    assert "CloudStreamHub" in providers
    assert "AnimeciX" in providers

def test_current_provider_versions():
    hub_ver = get_current_provider_version("CloudStreamHub")
    assert hub_ver >= 4

    animecix_ver = get_current_provider_version("AnimeciX")
    assert animecix_ver >= 91

    yesilcam_ver = get_current_provider_version("YesilCamTv")
    assert yesilcam_ver >= 13

def test_verify_version_bumps_on_current_worktree():
    # Comparing working tree against origin/main must pass with our bumped versions
    success, messages = verify_version_bumps("origin/main")
    assert success is True
    assert any("All affected provider versions were successfully bumped" in m for m in messages)

def test_verify_version_bumps_zero_sha_fallback():
    # When passed all-zeros SHA, it should fall back to candidates safely
    success, messages = verify_version_bumps("0000000000000000000000000000000000000000")
    assert success is True

