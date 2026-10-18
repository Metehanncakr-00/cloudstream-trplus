from inspect_cizgimax_detail import html
import re

bolums = re.findall(r'<a[^>]+href=["\']([^"\']+)["\'][^>]*>[^<]*(?:Bölüm|bolum|sezon)[^<]*</a>', html, re.IGNORECASE)
print('Bolum links with text:', bolums[:10])

# Look for bolum links anywhere
all_bolum = re.findall(r'href=["\']([^"\']*(?:bolum|sezon)[^"\']*)["\']', html)
print('All bolum hrefs:', list(dict.fromkeys(all_bolum))[:10])

# Look for episode listing container
ep_containers = re.findall(r'<div class=["\']([^"\']*(?:episode|bolum|sezon|list)[^"\']*)["\']', html)
print('Episode container classes:', list(dict.fromkeys(ep_containers))[:15])

# Print HTML around first bolum match
b_m = re.search(r'href=["\'][^"\']*(?:bolum|sezon)[^"\']*["\']', html)
if b_m:
    idx = b_m.start()
    print('Context around bolum:\n', html[max(0, idx-100):idx+300])
