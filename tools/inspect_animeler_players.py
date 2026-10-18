import urllib.request
import re

url = 'https://animeler.pw/steins-gate/bolum-1'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Referer': 'https://animeler.pw/'}
html = urllib.request.urlopen(urllib.request.Request(url, headers=headers)).read().decode('utf-8')

# Search for buttons, selects, or data-attributes for players/fansubs
fansubs = re.findall(r'<button[^>]+fansub[^>]*>.*?</button>', html, re.DOTALL)
print('Fansubs buttons:', fansubs[:5])
player_btns = re.findall(r'<button[^>]+player[^>]*>.*?</button>', html, re.DOTALL)
print('Player buttons:', player_btns[:5])

data_attrs = re.findall(r'data-[a-z0-9_-]+=["\'][^"\']+["\']', html)
print('Data attributes sample:', [d for d in data_attrs if any(k in d for k in ['embed', 'player', 'video', 'src', 'url', 'hash', 'source'])][:10])

# Look for embedded json or state
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
for s in scripts:
    if 'player' in s.lower() or 'embed' in s.lower():
        print('Script sample:\n', s[:500])
