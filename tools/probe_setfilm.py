import urllib.request
import re
import json
import base64

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.setfilmizle.ltd/'
}

for d in ['https://www.setfilmizle.ltd', 'https://setfilmizle.ltd', 'https://www.setfilmizle.vip', 'https://setfilmizle.org']:
    try:
        req = urllib.request.Request(d, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as res:
            print(d, "->", res.status, res.geturl())
    except Exception as e:
        print(d, "->", e)

# Test search
try:
    surl = "https://www.setfilmizle.ltd/?s=avatar"
    req = urllib.request.Request(surl, headers=headers)
    html = urllib.request.urlopen(req, timeout=8).read().decode('utf-8')
    movies = re.findall(r'href="([^"]+/(?:film|dizi)/[^"]+)"', html)
    print("Found movies:", movies[:5])
    if movies:
        murl = movies[0]
        print("Testing movie:", murl)
        mreq = urllib.request.Request(murl, headers=headers)
        mhtml = urllib.request.urlopen(mreq, timeout=8).read().decode('utf-8')
        
        post_id = re.search(r'data-post-id=[\'"]([^\'"]+)[\'"]', mhtml)
        if not post_id:
            post_id = re.search(r'data-id=[\'"]([^\'"]+)[\'"]', mhtml)
        nonce = re.search(r'video\s*:\s*[\'"]([^\'"]+)[\'"]', mhtml)
        print("post_id:", post_id.group(1) if post_id else None)
        print("nonce:", nonce.group(1) if nonce else None)
        
        # Check ajax
        if post_id and nonce:
            ajax_url = "https://www.setfilmizle.ltd/wp-admin/admin-ajax.php"
            data = f"action=get_video_url&nonce={nonce.group(1)}&post_id={post_id.group(1)}&player_name=SetPlay&part_key=".encode('utf-8')
            areq = urllib.request.Request(ajax_url, data=data, headers={
                'User-Agent': headers['User-Agent'],
                'Referer': murl,
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
            })
            aresp = urllib.request.urlopen(areq, timeout=8).read().decode('utf-8')
            print("Ajax resp:", aresp[:300])
except Exception as e:
    import traceback
    traceback.print_exc()
