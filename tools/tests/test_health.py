import os
import json
import pytest
from tools.provider_health import evaluate_overall_health, REQUIRED_HEALTH_TIERS

def test_overall_health_evaluator_semantics():
    baseline_pass = {
        'L0_config': 'pass',
        'L1_domain': 'pass',
        'L2_homepage': 'pass',
        'L3_search': 'pass',
        'L4_load': 'pass',
        'L5_player_discovery': 'player_discovered'
    }
    assert evaluate_overall_health(baseline_pass) == 'healthy'

    l3_fail = dict(baseline_pass, L3_search='fail')
    assert evaluate_overall_health(l3_fail) == 'degraded'

    l5_fail = dict(baseline_pass, L5_player_discovery='player_not_found')
    assert evaluate_overall_health(l5_fail) == 'degraded'

    config_err = dict(baseline_pass, L1_domain='config_error')
    assert evaluate_overall_health(config_err) == 'critical'

    untrusted_redir = dict(baseline_pass, L1_domain='untrusted_redirect')
    assert evaluate_overall_health(untrusted_redir) == 'critical'

    drift = dict(baseline_pass, L2_homepage='drift_detected')
    assert evaluate_overall_health(drift) == 'warning'

    l0_fail = dict(baseline_pass, L0_config='fail')
    assert evaluate_overall_health(l0_fail) == 'failed'

def test_overall_health_invariant_rule():
    for fail_tier in REQUIRED_HEALTH_TIERS:
        tiers = {
            'L0_config': 'pass',
            'L1_domain': 'pass',
            'L2_homepage': 'pass',
            'L3_search': 'pass',
            'L4_load': 'pass',
            'L5_player_discovery': 'player_discovered'
        }
        if fail_tier == 'L5_player_discovery':
            tiers[fail_tier] = 'player_not_found'
        else:
            tiers[fail_tier] = 'fail'

        status = evaluate_overall_health(tiers)
        assert status != 'healthy', f'Provider was unexpectedly HEALTHY despite {fail_tier} being FAIL'
        assert status in ('degraded', 'failed', 'critical')

def test_generated_health_report_invariant_if_exists():
    report_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'reports', 'provider-health.json')
    if not os.path.isfile(report_path):
        pytest.skip('reports/provider-health.json does not exist yet; tested on generator')

    with open(report_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    for item in data:
        prov = item.get('provider')
        overall = item.get('overallStatus')
        tiers = item.get('tierResults', {})
        has_failed_tier = any(
            v == 'fail' or v.startswith('fail') or v in ('untrusted_redirect', 'config_error', 'content_marker_mismatch')
            for k, v in tiers.items() if k in REQUIRED_HEALTH_TIERS
        )
        if tiers.get('L5_player_discovery') == 'player_not_found':
            has_failed_tier = True

        if has_failed_tier:
            assert overall != 'healthy', f'Invariant violated: {prov} is marked \'{overall}\' while having failed tiers: {tiers}'

def test_derive_health_score():
    from tools.provider_health import derive_health_score
    tiers_pass = {'L0_config': 'pass', 'L1_domain': 'pass', 'L2_homepage': 'pass', 'L3_search': 'pass', 'L4_load': 'pass', 'L5_player_discovery': 'player_discovered'}
    assert derive_health_score('healthy', tiers_pass) == 100

    tiers_warn = dict(tiers_pass, L2_homepage='drift_detected')
    assert derive_health_score('warning', tiers_warn) == 75

    tiers_degraded = dict(tiers_pass, L3_search='fail')
    score = derive_health_score('degraded', tiers_degraded)
    assert 35 <= score <= 50

    tiers_cf = dict(tiers_pass, L2_homepage='cloudflare_challenge', L3_search='cloudflare_challenge')
    score_cf = derive_health_score('degraded', tiers_cf)
    assert 35 <= score_cf <= 50
    assert score_cf == 45 # 2 degraded states -> 50 - 1*5 = 45

    tiers_auto = dict(tiers_pass, L3_search='automation_blocked')
    score_auto = derive_health_score('degraded', tiers_auto)
    assert 35 <= score_auto <= 50
    assert score_auto == 50 # 1 degraded state -> 50 - 0 = 50

    tiers_failed = dict(tiers_pass, L0_config='fail')
    assert derive_health_score('failed', tiers_failed) == 10

    tiers_crit = dict(tiers_pass, L1_domain='untrusted_redirect')
    assert derive_health_score('critical', tiers_crit) == 0

    # Strict Invariant: failed or critical status can NEVER have score 100
    for bad_status in ['critical', 'failed', 'degraded']:
        bad_score = derive_health_score(bad_status, tiers_pass)
        assert bad_score < 100, f'Impossible state: {bad_status} must never score 100'

def test_health_report_schema_and_playback_distinction():
    from tools.provider_health import inspect_provider
    # Verify report keys and discovery-versus-playback semantics
    dummy_provider = {
        'name': 'NonExistent',
        'module': 'NonExistent',
        'enabled': True,
        'domainKey': 'NonExistent'
    }
    dummy_domains = {'providers': {}}
    rep = inspect_provider(dummy_provider, '/tmp', dummy_domains, None)
    assert rep['schemaVersion'] == 1
    assert rep['playbackVerification'] == 'UNVERIFIED_BY_AUTOMATION'
    assert 'healthScore' in rep
    assert rep['overallStatus'] == 'failed'
    assert rep['healthScore'] == 10
