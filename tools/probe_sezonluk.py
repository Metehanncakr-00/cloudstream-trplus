import urllib.request
import re
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://sezonlukdizi.cc/'
}

surl = "https://sezonlukdizi.cc/ajax/arama.asp"
data = "q=breaking+bad".encode('utf-8')
req = urllib.request.Request(surl, data=data, headers={
    'User-Agent': headers['User-Agent'],
    'Referer': 'https://sezonlukdizi.cc/',
    'X-Requested-With': 'XMLHttpRequest',
    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
})
resp = urllib.request.urlopen(req, timeout=8).read().decode('cp1254', errors='replace')
sjson = json.loads(resp)
diziler = sjson.get('results', {}).get('diziler', {}).get('results', [])
print("Found diziler:", len(diziler))
if diziler:
    durl = "https://sezonlukdizi.cc" + diziler[0]['url']
    print("Dizi URL:", durl)
    dhtml = urllib.request.urlopen(urllib.request.Request(durl, headers=headers)).read().decode('cp1254', errors='replace')
    ep_links = re.findall(r'href="([^"]+-sezon-\d+-bolum[^"]*)"', dhtml)
    print("Found episode links:", ep_links[:3])
    if ep_links:
        ep_url = "https://sezonlukdizi.cc" + ep_links[0]
        print("EP URL:", ep_url)
        ep_html = urllib.request.urlopen(urllib.request.Request(ep_url, headers=headers)).read().decode('cp1254', errors='replace')
        bid_m = re.search(r'div#dilsec.*?data-id=["\'](\d+)["\']', ep_html, re.DOTALL)
        if not bid_m:
            bid_m = re.search(r'data-id=["\'](\d+)["\']', ep_html)
        print("bid:", bid_m.group(1) if bid_m else None)
        if bid_m:
            bid = bid_m.group(1)
            alt_url = "https://sezonlukdizi.cc/ajax/dataAlternatif22.asp"
            alt_data = f"bid={bid}&dil=1".encode('utf-8')
            areq = urllib.request.Request(alt_url, data=alt_data, headers={
                'User-Agent': headers['User-Agent'],
                'Referer': ep_url,
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
            })
            aresp = urllib.request.urlopen(areq, timeout=8).read().decode('cp1254', errors='replace')
            print("Alternatif resp:", aresp)
            aj = json.loads(aresp)
            for item in aj.get('data', []):
                sid = item.get('id')
                em_url = "https://sezonlukdizi.cc/ajax/dataEmbed22.asp"
                em_data = f"id={sid}".encode('utf-8')
                ereq = urllib.request.Request(em_url, data=em_data, headers={
                    'User-Agent': headers['User-Agent'],
                    'Referer': ep_url,
                    'X-Requested-With': 'XMLHttpRequest',
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                })
                eresp = urllib.request.urlopen(ereq, timeout=8).read().decode('cp1254', errors='replace')
                print(f"Embed for sid={sid} (kalite={item.get('kalite')}): {eresp.strip()}")
