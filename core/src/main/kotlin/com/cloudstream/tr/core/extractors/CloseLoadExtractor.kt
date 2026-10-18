package com.cloudstream.tr.core.extractors

import com.cloudstream.tr.core.network.SafeHttpClient
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class CloseLoadExtractor : ExtractorApi() {
    override val name = "CloseLoad"
    override val mainUrl = "https://closeload.com"
    override val requiresReferer = true

    companion object {
        fun decodeBase64Latin1(str: String): String {
            val padLen = (4 - str.length % 4) % 4
            val padded = str + "=".repeat(padLen)
            return try {
                val bytes = com.cloudstream.tr.core.utils.Base64Utils.decode(padded)
                String(bytes, Charsets.ISO_8859_1)
            } catch (_: Throwable) {
                ""
            }
        }

        fun unpackPacker(script: String): String {
            val regex = Regex("""eval\(function\(p,a,c,k,e,d\).+?return\s+p\s*\}\s*\(\s*'((?:\\'|[^'])*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'((?:\\'|[^'])*)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(script) ?: return script
            val payload = match.groupValues[1].replace("\\'", "'").replace("\\\\", "\\")
            val radix = match.groupValues[2].toIntOrNull() ?: return script
            val count = match.groupValues[3].toIntOrNull() ?: return script
            val symtab = match.groupValues[4].split('|')

            val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            fun baseN(num: Int, b: Int): String {
                if (num == 0) return "0"
                var n = num
                val sb = StringBuilder()
                while (n > 0) {
                    sb.append(digits[n % b])
                    n /= b
                }
                return sb.reverse().toString()
            }

            val lookup = HashMap<String, String>()
            for (i in 0 until count) {
                val key = baseN(i, radix)
                val sym = if (i < symtab.size && symtab[i].isNotEmpty()) symtab[i] else key
                lookup[key] = sym
            }

            return Regex("""\b\w+\b""").replace(payload) { m ->
                lookup[m.value] ?: m.value
            }
        }

        fun decodeCloseLoadPayload(parts: List<String>): String {
            val list = parts.toMutableList()
            val aik4z = list.size - 2
            val ozqzr = aik4z % 7
            val ft0q0 = 8 + (aik4z % 5)

            if (ft0q0 >= list.size || ozqzr >= list.size) return ""

            val t2o = list.removeAt(ft0q0)
            val xtqz = list.removeAt(ozqzr)
            var uvhq = list.joinToString("")

            if (xtqz.length > 4096) {
                uvhq = decodeBase64Latin1(uvhq)
            }

            var xbgh9 = 0
            var hqbz = 0
            for (v2jhe in xtqz.indices) {
                val xu25 = xtqz[v2jhe].code
                xbgh9 = (xbgh9 * 37 + xu25) % 241
                hqbz = (hqbz + ((xu25 shl 1) xor v2jhe)) and 255
            }

            val mlr3o = (xbgh9 * 3 + hqbz) % 256
            val gjy = (hqbz % 11) + 5
            var euerq = ((hqbz * 251 + xbgh9) % 65519) + 1

            for (v2jhe in t2o.length - 1 downTo 0) {
                val k1vh = t2o[v2jhe]
                when (k1vh) {
                    '7' -> {
                        uvhq = decodeBase64Latin1(uvhq)
                    }
                    '3' -> {
                        uvhq = uvhq.reversed()
                    }
                    else -> {
                        val v29c = (26 - ((k1vh.code - 96) % 26)) % 26
                        val sb = StringBuilder(uvhq.length)
                        for (ch in uvhq) {
                            val c9n = ch.code
                            if (ch in 'A'..'Z') {
                                sb.append(((c9n - 65 + v29c) % 26 + 65).toChar())
                            } else if (ch in 'a'..'z') {
                                sb.append(((c9n - 97 + v29c) % 26 + 97).toChar())
                            } else {
                                sb.append(ch)
                            }
                        }
                        uvhq = sb.toString()
                    }
                }
            }

            if (t2o.length > 2048) {
                uvhq = uvhq.reversed()
            }

            val finalAik4z = uvhq.length
            val gm7n = IntArray(finalAik4z)
            for (v2jhe in finalAik4z - 1 downTo 1) {
                euerq = (euerq * 97 + 41) % 65519
                gm7n[v2jhe] = euerq % (v2jhe + 1)
            }

            val dowxc = uvhq.toCharArray()
            for (v2jhe in 1 until finalAik4z) {
                val wrxyp = gm7n[v2jhe]
                val t2avk = dowxc[v2jhe]
                dowxc[v2jhe] = dowxc[wrxyp]
                dowxc[wrxyp] = t2avk
            }
            uvhq = String(dowxc)

            var uo2b0 = mlr3o
            val julf = StringBuilder(uvhq.length)
            for (v2jhe in uvhq.indices) {
                val xu25 = uvhq[v2jhe].code
                uo2b0 = (uo2b0 * 5 + gjy) % 256
                julf.append((xu25 xor uo2b0).toChar())
                uo2b0 = (uo2b0 + xu25) % 256
            }

            return julf.toString()
        }

        fun extractStreamUrl(html: String): String? {
            // 1. Direct file regex (.m3u8, .mp4, .txt)
            val directFile = Regex("""file:\s*["']([^"']+\.(?:m3u8|mp4|txt)[^"']*)["']""").find(html)?.groupValues?.get(1)
            if (!directFile.isNullOrBlank()) return directFile

            // 2. Unpack if script contains packed code
            val fullContent = if (html.contains("eval(function(p,a,c,k,e,d)")) {
                html + "\n" + unpackPacker(html)
            } else {
                html
            }

            // 3. Dynamic variable in sources: [{file: <varName>}]
            val varMatches = Regex("""sources:\s*\[\{file:\s*([a-zA-Z0-9_]+)""").findAll(fullContent)
            val varName = varMatches.map { it.groupValues[1] }.firstOrNull { it != "atob" }

            // 4. Try matching var varName = func("...".split("..."))
            if (!varName.isNullOrBlank()) {
                val payloadRegex = Regex("""var\s+$varName\s*=\s*[a-zA-Z0-9_]+\s*\(\s*["']([^"']+)["']\.split\(\s*["']([^"']+)["']\s*\)\s*\);""")
                val mPayload = payloadRegex.find(fullContent)
                if (mPayload != null) {
                    val rawPayload = mPayload.groupValues[1]
                    val delim = mPayload.groupValues[2]
                    val parts = rawPayload.split(delim)
                    val decoded = decodeCloseLoadPayload(parts)
                    if (decoded.startsWith("http")) return decoded
                }
            }

            // 5. Generic scan for func("...".split("...")) where payload has typical CloseLoad structure
            val genericRegex = Regex("""var\s+[a-zA-Z0-9_]+\s*=\s*[a-zA-Z0-9_]+\s*\(\s*["']([^"']{100,})["']\.split\(\s*["']([|\^*@#~])["']\s*\)\s*\);""")
            for (m in genericRegex.findAll(fullContent)) {
                val rawPayload = m.groupValues[1]
                val delim = m.groupValues[2]
                val parts = rawPayload.split(delim)
                if (parts.size >= 10) {
                    val decoded = decodeCloseLoadPayload(parts)
                    if (decoded.startsWith("http")) return decoded
                }
            }

            return null
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val host = Regex("""https?://[^/]+""").find(url)?.value ?: mainUrl
            val doc = app.get(
                url,
                headers = SafeHttpClient.defaultHeaders(referer = referer ?: host)
            ).document

            val rawHtml = doc.html()
            val videoSrc = doc.selectFirst("video source")?.attr("src")
                ?: doc.selectFirst("source")?.attr("src")
                ?: extractStreamUrl(rawHtml)

            if (!videoSrc.isNullOrBlank()) {
                val fullStream = if (videoSrc.startsWith("http")) videoSrc else if (videoSrc.startsWith("//")) "https:$videoSrc" else "${host}/$videoSrc"
                val preflight = com.cloudstream.tr.core.network.StreamValidator.validateStream(
                    url = fullStream,
                    headers = mapOf("Referer" to "${host}/"),
                    provider = name
                )
                if (preflight.isValid) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name ${if (preflight.streamType == ExtractorLinkType.M3U8) "HLS" else "Stream"}",
                            url = fullStream,
                            type = preflight.streamType
                        ) {
                            this.referer = "${host}/"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                provider = name,
                stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.EXTRACTOR,
                category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.EXTRACTOR,
                message = "CloseLoad getUrl failed: ${e.message}",
                url = url,
                throwable = e
            )
        }
    }
}
