import urllib.request
import urllib.parse
import http.cookiejar
import re
import json

cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://animeler.pw/'
}

url = 'https://animeler.pw/steins-gate/bolum-1'
req = urllib.request.Request(url, headers=headers)
html = opener.open(req, timeout=8).read().decode('utf-8')

token_m = re.search(r"_token:\s*['\"]([^'\"]+)['\"]", html)
print('_token:', token_m.group(1) if token_m else None)

source_ids = re.findall(r'data-source-id=["\'](\d+)["\']', html)
unique_ids = list(dict.fromkeys(source_ids))
print('Source IDs:', unique_ids)

ajax_url = 'https://animeler.pw/ajax/get-source-url'
for sid in unique_ids[:5]:
    data = urllib.parse.urlencode({
        '_token': token_m.group(1),
        'source_id': sid
    }).encode('utf-8')
    areq = urllib.request.Request(ajax_url, data=data, headers={
        'User-Agent': headers['User-Agent'],
        'Referer': url,
        'X-Requested-With': 'XMLHttpRequest',
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
    })
    try:
        with opener.open(areq, timeout=5) as res:
            resp = json.loads(res.read().decode('utf-8'))
            print(f'Source {sid} -> {resp}')
    except Exception as e:
        print(f'Source {sid} failed -> {e}')
