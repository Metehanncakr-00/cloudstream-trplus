package com.cloudstream.tr.one.dedup

import com.cloudstream.tr.one.models.CanonicalMedia
import com.cloudstream.tr.one.models.ProviderListing
import com.lagradost.cloudstream3.TvType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

object TRCanonicalEngine {

    private val TR_LOCALE = Locale("tr", "TR")

    private val NOISE_WORDS = listOf(
        "türkçe dublaj",
        "türkçe altyazılı",
        "turkce dublaj",
        "turkce altyazili",
        "türkçe dublajlı",
        "turkce dublajli",
        "altyazılı",
        "altyazili",
        "dublaj",
        "full hd",
        "tek parça",
        "tek parca",
        "sinema çekimi",
        "sinema cekimi",
        "1080p",
        "720p",
        "4k uhd",
        "4k",
        "uhd",
        "izle full",
        "filmi izle",
        "filmini izle",
        "dizisi izle",
        "dizisini izle",
        "izleyin",
        "izle",
        "filmi",
        "dizisi"
    )

    private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

    /**
     * Başlığı temizler, gürültü kelimeleri ve noktalama işaretlerini ayıklar.
     */
    fun normalizeTitle(raw: String): String {
        if (raw.isBlank()) return ""
        var cleaned = raw.lowercase(TR_LOCALE)

        // Çıkış yıllarını temizle
        cleaned = cleaned.replace(YEAR_REGEX, " ")

        // Gürültü kelimeleri temizle
        for (noise in NOISE_WORDS) {
            cleaned = cleaned.replace(noise, " ")
        }

        // Ayraçları boşlukla değiştir (parantez içindeki yabancı başlığı koru)
        cleaned = cleaned
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('-', ' ')
            .replace('–', ' ')
            .replace('/', ' ')
            .replace(':', ' ')

        // Harf, rakam ve boşluk dışındaki karakterleri boşluk yap
        cleaned = cleaned.replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")

        // Fazla boşlukları tek boşluğa indir
        return cleaned.trim().replace(Regex("""\s+"""), " ")
    }

    /**
     * Başlıktan veya URL'den 4 haneli çıkış yılını tespit eder.
     */
    fun extractYear(raw: String): Int? {
        val match = YEAR_REGEX.find(raw) ?: return null
        return match.value.toIntOrNull()
    }

    /**
     * Türkçe karakterleri ASCII benzerlerine dönüştürür (slug karşılaştırması için).
     */
    fun toAsciiSlug(text: String): String {
        return text.lowercase(TR_LOCALE)
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('i', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("""[^a-z0-9\s]"""), "")
            .trim()
            .replace(Regex("""\s+"""), "-")
    }

    /**
     * İki metin arasındaki Levenshtein benzerliğini hesaplar (0.0 - 1.0).
     */
    fun computeSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            val c1 = s1[i - 1]
            for (j in 1..len2) {
                val c2 = s2[j - 1]
                val cost = if (c1 == c2) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // silme
                    dp[i][j - 1] + 1,       // ekleme
                    dp[i - 1][j - 1] + cost // değiştirme
                )
            }
        }

        val distance = dp[len1][len2]
        val maxLen = max(len1, len2)
        return (maxLen - distance).toDouble() / maxLen.toDouble()
    }

    /**
     * İki kaynağın aynı içeriğe ait olup olmadığını doğrular.
     * Film ve Dizi ASLA birleştirilemez.
     * Farklı yıllardaki yeniden çevrimler ASLA birleştirilemez.
     */
    fun canMerge(a: ProviderListing, b: ProviderListing): Boolean {
        // 1. Tür uyumu: Film ve Dizi asla birleşemez
        if (a.mediaType != b.mediaType) return false

        // 2. Yıl kontrolü: İki tarafta da yıl varsa ve fark 1'den büyükse ASLA birleştirme (Remake koruması)
        if (a.year != null && b.year != null) {
            if (abs(a.year - b.year) > 1) return false
        }

        // 3. Başlık normalizasyonu ve benzerlik
        val normA = normalizeTitle(a.title)
        val normB = normalizeTitle(b.title)

        if (normA == normB && normA.isNotBlank()) return true

        val slugA = toAsciiSlug(normA)
        val slugB = toAsciiSlug(normB)
        if (slugA == slugB && slugA.isNotBlank()) return true

        // 4. Kelime / Token kümesi örtüşmesi (Örn: "Yıldızlararası Interstellar" ile "Yıldızlararası")
        val tokensA = slugA.split("-").filter { it.length >= 2 }.toSet()
        val tokensB = slugB.split("-").filter { it.length >= 2 }.toSet()

        if (tokensA.isNotEmpty() && tokensB.isNotEmpty()) {
            val intersection = tokensA.intersect(tokensB)
            val union = tokensA.union(tokensB)
            val jaccard = intersection.size.toDouble() / union.size.toDouble()

            // Eğer biri diğerinin alt kümesiyse ve ortak kelime varsa
            val isSubset = (tokensA.containsAll(tokensB) || tokensB.containsAll(tokensA)) && intersection.isNotEmpty()
            if (isSubset) {
                // Yıllar uyuşuyorsa veya en az birinin yılı boşsa birleştir
                if (a.year != null && b.year != null) {
                    if (abs(a.year - b.year) <= 1) return true
                } else if (intersection.size >= 2) {
                    return true
                }
            }

            if (jaccard >= 0.5) {
                return true
            }
        }

        // 5. Levenshtein benzerlik eşiği (en az 0.85 benzerlik ve yıl uyumu)
        val sim = computeSimilarity(slugA, slugB)
        if (sim >= 0.85) {
            if (sim < 0.95 && a.year != null && b.year != null && a.year != b.year) {
                return false
            }
            return true
        }

        return false
    }

    /**
     * Sağlayıcılardan gelen ham listeyi tekil CanonicalMedia kartları halinde birleştirir.
     */
    fun mergeListings(listings: List<ProviderListing>): List<CanonicalMedia> {
        val canonicalList = mutableListOf<CanonicalMedia>()

        for (listing in listings) {
            var matched: CanonicalMedia? = null

            for (canonical in canonicalList) {
                val rep = canonical.listings.firstOrNull() ?: continue
                if (canMerge(rep, listing)) {
                    matched = canonical
                    break
                }
            }

            if (matched != null) {
                matched.listings.add(listing)
                if (matched.year == null && listing.year != null) {
                    // Yılı zenginleştir
                }
            } else {
                val cleanTitle = normalizeTitle(listing.title).ifBlank { listing.title }
                val displayTitle = listing.title.split(Regex("""[:\-–]""")).firstOrNull()?.trim()
                    ?.ifBlank { null } ?: cleanTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(TR_LOCALE) else it.toString() }

                val year = listing.year ?: extractYear(listing.title)
                val slug = toAsciiSlug(cleanTitle)
                val canonicalId = "$slug-${year ?: "na"}-${listing.mediaType.name.lowercase()}"

                val canonical = CanonicalMedia(
                    canonicalId = canonicalId,
                    title = displayTitle,
                    mediaType = listing.mediaType,
                    year = year,
                    posterUrl = listing.posterUrl,
                    listings = mutableListOf(listing)
                )
                canonicalList.add(canonical)
            }
        }

        return canonicalList
    }
}
