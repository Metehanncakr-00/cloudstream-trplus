import urllib.request
import re

url = 'https://play.animeler.pw/fireplayer/player/assets/scripts.php'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://play.animeler.pw/fireplayer/video/b20fa060328b0cdf51b464ee37efe182'
}
req = urllib.request.Request(url, headers=headers)
js = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
arr_m = re.search(r'var\s+_0x9315\s*=\s*\[(.*?)\];', js)
if arr_m:
    # Split items by comma respecting quotes
    raw = arr_m.group(1)
    items = [x.strip().strip('"').strip("'") for x in raw.split(',')]
    print('Total items in _0x9315:', len(items))
    print('Item 36 (type):', items[36])
    print('Item 37 (url):', items[37])
    print('Item 38 (referrer):', items[38])
    print('Item 154 (ajax):', items[154])
