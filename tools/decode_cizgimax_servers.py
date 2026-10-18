from inspect_cizgimax_player import html
import urllib.request
import re
import base64
import json

b64_matches = re.findall(r'JSON\.parse\s*\(\s*atob\s*\(\s*["\']([^"\']+)["\']\s*\)\s*\)', html)
if not b64_matches:
    b64_matches = re.findall(r'atob\s*\(\s*["\']([^"\']+)["\']\s*\)', html)
print('Base64 server arrays found:', len(b64_matches))
for i, b64 in enumerate(b64_matches):
    try:
        raw_b64 = b64.replace(r'\/', '/').replace(r'\?', '?')
        pad = (4 - len(raw_b64) % 4) % 4
        decoded = base64.b64decode(raw_b64 + '='*pad).decode('utf-8')
        print(f'\n--- Array {i} ---')
        data = json.loads(decoded)
        print(json.dumps(data, indent=2))
        if isinstance(data, list):
            for s in data:
                src = s.get('src')
                if src:
                    print('\nTesting server src:', src)
                    surl = 'https://cizgimax.online' + src
                    sreq = urllib.request.Request(surl, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 'Referer': 'https://cizgimax.online/101-dalmacyali-sokagi-1-sezon-1-bolum-izle/'})
                    try:
                        with urllib.request.urlopen(sreq, timeout=8) as sres:
                            print('Status:', sres.status, sres.geturl())
                            shtml = sres.read().decode('utf-8')
                            print('HTML len:', len(shtml))
                            siframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', shtml)
                            sm3u8 = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', shtml)
                            smp4 = re.findall(r'https?://[^\s"\'<>]+\.mp4[^\s"\'<>]*', shtml)
                            print('Iframes in /oynat/:', siframes)
                            print('M3U8 in /oynat/:', sm3u8)
                            print('MP4 in /oynat/:', smp4)
                            if not (siframes or sm3u8 or smp4):
                                print('Snippet of /oynat/:\n', shtml[:600])
                    except Exception as e:
                        print('Server err:', e)
    except Exception as e:
        print(f'Error on array {i}:', e)
