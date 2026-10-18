import urllib.request
import re

url = 'https://animeler.pw/steins-gate/bolum-1'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Referer': 'https://animeler.pw/'}
html = urllib.request.urlopen(urllib.request.Request(url, headers=headers)).read().decode('utf-8')

elements = re.findall(r'<[^>]+data-source-id[^>]+>', html)
for el in elements:
    print(el)
