import os

path = r'Dizilla/src/main/kotlin/com/cloudstream/tr/dizilla/Dizilla.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'java.util.Base64.getDecoder().decode(base64Cipher.trim())',
    'try { android.util.Base64.decode(base64Cipher.trim(), android.util.Base64.DEFAULT) } catch (_: Throwable) { java.util.Base64.getDecoder().decode(base64Cipher.trim()) }'
)

content = content.replace('doc.select("div.serie-card, div.episode-card, a[href*=\'/dizi/\']").forEach { el ->', 'for (el in doc.select("div.serie-card, div.episode-card, a[href*=\'/dizi/\']")) {')
content = content.replace('parsedList?.forEach { item ->', 'for (item in parsedList.orEmpty()) {')
content = content.replace('doc.select("div.serie-card, a[href*=\'/dizi/\']").forEach { el ->', 'for (el in doc.select("div.serie-card, a[href*=\'/dizi/\']")) {')
content = content.replace('doc.select("a[href*=\'-sezon-\']").forEach { a ->', 'for (a in doc.select("a[href*=\'-sezon-\']")) {')
content = content.replace('doc.select("iframe").forEach { iframe ->', 'for (iframe in doc.select("iframe")) {')

content = content.replace('return@forEach', 'continue')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
