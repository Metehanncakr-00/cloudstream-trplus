"""Offline release guard and no-publication workflow contract tests."""

import json
from pathlib import Path
import re
import sys

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.verify_release_safety import ReleaseSafetyError, main, verify_release_safety


def plugin(name="CloudStreamHub", version=38, file_hash="a"):
    return {"internalName": name, "version": version, "fileHash": "sha256-" + file_hash * 64}


def inputs(tmp_path, local, published):
    paths = tmp_path / "local.json", tmp_path / "published.json"
    for path, entries in zip(paths, (local, published)):
        path.write_text(json.dumps(entries), encoding="utf-8")
    return paths


@pytest.mark.parametrize("local_version,remote_version,local_hash,remote_hash,passed", [
    (37, 38, "a", "a", False), (37, 38, "b", "a", False),
    (38, 38, "a", "a", True), (38, 38, "b", "a", False),
    (39, 38, "a", "a", True), (39, 38, "b", "a", True),
])
def test_version_and_bytes_invariant(tmp_path, local_version, remote_version,
                                     local_hash, remote_hash, passed):
    paths = inputs(tmp_path, [plugin(version=local_version, file_hash=local_hash)],
                   [plugin(version=remote_version, file_hash=remote_hash)])
    before = [path.read_bytes() for path in paths]
    report = verify_release_safety(*paths)
    assert report["passed"] is passed
    assert report["published"] is False
    assert len(report["publishedManifestSha256"]) == 64
    assert before == [path.read_bytes() for path in paths]


def test_animecix_107_changed_bytes_requires_bump(tmp_path):
    report = verify_release_safety(*inputs(tmp_path, [plugin("AnimeciX", 107, "b")],
                                           [plugin("AnimeciX", 107)]))
    assert report["errors"] == ["Changed bytes require version bump: AnimeciX version 107"]


def test_removed_published_id_fails_by_default_and_is_reported(tmp_path):
    paths = inputs(tmp_path, [plugin()], [plugin(), plugin("BlenderOpenMovies")])
    report = verify_release_safety(*paths)
    assert not report["passed"]
    assert report["missingIds"] == ["BlenderOpenMovies"]
    assert not report["releaseInventoryComplete"]
    with pytest.raises(ReleaseSafetyError, match="requires --mode test"):
        verify_release_safety(*paths, allow_missing=["BlenderOpenMovies"])
    report = verify_release_safety(*paths, mode="test", allow_missing=["BlenderOpenMovies"])
    assert report["passed"]
    assert report["allowedMissingIds"] == report["missingIds"] == ["BlenderOpenMovies"]
    assert not report["releaseInventoryComplete"]


def test_missing_exception_does_not_allow_other_removals_or_version_errors(tmp_path):
    paths = inputs(tmp_path, [plugin(version=37)],
                   [plugin(), plugin("BlenderOpenMovies"), plugin("Other")])
    report = verify_release_safety(*paths, mode="test", allow_missing=["BlenderOpenMovies"])
    assert report["errors"] == ["Removed published ID: Other", "Version regression: CloudStreamHub 37 < 38"]


def test_new_ids_require_explicit_authorization(tmp_path):
    paths = inputs(tmp_path, [plugin(), plugin("NewProvider", 1)], [plugin()])
    assert not verify_release_safety(*paths)["passed"]
    report = verify_release_safety(*paths, allow_new=["NewProvider"])
    assert report["passed"]
    assert report["newIds"] == report["allowedNewIds"] == ["NewProvider"]


@pytest.mark.parametrize("side", [0, 1])
@pytest.mark.parametrize("invalid", [
    [], {}, [None], [plugin(), plugin()],
    [plugin(version=True)], [plugin(version="38")], [plugin(version=0)],
    [plugin(version=-1)], [plugin(version=38.0)],
    [{"version": 1, "fileHash": "sha256-" + "a" * 64}],
    [plugin(name="../bad")], [plugin(name="")],
    [{**plugin(), "fileHash": "sha256-bad"}],
    [{**plugin(), "fileHash": None}], [{**plugin(), "fileHash": "SHA256-" + "a" * 64}],
])
def test_malformed_manifest_rejected_on_either_side(tmp_path, side, invalid):
    entries = [[plugin()], [plugin()]]
    entries[side] = invalid
    with pytest.raises(ReleaseSafetyError):
        verify_release_safety(*inputs(tmp_path, *entries))


@pytest.mark.parametrize("content", [b"<html>HTTP failure</html>", b"{", b"\xff"])
def test_invalid_download_fails_closed(tmp_path, content):
    local, published = inputs(tmp_path, [plugin()], [plugin()])
    published.write_bytes(content)
    assert main(["--local", str(local), "--published", str(published)]) == 1


def test_cli_records_failed_comparison_without_mutation(tmp_path):
    local, published = inputs(tmp_path, [plugin(version=37)], [plugin()])
    report = tmp_path / "report.json"
    assert main(["--local", str(local), "--published", str(published),
                 "--report", str(report)]) == 1
    result = json.loads(report.read_text())
    assert not result["passed"]
    assert "37 < 38" in result["errors"][0]
    assert main(["--local", str(local), "--published", str(published),
                 "--report", str(published)]) == 1
    assert json.loads(published.read_text()) == [plugin()]


def test_missing_baseline_and_required_cli_args_fail_closed(tmp_path):
    local, published = inputs(tmp_path, [plugin()], [plugin()])
    assert main(["--local", str(local), "--published", str(tmp_path / "absent")]) == 1
    with pytest.raises(SystemExit) as error:
        main(["--local", str(local)])
    assert error.value.code == 2


def test_workflow_is_read_only_candidate_build_not_publication():
    text = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
    assert "name: Build test distribution (no publish)" in text
    assert re.search(r"permissions:\s*\n\s+contents: read", text)
    assert not re.search(r"\bwrite\b|--force|git\s+(push|commit|rm|clone)|checkout --orphan", text)
    assert not re.search(r"ref:\s*['\"]?builds|secrets\.|gh\s+release|create-release", text)
    assert "persist-credentials: false" in text
    assert "--verify-artifacts" in text
    assert "--published \"$RUNNER_TEMP/published-plugins.json\"" in text
    assert "--mode test" in text
    assert "--allow-missing" not in text
    assert '"https://raw.githubusercontent.com/$TARGET_REPOSITORY/builds/plugins.json"' in text
    assert "--fail" in text and "--proto '=https'" in text
    assert "TARGET_REPOSITORY: ${{ github.repository }}" in text
    assert "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02" in text
    assert "overwrite: false" in text
    # Context values are passed through env, never interpolated into shell code.
    for block in re.findall(r"run: \|\n((?:          .*\n|\n)+)", text):
        assert "${{" not in block
