import urllib.request
import re

sreq = urllib.request.Request("https://4khdhub.one/?s=Inception", headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
shtml = urllib.request.urlopen(sreq, timeout=10).read().decode('utf-8')
# Find card links
card_links = re.findall(r'<a[^>]+href=["\']([^"\']*(?:-movie-|-series-|-season-)[^"\']*)["\'][^>]*>(.*?)</a>', shtml, re.DOTALL)
print('Card links found:', len(card_links))
for h, t in card_links[:5]:
    # find images
    imgs = re.findall(r'<img[^>]+src=["\']([^"\']+)["\']', t)
    title = re.sub(r'<[^>]+>', ' ', t).strip()
    print('  Card:', h, 'img:', imgs, 'title snippet:', ' '.join(title.split()[:4]))




