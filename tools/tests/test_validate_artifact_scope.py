"""Canonical artifact validation must not consume preserved delivery copies."""

import hashlib
from pathlib import Path
import sys

import pytest

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.validate_repo import validate_artifacts


def write(root, relative, data):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return path


def plugin(name="CloudStreamHub", data=b"current v39", display=None):
    return {"internalName": name, "name": display or name,
            "fileHash": "sha256-" + hashlib.sha256(data).hexdigest()}


@pytest.fixture
def current(tmp_path):
    write(tmp_path, "CloudStreamHub/build.gradle.kts", b"version = 39")
    write(tmp_path, "CloudStreamHub/build/CloudStreamHub.cs3", b"current v39")
    return tmp_path


def validate(root, plugins=None, enabled=("CloudStreamHub",), physical=("CloudStreamHub",)):
    return validate_artifacts(root, [plugin()] if plugins is None else plugins, enabled, physical)


def test_current_artifact_passes_despite_immutable_old_deliveries(current):
    old_paths = [write(current, relative, b"immutable old v37") for relative in (
        "cloudstream-trplus-final/builds/CloudStreamHub.cs3",
        "backups/published-builds/CloudStreamHub.cs3", "build/CloudStreamHub.cs3",
        "other-test-delivery/builds/CloudStreamHub.cs3")]
    assert validate(current) == []
    assert all(path.read_bytes() == b"immutable old v37" for path in old_paths)
    assert (current / "CloudStreamHub/build/CloudStreamHub.cs3").read_bytes() == b"current v39"


def test_historical_copy_cannot_satisfy_missing_current_artifact(tmp_path):
    write(tmp_path, "CloudStreamHub/build.gradle.kts", b"version = 39")
    write(tmp_path, "cloudstream-trplus-final/builds/CloudStreamHub.cs3", b"current v39")
    assert validate(tmp_path) == [
        "Missing canonical .cs3 artifact: CloudStreamHub/build/CloudStreamHub.cs3"]


def test_matching_archive_does_not_hide_current_hash_mismatch(current):
    write(current, "CloudStreamHub/build/CloudStreamHub.cs3", b"wrong current bytes")
    write(current, "cloudstream-trplus-final/builds/CloudStreamHub.cs3", b"current v39")
    assert validate(current) == ["Hash mismatch for CloudStreamHub/build/CloudStreamHub.cs3!"]


def test_internal_name_not_display_name_selects_canonical_artifact(tmp_path):
    write(tmp_path, "FourKHDHub/build.gradle.kts", b"version = 9")
    write(tmp_path, "FourKHDHub/build/FourKHDHub.cs3", b"4k current")
    assert validate(tmp_path, [plugin("FourKHDHub", b"4k current", "4KHDHub")],
                    enabled=["FourKHDHub"], physical=["FourKHDHub"]) == []


def test_duplicate_and_unknown_ids_cannot_replace_enabled_id(current):
    errors = validate(current, [plugin(), plugin(), plugin("Other")],
                      enabled=["CloudStreamHub", "Expected"],
                      physical=["CloudStreamHub", "Expected", "Other"])
    assert "Duplicate internalName in build/plugins.json: CloudStreamHub" in errors
    assert "Unknown/disabled plugin internalName: Other" in errors
    assert "Enabled module missing from plugins.json: Expected" in errors


def test_metadata_id_requires_physical_enabled_module(current):
    assert "Unknown/disabled plugin internalName: CloudStreamHub" in validate(current, physical=[])
    assert "Unknown/disabled plugin internalName: CloudStreamHub" in validate(current, enabled=[])
    (current / "CloudStreamHub/build.gradle.kts").unlink()
    assert validate(current) == ["Missing physical provider module: CloudStreamHub"]


@pytest.mark.parametrize("entries", [{}, [None], [{"name": "CloudStreamHub"}],
                                   [plugin("../CloudStreamHub")], [plugin("/absolute")],
                                   [plugin("CloudStreamHub/../../outside")]])
def test_malformed_or_traversing_identity_rejected(current, entries):
    assert validate(current, entries)


@pytest.mark.parametrize("value", [None, "", 123, "sha256-bad", "a" * 64])
def test_missing_or_invalid_hash_rejected(current, value):
    entry = plugin()
    entry["fileHash"] = value
    assert validate(current, [entry]) == [
        "Plugin CloudStreamHub has missing/invalid fileHash in plugins.json"]


@pytest.mark.parametrize("linked", ["artifact", "build", "module", "module_gradle"])
def test_symlink_artifact_inputs_rejected(tmp_path, linked):
    outside = tmp_path / "outside"
    outside.mkdir()
    write(outside, "module/build.gradle.kts", b"version = 39")
    write(outside, "module/build/CloudStreamHub.cs3", b"current v39")
    root = tmp_path / "repo"
    root.mkdir()
    try:
        if linked == "module":
            (root / "CloudStreamHub").symlink_to(outside / "module", target_is_directory=True)
        else:
            (root / "CloudStreamHub").mkdir()
            if linked == "module_gradle":
                (root / "CloudStreamHub/build.gradle.kts").symlink_to(outside / "module/build.gradle.kts")
            else:
                write(root, "CloudStreamHub/build.gradle.kts", b"version = 39")
            if linked == "build":
                (root / "CloudStreamHub/build").symlink_to(outside / "module/build", target_is_directory=True)
            else:
                (root / "CloudStreamHub/build").mkdir()
                if linked == "artifact":
                    (root / "CloudStreamHub/build/CloudStreamHub.cs3").symlink_to(
                        outside / "module/build/CloudStreamHub.cs3")
                else:
                    write(root, "CloudStreamHub/build/CloudStreamHub.cs3", b"current v39")
    except OSError:
        pytest.skip("Symlinks unavailable without Windows developer mode")
    assert validate(root) == ["Unsafe symlink/junction artifact input: CloudStreamHub"]


def test_all_26_canonical_modules_validate_without_archive_inventory_interference(tmp_path):
    names = [f"Provider{index}" for index in range(26)]
    entries = []
    for name in names:
        data = name.encode()
        write(tmp_path, f"{name}/build.gradle.kts", b"version = 39")
        write(tmp_path, f"{name}/build/{name}.cs3", data)
        write(tmp_path, f"cloudstream-trplus-final/builds/{name}.cs3", b"old")
        entries.append(plugin(name, data))
    assert validate(tmp_path, entries, names, names) == []
