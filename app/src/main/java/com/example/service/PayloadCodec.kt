package com.example.service

import java.nio.charset.Charset
import java.util.Locale

/** Expands the payload syntax without changing literal bytes unnecessarily. */
object PayloadCodec {
    fun expand(template: String, host: String, port: Int, userAgent: String): ByteArray =
        expandBlocks(template, host, port, userAgent)
            .fold(ByteArray(0)) { result, block -> result + block }

    /** Returns exact transmission blocks; [split] never becomes a literal byte. */
    fun expandBlocks(template: String, host: String, port: Int, userAgent: String): List<ByteArray> {
        val protocol = if (port == 443) "HTTP/1.0" else "HTTP/1.0"
        val replacements = mapOf(
            "[host]" to host,
            "[ssh]" to "$host:$port",
            "[host_port]" to "$host:$port",
            "[port]" to port.toString(),
            "[method]" to "CONNECT",
            "[protocol]" to protocol,
            "[ua]" to userAgent,
            "[raw]" to "CONNECT $host:$port HTTP/1.0\\r\\n\\r\\n",
            "[real_raw]" to "CONNECT $host:$port HTTP/1.0\\r\\n\\r\\n",
            "[netData]" to "CONNECT $host:$port HTTP/1.0",
            "[realData]" to "CONNECT $host:$port HTTP/1.0",
            "[auth]" to ""
        )
        return template.split(Regex("\\[split(?:_(?:delay|instant))?\\]", RegexOption.IGNORE_CASE)).map { raw ->
            var value = raw
                .replace("[crlf*2]", "\\r\\n\\r\\n", ignoreCase = true)
                .replace("[crlf]", "\\r\\n", ignoreCase = true)
                .replace("[lfcr]", "\\n\\r", ignoreCase = true)
                .replace("[lf]", "\\n", ignoreCase = true)
                .replace("[cr]", "\\r", ignoreCase = true)
                .replace("\\\\r", "\\r")
                .replace("\\\\n", "\\n")
            replacements.forEach { (key, replacement) -> value = value.replace(key, replacement, ignoreCase = true) }
            value.toByteArray(Charset.forName("ISO-8859-1"))
        }.filter { it.isNotEmpty() }
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
