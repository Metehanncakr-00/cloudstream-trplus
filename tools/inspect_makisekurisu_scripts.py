from inspect_makisekurisu import html
import re

scripts = re.findall(r'<script[^>]*src=["\']([^"\']+)["\']', html)
print('Script src tags:', scripts)
body_sample = html[html.find('<body'):html.find('<body')+2000]
print('Body sample:\n', body_sample)
