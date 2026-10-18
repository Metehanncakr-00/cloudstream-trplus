import copy
import json

import pytest

from tools import manage_health_issues as manager


@pytest.fixture
def monitor(tmp_path, monkeypatch):
    (tmp_path / "tools").mkdir()
    (tmp_path / "config").mkdir()
    (tmp_path / "reports").mkdir()
    monkeypatch.setattr(manager, "__file__", str(tmp_path / "tools" / "manage_health_issues.py"))
    monkeypatch.setattr(manager, "GITHUB_TOKEN", "mock-token")
    monkeypatch.setattr(manager, "GITHUB_REPOSITORY", "test/repo")
    state = {"issues": [], "comments": {}, "writes": []}

    def api(endpoint, method="GET", data=None):
        if method == "GET":
            if endpoint.startswith("/issues?"):
                return copy.deepcopy(state["issues"])
            number = int(endpoint.split("/")[2])
            return copy.deepcopy(state["comments"].get(number, []))
        state["writes"].append((endpoint, method, data))
        if endpoint == "/issues":
            issue = dict(data, number=len(state["issues"]) + 1)
            issue["labels"] = [{"name": label} for label in data["labels"]]
            state["issues"].append(issue)
            return copy.deepcopy(issue)
        number = int(endpoint.split("/")[2])
        if endpoint.endswith("/comments"):
            state["comments"].setdefault(number, []).append(data)
        return data

    monkeypatch.setattr(manager, "github_api", api)

    def run(reports=(), domains=(), enabled=True, dead=False):
        files = {
            "config/providers.json": {"providers": [{"name": "Example", "enabled": enabled}]},
            "config/domains.json": {"providers": {"Example": {"status": "dead" if dead else "active"}}},
            "reports/provider-health.json": list(reports),
            "reports/domain-health.json": list(domains),
        }
        for path, value in files.items():
            (tmp_path / path).write_text(json.dumps(value), encoding="utf-8")
        manager.main()

    return state, run


def issue(number, title, labels, body=""):
    return {"number": number, "title": title, "body": body,
            "labels": [{"name": label} for label in labels + ["Example"]]}


def report(**tiers):
    return {"provider": "Example", "tierResults": tiers, "overallStatus": "healthy"}


def test_distinct_problem_types_create_distinct_issues(monitor):
    state, run = monitor
    run([report(L0_config="fail", L1_domain="fail (HTTP_451)", L2_homepage="drift_detected")])
    assert {manager.problem_type(i) for i in state["issues"]} == {
        "L0_config", "L1_domain", "selector_drift"}


def test_domain_and_provider_untrusted_redirect_share_one_issue(monitor):
    state, run = monitor
    run([dict(report(L1_domain="untrusted_redirect"), candidateHost="bad.example")],
        [{"provider": "Example", "status": "untrusted_redirect", "candidateHost": "bad.example"}])
    assert len(state["issues"]) == 1
    assert manager.problem_type(state["issues"][0]) == "untrusted_redirect"


def test_unchanged_evidence_ignores_time_duration_and_workflow_run(monitor, monkeypatch):
    state, run = monitor
    failing = dict(report(L1_domain="fail (HTTP_451)"), checkedAt="first", durationMs=1)
    run([failing])
    monkeypatch.setattr(manager, "RUN_ID", "second-run")
    run([dict(failing, checkedAt="second", durationMs=999)])
    assert len(state["writes"]) == 1
    changed = dict(failing, diagnostic={"L1": "HTTP 403"})
    run([changed])
    assert len(state["writes"]) == 2
    run([changed])
    assert len(state["writes"]) == 2
    run([failing])
    assert len(state["writes"]) == 3  # A -> B -> A is a material change.


def test_legacy_issue_gets_one_state_comment_then_stays_quiet(monitor):
    state, run = monitor
    state["issues"] = [issue(17, "[Provider Health] Example — L1_domain regression", ["provider-health"])]
    run([report(L1_domain="fail (HTTP_451)")])
    run([report(L1_domain="fail (HTTP_451)")])
    assert len(state["writes"]) == 1
    assert state["writes"][0][0] == "/issues/17/comments"


def test_same_tier_recovery_only_not_all_provider_issues(monitor):
    state, run = monitor
    state["issues"] = [
        issue(1, "[Provider Health] Example — L1_domain regression", ["provider-health"]),
        issue(2, "[Provider Health] Example — L0_config regression", ["provider-health"]),
        issue(3, "[Security Alert] Example — untrusted redirect", ["security", "provider-health"]),
        issue(4, "Example playback failure", ["playback"]),
        issue(5, "Example unrelated bug", ["bug"]),
        issue(6, "[Provider Health] Example — L1_domain regression", ["provider-health", "playback"]),
    ]
    run([report(L0_config="untested", L1_domain="pass")])
    assert [endpoint for endpoint, method, _ in state["writes"] if method == "PATCH"] == ["/issues/1"]


def test_conflicting_domain_report_blocks_l1_recovery(monitor):
    state, run = monitor
    state["issues"] = [issue(1, "[Provider Health] Example — L1_domain regression", ["provider-health"])]
    run([report(L1_domain="pass")], [{"provider": "Example", "status": "http_451"}])
    assert state["writes"] == []


@pytest.mark.parametrize("enabled,dead", [(False, False), (True, True)])
def test_inactive_provider_does_not_close_comment_or_create(monitor, enabled, dead):
    state, run = monitor
    state["issues"] = [issue(1, "[Provider Health] Example — L1_domain regression", ["provider-health"])]
    run([report(L1_domain="pass")], enabled=enabled, dead=dead)
    run([report(L0_config="fail", L1_domain="untrusted_redirect")],
        [{"provider": "Example", "candidateHost": "bad.example"}], enabled=enabled, dead=dead)
    assert state["writes"] == []


def test_drift_recovery_requires_original_tiers_pass(monitor):
    state, run = monitor
    run([report(L2_homepage="drift_detected", L3_search="drift_detected")])
    state["writes"].clear()
    run([report(L2_homepage="pass", L3_search="untested")])
    assert state["writes"] == []
    run([report(L2_homepage="pass", L3_search="pass", L4_load="fail")])
    assert any(method == "PATCH" for _, method, _ in state["writes"])


def test_config_error_is_not_reported_as_untrusted_redirect(monitor):
    state, run = monitor
    run([dict(report(L1_domain="config_error"), overallStatus="critical")])
    assert [manager.problem_type(i) for i in state["issues"]] == ["L1_domain"]


def test_drift_recovery_uses_latest_recorded_failure_tiers(monitor):
    state, run = monitor
    run([report(L2_homepage="drift_detected")])
    run([report(L2_homepage="drift_detected", L3_search="drift_detected")])
    state["writes"].clear()
    run([report(L2_homepage="pass", L3_search="untested")])
    assert state["writes"] == []


@pytest.mark.parametrize("previous_status", ["untested", "fail", None])
def test_drift_updates_preserve_unresolved_previous_tiers(monitor, previous_status):
    state, run = monitor
    run([report(L2_homepage="drift_detected")])
    current = {"L3_search": "drift_detected"}
    if previous_status is not None:
        current["L2_homepage"] = previous_status
    run([report(**current)])
    metadata = manager.latest_issue_metadata(state["issues"][0], "selector_drift")
    assert metadata["tiers"] == ["L2_homepage", "L3_search"]
    state["writes"].clear()
    run([report(**current)])
    assert state["writes"] == []  # Unchanged evidence and tier history remain quiet.
    current["L3_search"] = "pass"
    run([report(**current)])
    assert state["writes"] == []  # L2 has never recovered.
    run([report(L2_homepage="pass", L3_search="pass")])
    assert [endpoint for endpoint, method, _ in state["writes"] if method == "PATCH"] == ["/issues/1"]


@pytest.mark.parametrize("recovery_status", ["pass", "player_discovered"])
def test_drift_updates_remove_only_explicitly_recovered_previous_tiers(monitor, recovery_status):
    state, run = monitor
    run([report(L2_homepage="drift_detected", L3_search="drift_detected")])
    run([report(L2_homepage=recovery_status, L3_search="drift_detected")])
    metadata = manager.latest_issue_metadata(state["issues"][0], "selector_drift")
    assert metadata["tiers"] == ["L3_search"]
    state["writes"].clear()
    run([report(L2_homepage="untested", L3_search="pass")])
    assert [endpoint for endpoint, method, _ in state["writes"] if method == "PATCH"] == ["/issues/1"]


def test_comment_fetch_failure_prevents_recovery_and_updates(monitor, monkeypatch):
    state, run = monitor
    state["issues"] = [issue(1, "[Provider Health] Example — L1_domain regression", ["provider-health"])]
    original = manager.github_api

    def api(endpoint, method="GET", data=None):
        if method == "GET" and "/comments?" in endpoint:
            return None
        return original(endpoint, method, data)

    monkeypatch.setattr(manager, "github_api", api)
    run([report(L1_domain="pass")])
    run([report(L1_domain="fail")])
    assert state["writes"] == []


def test_issue_fetch_failure_prevents_duplicate_creation(monitor, monkeypatch):
    state, run = monitor
    monkeypatch.setattr(manager, "get_open_issues", lambda: None)
    run([report(L1_domain="fail")])
    assert state["writes"] == []


def test_get_open_issues_paginates_and_excludes_pull_requests(monkeypatch):
    pages = [[{"number": i} for i in range(100)], [{"number": 100, "pull_request": {}}, {"number": 101}]]
    calls = []

    def api(endpoint):
        calls.append(endpoint)
        return pages[len(calls) - 1]

    monkeypatch.setattr(manager, "github_api", api)
    assert len(manager.get_open_issues()) == 101
    assert calls[-1].endswith("page=2")


def test_latest_state_reads_all_comment_pages(monkeypatch):
    marker = lambda fingerprint: {"body": '<!-- health-state: ' + json.dumps({
        "problem_type": "L1_domain", "fingerprint": fingerprint}) + ' -->'}
    pages = [[marker("old")] * 100, [marker("latest")]]
    calls = []

    def api(endpoint):
        calls.append(endpoint)
        return pages[len(calls) - 1]

    monkeypatch.setattr(manager, "github_api", api)
    assert manager.latest_issue_metadata({"number": 1}, "L1_domain")["fingerprint"] == "latest"
    assert calls[-1].endswith("page=2")


def test_failed_close_does_not_post_repeated_resolution_comments(monkeypatch):
    calls = []

    def api(endpoint, method="GET", data=None):
        calls.append((endpoint, method))
        return None

    monkeypatch.setattr(manager, "github_api", api)
    assert manager.close_issue(1) is None
    assert calls == [("/issues/1", "PATCH")]
