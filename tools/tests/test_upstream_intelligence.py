import subprocess

from tools.upstream_intelligence import analyze, render_markdown


def init_repo(path):
    path.mkdir()
    subprocess.run(["git", "init", "-b", "main", str(path)], check=True, capture_output=True)
    subprocess.run(["git", "-C", str(path), "config", "user.name", "Test"], check=True)
    subprocess.run(["git", "-C", str(path), "config", "user.email", "test@example.invalid"], check=True)
    return path


def commit(path, filename, contents, message):
    (path / filename).write_text(contents, encoding="utf-8")
    subprocess.run(["git", "-C", str(path), "add", filename], check=True)
    subprocess.run(["git", "-C", str(path), "commit", "-m", message], check=True, capture_output=True)
    return subprocess.run(
        ["git", "-C", str(path), "rev-parse", "HEAD"], check=True, capture_output=True, text=True
    ).stdout.strip()


def branch_from(path, target, commit_sha, branch):
    subprocess.run(["git", "clone", str(target), str(path)], check=True, capture_output=True)
    subprocess.run(["git", "-C", str(path), "checkout", "-b", branch, commit_sha], check=True, capture_output=True)
    subprocess.run(["git", "-C", str(path), "config", "user.name", "Test"], check=True)
    subprocess.run(["git", "-C", str(path), "config", "user.email", "test@example.invalid"], check=True)
    return path


def test_reports_unique_and_target_equivalent_patches(tmp_path):
    target = init_repo(tmp_path / "target")
    base = commit(target, "base.txt", "base\n", "base")
    target_patch = commit(target, "core.kt", "common fix\n", "target fix")

    source = branch_from(tmp_path / "source", target, base, "fork")
    source_patch = commit(source, "core.kt", "common fix\n", "cherry-picked fix")
    unique = commit(source, "provider.kt", "new fix\n", "unique provider fix")

    report = analyze(target, [("fork:test", source, None)], 20)
    source_report = report["sources"][0]
    by_sha = {item["sha"]: item for item in source_report["commits"]}

    assert source_report["mergeBase"]
    assert by_sha[source_patch]["presentInTarget"]
    assert by_sha[source_patch]["matchingTargetCommit"] == target_patch
    assert by_sha[unique]["presentInTarget"] is False
    assert by_sha[unique]["paths"] == ["provider.kt"]
    assert "candidate" in render_markdown(report)


def test_reports_equivalent_patches_across_sources_and_missing_base(tmp_path):
    target = init_repo(tmp_path / "target")
    base = commit(target, "base.txt", "base\n", "base")

    first = branch_from(tmp_path / "first", target, base, "first")
    shared_first = commit(first, "feature.kt", "same patch\n", "first equivalent patch")

    second = init_repo(tmp_path / "second")
    commit(second, "unrelated.txt", "different root\n", "unrelated history")
    shared_second = commit(second, "feature.kt", "same patch\n", "second equivalent patch")

    report = analyze(target, [("first", first, None), ("second", second, None), ("offline-fork", None, "clone failed")], 20)

    assert report["sources"][0]["mergeBase"]
    assert report["sources"][1]["mergeBase"] is None
    assert report["sources"][1]["comparison"] == "patch-id-only-no-common-base"
    assert report["sources"][2]["error"] == "clone failed"
    assert any({item["sha"] for item in group["candidates"]} == {shared_first, shared_second}
               for group in report["duplicatePatches"])
