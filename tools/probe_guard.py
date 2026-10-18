import urllib.request
import re

url = "https://fastplay.mom/video/7Bxd8b1eYRN8"
html = urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0', 'Referer': 'https://setplay.shop/'})).read().decode('utf-8')
scripts = re.findall(r'<script>(.*?)</script>', html, re.DOTALL)
print("Script 1 full:")
print(scripts[1])
