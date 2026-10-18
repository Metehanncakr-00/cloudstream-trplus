import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://www.fullhdfilmizlesene.now/'
}
url = 'https://rapidvid.org/vx/v1x348972af'
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, timeout=10).read().decode('utf-8')

# Search for script tags or references to _p8
p8_refs = re.findall(r'.{0,100}_p8.{0,100}', html)
for ref in p8_refs:
    print('Ref:', ref)

scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
print('Inline scripts count:', len(scripts))
for i, s in enumerate(scripts):
    if '_p8' in s or 'player' in s.lower() or 'source' in s.lower():
        print(f'\n--- Script {i} ---')
        print(s[:1000])

external_scripts = re.findall(r'<script[^>]+src=["\']([^"\']+)["\']', html)
print('External scripts:', external_scripts)
