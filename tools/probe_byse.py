import urllib.request
import re

req = urllib.request.Request("https://bysejikuar.com/assets/index-DocunfmE.js", headers={"User-Agent": "Mozilla/5.0"})
content = urllib.request.urlopen(req).read().decode("utf-8")

for kw in ['videoUrl', 'streamUrl', 'master.m3u8', 'source', 'hls']:
    matches = [m.start() for m in re.finditer(re.escape(kw), content, re.IGNORECASE)]
    print(f"Keyword '{kw}' matches:", len(matches))
    for idx in matches[:2]:
        print("   ", content[max(0, idx - 40):min(len(content), idx + 80)])
