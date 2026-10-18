import urllib.request
import re
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://animeler.pw/'
}

episodes = [
    'https://animeler.pw/one-piece/bolum-1',
    'https://animeler.pw/code-geass-hangyaku-no-lelouch/bolum-1',
    'https://animeler.pw/naruto-shippuuden/bolum-1-2'
]

for ep in episodes:
    print('\nTesting episode:', ep)
    try:
        ehtml = urllib.request.urlopen(urllib.request.Request(ep, headers=headers), timeout=8).read().decode('utf-8')
        iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', ehtml)
        print('  Iframes:', iframes)
        for ifr in iframes:
            if not ifr.startswith('http'): ifr = 'https://animeler.pw' + ifr
            ifr = ifr.replace('&amp;', '&')
            print('  Checking embed:', ifr)
            ihtml = urllib.request.urlopen(urllib.request.Request(ifr, headers={'User-Agent': headers['User-Agent'], 'Referer': ep}), timeout=8).read().decode('utf-8')
            u_m = re.findall(r'var\s+u\s*=\s*["\']([^"\']+)["\']', ihtml)
            print('  u redirect:', u_m)
            if u_m:
                u_url = u_m[0].replace(r'\/', '/').replace(r'\u0026', '&')
                print('  Cleaned redirect:', u_url)
                mreq = urllib.request.Request(u_url, headers={'User-Agent': headers['User-Agent'], 'Referer': ifr})
                mhtml = urllib.request.urlopen(mreq, timeout=8).read().decode('utf-8')
                dconfig = re.search(r'data-config=["\']([^"\']+)["\']', mhtml)
                if dconfig:
                    print('  data-config:', dconfig.group(1))
                    creq = urllib.request.Request(dconfig.group(1), headers={'User-Agent': headers['User-Agent'], 'Referer': u_url})
                    cdata = json.loads(urllib.request.urlopen(creq, timeout=8).read().decode('utf-8'))
                    print('  playable:', cdata.get('playable'), 'master:', cdata.get('master'))
                    print('  qualities:', [(q.get('quality'), q.get('url')) for q in cdata.get('qualities', [])])
                    print('  domains:', cdata.get('server', {}).get('domains'))
                    # Test master or quality URL
                    test_url = cdata.get('master')
                if 'fireplayer' in u_url:
                    freq = urllib.request.Request(u_url, headers={'User-Agent': headers['User-Agent'], 'Referer': ifr})
                    fhtml = urllib.request.urlopen(freq, timeout=8).read().decode('utf-8')
                    print('    Fireplayer HTML len:', len(fhtml))
                    fiframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', fhtml)
                    fm3u8 = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', fhtml)
                    ffiles = re.findall(r'file\s*:\s*["\']([^"\']+)["\']', fhtml)
                    print('    Fireplayer iframes:', fiframes)
                    print('    Fireplayer m3u8:', fm3u8)
                    print('    Fireplayer files:', ffiles)
                    if not (fiframes or fm3u8 or ffiles):
                        print('    Fireplayer snippet:\n', fhtml[:600])
    except Exception as e:
        print('  Error:', e)
