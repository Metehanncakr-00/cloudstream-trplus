package com.cloudstream.tr.hub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class HubTorrentTransportTest {
    @Test fun realLoopbackPreloadAndRangeReadsPrepareSelectedFile() = runBlocking {
        ServerSocket(0, 3, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val requests = mutableListOf<List<String>>()
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            thread(isDaemon = true) {
                try {
                    repeat(3) { index ->
                        server.accept().use { socket ->
                            socket.soTimeout = 5000
                            val reader = socket.getInputStream().bufferedReader()
                            val headers = mutableListOf<String>()
                            while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; headers.add(line) }
                            requests.add(headers)
                            val body = if (index == 0)
                                """{"file_stats":[{"id":2,"path":"BDMV/STREAM/00001.m2ts","length":10}]}""".toByteArray()
                            else ByteArray(10) { 7 }
                            val response = if (index == 0) "200 OK" else "206 Partial Content"
                            val range = if (index == 0) "" else "Content-Range: bytes 0-9/10\r\n"
                            socket.getOutputStream().write(("HTTP/1.1 $response\r\n${range}Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            socket.getOutputStream().write(body)
                        }
                    }
                } catch (e: Throwable) { failure = e } finally { done.countDown() }
            }
            awaitTorrentReadiness("http://127.0.0.1:${server.localPort}/stream/file?link=test&index=2&play",
                budgetMs = 5000, diagnostic = {})
            assertTrue(done.await(2, TimeUnit.SECONDS))
            failure?.let { throw AssertionError(it) }
            assertTrue(requests[0][0].contains("preload="))
            assertTrue(requests[0][0].contains("stat="))
            assertTrue(requests.all { it[0].contains("index=2") })
            assertTrue(requests[1].any { it.equals("Range: bytes=0-65535", true) })
            assertTrue(requests[2].any { it.equals("Range: bytes=0-9", true) })
            assertTrue(requests.all { it.any { header -> header.equals("Accept-Encoding: identity", true) } })
        }
    }

    @Test fun realTransportReturnsRedirectWithoutFollowingRemoteLocation() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 3000
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: http://example.invalid/video\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
            val response = LocalTorrentTransport.get("http://127.0.0.1:${server.localPort}/stream/file", null, 2000, 20)
            assertEquals(302, response.code)
        }
    }

    @Test fun cancellingDuringResponseBodyReadClosesSocket() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 3000
            val closed = CountDownLatch(1)
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 206 Partial Content\r\nContent-Length: 65536\r\nContent-Range: bytes 0-65535/200000\r\n\r\nx".toByteArray())
                    socket.getOutputStream().flush()
                    if (socket.getInputStream().read() == -1) closed.countDown()
                }
            }
            try {
                withTimeout(400) {
                    LocalTorrentTransport.get("http://127.0.0.1:${server.localPort}/stream/file", "bytes=0-65535", 10_000, 65_536)
                }
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertTrue("HTTP body read must be cancelled", closed.await(2, TimeUnit.SECONDS))
        }
    }
}
