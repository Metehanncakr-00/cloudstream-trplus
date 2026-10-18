import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://animeler.pw/steins-gate/bolum-1'
}
embed_url = 'https://animeler.pw/embed/347969/d1fa9f36dde6da0f?skip_ui=0&next=1'
req = urllib.request.Request(embed_url, headers=headers)
html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
print('Embed HTML len:', len(html))
iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html)
print('Embed iframes:', iframes)
u_m = re.findall(r'var\s+u\s*=\s*["\']([^"\']+)["\']', html)
print('u redirects:', u_m)
loc_m = re.findall(r'location\.(?:replace|href)\s*=\s*["\']([^"\']+)["\']', html)
print('loc redirects:', loc_m)
m3u8 = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', html)
print('m3u8 in embed:', m3u8)
if not (iframes or u_m or loc_m or m3u8):
    print('Embed snippet:\n', html[:1000])
