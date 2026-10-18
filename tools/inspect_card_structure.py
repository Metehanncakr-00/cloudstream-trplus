from inspect_cizgimax_cartoons import html
import re

# Look for classes of div/article elements
divs = re.findall(r'<div class=["\']([^"\']+)["\']', html)
print('Div classes sample:', list(dict.fromkeys(divs))[:20])

# Look for posters or images
imgs = re.findall(r'<img[^>]+src=["\']([^"\']+)["\'][^>]*>', html)
print('Images sample:', [i for i in imgs if not i.endswith('.svg')][:5])

# Print HTML around first image
first_img = re.search(r'<img[^>]+src=["\'][^"\']*(?:uploads|poster|cover|jpg|webp)[^"\']*["\'][^>]*>', html)
if first_img:
    idx = first_img.start()
    print('\nContext around first image:\n', html[max(0, idx-200):idx+400])
