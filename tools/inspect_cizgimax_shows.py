from inspect_cizgimax_links import filtered
import re

non_tur = [item for item in filtered if not item[0].startswith('/tur/') and not item[0].startswith('/sayfa/')]
print('Non-genre links count:', len(non_tur))
for href, title in non_tur[:30]:
    print(f'{href} -> {title}')
