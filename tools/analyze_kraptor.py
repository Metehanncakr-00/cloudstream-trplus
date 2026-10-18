import json

with open("legacy/migration-matrix.json", encoding="utf-8") as f:
    matrix = json.load(f)

with open("config/providers.json", encoding="utf-8") as f:
    current_conf = json.load(f)

current_modules = {p["module"].lower() for p in current_conf["providers"]}

missing = []
for m in matrix:
    name = m["name"]
    mod = m.get("replacementModule") or name
    if mod.lower() not in current_modules and name.lower() not in current_modules:
        missing.append(m)

print(f"Total in matrix: {len(matrix)}")
print(f"Already in repo: {len(matrix) - len(missing)}")
print(f"Remaining missing providers: {len(missing)}")
print("=" * 60)

for m in missing:
    types_str = ",".join(m.get("tvTypes", []))
    print(f"- {m['name']:<18} | Types: {types_str:<22} | Status: {m.get('implementationStatus'):<12} | Domain: {m.get('currentDomain')}")
