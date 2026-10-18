package com.cloudstream.tr.hub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

class HubSourceResolutionTest {
    @Test fun fast4kSourceIsPublishedBeforeSlowProviderFinishes() = runTest {
        val releaseSlow = CompletableDeferred<Unit>()
        val emitted = mutableListOf<String>()
        val job = async {
            resolveHubSourcesProgressively(
                resolvers = listOf(
                    { releaseSlow.await(); listOf("720p-YTS") },
                    { listOf("2160p-BluRay") }
                ),
                onBatch = { emitted.addAll(it) }
            )
        }
        runCurrent()
        assertEquals(listOf("2160p-BluRay"), emitted)
        releaseSlow.complete(Unit)
        job.await()
        assertEquals(listOf("2160p-BluRay", "720p-YTS"), emitted)
    }

    @Test fun failedSourceDoesNotSuppressHealthyOrigin() = runTest {
        val emitted = mutableListOf<String>()
        val errors = mutableListOf<String>()
        resolveHubSourcesProgressively<String>(
            resolvers = listOf(
                { throw IllegalStateException("origin unavailable") },
                { listOf("2160p") }
            ),
            onBatch = { emitted.addAll(it) },
            onFailure = { errors.add(it.javaClass.simpleName) }
        )
        assertEquals(listOf("2160p"), emitted)
        assertEquals(listOf("IllegalStateException"), errors)
    }

    @Test fun cancellationIsNotConvertedToEmptySuccess() = runTest {
        try {
            resolveHubSourcesProgressively<String>(
                resolvers = listOf({ throw CancellationException("player exited") }),
                onBatch = { fail("Must not emit") }
            )
            fail("Cancellation must propagate")
        } catch (error: CancellationException) {
            assertEquals("player exited", error.message)
        }
    }
}
