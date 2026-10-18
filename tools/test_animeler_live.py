import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://animeler.pw/'
}

for d in ['https://animeler.pw', 'https://animeler.me', 'https://www.animeler.pw']:
    try:
        req = urllib.request.Request(d, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as res:
            print(d, '->', res.status, res.geturl())
    except Exception as e:
        print(d, '->', e)

# Test search
try:
    surl = 'https://animeler.pw/filter?search=naruto'
    req = urllib.request.Request(surl, headers=headers)
    html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
    animes = re.findall(r'href="([^"]+/(?:anime/|detail/|watch/)[^"]+)"', html)
    all_links = re.findall(r'<a[^>]+href="([^"]+)"[^>]*>', html)
    print('All links sample on search:', [l for l in all_links if 'filter' not in l and '#' not in l][:10])
    
    # Try home page
    hreq = urllib.request.Request('https://animeler.pw', headers=headers)
    hhtml = urllib.request.urlopen(hreq, timeout=8).read().decode('utf-8')
    home_episodes = re.findall(r'href="([^"]+bolum[^"]*)"', hhtml)
    print('Home episodes:', home_episodes[:5])
    if home_episodes:
        ep_url = home_episodes[0]
        if not ep_url.startswith('http'):
            ep_url = 'https://animeler.pw' + ('' if ep_url.startswith('/') else '/') + ep_url
        print('\nTesting episode page:', ep_url)
        ehtml = urllib.request.urlopen(urllib.request.Request(ep_url, headers=headers), timeout=8).read().decode('utf-8')
        iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', ehtml)
        print('Episode iframes:', iframes)
        players = re.findall(r'.{0,40}(?:fansub|player|embed|video).{0,40}', ehtml)
        print('Player references sample:', players[:5])
except Exception as e:
    import traceback
    traceback.print_exc()
