import urllib.request
import re
import json
import html
import ssl

ctx = ssl._create_unverified_context()
url = "https://odnoklassniki.ru/videoembed/6830329236216"
body = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'}), context=ctx).read().decode('utf-8')
opts = re.search(r'data-options=["\']([^"\']+)["\']', body)
j = json.loads(html.unescape(opts.group(1)))
meta = j['flashvars']['metadata']
print("meta keys:", meta.keys())
print("videos:", [v.get('name') for v in meta.get('videos', [])])
print("hlsManifestUrl:", meta.get('hlsManifestUrl'))
if meta.get('videos'):
    print("first video url:", meta.get('videos')[0].get('url')[:60])
