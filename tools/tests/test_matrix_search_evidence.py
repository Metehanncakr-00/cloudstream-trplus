from types import SimpleNamespace
from unittest.mock import patch

from tools.generate_playback_matrix import probe_provider_dynamically
from tools.playback_verifier import PlaybackVerifier


def test_configured_query_does_not_claim_search_pass():
    with patch("tools.generate_playback_matrix.ProviderFetcher") as fetcher:
        fetcher.return_value.fetch.return_value = SimpleNamespace(statusCode=200)
        result = probe_provider_dynamically(
            "FixtureProvider",
            {"smokeTest": {"searchQuery": "movie"}},
            {"canonical": "https://example.invalid", "allowedHosts": ["example.invalid"]},
            PlaybackVerifier(),
        )
    assert result["l1"] == "PASS"
    assert result["l3"] == "UNVERIFIED"
    assert result["l6"] == "UNVERIFIED"
    fetcher.return_value.fetch.assert_called_once()
