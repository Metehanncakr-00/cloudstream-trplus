import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile

ROOT = Path(".").resolve()

EXCLUDED_DIRS = {
    ".git", ".gradle", ".kotlin", "build", "__pycache__",
    ".pytest_cache", ".mypy_cache", ".ruff_cache", "cache",
    "caches", "backups", "backup", ".codex", ".agents", ".ai-workflow",
    "cloudstream-trplus-final", "cloudstream-trplus-final-v40",
    "cloudstream-trplus-final-v41", "cloudstream-trplus-final-v42"
}

def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def format_size_tr(size: int) -> str:
    return f"{size:,}".replace(",", ".")

def update_distribution(target_dir_name: str):
    target_dir = ROOT / target_dir_name
    builds_dir = target_dir / "builds"
    main_dir = target_dir / "main"
    
    if builds_dir.exists():
        shutil.rmtree(builds_dir)
    if main_dir.exists():
        shutil.rmtree(main_dir)
        
    builds_dir.mkdir(parents=True, exist_ok=True)
    main_dir.mkdir(parents=True, exist_ok=True)
    
    # 1. Copy all fresh cs3 files
    cs3_files = list(ROOT.glob("*/build/*.cs3"))
    print(f"Found {len(cs3_files)} fresh cs3 files.")
    for cs3 in cs3_files:
        shutil.copy2(cs3, builds_dir / cs3.name)
        
    # 2. Copy plugins.json and repo.json
    shutil.copy2(ROOT / "build/plugins.json", builds_dir / "plugins.json")
    shutil.copy2(ROOT / "repo.json", builds_dir / "repo.json")
    
    # 3. Create builds/SHA256SUMS
    build_files = sorted([f for f in builds_dir.iterdir() if f.is_file() and f.name != "SHA256SUMS"])
    sums_lines = []
    for f in build_files:
        sums_lines.append(f"{sha256_file(f)}  {f.name}")
    (builds_dir / "SHA256SUMS").write_text("\n".join(sums_lines) + "\n", encoding="utf-8")
    
    def should_ignore_for_distribution(p, names):
        ignored = set()
        for n in names:
            if n in ("build", ".gradle", ".kotlin", "__pycache__", ".git"):
                ignored.add(n)
            elif n.endswith(".log"):
                ignored.add(n)
            elif n.endswith(".zip"):
                ignored.add(n)
        return ignored

    # 4. Copy main repository sources
    for item in ROOT.iterdir():
        if item.name in EXCLUDED_DIRS or item.name.startswith("cloudstream-trplus-") or item.name.endswith(".zip") or item.name.endswith(".log"):
            continue
        dest = main_dir / item.name
        if item.is_dir():
            shutil.copytree(
                item, dest,
                ignore=should_ignore_for_distribution
            )
        elif item.is_file():
            shutil.copy2(item, dest)

    # Inside main_dir (which will be zipped into main.zip), avoid self-referential hash paradox
    for doc_name in ("README-v42.md", "TEST-REPORT-v42.md"):
        inner_doc = main_dir / doc_name
        if inner_doc.exists():
            txt = inner_doc.read_text(encoding="utf-8")
            import re
            txt = re.sub(
                r"\| `cloudstream-trplus-v42-main\.zip` \| .* \|",
                "| `cloudstream-trplus-v42-main.zip` | Öz-referanslı arşiv (bu belgeyi içerir) | `Dış sağlama toplamı: kök README-v42.md ve SHA256SUMS` |",
                txt
            )
            inner_doc.write_text(txt, encoding="utf-8")
            
    # 5. Create zip archives (reproducible deterministic build)
    builds_zip = target_dir / "cloudstream-trplus-builds.zip"
    main_zip = target_dir / "cloudstream-trplus-main.zip"
    fixed_time = (2026, 10, 18, 12, 0, 0)
    
    with zipfile.ZipFile(builds_zip, "w", zipfile.ZIP_DEFLATED) as zf:
        for f in sorted(builds_dir.iterdir()):
            if f.is_file():
                zinfo = zipfile.ZipInfo(f.name, date_time=fixed_time)
                zinfo.compress_type = zipfile.ZIP_DEFLATED
                zf.writestr(zinfo, f.read_bytes())
                
    with zipfile.ZipFile(main_zip, "w", zipfile.ZIP_DEFLATED) as zf:
        for root_path, dirs, files in os.walk(main_dir):
            dirs.sort()
            for file_name in sorted(files):
                full_path = Path(root_path) / file_name
                rel_path = str(full_path.relative_to(main_dir)).replace("\\", "/")
                zinfo = zipfile.ZipInfo(rel_path, date_time=fixed_time)
                zinfo.compress_type = zipfile.ZIP_DEFLATED
                zf.writestr(zinfo, full_path.read_bytes())
                
    # 6. Copy zip files to root for v42 release
    if "v42" in target_dir_name:
        shutil.copy2(builds_zip, ROOT / "cloudstream-trplus-v42-builds.zip")
        shutil.copy2(main_zip, ROOT / "cloudstream-trplus-v42-main.zip")
        
    # 7. Generate root SHA256SUMS for target directory
    all_files = []
    for r, dirs, files in os.walk(target_dir):
        dirs.sort()
        for f in sorted(files):
            if f == "SHA256SUMS" and r == str(target_dir):
                continue
            p = Path(r) / f
            all_files.append(p)
            
    sums_lines = []
    for p in sorted(all_files):
        rel = p.relative_to(target_dir).as_posix()
        sums_lines.append(f"{sha256_file(p)}  {rel}")
    (target_dir / "SHA256SUMS").write_text("\n".join(sums_lines) + "\n", encoding="utf-8")
    print(f"Updated {target_dir_name} successfully.")

if __name__ == "__main__":
    update_distribution("cloudstream-trplus-final-v42")
    update_distribution("cloudstream-trplus-final")

    # 8. Now write the exact finalized size and SHA-256 of cloudstream-trplus-v42-main.zip into root README-v42.md and TEST-REPORT-v42.md
    import re
    main_zip_root = ROOT / "cloudstream-trplus-v42-main.zip"
    main_size_str = format_size_tr(main_zip_root.stat().st_size)
    main_sha_str = sha256_file(main_zip_root)
    for doc_name in ("README-v42.md", "TEST-REPORT-v42.md"):
        doc_path = ROOT / doc_name
        if doc_path.exists():
            txt = doc_path.read_text(encoding="utf-8")
            txt = re.sub(
                r"\| `cloudstream-trplus-v42-main\.zip` \| .* \|",
                f"| `cloudstream-trplus-v42-main.zip` | {main_size_str} | `{main_sha_str}` |",
                txt
            )
            doc_path.write_text(txt, encoding="utf-8")
    print(f"Synchronized root docs with exact main.zip: {main_size_str} bytes / {main_sha_str}")

