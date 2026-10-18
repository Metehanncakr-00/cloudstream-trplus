from probe_fhf import decrypt_av
import urllib.request
import re
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://www.fullhdfilmizlesene.now/'
}
url = 'https://rapidvid.org/vx/v1x348972af'
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, timeout=10).read().decode('utf-8')
p8_token = re.search(r"window\._p8\s*=\s*['\"]([^'\"]+)['\"]", html).group(1)
dec = decrypt_av(p8_token)
data = json.loads(dec)
print('Keys in _p8:', list(data.keys()))
print('Testing cm stream...')
cm_url = data['cm']
creq = urllib.request.Request(cm_url, headers={'User-Agent': headers['User-Agent'], 'Referer': 'https://rapidvid.org/'})
with urllib.request.urlopen(creq, timeout=10) as cres:
    print('CM status:', cres.status)
    cbody = cres.read().decode('utf-8')
    print('CM body preview:\n', cbody[:300])
