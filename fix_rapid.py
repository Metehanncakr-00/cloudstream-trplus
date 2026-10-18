import os

path = r'core/src/main/kotlin/com/cloudstream/tr/core/extractors/RapidVidExtractor.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'val decodedBytes = Base64.getDecoder().decode(padded)',
    'val decodedBytes = try { android.util.Base64.decode(padded, android.util.Base64.DEFAULT) } catch (_: Throwable) { java.util.Base64.getDecoder().decode(padded) }'
)
content = content.replace(
    'String(Base64.getDecoder().decode(innerPadded), Charsets.UTF_8)',
    'String(try { android.util.Base64.decode(innerPadded, android.util.Base64.DEFAULT) } catch (_: Throwable) { java.util.Base64.getDecoder().decode(innerPadded) }, Charsets.UTF_8)'
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
