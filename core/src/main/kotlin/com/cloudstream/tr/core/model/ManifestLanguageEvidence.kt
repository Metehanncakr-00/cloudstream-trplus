package com.cloudstream.tr.core.model

import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** Explicit manifest track metadata only; not decoded speech or playback evidence. */
data class ManifestLanguageEvidence(
    val audio: Set<String> = emptySet(),
    val subtitles: Set<String> = emptySet()
) {
    fun tier(preference: String = "TR_DUB", unknownPolicy: String = "DEPRIORITIZE"): Int {
        val isUnknown = audio.isEmpty() && subtitles.isEmpty()
        val unknownTier = when (unknownPolicy) {
            "NEUTRAL", "EQUAL_TO_SUB" -> 2
            "BOTTOM" -> -1
            else -> 1 // "DEPRIORITIZE": below verified dub (3) and sub (2), above foreign (0)
        }
        return when {
            // ORIGINAL means explicitly known non-Turkish, not an unlabelled source.
            preference == "ORIGINAL" && audio.any { it != "tr" } -> 4
            preference == "ORIGINAL" && subtitles.any { it != "tr" } -> 2
            preference == "ORIGINAL" && isUnknown -> unknownTier
            preference == "ORIGINAL" -> 0
            "tr" in audio -> 3
            "tr" in subtitles -> 2
            isUnknown -> unknownTier
            else -> 0 // Explicitly other languages (e.g. English, Russian)
        }
    }

}

object ManifestLanguageParser {
    // ISO-639-1 and their ISO-639-2/T codes, plus the bibliographic aliases.
    // Deliberately conservative: arbitrary three-letter words are not languages.
    private val languages: Map<String, String> by lazy {
        val codes = mutableMapOf<String, String>()
        Locale.getISOLanguages().forEach { language ->
            codes[language] = language
            try { codes[Locale(language).isO3Language] = language } catch (_: java.util.MissingResourceException) { }
        }
        // Some Java locales have a registered ISO-639 language with no two-letter
        // equivalent (e.g. fil). Accept these known codes, not arbitrary ISO-shaped text.
        Locale.getAvailableLocales().map { it.language }.filter { it.matches(Regex("[a-z]{3}")) && it !in setOf("und", "mul", "zxx") }
            .forEach { codes.putIfAbsent(it, it) }
        mapOf("alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my",
            "chi" to "zh", "cze" to "cs", "dut" to "nl", "fre" to "fr",
            "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is",
            "mac" to "mk", "mao" to "mi", "may" to "ms", "per" to "fa",
            "rum" to "ro", "slo" to "sk", "tib" to "bo", "wel" to "cy",
            "iw" to "he", "in" to "id", "ji" to "yi").forEach { (alias, canonical) -> codes[alias] = canonical }
        codes
    }

    fun normalize(value: String?): String? {
        val tag = value?.trim()?.replace('_', '-') ?: return null
        // Accept language, optional script, optional region; reject private-use/extensions,
        // und/mul/zxx, malformed tags and labels such as "Turkish dub".
        if (!tag.matches(Regex("[A-Za-z]{2,3}(?:-[A-Za-z]{4})?(?:-(?:[A-Za-z]{2}|[0-9]{3}))?"))) return null
        return languages[tag.substringBefore('-').lowercase(Locale.ROOT)]
    }

    fun parse(bytes: ByteArray, truncated: Boolean = false): ManifestLanguageEvidence {
        val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
        return when {
            text.startsWith("#EXTM3U\n") || text.startsWith("#EXTM3U\r\n") -> hls(text, truncated)
            !truncated && (text.startsWith("<?xml") || text.startsWith("<MPD")) -> dash(text)
            else -> ManifestLanguageEvidence()
        }
    }

    private fun attributes(line: String): Map<String, String>? {
        val regex = Regex("""([A-Z0-9-]+)=(?:"([^"]*)"|([^,\s]+))(?:,|$)""")
        val matches = regex.findAll(line).toList()
        if (line.endsWith(',') || matches.joinToString("") { it.value } != line ||
            matches.map { it.groupValues[1] }.distinct().size != matches.size) return null
        return matches.associate { it.groupValues[1] to (it.groups[2]?.value ?: it.groupValues[3]) }
    }

    private fun hls(text: String, truncated: Boolean): ManifestLanguageEvidence {
        val lines = text.lines().let { if (truncated) it.dropLast(1) else it }.map { it.trim() }
        val referencedAudio = mutableSetOf<String>()
        val referencedText = mutableSetOf<String>()
        for (i in 0 until lines.lastIndex) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF:")) continue
            if (lines[i + 1].isBlank() || lines[i + 1].startsWith('#')) continue
            val attrs = attributes(lines[i].substringAfter(':')) ?: continue
            attrs["AUDIO"]?.let(referencedAudio::add)
            attrs["SUBTITLES"]?.let(referencedText::add)
        }
        val audio = mutableSetOf<String>()
        val subtitles = mutableSetOf<String>()
        for (line in lines.filter { it.startsWith("#EXT-X-MEDIA:") }) {
            val attrs = attributes(line.substringAfter(':')) ?: continue
            val language = normalize(attrs["LANGUAGE"]) ?: continue
            when {
                attrs["TYPE"] == "AUDIO" && attrs["GROUP-ID"] in referencedAudio -> audio.add(language)
                attrs["TYPE"] == "SUBTITLES" && attrs["GROUP-ID"] in referencedText -> subtitles.add(language)
            }
        }
        return ManifestLanguageEvidence(audio, subtitles)
    }

    private fun dash(text: String): ManifestLanguageEvidence {
        return try {
            // No external entities, DTDs, or network access, even on Android parser variants.
            if (text.contains("<!DOCTYPE", true) || text.contains("<!ENTITY", true)) return ManifestLanguageEvidence()
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
            if (document.documentElement.localName != "MPD") return ManifestLanguageEvidence()
            val audio = mutableSetOf<String>()
            val subtitles = mutableSetOf<String>()
            fun visit(element: org.w3c.dom.Element, inheritedLang: String?, inheritedType: String?) {
                val rawLang = element.getAttribute("lang").ifBlank { element.getAttribute("xml:lang") }
                val language = if (rawLang.isBlank()) inheritedLang else normalize(rawLang)
                val mime = element.getAttribute("mimeType")
                val codecs = element.getAttribute("codecs")
                val type = element.getAttribute("contentType").ifBlank {
                    when {
                        mime == "application/ttml+xml" || codecs.startsWith("stpp") || codecs.startsWith("wvtt") -> "text"
                        else -> mime.substringBefore('/')
                    }
                }.ifBlank { inheritedType.orEmpty() }
                if (element.localName == "Representation" ||
                    (element.localName == "AdaptationSet" && element.getElementsByTagNameNS("*", "Representation").length == 0)) {
                    if (language != null && type == "audio") audio.add(language)
                    if (language != null && type == "text") subtitles.add(language)
                }
                for (i in 0 until element.childNodes.length) (element.childNodes.item(i) as? org.w3c.dom.Element)?.let { visit(it, language, type) }
            }
            visit(document.documentElement, null, null)
            ManifestLanguageEvidence(audio, subtitles)
        } catch (_: Exception) { ManifestLanguageEvidence() }
    }
}
