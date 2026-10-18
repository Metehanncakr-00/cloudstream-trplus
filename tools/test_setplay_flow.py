import urllib.request
import re
import json

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'https://www.setfilmizle.ltd/'
}

home_html = urllib.request.urlopen(urllib.request.Request('https://www.setfilmizle.ltd/film/', headers=headers), timeout=8).read().decode('utf-8')
movies = re.findall(r'href="(https://www.setfilmizle.ltd/film/[^"/]+/)?"', home_html)
raw_links = re.findall(r'href="([^"]+/(?:film|dizi)/[^"]+)"', home_html)
print('Raw links count:', len(raw_links))
unique_links = list(dict.fromkeys(raw_links))
print('Unique links:', unique_links[:6])

for murl in unique_links[:4]:
    print('\nChecking:', murl)
    try:
        mhtml = urllib.request.urlopen(urllib.request.Request(murl, headers=headers), timeout=8).read().decode('utf-8')
        post_id = re.search(r'data-post-id=["\']([^"\']+)["\']', mhtml) or re.search(r'data-id=["\']([^"\']+)["\']', mhtml)
        nonce = re.search(r'video\s*:\s*["\']([^"\']+)["\']', mhtml)
        players = re.findall(r'data-player-name=["\']([^"\']+)["\']', mhtml)
        iframes = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', mhtml)
        print('  post_id:', post_id.group(1) if post_id else None)
        print('  nonce:', nonce.group(1) if nonce else None)
        print('  players:', players)
        print('  iframes:', iframes)
        
        if post_id and nonce:
            ajax_url = 'https://www.setfilmizle.ltd/wp-admin/admin-ajax.php'
            pname = players[0] if players else 'SetPlay'
            data = f'action=get_video_url&nonce={nonce.group(1)}&post_id={post_id.group(1)}&player_name={pname}&part_key='.encode('utf-8')
            areq = urllib.request.Request(ajax_url, data=data, headers={
                'User-Agent': headers['User-Agent'],
                'Referer': murl,
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
            })
            aresp = urllib.request.urlopen(areq, timeout=8).read().decode('utf-8')
            print('  ajax response:', aresp[:200])
    except Exception as e:
        print('  Error:', e)
