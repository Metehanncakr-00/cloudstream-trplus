with open('player_dump.js', 'w', encoding='utf-8') as f:
    import urllib.request
    url = "https://fastplay.mom/assets/player/player.js"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    pj = urllib.request.urlopen(req).read().decode('utf-8')
    f.write(pj)

import re
with open('player_dump.js', 'r', encoding='utf-8') as f:
    pj = f.read()

# search for xhrSetup or hls config
print("--- HLS config in player.js ---")
for m in re.finditer(r'(?:xhrSetup|beforeContent|loadPolicy|p2pConfig|hlsjsConfig|Hls\.DefaultConfig).*?\}', pj, re.DOTALL):
    print(m.group(0)[:500])

for m in re.finditer(r'xhr\.setRequestHeader.*?;', pj):
    print("Header:", m.group(0))
