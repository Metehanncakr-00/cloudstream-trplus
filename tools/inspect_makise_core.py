import urllib.request
import re

url = 'https://makisekurisu.site/player/core.js?v=1792274237-b2'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
js = urllib.request.urlopen(req, timeout=10).read().decode('utf-8')
print('core.js len:', len(js))
for m in re.finditer(r'(?:fetch|\.get|\.post|open)\s*\([\'"`]([^\'"`]+)[\'"`]', js, re.I):
    print('Endpoint:', m.group(1))

# Check for keywords
for kw in ['/api', '/source', '/video', '/play', '/stream', 'm3u8', 'master', 'hls', 'url']:
    matches = [m.start() for m in re.finditer(re.escape(kw), js, re.I)]
    print(f'Keyword "{kw}" occurrences: {len(matches)}')
    for idx in matches[:3]:
        snippet = js[max(0, idx - 40):min(len(js), idx + 60)].replace('\n', ' ')
        print(f'   [{kw}]: {snippet}')

