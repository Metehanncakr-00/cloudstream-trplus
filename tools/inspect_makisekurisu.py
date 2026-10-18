import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://animeler.pw/'
}
url = 'https://makisekurisu.site/embed/art/myzyy5kz5pfm?next=1&skip_ui=0'
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
print('Makisekurisu HTML len:', len(html))
cfg = re.findall(r'data-config=["\']([^"\']+)["\']', html)
config_url = cfg[0]
print('Fetching config URL:', config_url)
creq = urllib.request.Request(config_url, headers={'User-Agent': headers['User-Agent'], 'Referer': url})
cresp = urllib.request.urlopen(creq, timeout=8)
cjson = cresp.read().decode('utf-8')
import json
data = json.loads(cjson)

master = data.get('master', '')
for dom in data.get('server', {}).get('domains', []):
    test_master = master.replace('https://steinsga.site', dom)

    print('Testing domain master:', test_master)
    try:
        req = urllib.request.Request(test_master, headers={'User-Agent': headers['User-Agent'], 'Referer': url})
        resp = urllib.request.urlopen(req, timeout=8)
        print('Status:', resp.status)
    except Exception as e:
        print('Error:', e)







