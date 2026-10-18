import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.cizgifilmizle.info/'
}

url = 'https://www.cizgifilmizle.info/'
try:
    surl = 'https://video.sibnet.ru/shell.php?videoid=5036611'
    sreq = urllib.request.Request(surl, headers={'User-Agent': headers['User-Agent'], 'Referer': 'https://www.cizgifilmizle.info/'})
    shtml = urllib.request.urlopen(sreq, timeout=8).read().decode('utf-8')
    print('Sibnet HTML len:', len(shtml))
    # Sibnet uses player.src([{src: "/v/...", type: "video/mp4"}])
    m = re.search(r'player\.src\(\[\{src:\s*["\']([^"\']+)["\']', shtml)
    if m:
        mp4_path = m.group(1)
        full_mp4 = 'https://video.sibnet.ru' + mp4_path
        print('Extracted Sibnet MP4 URL:', full_mp4)
        mreq = urllib.request.Request(full_mp4, headers={'User-Agent': headers['User-Agent'], 'Referer': surl})
        mresp = urllib.request.urlopen(mreq, timeout=8)
        print('Sibnet MP4 stream HTTP status:', mresp.status, 'Content-Type:', mresp.headers.get('Content-Type'))












except Exception as e:
    import traceback
    traceback.print_exc()


