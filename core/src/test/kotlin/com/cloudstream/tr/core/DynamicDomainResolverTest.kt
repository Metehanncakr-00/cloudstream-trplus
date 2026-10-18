package com.cloudstream.tr.core

import com.cloudstream.tr.core.network.DomainConfig
import com.cloudstream.tr.core.network.DomainDocument
import com.cloudstream.tr.core.network.DynamicDomainResolver
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.*
import org.junit.Test

class DynamicDomainResolverTest {
    @Test
    fun wrappedRepositorySchemaUsesCanonicalUrl() {
        val document = jacksonObjectMapper().readValue<DomainDocument>("""
            {"version": 1, "providers": {"YTS": {
                "canonical": "https://yts.gg/", "allowedHosts": ["yts.gg", "yts.mx"],
                "expectedMarkers": ["YTS"], "status": "active"
            }}}
        """.trimIndent())
        assertEquals(listOf("https://yts.gg"), DynamicDomainResolver.validatedSnapshot(document)?.get("YTS"))
    }

    @Test
    fun canonicalAndMirrorsAreNormalizedAndDeduplicated() {
        val document = DomainDocument(mapOf("YTS" to DomainConfig(
            allowedHosts = listOf("YTS.GG", "yts.mx"),
            canonical = " HTTPS://YTS.GG:443/ ",
            mirrors = listOf("https://yts.gg", "https://yts.mx/")
        )))
        val endpoints = DynamicDomainResolver.validatedSnapshot(document)?.get("YTS")
        assertEquals(listOf("https://yts.gg", "https://yts.mx"), endpoints)
        assertEquals("https://yts.mx", DynamicDomainResolver.selectEndpoint(endpoints, 1, "https://default.test"))
        assertEquals("https://yts.gg", DynamicDomainResolver.selectEndpoint(endpoints, 2, "https://default.test"))
    }

    @Test
    fun allowlistIsNeverUsedAsAListOfEndpoints() {
        val document = DomainDocument(mapOf("YTS" to DomainConfig(allowedHosts = listOf("yts.gg"))))
        assertNull(DynamicDomainResolver.validatedSnapshot(document))
        assertEquals("https://default.test", DynamicDomainResolver.selectEndpoint(null, 0, "https://default.test"))
        assertEquals("https://default.test", DynamicDomainResolver.selectEndpoint(emptyList(), 0, "https://default.test"))
    }

    @Test
    fun unsafeAndEmptySnapshotsAreRejected() {
        val unsafe = listOf(
            "yts.gg", "http://yts.gg", "https://yts.gg.evil.test", "https://evil.test/yts.gg",
            "https://user:password@yts.gg", "https://yts.gg:8080", "https://yts.gg?redirect=evil",
            "https://yts.gg#fragment", "not a url"
        )
        for (url in unsafe) {
            val document = DomainDocument(mapOf("YTS" to DomainConfig(
                allowedHosts = listOf("yts.gg"), canonical = url
            )))
            assertNull(url, DynamicDomainResolver.validatedSnapshot(document))
        }
        assertNull(DynamicDomainResolver.validatedSnapshot(DomainDocument()))
        assertNull(DynamicDomainResolver.validatedSnapshot(DomainDocument(emptyMap())))
    }
}
