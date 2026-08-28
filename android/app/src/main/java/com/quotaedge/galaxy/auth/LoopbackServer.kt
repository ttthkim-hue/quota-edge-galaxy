package com.quotaedge.galaxy.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class OAuthCallback(
    val code: String?,
    val state: String?,
    val error: String?,
)

/**
 * Tiny loopback HTTP server for OAuth redirect (RFC 8252).
 * Chrome Custom Tabs redirects to http://127.0.0.1:port/path and hits this server.
 */
class LoopbackServer(
    private val port: Int,
    private val pathPrefix: String = "/callback",
) {
    private var server: ServerSocket? = null

    fun redirectUri(host: String = "127.0.0.1"): String =
        "http://$host:$port$pathPrefix"

    suspend fun awaitCallback(timeoutMs: Long = 180_000): OAuthCallback = withContext(Dispatchers.IO) {
        withTimeout(timeoutMs) {
            val sock = ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))
            server = sock
            sock.soTimeout = timeoutMs.toInt()
            sock.use { ss ->
                val client = ss.accept()
                client.use { c ->
                    val reader = BufferedReader(InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))
                    val requestLine = reader.readLine() ?: ""
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) break
                    }
                    val uri = requestLine.split(" ").getOrNull(1).orEmpty()
                    val result = parseQuery(uri)
                    val html = if (result.error != null) {
                        """<!doctype html><html><body style="font-family:sans-serif;padding:24px;background:#111;color:#fff">
                        <h2>Login failed</h2><p>${result.error}</p><p>Quota Edge로 돌아가세요.</p></body></html>"""
                    } else {
                        """<!doctype html><html><body style="font-family:sans-serif;padding:24px;background:#111;color:#fff">
                        <h2>연동 완료</h2><p>이 창을 닫고 Quota Edge로 돌아가세요.</p></body></html>"""
                    }
                    val bytes = html.toByteArray(StandardCharsets.UTF_8)
                    PrintWriter(c.getOutputStream(), false, StandardCharsets.UTF_8).use { out ->
                        out.print("HTTP/1.1 200 OK\r\n")
                        out.print("Content-Type: text/html; charset=utf-8\r\n")
                        out.print("Content-Length: ${bytes.size}\r\n")
                        out.print("Connection: close\r\n\r\n")
                        out.flush()
                    }
                    c.getOutputStream().write(bytes)
                    c.getOutputStream().flush()
                    result
                }
            }
        }
    }

    fun close() {
        runCatching { server?.close() }
        server = null
    }

    private fun parseQuery(uri: String): OAuthCallback {
        val q = uri.substringAfter('?', "")
        val map = mutableMapOf<String, String>()
        q.split('&').forEach { part ->
            if (part.isBlank()) return@forEach
            val k = URLDecoder.decode(part.substringBefore('='), "UTF-8")
            val v = URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
            map[k] = v
        }
        return OAuthCallback(
            code = map["code"],
            state = map["state"],
            error = map["error"] ?: map["error_description"],
        )
    }
}
