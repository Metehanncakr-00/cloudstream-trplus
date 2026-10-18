import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://cizgimax.online/'
}

# 1. Test search on cizgimax
for q in ['surekli-dizi', 'doraemon', 'esrarengiz-kasaba']:
    url = f'https://cizgimax.online/?s={q}'
    try:
        html = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=8).read().decode('utf-8')
        items = re.findall(r'href="([^"]+/(?:cizgi-dizi|anime|cizgi-film|dizi)/[^"]+)"', html)
        if not items:
            items = re.findall(r'<article[^>]*>.*?href="([^"]+)".*?</article>', html, re.DOTALL)
        print(f'Search {q} found:', list(dict.fromkeys(items))[:3])
    except Exception as e:
        print(f'Search {q} err:', e)

# 2. Test home page categories
try:
    hhtml = urllib.request.urlopen(urllib.request.Request('https://cizgimax.online/', headers=headers), timeout=8).read().decode('utf-8')
    cat_links = re.findall(r'href="([^"]+/(?:cizgi-dizi|animeler|cizgi-filmler)[^"]*)"', hhtml)
    print('Category links on home:', list(dict.fromkeys(cat_links)))
    
    # Check regular shows on cizgimax
    shows = re.findall(r'href="(https://cizgimax.online/[^"/]+-[^"/]+-[^"/]+/)"', hhtml)
    print('Sample show links on home:', list(dict.fromkeys(shows))[:5])
    all_links = re.findall(r'href="(https://cizgimax.online/[^"#]+)"', hhtml)
    content_links = [l for l in all_links if not any(x in l for x in ['/page/', '/category/', '/tag/', '/wp-', '.css', '.js', '.png', '.jpg'])]
    print('Content links on cizgimax:', list(dict.fromkeys(content_links))[:6])
except Exception as e:
    import traceback
    traceback.print_exc()
