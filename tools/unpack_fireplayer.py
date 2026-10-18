import urllib.request
import re

url = 'https://play.animeler.pw/fireplayer/video/b20fa060328b0cdf51b464ee37efe182'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://animeler.pw/'
}
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
packed = re.search(r'eval\(function\(p,a,c,k,e,d\).+?return p\}\((.+?)\)\)', html, re.DOTALL)
if packed:
    args = packed.group(1)
    # Parse p, a, c, k
    # Let's extract string p and k array
    p_match = re.search(r'\'(.+?)\'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*\'(.+?)\'\.split\(\'\|\'\)', args, re.DOTALL)
    if p_match:
        p, a, c, k = p_match.group(1), int(p_match.group(2)), int(p_match.group(3)), p_match.group(4).split('|')
        print(f'Unpacking: a={a}, c={c}, k len={len(k)}')
        def baseN(num, b):
            return ((num == 0) and "0") or (baseN(num // b, b).lstrip("0") + "0123456789abcdefghijklmnopqrstuvwxyz"[num % b])
        
        def e(c):
            return ("" if c < a else e(c // a)) + (chr(c % a + 29) if (c % a) > 35 else baseN(c % a, 36))
            
        d = {}
        for idx in range(c - 1, -1, -1):
            key = e(idx)
            val = k[idx] if idx < len(k) and k[idx] else key
            d[key] = val
        
        words = re.findall(r'\b\w+\b', p)
        res = re.sub(r'\b\w+\b', lambda m: d.get(m.group(0), m.group(0)), p)
        print('Unpacked length:', len(res))
        print('Unpacked sample:\n', res[:1000])
        urls = re.findall(r'https?://[^\s"\'<>,]+', res)
        print('URLs found in unpacked:\n', urls)
else:
    print('Packed script not found')
