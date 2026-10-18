import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.cizgifilmizle.info/'
}

url = 'https://www.cizgifilmizle.info/kategori/paw-patrol'
html = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=8).read().decode('utf-8')
print('Paw patrol HTML len:', len(html))

# Look for episode links
ep_links = re.findall(r'<a[^>]+href=["\'](https://www\.cizgifilmizle\.info/[^"\'?#]+/[^"\'?#]+\.html)["\'][^>]*>(.*?)</a>', html, re.DOTALL)
print('Episodes found count:', len(ep_links))
unique_eps = list(dict.fromkeys(ep_links))
for href, text in unique_eps[:5]:
    clean = re.sub(r'<[^>]+>', '', text).strip()
    print(f'  {href} -> {clean}')

if unique_eps:
    ep_url = unique_eps[0][0]
    print('\nTesting episode page:', ep_url)
    ehtml = urllib.request.urlopen(urllib.request.Request(ep_url, headers=headers), timeout=8).read().decode('utf-8')
    iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', ehtml)
    print('Episode iframes:', iframes)
    videos = re.findall(r'<video[^>]+src=["\']([^"\']+)["\']', ehtml)
    print('Episode videos:', videos)
    scripts = re.findall(r'<script[^>]*>(.*?)</script>', ehtml, re.DOTALL)
    for s in scripts:
        if any(k in s.lower() for k in ['youtube', 'player', 'm3u8', 'mp4', 'dailymotion']):
            print('Script match:\n', s[:500])
            print('---')
