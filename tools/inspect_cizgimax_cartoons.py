import urllib.request
import re

url = 'https://cizgimax.online/diziler/cizgi-film/'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
html = urllib.request.urlopen(urllib.request.Request(url, headers=headers)).read().decode('utf-8')
print('HTML len:', len(html))

# Extract cartoon cards/links
cards = re.findall(r'<a[^>]+href=["\']([^"\']+)["\'][^>]*class=["\'][^"\']*card[^"\']*["\'][^>]*>', html)
if not cards:
    # Look for any links inside articles or grid
    all_links = re.findall(r'<a[^>]+href=["\'](/[^"\'?#]+/)["\'][^>]*>(.*?)</a>', html, re.DOTALL)
    content = [l for l in all_links if not any(x in l[0] for x in ['/tur/', '/sayfa/', '/diziler/', '/film/', '/arsiv/', '/giris/', '/uygulamalar/', '/sosyal', '/takvim/', '/haberler/', '/premium/'])]
    print('Found cartoons count:', len(content))
    for href, text in content[:15]:
        title = re.sub(r'<[^>]+>', '', text).strip()
        print(f'{href} -> {title}')
