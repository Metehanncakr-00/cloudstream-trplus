import urllib.request
import re
import random
import time

url = "https://fastplay.mom/video/7Bxd8b1eYRN8"
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://setplay.shop/'
}
html = urllib.request.urlopen(urllib.request.Request(url, headers=headers)).read().decode('utf-8')
stream_match = re.search(r'stream\s*:\s*["\']([^"\']+)["\']', html)
manifest_url = "https://fastplay.mom" + stream_match.group(1)

sp = re.search(r'"sp"\s*:\s*"([^"]+)"', html).group(1)
spT = int(re.search(r'"spT"\s*:\s*(\d+)', html).group(1))

def gen_x_sp():
    n = spT # or current timestamp
    # r = Math.floor(2176782336*Math.random()).toString(36)
    r_val = int(random.random() * 2176782336)
    chars = '0123456789abcdefghijklmnopqrstuvwxyz'
    r = ''
    while r_val:
        r = chars[r_val % 36] + r
        r_val //= 36
    r = r or '0'
    
    # hash(o + "|" + n + "|" + r)
    s = f"{sp}|{n}|{r}"
    t = 2166136261
    for b in s.encode('utf-8'):
        t ^= b
        t = (t * 16777619) & 0xffffffff
    hex_hash = f"{t:x}"
    return f"{n}.{r}.{hex_hash}"

mheaders = {
    'User-Agent': headers['User-Agent'],
    'Referer': url,
    'Origin': 'https://fastplay.mom',
    'X-Sp': gen_x_sp()
}
print("Master X-Sp:", mheaders['X-Sp'])

mreq = urllib.request.Request(manifest_url, headers=mheaders)
with urllib.request.urlopen(mreq) as mres:
    print("Master Content-Type:", mres.headers.get('Content-Type'))
    master = mres.read().decode('utf-8')
print("Master loaded successfully!")

variants = [l.strip() for l in master.split('\n') if l.strip() and not l.startswith('#')]
print("Variant url:", variants[0])

vheaders = {
    'User-Agent': headers['User-Agent'],
    'Referer': url,
    'Origin': 'https://fastplay.mom',
    'X-Sp': gen_x_sp()
}
print("Variant X-Sp:", vheaders['X-Sp'])
vreq = urllib.request.Request(variants[0], headers=vheaders)
try:
    with urllib.request.urlopen(vreq) as vres:
        print("Variant status:", vres.status)
        vcontent = vres.read().decode('utf-8')
        print("Variant content preview:\n", vcontent[:300])
        
        # Test segment
        segs = [l.strip() for l in vcontent.split('\n') if l.strip() and not l.startswith('#')]
        print("Segment url:", segs[0])
        sheaders = {
            'User-Agent': headers['User-Agent'],
            'Referer': url,
            'Origin': 'https://fastplay.mom',
            'X-Sp': gen_x_sp()
        }
        sreq = urllib.request.Request(segs[0], headers=sheaders)
        with urllib.request.urlopen(sreq) as sres:
            print("Segment status:", sres.status, "len:", len(sres.read(188)))
except Exception as e:
    print("Variant error:", e)
