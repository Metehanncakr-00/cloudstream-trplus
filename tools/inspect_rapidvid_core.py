import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://rapidvid.org/vx/v1x348972af'
}
url = 'https://rapidvid.org/ifr/vod/js/core.ff6099604eba272d.js'
req = urllib.request.Request(url, headers=headers)
js = urllib.request.urlopen(req, timeout=10).read().decode('utf-8')
print('Core JS len:', len(js))

# Search for _p8 or ajax calls or m3u8
p8_matches = re.findall(r'.{0,100}_p8.{0,100}', js)
for m in p8_matches[:5]:
    print('Match _p8:', m)

idx = js.find('M.sources')
print(js[idx-500:idx+300])
