import hashlib
import json
from pathlib import Path
import sys
import zipfile

final_dir = Path("cloudstream-trplus-final")
errors = []

# 1. Verify SHA256SUMS at root of final
lines = (final_dir / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
verified_count = 0
for line in lines:
    if not line.strip():
        continue
    digest, rel = line.split(None, 1)
    file_path = final_dir / rel
    if not file_path.is_file():
        errors.append(f"Missing file from SHA256SUMS: {rel}")
        continue
    actual = hashlib.sha256(file_path.read_bytes()).hexdigest()
    if actual != digest:
        errors.append(f"Checksum mismatch: {rel}")
    else:
        verified_count += 1

print(f"Root SHA256SUMS: {verified_count} files verified successfully.")

# 2. Verify ZIPs testzip
main_zip = final_dir / "cloudstream-trplus-main.zip"
builds_zip = final_dir / "cloudstream-trplus-builds.zip"

main_sha256 = hashlib.sha256(main_zip.read_bytes()).hexdigest()
builds_sha256 = hashlib.sha256(builds_zip.read_bytes()).hexdigest()

print(f"cloudstream-trplus-main.zip: {main_zip.stat().st_size} bytes, SHA-256: {main_sha256}")
print(f"cloudstream-trplus-builds.zip: {builds_zip.stat().st_size} bytes, SHA-256: {builds_sha256}")

for zpath in [main_zip, builds_zip]:
    with zipfile.ZipFile(zpath) as z:
        res = z.testzip()
        if res is not None:
            errors.append(f"Corrupt entry in {zpath.name}: {res}")
        print(f"  {zpath.name}: valid ZIP, {len(z.namelist())} entries")

# 3. Verify builds contents
builds_dir = final_dir / "builds"
cs3_files = list(builds_dir.glob("*.cs3"))
print(f"CS3 count in builds/: {len(cs3_files)}")
if len(cs3_files) != 26:
    errors.append(f"Expected 26 cs3 files, found {len(cs3_files)}")

plugins_json = json.loads((builds_dir / "plugins.json").read_text(encoding="utf-8"))
print(f"plugins.json entries: {len(plugins_json)}")
if len(plugins_json) != 26:
    errors.append(f"Expected 26 plugins in plugins.json, found {len(plugins_json)}")

for p in plugins_json:
    name = p["internalName"]
    cs3 = builds_dir / f"{name}.cs3"
    if not cs3.is_file():
        errors.append(f"Missing cs3 for {name}")
        continue
    data = cs3.read_bytes()
    expected_hash = "sha256-" + hashlib.sha256(data).hexdigest()
    if p["fileHash"] != expected_hash:
        errors.append(f"Hash mismatch for {name}: {p['fileHash']} vs {expected_hash}")
    if p["fileSize"] != len(data):
        errors.append(f"Size mismatch for {name}: {p['fileSize']} vs {len(data)}")

# 4. Check specific versions
plugins_by_name = {p["internalName"]: p for p in plugins_json}
expected_versions = {"CloudStreamHub": 42, "DiziYou": 22, "BlenderOpenMovies": 6, "DiziKorea": 22}
for name, exp_ver in expected_versions.items():
    actual_ver = plugins_by_name.get(name, {}).get("version")
    print(f"  {name} version: {actual_ver} (expected {exp_ver})")
    if actual_ver != exp_ver:
        errors.append(f"{name} version mismatch: {actual_ver} != {exp_ver}")

# 5. Check distribution status
status = json.loads((final_dir / "reports" / "distribution-status.json").read_text(encoding="utf-8"))
print(f"distribution-status.json: published={status.get('published')}, manualPlayback={status.get('manualPlayback')}")
if status.get("published") is not False:
    errors.append(f"published must be False, got {status.get('published')}")
if status.get("manualPlayback") != "NOT_TESTED":
    errors.append(f"manualPlayback must be NOT_TESTED, got {status.get('manualPlayback')}")

# 6. Check backups integrity
backups = [
    Path("backups/cloudstream-v36-original-20261016-111427.zip"),
    Path("backups/v36-baseline-built-sdk35.zip"),
    Path("backups/published-builds-20261016T175412Z/plugins.json"),
    Path("backups/cloudstream-trplus-final-v37/cloudstream-trplus-builds.zip")
]
for b in backups:
    exists = b.exists()
    print(f"Backup {b}: exists={exists}")
    if not exists:
        errors.append(f"Missing backup: {b}")

if errors:
    print("\nERRORS ENCOUNTERED:")
    for e in errors:
        print("  -", e)
    sys.exit(1)
else:
    print("\n>>> ALL 6 VERIFICATION CHECKS PASSED WITH 0 ERRORS! <<<")
