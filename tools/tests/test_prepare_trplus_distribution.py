"""Isolated distribution tests: no Git, builds, network, or real artifacts."""

import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.prepare_trplus_distribution import DistributionError, main, prepare_distribution, validate


REPOSITORY = "example/trplus"
RAW = f"https://raw.githubusercontent.com/{REPOSITORY}/builds"


def write(root, name, data):
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data.encode() if isinstance(data, str) else data)


def artifact(manifest):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("manifest.json", json.dumps(manifest))
        archive.writestr("classes.dex", b"synthetic dex")
    return buffer.getvalue()


@pytest.fixture
def repo(tmp_path):
    root = tmp_path / "repo"
    root.mkdir()
    for name in ("README.md", "LICENSE", "gradlew", "gradlew.bat", "settings.gradle.kts",
                 "build.gradle.kts", "gradle/wrapper/gradle-wrapper.jar", "gradle.lockfile",
                 "Disabled/src/main/kotlin/Disabled.kt", "Enabled/src/main/kotlin/Enabled.kt",
                 "core/src/main/kotlin/Core.kt", "core/src/test/kotlin/CoreTest.kt",
                 "tools/tests/test_example.py", ".github/workflows/build.yml", ".gitignore",
                 ".gitattributes", "docs/ATTRIBUTION.md"):
        write(root, name, "source")
    write(root, "Enabled/build.gradle.kts", "version = 7\n")
    write(root, "Enabled/src/main/kotlin/EnabledPlugin.kt", """package com.example

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

// @CloudstreamPlugin class CommentedOut
val ignored = "@CloudstreamPlugin class NotAPlugin"
@CloudstreamPlugin
class EnabledPlugin : BasePlugin()
""")
    write(root, "Disabled/build.gradle.kts", "version = 2\n")
    write(root, "config/providers.json", json.dumps({"providers": [
        {"module": "Enabled", "enabled": True}, {"module": "Disabled", "enabled": False}]}))
    write(root, "repo.json", json.dumps({"pluginLists": [f"{RAW}/plugins.json"]}))
    data = artifact({"name": "Enabled", "version": 7,
                     "pluginClassName": "com.example.EnabledPlugin", "requiresResources": False})
    write(root, "Enabled/build/Enabled.cs3", data)
    write(root, "build/plugins.json", json.dumps([{
        "internalName": "Enabled", "version": 7, "fileSize": len(data),
        "fileHash": "sha256-" + hashlib.sha256(data).hexdigest(),
        "repositoryUrl": f"https://github.com/{REPOSITORY}", "url": f"{RAW}/Enabled.cs3"}]))
    return root


def update_plugin(root, **changes):
    path = root / "build/plugins.json"
    plugins = json.loads(path.read_text())
    plugins[0].update(changes)
    path.write_text(json.dumps(plugins))


def verify_sums(directory):
    for line in (directory / "SHA256SUMS").read_text().splitlines():
        expected, name = line.split("  ", 1)
        assert hashlib.sha256((directory / name).read_bytes()).hexdigest() == expected


def test_full_local_distribution_and_exclusions(repo):
    excluded = ["local.properties", ".env", ".git/config", ".gradle/state", ".kotlin/state",
                ".codex/agents/prompt.md", ".agents/skills/prompt.md", ".ai-workflow/PLAN.md",
                ".opencode/agents/prompt.md", "backups/stale.md", "unrelated.zip", "MANIFEST.sha256",
                "core/build/classes.bin", "tools/__pycache__/x.pyc", "tools/.scrapling/state",
                "tools/cookies.json", "core/private.key", "tools/run.log", "AGENTS.md",
                "cloudstream-trplus-final-old/README.md", "tools/secrets/account.json",
                "core/keys/signing.txt", "tools/.env.private/account.json"]
    for name in excluded:
        write(repo, name, "must not ship")
    write(repo, "reports/STATUS.md", "Current evidence\nCookie: credential\nhttps://example.com/?token=x\n")
    write(repo, "reports/raw-probe.json", '{"cookie": "credential"}')
    write(repo, "reports/raw-network.log", "cookie credential")
    write(repo, "reports/raw-probe.md", "full raw network capture")
    write(repo, "reports/validation-summary.txt", "Local integrity summary")
    write(repo, "README-TR.md", "[Current evidence](reports/STATUS.md)\n")
    write(repo, "reports/baseline-pytest.xml", '<testsuites><testsuite tests="2" failures="1" errors="0" skipped="0"><testcase><failure>secret raw failure and cookie</failure></testcase><system-out>private log</system-out></testsuite></testsuites>')
    write(repo, "reports/TEST-results.xml", '<testsuite tests="3" failures="0"><system-out>secret</system-out></testsuite>')
    output = prepare_distribution(repo, REPOSITORY)
    assert (repo / "build/plugins.json").read_bytes() == (output / "builds/plugins.json").read_bytes()
    main_files = {p.relative_to(output / "main").as_posix() for p in (output / "main").rglob("*") if p.is_file()}
    for required in ["Disabled/src/main/kotlin/Disabled.kt", "Enabled/src/main/kotlin/Enabled.kt",
                     "core/src/test/kotlin/CoreTest.kt", "gradle/wrapper/gradle-wrapper.jar",
                     ".github/workflows/build.yml", ".gitignore", ".gitattributes", "docs/ATTRIBUTION.md"]:
        assert required in main_files
    assert not set(excluded) & main_files
    assert "reports/STATUS.md" in main_files
    assert (output / "main/reports/STATUS.md").read_bytes() == (output / "reports/STATUS.md").read_bytes()
    assert (output / "main/README-TR.md").read_bytes() == (repo / "README-TR.md").read_bytes()
    assert (output / "builds/Enabled.cs3").read_bytes() == (repo / "Enabled/build/Enabled.cs3").read_bytes()
    status = json.loads((output / "reports/distribution-status.json").read_text())
    assert status["published"] is False
    assert status["manualPlayback"] == "NOT_TESTED"
    assert status["networkChecked"] is False
    assert status["plannedPublicUrls"]["repo"] == f"{RAW}/repo.json"
    assert "credential" not in (output / "reports/STATUS.md").read_text()
    assert "token=x" not in (output / "reports/STATUS.md").read_text()
    assert not (output / "reports/raw-probe.json").exists()
    assert not (output / "reports/raw-probe.md").exists()
    assert (output / "reports/validation-summary.txt").is_file()
    assert "secret" not in (output / "reports/TEST-results.xml.summary.json").read_text()
    pytest_summary = json.loads((output / "reports/baseline-pytest.xml.summary.json").read_text())
    assert pytest_summary == {"testSuites": [{"tests": 2, "failures": 1, "errors": 0, "skipped": 0}]}
    assert not (output / "reports/baseline-pytest.xml").exists()
    assert not (output / "main/reports/baseline-pytest.xml").exists()
    assert (output / "main/reports/baseline-pytest.xml.summary.json").is_file()
    for directory in (output, output / "main", output / "builds", output / "reports"):
        verify_sums(directory)
    for branch in ("main", "builds"):
        with zipfile.ZipFile(output / f"cloudstream-trplus-{branch}.zip") as archive:
            assert "repo.json" in archive.namelist()
            assert not any(n.startswith(f"{branch}/") for n in archive.namelist())
            for name in archive.namelist():
                assert archive.read(name) == (output / branch / name).read_bytes()


@pytest.mark.parametrize("changes", [
    {"fileHash": "sha256-" + "0" * 64}, {"fileSize": 1}, {"version": 8},
    {"internalName": "Other"}, {"internalName": "../Enabled"},
    {"url": f"{RAW}/Disabled.cs3"}, {"url": f"{RAW}/Enabled.cs3?token=abc"},
    {"repositoryUrl": "https://github.com/other/repo"},
])
def test_metadata_mismatch_fails_before_output(repo, changes):
    update_plugin(repo, **changes)
    with pytest.raises(DistributionError):
        prepare_distribution(repo, REPOSITORY)
    assert not (repo / "cloudstream-trplus-final").exists()


@pytest.mark.parametrize("field,value", [("name", "Other"), ("version", 8),
                                        ("pluginClassName", ""),
                                        ("pluginClassName", "com.example.OtherPlugin"),
                                        ("requiresResources", "false")])
def test_manifest_mismatch_fails_before_output(repo, field, value):
    manifest = {"name": "Enabled", "version": 7,
                "pluginClassName": "com.example.EnabledPlugin", "requiresResources": False}
    manifest[field] = value
    data = artifact(manifest)
    write(repo, "Enabled/build/Enabled.cs3", data)
    update_plugin(repo, fileHash="sha256-" + hashlib.sha256(data).hexdigest(), fileSize=len(data))
    with pytest.raises(DistributionError):
        prepare_distribution(repo, REPOSITORY)
    assert not (repo / "cloudstream-trplus-final").exists()


@pytest.mark.parametrize("kind", ["missing", "duplicate", "disabled", "repo_url", "artifact", "source"])
def test_inventory_and_missing_inputs_fail_closed(repo, kind):
    path = repo / "build/plugins.json"
    plugins = json.loads(path.read_text())
    if kind == "missing":
        path.write_text("[]")
    elif kind == "duplicate":
        path.write_text(json.dumps(plugins * 2))
    elif kind == "disabled":
        update_plugin(repo, internalName="Disabled")
    elif kind == "repo_url":
        write(repo, "repo.json", json.dumps({"pluginLists": ["http://example.com/plugins.json"]}))
    elif kind == "artifact":
        (repo / "Enabled/build/Enabled.cs3").unlink()
    elif kind == "source":
        (repo / "gradle/wrapper/gradle-wrapper.jar").unlink()
    with pytest.raises(DistributionError):
        prepare_distribution(repo, REPOSITORY)
    assert not (repo / "cloudstream-trplus-final").exists()


def test_corrupt_zip_even_with_matching_metadata_is_rejected(repo):
    data = b"not a zip"
    write(repo, "Enabled/build/Enabled.cs3", data)
    update_plugin(repo, fileSize=len(data), fileHash="sha256-" + hashlib.sha256(data).hexdigest())
    with pytest.raises(DistributionError):
        prepare_distribution(repo, REPOSITORY)
    assert not (repo / "cloudstream-trplus-final").exists()


@pytest.mark.parametrize("existing_file", [False, True])
def test_existing_target_preserved(repo, existing_file):
    output = repo / "cloudstream-trplus-final"
    if existing_file:
        output.write_bytes(b"sentinel zip")
    else:
        write(repo, "cloudstream-trplus-final/sentinel", b"keep me")
    with pytest.raises(DistributionError, match="existing output"):
        prepare_distribution(repo, REPOSITORY)
    assert (output if existing_file else output / "sentinel").read_bytes() == (
        b"sentinel zip" if existing_file else b"keep me")


@pytest.mark.parametrize("output", ["../outside", "build/package", "core/package", "Enabled/package"])
def test_unsafe_output_rejected(repo, output):
    with pytest.raises(DistributionError):
        prepare_distribution(repo, REPOSITORY, output)


def test_symlink_source_not_followed(repo, tmp_path):
    outside = tmp_path / "outside.txt"
    outside.write_text("private external data")
    try:
        (repo / "tools/external.txt").symlink_to(outside)
    except OSError:
        pytest.skip("Symlinks unavailable without Windows developer mode")
    output = prepare_distribution(repo, REPOSITORY)
    assert not (output / "main/tools/external.txt").exists()


@pytest.mark.parametrize("repository", ["https://github.com/example/trplus", "example/trplus/extra", "example/.."])
def test_invalid_repository_rejected(repo, repository):
    with pytest.raises(DistributionError):
        prepare_distribution(repo, repository)
    assert not (repo / "cloudstream-trplus-final").exists()


def test_output_outside_repository_rejected(repo, tmp_path):
    with pytest.raises(DistributionError):
        prepare_distribution(repo, REPOSITORY, tmp_path / "external")
    assert not (tmp_path / "external").exists()


def test_missing_disabled_module_accepted_and_physical_unregistered_module_included(repo):
    registry = json.loads((repo / "config/providers.json").read_text())
    registry["providers"].append({"module": "AbsentDisabled", "enabled": False})
    write(repo, "config/providers.json", json.dumps(registry))
    write(repo, "Unregistered/build.gradle.kts", "version = 1\n")
    write(repo, "Unregistered/src/main/kotlin/Provider.kt", "source")
    output = prepare_distribution(repo, REPOSITORY)
    assert not (output / "main/AbsentDisabled").exists()
    assert (output / "main/Disabled/src/main/kotlin/Disabled.kt").is_file()
    assert (output / "main/Unregistered/src/main/kotlin/Provider.kt").is_file()


def test_missing_enabled_gradle_module_rejected(repo):
    (repo / "Enabled/build.gradle.kts").unlink()
    with pytest.raises(DistributionError, match="Missing input: Enabled/build.gradle.kts"):
        prepare_distribution(repo, REPOSITORY)
    assert not (repo / "cloudstream-trplus-final").exists()


@pytest.mark.parametrize("source", [
    "package com.example\nclass EnabledPlugin : BasePlugin()\n",
    "package other.example\n@CloudstreamPlugin\nclass EnabledPlugin : BasePlugin()\n",
    "package com.example\n@CloudstreamPlugin\nclass EnabledPlugin : BasePlugin()\n"
    "@CloudstreamPlugin\nclass OtherPlugin : BasePlugin()\n",
])
def test_missing_wrong_package_or_ambiguous_source_identity_rejected(repo, source):
    write(repo, "Enabled/src/main/kotlin/EnabledPlugin.kt", source)
    with pytest.raises(DistributionError):
        prepare_distribution(repo, REPOSITORY)
    assert not (repo / "cloudstream-trplus-final").exists()


@pytest.mark.parametrize("relative", [
    "Enabled/src/main/kotlin/EnabledPlugin.kt", "Enabled/build.gradle.kts",
])
def test_utf8_bom_source_identity_and_gradle_accepted(repo, relative):
    path = repo / relative
    path.write_bytes(b"\xef\xbb\xbf" + path.read_bytes())
    artifacts, _, _ = validate(repo, REPOSITORY)
    assert "Enabled.cs3" in artifacts
    assert not (repo / "cloudstream-trplus-final").exists()


def test_utf8_bom_wrong_source_class_rejected(repo):
    write(repo, "Enabled/src/main/kotlin/EnabledPlugin.kt",
          "\ufeffpackage com.example\n@CloudstreamPlugin\nclass WrongPlugin : BasePlugin()\n")
    with pytest.raises(DistributionError, match="pluginClassName does not match annotated source"):
        validate(repo, REPOSITORY)
    assert not (repo / "cloudstream-trplus-final").exists()


def baseline(repo, version=7, missing=False):
    plugins = json.loads((repo / "build/plugins.json").read_text())
    plugins[0]["version"] = version
    if missing:
        plugins.append({"internalName": "BlenderOpenMovies", "version": 1,
                        "fileHash": "sha256-" + "a" * 64})
    path = repo / "backups/published.json"
    write(repo, "backups/published.json", json.dumps(plugins))
    return path


def test_packager_cli_requires_frozen_baseline(repo):
    with pytest.raises(SystemExit) as error:
        main(["--root", str(repo), "--repository", REPOSITORY])
    assert error.value.code == 2
    assert not (repo / "cloudstream-trplus-final").exists()


@pytest.mark.parametrize("failure", ["downgrade", "changed_hash", "missing_id", "invalid"])
def test_release_guard_failure_leaves_no_output(repo, failure):
    published = baseline(repo, version=8 if failure == "downgrade" else 7,
                         missing=failure == "missing_id")
    if failure == "changed_hash":
        entries = json.loads(published.read_text())
        entries[0]["fileHash"] = "sha256-" + "0" * 64
        published.write_text(json.dumps(entries))
    elif failure == "invalid":
        published.write_text("<html>bad baseline</html>")
    before = published.read_bytes()
    assert main(["--root", str(repo), "--repository", REPOSITORY,
                 "--published", str(published), "--output", "test-v39"]) == 1
    assert not (repo / "test-v39").exists()
    assert published.read_bytes() == before


def test_test_only_missing_inventory_exception_is_packaged(repo):
    published = baseline(repo, missing=True)
    output = prepare_distribution(repo, REPOSITORY, "test-v39", published,
                                  allow_missing=["BlenderOpenMovies"])
    report = json.loads((output / "reports/release-safety.json").read_text())
    assert report["passed"] and report["mode"] == "test"
    assert report["missingIds"] == ["BlenderOpenMovies"]
    assert not report["releaseInventoryComplete"]
    assert (output / "main/reports/release-safety.json").read_bytes() == (
        output / "reports/release-safety.json").read_bytes()
    assert json.loads((output / "reports/distribution-status.json").read_text())["releaseSafetyChecked"]


def test_packager_cli_valid_baseline_passes(repo):
    published = baseline(repo)
    assert main(["--root", str(repo), "--repository", REPOSITORY,
                 "--published", str(published), "--output", "test-v39"]) == 0
    assert (repo / "test-v39/reports/release-safety.json").is_file()
