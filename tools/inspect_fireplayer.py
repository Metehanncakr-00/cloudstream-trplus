import urllib.request
import re

url = 'https://play.animeler.pw/fireplayer/video/b20fa060328b0cdf51b464ee37efe182'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://animeler.pw/'
}
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
for i, s in enumerate(scripts):
    if len(s.strip()) > 50:
        print(f'Script {i}:\n', s[:500])
        print('---')
