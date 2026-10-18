import urllib.request
import re
import base64

def decrypt_av(token):
    # let t=atob(e.split("").reverse().join(""))
    rev = token[::-1]
    # pad base64
    pad = (4 - len(rev) % 4) % 4
    t = base64.b64decode(rev + '=' * pad).decode('latin1')
    o = []
    key = "K9L"
    for i, c in enumerate(t):
        r = key[i % 3]
        shift = (ord(r) % 5) + 1
        n = ord(c) - shift
        o.append(chr(n))
    o_str = "".join(o)
    pad2 = (4 - len(o_str) % 4) % 4
    return base64.b64decode(o_str + '=' * pad2).decode('utf-8')

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.fullhdfilmizlesene.now/'
}

url = "https://rapidvid.org/vod/v1x264296bd"
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req, timeout=10) as res:
    body = res.read().decode('utf-8')
    av_token = re.search(r'av\s*\(\s*[\'"]([^\'"]+)[\'"]\s*\)', body).group(1)
    print("Found av token:", av_token[:40])
    decrypted_url = decrypt_av(av_token)
    print("Decrypted URL:", decrypted_url)
    
    # Let's test HEAD or GET on decrypted URL
    mreq = urllib.request.Request(decrypted_url, headers={
        'User-Agent': headers['User-Agent'],
        'Referer': 'https://rapidvid.org/'
    })
    with urllib.request.urlopen(mreq, timeout=10) as mres:
        print("M3U8 status:", mres.status)
        first_chunk = mres.read(500).decode('utf-8', errors='ignore')
        print("M3U8 content:\n", first_chunk)
