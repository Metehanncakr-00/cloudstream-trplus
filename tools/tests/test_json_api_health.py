import json
from unittest.mock import Mock, patch

import pytest

from tools.provider_health import inspect_provider
from tools.scraping.models import FetchResult, FetchMode, FetchStatus


def response(payload, status=FetchStatus.SUCCESS, code=200):
    return FetchResult("https://api.test/", "https://api.test/?api_key=private-test-key", code,
                       FetchMode.HTTP, status, body=json.dumps(payload) if not isinstance(payload, str) else payload)


def inspect(name, responses):
    search = "/3/search/multi?query={query}&api_key=private-test-key" if name == "CloudStreamHub" else "/api/v2/list_movies.json?query_term={query}"
    detail = "https://api.test/3/movie/42?api_key=private-test-key" if name == "CloudStreamHub" else "https://api.test/api/v2/movie_details.json?movie_id=42"
    provider = {"name": name, "module": name, "smokeTest": {"searchQuery": "test", "knownDetail": detail},
                "monitoring": {"search": {"path": search}}}
    domains = {"providers": {name: {"canonical": "https://api.test", "allowedHosts": ["api.test"]}}}
    fetcher = Mock()
    fetcher.fetch.side_effect = responses
    with patch("tools.provider_health.check_l0_config", return_value=("pass", None)), \
         patch("tools.provider_health.ProviderFetcher") as factory:
        factory.return_value.__enter__.return_value = fetcher
        report = inspect_provider(provider, ".", domains, Mock())
    return report, fetcher


@pytest.mark.parametrize("name", ["CloudStreamHub", "YTS"])
def test_api_catalog_search_detail_do_not_claim_player(name):
    item = {"id": 42, "title": "Test"}
    catalog = {"results": [item]} if name == "CloudStreamHub" else {"status": "ok", "data": {"movies": [item]}}
    detail = item if name == "CloudStreamHub" else {"status": "ok", "data": {"movie": item}}
    report, fetcher = inspect(name, [response(catalog), response(catalog), response(detail)])
    assert all(report["tierResults"][tier] == "pass" for tier in ["L1_domain", "L2_homepage", "L3_search", "L4_load"])
    assert report["tierResults"]["L5_player_discovery"] == "untested"
    assert report["playbackVerification"] == "UNVERIFIED_BY_AUTOMATION"
    assert report["overallStatus"] == "degraded"
    assert "private-test-key" not in json.dumps(report)
    url = fetcher.fetch.call_args_list[0].args[0]
    assert ("/3/trending/all/day" if name == "CloudStreamHub" else "/api/v2/list_movies.json") in url


@pytest.mark.parametrize("payload", ["<html>not an API</html>", {"success": False}, []])
def test_invalid_api_root_cannot_pass(payload):
    report, fetcher = inspect("CloudStreamHub", [response(payload)])
    assert report["tierResults"]["L1_domain"] == "fail"
    assert report["overallStatus"] == "failed"
    assert fetcher.fetch.call_count == 1


def test_api_redirect_security_is_preserved():
    report, _ = inspect("YTS", [response({}, FetchStatus.UNTRUSTED_REDIRECT)])
    assert report["tierResults"]["L1_domain"] == "untrusted_redirect"
    assert report["overallStatus"] == "critical"


@pytest.mark.parametrize("name", ["CloudStreamHub", "YTS"])
def test_empty_search_and_wrong_detail_identity_fail(name):
    item = {"id": 42, "title": "Test"}
    wrong = {"id": 99, "title": "Different movie"}
    catalog = {"results": [item]} if name == "CloudStreamHub" else {"status": "ok", "data": {"movies": [item]}}
    empty = {"results": []} if name == "CloudStreamHub" else {"status": "ok", "data": {"movies": []}}
    detail = wrong if name == "CloudStreamHub" else {"status": "ok", "data": {"movie": wrong}}
    report, _ = inspect(name, [response(catalog), response(empty), response(detail)])
    assert report["tierResults"]["L3_search"] == "fail"
    assert report["tierResults"]["L4_load"] == "fail"


def test_yts_error_status_does_not_pass():
    report, _ = inspect("YTS", [response({"status": "error", "data": {"movies": [{"id": 42, "title": "Test"}]}})])
    assert report["tierResults"]["L1_domain"] == "fail"


@pytest.mark.parametrize("name", ["CloudStreamHub", "YTS"])
def test_catalog_objects_without_ids_or_titles_do_not_pass(name):
    invalid = [{"title": "Missing ID"}, {"id": 42, "title": " "}]
    payload = {"results": invalid} if name == "CloudStreamHub" else {"status": "ok", "data": {"movies": invalid}}
    report, _ = inspect(name, [response(payload), response(payload), response(payload)])
    assert report["tierResults"]["L2_homepage"] == "fail"
    assert report["tierResults"]["L3_search"] == "fail"
    assert report["tierResults"]["L4_load"] == "fail"


def test_api_challenge_is_not_json_success():
    report, _ = inspect("CloudStreamHub", [response({}, FetchStatus.CLOUDFLARE, 403)])
    assert report["tierResults"]["L1_domain"] == "automation_blocked"
    assert report["tierResults"]["L2_homepage"] == "untested"
    assert report["overallStatus"] == "degraded"
