package com.cloudstream.tr.hdfilmcehennemi

import com.cloudstream.tr.core.extractors.CloseLoadExtractor
import com.cloudstream.tr.core.network.SafeHttpClient
import com.cloudstream.tr.core.network.StreamValidator
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.regex.Pattern

open class RapidrameExtractor : ExtractorApi() {
    override val name = "Rapidrame"
    override val mainUrl = "https://hdfilmcehennemi.mobi"
    override val requiresReferer = true

    companion object {
        // Backward compatibility helper for legacy format if ever encountered
        fun decodeRapidrame(jsCode: String, arrStr: String): String {
            val arrRegex = Pattern.compile("\"([^\"]+)\"")
            val arrMatcher = arrRegex.matcher(arrStr)
            val sb = java.lang.StringBuilder()
            while (arrMatcher.find()) {
                sb.append(arrMatcher.group(1))
            }
            var kspgo = sb.toString()

            val keysRegex = Pattern.compile("var\\s+[a-zA-Z0-9_]+\\s*=\\s*\"([^\"]+)\";\\s*var\\s+[a-zA-Z0-9_]+\\s*=\\s*\"([^\"]+)\";")
            val keysMatcher = keysRegex.matcher(jsCode)
            if (!keysMatcher.find()) return ""
            val key1 = keysMatcher.group(1) ?: return ""
            val key2 = keysMatcher.group(2) ?: return ""

            var o0v = 0
            var rbc = 0
            for (ro7 in key1.indices) {
                val wlv = key1[ro7].code
                o0v = (o0v * 31 + wlv) % 251
                rbc = (rbc xor (wlv + ro7)) and 255
            }

            val lbxe = (o0v + rbc) % 256
            val u2u5r = (o0v % 13) + 3
            var d6en9 = ((o0v * 256 + rbc) % 65521) + 1

            for (ro7 in key2.length - 1 downTo 0) {
                val x7ed6 = key2[ro7]
                if (x7ed6 == 'b') {
                    var padded = kspgo
                    val missing = padded.length % 4
                    if (missing != 0) {
                        padded += "=".repeat(4 - missing)
                    }
                    val decodedBytes = com.cloudstream.tr.core.utils.Base64Utils.decode(padded)
                    kspgo = String(decodedBytes, Charsets.ISO_8859_1)
                } else if (x7ed6 == 'v') {
                    kspgo = kspgo.reversed()
                } else {
                    val ql55 = (26 - ((x7ed6.code - 64) % 26)) % 26
                    val chars = java.lang.StringBuilder()
                    for (c in kspgo) {
                        if (c.isLetter()) {
                            val y85 = c.code
                            val fro = if (y85 <= 90) 65 else 97
                            chars.append(((y85 - fro + ql55) % 26 + fro).toChar())
                        } else {
                            chars.append(c)
                        }
                    }
                    kspgo = chars.toString()
                }
            }

            val trn = kspgo.length
            val npz8 = IntArray(trn)
            for (ro7 in trn - 1 downTo 1) {
                d6en9 = (d6en9 * 75 + 74) % 65537
                npz8[ro7] = d6en9 % (ro7 + 1)
            }

            val oe7 = kspgo.toCharArray()
            for (ro7 in 1 until trn) {
                val fy6 = npz8[ro7]
                val awn = oe7[ro7]
                oe7[ro7] = oe7[fy6]
                oe7[fy6] = awn
            }
            kspgo = String(oe7)

            var tds = lbxe
            val v8y7l = java.lang.StringBuilder()
            for (ro7 in kspgo.indices) {
                val wlv = kspgo[ro7].code
                tds = (tds + u2u5r) % 256
                v8y7l.append((wlv xor tds).toChar())
                tds = (tds + wlv) % 256
            }

            return v8y7l.toString()
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
            val effectiveReferer = referer ?: "https://www.hdfilmcehennemi.nl/"
            val headers = SafeHttpClient.defaultHeaders(referer = effectiveReferer)
            val doc = app.get(url, headers = headers).document
            val rawHtml = doc.html()

            // 1. Try modern CloseLoad / Rapidrame algorithmic extraction
            var streamUrl = CloseLoadExtractor.extractStreamUrl(rawHtml)

            // 2. Legacy array extraction fallback if not found
            if (streamUrl.isNullOrBlank()) {
                val arrRegex = Pattern.compile("\\[\\s*\"[^\\]]+\"\\s*\\]")
                val arrMatcher = arrRegex.matcher(rawHtml)
                while (arrMatcher.find()) {
                    val arrStr = arrMatcher.group(0) ?: continue
                    if (arrStr.contains(".jpg") || arrStr.contains(".png") || arrStr.contains(".webp")) continue
                    try {
                        val legacyUrl = decodeRapidrame(rawHtml, arrStr)
                        if (legacyUrl.isNotEmpty() && (legacyUrl.contains(".m3u8") || legacyUrl.contains(".txt") || legacyUrl.contains("/hls/"))) {
                            streamUrl = legacyUrl
                            break
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. Validate and emit stream
            if (!streamUrl.isNullOrBlank()) {
                val fullStream = if (streamUrl.startsWith("http")) streamUrl else if (streamUrl.startsWith("//")) "https:$streamUrl" else "${host}/$streamUrl"
                val preflight = StreamValidator.validateStream(
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

            // 4. Subtitles
            val tracksRegex = Regex("""\{"file":"(https?:[^"]+\.vtt)"[^}]+?"label":"([^"]+)"""")
            for (match in tracksRegex.findAll(rawHtml)) {
                val subUrl = match.groupValues[1].replace("""\/""", "/")
                val label = match.groupValues[2]
                subtitleCallback(
                    SubtitleFile(
                        lang = label,
                        url = subUrl
                    )
                )
            }
        } catch (e: Exception) {
            com.cloudstream.tr.core.diagnostics.DiagnosticLogger.log(
                provider = name,
                stage = com.cloudstream.tr.core.diagnostics.DiagnosticStage.EXTRACTOR,
                category = com.cloudstream.tr.core.diagnostics.DiagnosticCategory.EXTRACTOR,
                message = "Rapidrame getUrl failed: ${e.message}",
                url = url,
                throwable = e
            )
        }
    }
}
