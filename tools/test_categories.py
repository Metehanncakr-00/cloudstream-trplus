import urllib.request
import re

headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

# 1. Test FilmMakinesi
try:
    url = 'https://filmmakinesi.to/tur/animasyon-fmasd6/film/'
    req = urllib.request.Request(url, headers=headers)
    html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
    cards = re.findall(r'<a[^>]+href=["\']([^"\']+)["\'][^>]*class=["\'][^"\']*item[^"\']*["\']', html)
    print('FM Animasyon len:', len(html), 'cards:', len(cards))
except Exception as e:
    print('FM Animasyon error:', e)

# 2. Test HDFilmCehennemi
try:
    url = 'https://www.hdfilmcehennemi.land/tur/animasyon-izle/'
    req = urllib.request.Request(url, headers=headers)
    html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
    cards = re.findall(r'<a[^>]+href=["\']([^"\']+)["\']', html)
    print('HDC Animasyon len:', len(html), 'links:', len(cards))
except Exception as e:
    print('HDC Animasyon error:', e)
