import urllib.request
import re

url = 'https://www.diziizle.you/player/?v=24754'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://www.diziizle.you/20-dakika/bolum-25/'})
html = urllib.request.urlopen(req).read().decode('utf-8')

# Search for file: or scripts
files = re.findall(r'file\s*:\s*["\']([^"\']+)["\']', html)
print("Files:", files)
yt = re.findall(r'YT_SRC\s*=\s*["\']([^"\']+)["\']', html)
print("YT_SRC:", yt)
vd = re.findall(r'VD_SRC\s*=\s*["\']([^"\']+)["\']', html)
print("VD_SRC:", vd)
iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html)
print("iframes:", iframes)

for line in html.split('\n'):
    if any(k in line for k in ['jwplayer', 'setup', 'sources', 'http']):
        if len(line.strip()) < 200:
            print("LINE:", line.strip().encode('ascii', errors='replace').decode('ascii'))
