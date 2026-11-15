package com.syam.desitv.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun get(url: String, timeoutMs: Int = 30_000): ByteArray {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Config.USER_AGENT)
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code for $current")
                return conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects for $url")
    }
}
