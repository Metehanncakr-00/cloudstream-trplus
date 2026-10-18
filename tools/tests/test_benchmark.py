import pytest
from tools.benchmark_requests import compute_percentiles, run_simulated_benchmark

def test_compute_percentiles():
    durations = [10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0]
    res = compute_percentiles(durations)
    assert res["min"] == 10.0
    assert res["max"] == 100.0
    assert res["p50"] == 60.0
    assert res["p95"] == 100.0

def test_run_simulated_benchmark():
    scenarios = [
        {"name": "CPU_microbenchmark_fast_op", "fn": lambda: 1 + 1},
        {"name": "provider_fixture_mock_load", "fn": lambda: 3} # Mocking 3 requests made
    ]
    res = run_simulated_benchmark(scenarios, iterations=10)
    assert "CPU_microbenchmark_fast_op" in res
    assert res["CPU_microbenchmark_fast_op"]["requestCount"] == 0
    assert res["CPU_microbenchmark_fast_op"]["successRate"] == 1.0
    
    assert "provider_fixture_mock_load" in res
    assert res["provider_fixture_mock_load"]["requestCount"] == 30 # 3 requests * 10 iterations
    assert res["provider_fixture_mock_load"]["successRate"] == 1.0

def test_benchmark_rejects_unallowlisted_redirect(monkeypatch, tmp_path):
    import json
    import os
    from tools.benchmark_requests import run_live_benchmark
    from tools.scraping.models import FetchResult, FetchStatus

    class MockFetcher:
        captured_kwargs = []
        captured_init = {}

        def __init__(self, allowed_hosts=None, canonical=None, timeout=15):
            MockFetcher.captured_init = {
                "allowed_hosts": allowed_hosts,
                "canonical": canonical,
                "timeout": timeout
            }
            self.allowed_hosts = allowed_hosts
            self.canonical = canonical
            self.timeout = timeout
        def fetch(self, url, **kwargs):
            MockFetcher.captured_kwargs.append(kwargs)
            return FetchResult(
                requestedUrl=url,
                finalUrl="https://evil.com",
                statusCode=301,
                fetchMode="HTTP",
                status=FetchStatus.UNTRUSTED_REDIRECT,
                body=""
            )
        def close(self):
            pass

    monkeypatch.setattr("tools.benchmark_requests.ProviderFetcher", MockFetcher)

    # Mock domains.json
    domains_data = {
        "providers": {
            "test_key": {
                "allowedHosts": ["test.com"],
                "expectedMarkers": ["TestMarker"]
            }
        }
    }
    mock_repo_root = tmp_path
    mock_config_dir = mock_repo_root / "config"
    mock_config_dir.mkdir()
    (mock_config_dir / "domains.json").write_text(json.dumps(domains_data))

    monkeypatch.setattr("tools.benchmark_requests.REPO_ROOT", str(mock_repo_root))

    providers = [{
        "name": "test_prov",
        "domainKey": "test_key",
        "smokeTest": {"knownDetail": "https://test.com/movie"}
    }]
    
    res = run_live_benchmark(providers, iterations=1)
    
    assert res["test_prov"]["successRate"] == 0.0
    assert res["test_prov"]["requestCount"] == 1
    
    # Verify constructor args
    assert MockFetcher.captured_init["allowed_hosts"] == {"test.com"}
    assert MockFetcher.captured_init["canonical"] == "https://test.com/movie"
    
    # Verify fetch kwargs
    assert len(MockFetcher.captured_kwargs) == 1
    kwargs = MockFetcher.captured_kwargs[0]
    assert kwargs.get("expected_markers") == ["TestMarker"]
    assert kwargs.get("allow_dynamic_fallback") is False
    assert kwargs.get("allow_stealth_fallback") is False

