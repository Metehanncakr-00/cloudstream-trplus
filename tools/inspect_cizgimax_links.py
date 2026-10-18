import urllib.request
import re

url = 'https://cizgimax.online/'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'})
html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')

# Find all a hrefs
links = re.findall(r'<a[^>]+href=["\']([^"\']+)["\'][^>]*>(.*?)</a>', html, re.DOTALL)
print('Total links found:', len(links))
filtered = []
for href, text in links:
    clean_text = re.sub(r'<[^>]+>', '', text).strip()
    if href.startswith('/') or 'cizgimax.online' in href:
        if not any(x in href for x in ['#', 'javascript:', 'cdn-cgi', 'login', 'register']):
            filtered.append((href, clean_text[:40]))

print('Sample content links:\n', filtered[:25])
