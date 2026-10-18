import urllib.request
import re

url = 'https://cizgimax.online/101-dalmacyali-sokagi-1-sezon-1-bolum-izle/'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', 'Referer': 'https://cizgimax.online/'}
html = urllib.request.urlopen(urllib.request.Request(url, headers=headers)).read().decode('utf-8')
print('Episode HTML len:', len(html))

iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html)
print('Iframes:', iframes)
videos = re.findall(r'<video[^>]+src=["\']([^"\']+)["\']', html)
print('Videos:', videos)
sources = re.findall(r'<source[^>]+src=["\']([^"\']+)["\']', html)
print('Sources:', sources)

# Look for player containers, data-attributes, or player scripts
player_divs = re.findall(r'<div[^>]+(?:player|video|embed)[^>]*>', html)
print('Player divs sample:', player_divs[:5])

scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
for s in scripts:
    if any(k in s.lower() for k in ['player', 'iframe', 'source', 'm3u8', 'mp4', 'stream']):
        print('Script match:\n', s[:600])
        print('---')
