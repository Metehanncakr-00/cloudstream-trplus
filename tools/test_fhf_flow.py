import urllib.request
import re

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.fullhdfilmizlesene.now/'
}

for d in ['https://www.fullhdfilmizlesene.now', 'https://fullhdfilmizlesene.now', 'https://www.fullhdfilmizlesene.pw']:
    try:
        req = urllib.request.Request(d, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as res:
            print(d, '->', res.status, res.geturl())
    except Exception as e:
        print(d, '->', e)

# Let's test home page and movies
try:
    hreq = urllib.request.Request('https://www.fullhdfilmizlesene.now/', headers=headers)
    hhtml = urllib.request.urlopen(hreq, timeout=8).read().decode('utf-8')
    movies = re.findall(r'href="([^"]+/film/[^"]+)"', hhtml)
    print('Found movies:', list(dict.fromkeys(movies))[:5])
    for murl in list(dict.fromkeys(movies))[:3]:
        print('\nChecking movie:', murl)
        mreq = urllib.request.Request(murl, headers=headers)
        mhtml = urllib.request.urlopen(mreq, timeout=8).read().decode('utf-8')
        
        # Check direct iframes
        iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', mhtml)
        print('  Direct iframes:', iframes)
        
        # Check scx
        scx_m = re.search(r'var\s+scx\s*=\s*(\{.+?\});', mhtml, re.DOTALL)
        print('  Has var scx:', bool(scx_m))
        if scx_m:
            raw_json = scx_m.group(1)
            tokens = re.findall(r'"([a-zA-Z0-9+/=_-]{10,})"', raw_json)
            print('  scx tokens count:', len(tokens))
            import base64
            for tok in tokens[:5]:
                # rot13
                rtt = "".join([chr(ord(c)+13 if 'a'<=c<='m' or 'A'<=c<='M' else ord(c)-13 if 'n'<=c<='z' or 'N'<=c<='Z' else ord(c)) for c in tok])
                pad = (4 - len(rtt) % 4) % 4
                try:
                    dec = base64.b64decode(rtt + '='*pad).decode('utf-8')
                    print('    Token decoded:', dec)
                    vreq = urllib.request.Request(dec, headers={'User-Agent': headers['User-Agent'], 'Referer': murl})
                    with urllib.request.urlopen(vreq, timeout=8) as vres:
                        print('    Rapidvid status:', vres.status, vres.geturl())
                        vhtml = vres.read().decode('utf-8')
                        print('    Rapidvid HTML len:', len(vhtml))
                        av_m = re.search(r'av\s*\(\s*["\']([^"\']+)["\']\s*\)', vhtml)
                        p8_m = re.search(r'window\._p8\s*=\s*["\']([^"\']+)["\']', vhtml)
                        print('    Has av:', bool(av_m), 'Has p8:', bool(p8_m))
                        if p8_m:
                            from probe_fhf import decrypt_av
                            p8_token = p8_m.group(1)
                            dec_p8 = decrypt_av(p8_token)
                            print('    Decrypted p8 JSON:\n', dec_p8[:300])
                        elif av_m:
                            from probe_fhf import decrypt_av
                            dec_url = decrypt_av(av_m.group(1))
                            print('    Decrypted stream URL:', dec_url[:60])
                except Exception as e:
                    print('    Decode err:', e)
        else:
            # Check what other player tags or scripts exist
            player_snippets = re.findall(r'.{0,40}(?:rapidvid|player|video|source|embed).{0,40}', mhtml)
            print('  Player snippets sample:\n', player_snippets[:5])
except Exception as e:
    import traceback
    traceback.print_exc()
