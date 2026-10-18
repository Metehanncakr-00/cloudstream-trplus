import urllib.request
import re

url = "https://s1.cdnimg5891.shop/m2/DKMuqTSlYwVjZQxhEIuHEH5REHDhHxIDDHAYYwRjBQOjYxWfqIWurGZmd0zxL2EhnJ1aAGt5ZF5mnT9js0xi1vr1"

for ref in [None, "https://rapidvid.org/", "https://www.fullhdfilmizlesene.now/"]:
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    if ref:
        headers["Referer"] = ref
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as res:
            print(f"Master manifest with ref={ref}: status {res.status}")
    except Exception as e:
        print(f"Master manifest with ref={ref}: error {e}")

# Now fetch master manifest and parse stream-inf URL and segment
req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://rapidvid.org/"})
m3u8 = urllib.request.urlopen(req).read().decode('utf-8')
variant_path = [l for l in m3u8.split('\n') if l and not l.startswith('#')][0]
variant_url = "https://s1.cdnimg5891.shop" + variant_path
print("Variant url:", variant_url)

vreq = urllib.request.Request(variant_url, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://rapidvid.org/"})
vm3u8 = urllib.request.urlopen(vreq).read().decode('utf-8')
print("Variant m3u8 preview:\n", vm3u8[:300])

segment_path = [l for l in vm3u8.split('\n') if l and not l.startswith('#')][0]
segment_url = "https://s1.cdnimg5891.shop" + segment_path
print("Segment url:", segment_url)

# Test segment fetch
for sref in [None, "https://rapidvid.org/"]:
    sheaders = {"User-Agent": "Mozilla/5.0"}
    if sref:
        sheaders["Referer"] = sref
    try:
        sreq = urllib.request.Request(segment_url, headers=sheaders)
        with urllib.request.urlopen(sreq, timeout=5) as sres:
            data = sres.read(100)
            print(f"Segment with ref={sref}: status {sres.status}, len {len(data)}")
    except Exception as e:
        print(f"Segment with ref={sref}: error {e}")
