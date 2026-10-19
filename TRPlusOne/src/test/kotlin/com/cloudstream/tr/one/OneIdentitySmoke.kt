package com.cloudstream.tr.one

/** No Android/JUnit dependency; run with plain kotlinc + kotlin. */
object OneIdentitySmoke {
    @JvmStatic fun main(args: Array<String>) {
        val first = ProviderListing("A", "https://a.example/i", "Interstellar", 2014, OneKind.MOVIE)
        val second = ProviderListing("B", "https://b.example/i", "Interstellar", 2014, OneKind.MOVIE)
        check(OneIdentity.key(first) == OneIdentity.key(second))
        val merged = CanonicalEngine.merge(emptyList(), listOf(first, second))
        check(merged.size == 1 && merged.single().listings.size == 2)
        check(CanonicalEngine.merge(emptyList(), listOf(first, first.copy(year = 2015))).size == 2)
        check(CanonicalEngine.merge(emptyList(), listOf(first, first.copy(kind = OneKind.SERIES))).size == 2)
        val withoutYear = first.copy(year = null)
        check(CanonicalEngine.merge(emptyList(), listOf(withoutYear, withoutYear.copy(providerId="B", url="https://b.example/i"))).size == 2)
        println("PASS: 5 canonical identity checks")
    }
}
