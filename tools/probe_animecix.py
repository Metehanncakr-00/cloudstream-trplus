import urllib.request
import json

headers = {
    "x-e-h": "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk",
    "Referer": "https://animecix.tv/",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}

url = "https://animecix.tv/secure/search/naruto?limit=5"
try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=8) as res:
        print("Search status:", res.status)
        j = json.loads(res.read().decode('utf-8'))
        results = j.get('results', [])
        print("Results count:", len(results))
        if results:
            aid = results[0].get('id')
            print("First anime id:", aid, results[0].get('name'))
            
            # Fetch videos
            vurl = f"https://animecix.tv/secure/related-videos?episode=1&season=1&videoId=0&titleId={aid}"
            vreq = urllib.request.Request(vurl, headers=headers)
            with urllib.request.urlopen(vreq, timeout=8) as vres:
                vj = json.loads(vres.read().decode('utf-8'))
                vids = vj.get('videos', [])
                print("Videos count:", len(vids))
                if vids:
                    print("First video URL:", vids[0].get('url'))
                    # Probe video url
                    target_url = vids[0].get('url')
                    if "tau-video.xyz" in target_url:
                        key = target_url.split('/')[-1]
                        tau_api = f"https://tau-video.xyz/api/video/{key}"
                        treq = urllib.request.Request(tau_api, headers={'User-Agent': headers['User-Agent'], 'Referer': 'https://animecix.tv/'})
                        with urllib.request.urlopen(treq, timeout=8) as tres:
                            tj = json.loads(tres.read().decode('utf-8'))
                            print("Tau API urls count:", len(tj.get('urls', [])))
                            print("Tau API tracks count:", len(tj.get('tracks', [])))
                            for u in tj.get('urls', []):
                                print("Video stream:", u.get('label'), u.get('url')[:60])
                            for trk in tj.get('tracks', []):
                                print("Track:", trk.get('label'), trk.get('file'))
except Exception as e:
    import traceback
    traceback.print_exc()
