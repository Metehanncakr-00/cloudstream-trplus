import urllib.request
import re

url = 'https://cizgimax.online/diziler/101-dalmatian-street-izle/'
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
html = urllib.request.urlopen(urllib.request.Request(url, headers=headers)).read().decode('utf-8')
print('Detail HTML len:', len(html))

# Look for episodes or seasons
ep_links = re.findall(r'<a[^>]+href=["\'](/diziler/[^"\'?#]+-bolum-[^"\'?#]*)["\'][^>]*>(.*?)</a>', html, re.DOTALL)
if not ep_links:
    ep_links = re.findall(r'<a[^>]+href=["\'](/diziler/[^"\'?#]+/)["\'][^>]*>(.*?)</a>', html, re.DOTALL)
print('Episode links found:', len(ep_links))
for href, text in ep_links[:10]:
    clean = re.sub(r'<[^>]+>', '', text).strip()
    print(f'  {href} -> {clean}')

# If no episode links, let's look for episode items or scripts
if len(ep_links) <= 2:
    divs = re.findall(r'<[^>]+(?:episode|bolum|sezon)[^>]*>', html)
    print('Episode tags:', divs[:10])
