import urllib.request
import re
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://dizigecesi.com/'
}

for d in ['https://dizigecesi.com', 'https://www.dizigecesi.com']:
    try:
        req = urllib.request.Request(d, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as res:
            print(d, '->', res.status, res.geturl())
    except Exception as e:
        print(d, '->', e)

# Test search
try:
    surl = 'https://dizigecesi.com/?s=the'
    req = urllib.request.Request(surl, headers=headers)
    html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
    links = re.findall(r'href="([^"]+/(?:dizi|film)/[^"]+)"', html)
    print('Found search links:', list(dict.fromkeys(links))[:5])
    if links:
        turl = links[0]
        print('\nTesting target:', turl)
        thtml = urllib.request.urlopen(urllib.request.Request(turl, headers=headers), timeout=8).read().decode('utf-8')
        embeds = re.findall(r'data-embed=["\']([^"\']+)["\']', thtml)
        print('Target embeds:', embeds)
        if not embeds:
            # Check if it has episodes
            ep_links = re.findall(r'href="([^"]+/(?:dizi/[^"/]+/\d+-sezon/\d+-bolum|[^"]+-sezon/[^"]+-bolum[^"]*))"', thtml)
            print('Episode links found:', list(dict.fromkeys(ep_links))[:5])
            if ep_links:
                ep_url = ep_links[0]
                print('\nTesting episode:', ep_url)
                ehtml = urllib.request.urlopen(urllib.request.Request(ep_url, headers=headers), timeout=8).read().decode('utf-8')
                ep_embeds = re.findall(r'data-embed=["\']([^"\']+)["\']', ehtml)
                print('Episode embeds:', ep_embeds)
                if ep_embeds:
                    # Test ajax embed
                    ajax_url = 'https://dizigecesi.com/ajax/embed'
                    data = f'id={ep_embeds[0]}'.encode('utf-8')
                    areq = urllib.request.Request(ajax_url, data=data, headers={
                        'User-Agent': headers['User-Agent'],
                        'Referer': ep_url,
                        'X-Requested-With': 'XMLHttpRequest',
                        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                    })
                    for eid in ep_embeds[:4]:
                        data = f'id={eid}'.encode('utf-8')
                        areq = urllib.request.Request(ajax_url, data=data, headers={
                            'User-Agent': headers['User-Agent'],
                            'Referer': ep_url,
                            'X-Requested-With': 'XMLHttpRequest',
                            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                        })
                        try:
                            aresp = urllib.request.urlopen(areq, timeout=8).read().decode('utf-8')
                            iframe_m = re.search(r'<iframe[^>]+src=["\']([^"\']+)["\']', aresp)
                            if iframe_m:
                                burl = iframe_m.group(1)
                                breq = urllib.request.Request(burl, headers={'User-Agent': headers['User-Agent'], 'Referer': 'https://dizigecesi.com/'})
                                bhtml = urllib.request.urlopen(breq, timeout=8).read().decode('utf-8')
                                b_iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', bhtml)
                                print('Embed', eid, '-> bridge:', burl[:40], '-> final:', b_iframes)
                        except Exception as e:
                            print('Embed', eid, '-> failed:', e)
except Exception as e:
    import traceback
    traceback.print_exc()
