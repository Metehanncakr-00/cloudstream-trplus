import urllib.request
import re
import json
import urllib.parse

iframe_url = "https://four.pichive.online/iframe.php?v=62d045908c58ee6f481d113712733d49"
headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://dizilla.now/"
}

req = urllib.request.Request(iframe_url, headers=headers)
content = urllib.request.urlopen(req, timeout=10).read().decode("utf-8")
token = re.search(r'openPlayer\s*\(\s*[\'"]([^\'"]+)[\'"]', content).group(1)

source_url = f"https://four.pichive.online/source2.php?v={urllib.parse.quote(token)}"
sreq = urllib.request.Request(source_url, headers={
    "Referer": iframe_url,
    "X-Requested-With": "XMLHttpRequest",
    "User-Agent": headers["User-Agent"]
})
sdata = json.loads(urllib.request.urlopen(sreq, timeout=10).read().decode("utf-8"))
file_url = sdata["playlist"][0]["sources"][0]["file"]

# Test HEAD
class HeadRequest(urllib.request.Request):
    def get_method(self):
        return "HEAD"

try:
    hreq = HeadRequest(file_url, headers={"User-Agent": headers["User-Agent"], "Referer": "https://four.pichive.online/"})
    with urllib.request.urlopen(hreq, timeout=10) as hres:
        print("HEAD status:", hres.status)
except Exception as e:
    print("HEAD error:", e)

# Test GET range
try:
    greq = urllib.request.Request(file_url, headers={"User-Agent": headers["User-Agent"], "Referer": "https://four.pichive.online/", "Range": "bytes=0-100"})
    with urllib.request.urlopen(greq, timeout=10) as gres:
        print("GET status:", gres.status)
except Exception as e:
    print("GET error:", e)
