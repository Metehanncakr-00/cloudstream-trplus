#!/usr/bin/env python3
"""
benchmark_requests.py

Benchmark harness for representative per-provider requests (search, load, link resolution).
Used for RD-004 and RD-005 measurement gates.

Captures:
- p50 / p95 duration (ms)
- Request count
- Timeout rate
- Success rate

Default mode uses offline/fixture-backed repeatable benchmarks.
--live enables opt-in live probes against canonical endpoints.
"""

import os
import sys
import json
import time
import argparse
import statistics
from typing import Dict, Any, List, Optional
from datetime import datetime, timezone

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from tools.scraping.fetch import ProviderFetcher
from tools.scraping.models import FetchStatus

def compute_percentiles(durations_ms: List[float]) -> Dict[str, float]:
    if not durations_ms:
        return {"p50": 0.0, "p95": 0.0, "mean": 0.0, "min": 0.0, "max": 0.0}
    sorted_d = sorted(durations_ms)
    n = len(sorted_d)
    idx_50 = int(n * 0.50)
    idx_95 = min(int(n * 0.95), n - 1)
    return {
        "p50": round(sorted_d[idx_50], 2),
        "p95": round(sorted_d[idx_95], 2),
        "mean": round(statistics.mean(sorted_d), 2),
        "min": round(sorted_d[0], 2),
        "max": round(sorted_d[-1], 2),
    }

def run_simulated_benchmark(scenarios: List[Dict[str, Any]], iterations: int = 20) -> Dict[str, Any]:
    """Runs repeatable benchmarks using deterministic execution scenarios."""
    results = {}
    for scenario in scenarios:
        name = scenario["name"]
        fn = scenario["fn"]
        durations = []
        errors = 0
        timeouts = 0
        total_requests = 0

        for _ in range(iterations):
            t0 = time.perf_counter()
            try:
                res = fn()
                durations.append((time.perf_counter() - t0) * 1000.0)
                # If the function returns an integer or a mock FetchResult with requestCount, use it
                if name.startswith("CPU_microbenchmark"):
                    total_requests += 0
                elif isinstance(res, int):
                    total_requests += res
                elif hasattr(res, "requestCount"):
                    total_requests += getattr(res, "requestCount", 1)
                else:
                    total_requests += 1
            except TimeoutError:
                timeouts += 1
                errors += 1
            except Exception:
                errors += 1

        stats = compute_percentiles(durations)
        stats["requestCount"] = total_requests
        stats["successRate"] = round((iterations - errors) / iterations, 4) if iterations else 0.0
        stats["timeoutRate"] = round(timeouts / iterations, 4) if iterations else 0.0
        results[name] = stats

    return results

def run_live_benchmark(providers: List[Dict[str, Any]], iterations: int = 3, timeout: float = 10.0) -> Dict[str, Any]:
    """Runs opt-in live network benchmarks against active provider endpoints."""
    results = {}
    
    # Load domains.json
    domains_path = os.path.join(REPO_ROOT, "config", "domains.json")
    all_domains = {}
    if os.path.exists(domains_path):
        with open(domains_path, "r", encoding="utf-8") as f:
            all_domains = json.load(f).get("providers", {})

    for prov in providers:
        name = prov.get("name", "unknown")
        smoke = prov.get("smokeTest", {})
        known_detail = smoke.get("knownDetail")
        if not known_detail:
            continue

        domain_key = prov.get("domainKey")
        domain_config = all_domains.get(domain_key, {})
        allowed_hosts = set(domain_config.get("allowedHosts", []))
        expected_markers = set(domain_config.get("expectedMarkers", []))
        canonical = domain_config.get("canonical", known_detail)

        durations = []
        errors = 0
        timeouts = 0
        total_requests = 0
        
        fetcher = ProviderFetcher(
            allowed_hosts=allowed_hosts,
            canonical=canonical,
            timeout=int(timeout)
        )
        
        for _ in range(iterations):
            t0 = time.perf_counter()
            try:
                # Disable fallbacks to truthfully map 1 requestCount to 1 HTTP attempt
                res = fetcher.fetch(
                    known_detail, 
                    expected_markers=list(expected_markers),
                    allow_dynamic_fallback=False,
                    allow_stealth_fallback=False
                )
                total_requests += 1
                if res.status != FetchStatus.SUCCESS:
                    errors += 1
                if res.status == FetchStatus.TIMEOUT:
                    timeouts += 1
                
                durations.append((time.perf_counter() - t0) * 1000.0)
            except Exception as e:
                errors += 1
                total_requests += 1

        fetcher.close()
        
        stats = compute_percentiles(durations)
        stats["requestCount"] = total_requests
        stats["successRate"] = round((iterations - errors) / iterations, 4) if iterations else 0.0
        stats["timeoutRate"] = round(timeouts / iterations, 4) if iterations else 0.0
        results[name] = stats

    return results

def main():
    parser = argparse.ArgumentParser(description="Per-provider request benchmark harness")
    parser.add_argument("--live", action="store_true", help="Opt-in live network benchmarking")
    parser.add_argument("--iterations", type=int, default=10, help="Number of iterations per scenario")
    parser.add_argument("--report-path", default="reports/benchmark_baseline.json", help="Path to write report")
    args = parser.parse_args()

    report = {
        "schemaVersion": 1,
        "mode": "live" if args.live else "offline_repeatable",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "benchmarks": {}
    }

    if args.live:
        print("[Benchmark] Running live opt-in benchmarks...")
        providers_path = os.path.join(REPO_ROOT, "config", "providers.json")
        with open(providers_path, "r", encoding="utf-8") as f:
            providers = [p for p in json.load(f).get("providers", []) if p.get("enabled")]
        report["benchmarks"] = run_live_benchmark(providers, iterations=args.iterations)
    else:
        print("[Benchmark] Running offline repeatable fixture-backed benchmarks...")
        scenarios = [
            {
                "name": "CPU_microbenchmark_search_json",
                "fn": lambda: json.loads(json.dumps([{"baslik": "test", "dizilink": "test-link", "vidresim": "img.jpg"}] * 20))
            },
            {
                "name": "CPU_microbenchmark_scx_decoding",
                "fn": lambda: [
                    __import__("base64").b64decode(
                        "".join(chr((ord(c) + 13 - 65) % 26 + 65) if c.isupper() else chr((ord(c) + 13 - 97) % 26 + 97) if c.islower() else c for c in "nUE0pUZ6Yl9lLKOcMUMcMP5ipzpiqz9xY3LkrTZ3ZQVlBJV5") + "=="
                    )
                    for _ in range(10)
                ]
            }
        ]
        
        # Add a fixture-backed HTML discovery scenario
        try:
            from tools.scraping.discovery import discover_homepage_cards
            html_fixture = '<html><div class="movies">' + '<a class="poster" href="/film/x"><img src="/img.jpg"/></a>'*50 + '</div></html>'
            scenarios.append({
                "name": "provider_fixture_homepage_discovery",
                "fn": lambda: discover_homepage_cards(html_fixture, "https://example.com")
            })
        except ImportError:
            pass

        report["benchmarks"] = run_simulated_benchmark(scenarios, iterations=args.iterations)

    os.makedirs(os.path.dirname(os.path.join(REPO_ROOT, args.report_path)), exist_ok=True)
    with open(os.path.join(REPO_ROOT, args.report_path), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print(f"[Benchmark] Report written to {args.report_path}")
    print(json.dumps(report["benchmarks"], indent=2))

if __name__ == "__main__":
    main()
