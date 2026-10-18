import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://www.fullhdfilmizlesene.now/'
}
url = 'https://rapidvid.org/vod/v1x264296bd'
req = urllib.request.Request(url, headers=headers)
body = urllib.request.urlopen(req, timeout=10).read().decode('utf-8')

m = re.search(r'.{0,50}av\s*\(\s*["\'][^"\']+["\']\s*\).{0,50}', body)
if m:
    print('Context around av():\n', m.group(0))

p8 = 'window._p8' in body
print('Has window._p8:', p8)
av_strict = re.search(r'"?file"?\s*:\s*av\(["\']([^"\']+)["\']\)', body)
print('Matches strict avPattern ("file": av(...)):', bool(av_strict))
av_flexible = re.search(r'av\s*\(\s*["\']([^"\']+)["\']\s*\)', body)
print('Matches flexible avPattern:', bool(av_flexible))
