import urllib.request
import json

slug = "cfdn77seb3zi"
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': f'https://bysejikuar.com/e/{slug}',
    'Accept': 'application/json'
}

for path in [f"/api/{slug}", f"/api/e/{slug}", f"/api/video/{slug}", f"/api/player/{slug}", f"/api/embed/{slug}"]:
    url = f"https://bysejikuar.com{path}"
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as res:
            print(path, "->", res.status, res.read().decode('utf-8')[:200])
    except Exception as e:
        print(path, "->", e)
