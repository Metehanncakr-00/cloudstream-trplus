import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://yesilcamtv.com.tr/'
}

url = "https://yesilcamtv.com.tr/"
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, timeout=10).read().decode('utf-8')
movies = re.findall(r'<a[^>]+href="(https://yesilcamtv\.com\.tr/[^"/]+/)"', html)
print("Found movies:", len(set(movies)))
unique_movies = list(dict.fromkeys(movies))
for murl in unique_movies[:5]:
    mhtml = urllib.request.urlopen(urllib.request.Request(murl, headers=headers), timeout=10).read().decode('utf-8')
    iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', mhtml)
    video_src = re.findall(r'<video[^>]+src=["\']([^"\']+)["\']', mhtml)
    print(f"Movie: {murl}\n  iframes: {iframes}\n  video_src: {video_src}")
