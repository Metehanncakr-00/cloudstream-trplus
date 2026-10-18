import urllib.request
import re

url = "https://vidmoly.net/embed-sfez3zg0tgx6.html"
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://dizigecesi.com/'
}
try:
    req = urllib.request.Request(url, headers=headers)
    html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
    print('Vidmoly HTML len:', len(html))
    m3u8 = re.findall(r'https?://[^"\'\s<>]+\.m3u8[^"\'\s<>]*', html)
    print('M3U8 URLs found:', m3u8)
    if m3u8:
        murl = m3u8[0]
        mreq = urllib.request.Request(murl, headers={'User-Agent': headers['User-Agent'], 'Referer': 'https://vidmoly.net/'})
        with urllib.request.urlopen(mreq, timeout=8) as mres:
            print('M3U8 status:', mres.status)
            mbody = mres.read().decode('utf-8')
            print('M3U8 preview:\n', mbody[:300])
except Exception as e:
    import traceback
    traceback.print_exc()
