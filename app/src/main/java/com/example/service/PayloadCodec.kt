package com.example.service

import java.nio.charset.Charset
import java.util.Locale

/** Expands the payload syntax without changing literal bytes unnecessarily. */
object PayloadCodec {
    fun expand(template: String, host: String, port: Int, userAgent: String): ByteArray {
        val protocol = if (port == 443) "HTTPS" else "HTTP/1.1"
        val replacements = mapOf(
            "[host]" to host,
            "[host_port]" to "$host:$port",
            "[port]" to port.toString(),
            "[method]" to "GET",
            "[protocol]" to protocol,
            "[ua]" to userAgent
        )
        var value = template
            .replace("[crlf]", "\r\n", ignoreCase = true)
            .replace("[lf]", "\n", ignoreCase = true)
            .replace("[cr]", "\r", ignoreCase = true)
        replacements.forEach { (key, replacement) ->
            value = value.replace(key, replacement, ignoreCase = true)
        }
        // [split] is a transmission boundary, not part of the request bytes.
        value = value.replace("[split]", "", ignoreCase = true)
        return value.toByteArray(Charset.forName("ISO-8859-1"))
    }

    fun parseStatus(response: ByteArray): HttpStatus {
        val headerEnd = response.indexOfHeaderEnd()
        if (headerEnd < 0) return HttpStatus.Incomplete
        val header = response.copyOfRange(0, headerEnd).toString(Charsets.ISO_8859_1)
        val firstLine = header.lineSequence().firstOrNull()?.trim() ?: return HttpStatus.Invalid
        val match = Regex("^HTTP/\\d(?:\\.\\d)?\\s+(\\d{3})(?:\\s|$)", RegexOption.IGNORE_CASE).find(firstLine)
            ?: return HttpStatus.Invalid
        return when (val code = match.groupValues[1].toInt()) {
            101 -> HttpStatus.Upgrade(code)
            in 400..599 -> HttpStatus.Rejected(code)
            else -> HttpStatus.Other(code)
        }
    }

    private fun ByteArray.indexOfHeaderEnd(): Int {
        for (i in 0 until size - 3) {
            if (this[i] == '\r'.code.toByte() && this[i + 1] == '\n'.code.toByte() &&
                this[i + 2] == '\r'.code.toByte() && this[i + 3] == '\n'.code.toByte()) return i + 4
        }
        for (i in 0 until size - 1) {
            if (this[i] == '\n'.code.toByte() && this[i + 1] == '\n'.code.toByte()) return i + 2
        }
        return -1
    }
}

sealed interface HttpStatus {
    data object Incomplete : HttpStatus
    data object Invalid : HttpStatus
    data class Upgrade(val code: Int) : HttpStatus
    data class Rejected(val code: Int) : HttpStatus
    data class Other(val code: Int) : HttpStatus
}
