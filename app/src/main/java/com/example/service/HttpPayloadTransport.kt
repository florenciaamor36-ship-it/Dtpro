package com.example.service

import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/** Opens a persistent TCP/TLS connection, writes Payload blocks, and gates SSH on HTTP status. */
class HttpPayloadTransport(
    private val timeoutMillis: Int = 10_000,
    private val readLimit: Int = 64 * 1024
) : AutoCloseable {
    private var socket: Socket? = null

    fun open(
        connectHost: String,
        connectPort: Int,
        payloadHost: String,
        payloadPort: Int,
        payload: String,
        userAgent: String = "Dtpro/1.0",
        tls: Boolean = false
    ): Socket {
        close()
        val raw = if (tls) SSLSocketFactory.getDefault().createSocket() else Socket()
        raw.soTimeout = timeoutMillis
        raw.tcpNoDelay = true
        raw.connect(InetSocketAddress(connectHost, connectPort), timeoutMillis)
        if (tls) (raw as? javax.net.ssl.SSLSocket)?.startHandshake()
        val blocks = PayloadCodec.expandBlocks(payload, payloadHost, payloadPort, userAgent)
        val output = raw.getOutputStream()
        for (block in blocks) {
            output.write(block)
            output.flush()
        }
        val input = raw.getInputStream()
        val response = readHeaders(input)
        when (val status = PayloadCodec.parseStatus(response)) {
            is HttpStatus.Upgrade -> Unit
            is HttpStatus.Rejected -> throw IllegalStateException("HTTP rechazó el Payload: ${status.code}")
            HttpStatus.Incomplete -> throw IllegalStateException("Respuesta HTTP incompleta")
            HttpStatus.Invalid -> throw IllegalStateException("Respuesta HTTP inválida")
            is HttpStatus.Other -> throw IllegalStateException("HTTP no autorizó el upgrade: ${status.code}")
        }
        socket = raw
        return raw
    }

    private fun readHeaders(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(readLimit)
        var count = 0
        while (count < buffer.size) {
            val next = input.read()
            if (next < 0) break
            buffer[count++] = next.toByte()
            if (count >= 4 && buffer.copyOf(count).indexOfHeaderEnd() >= 0) return buffer.copyOf(count)
        }
        return buffer.copyOf(count)
    }

    override fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun ByteArray.indexOfHeaderEnd(): Int {
        for (i in 0 until size - 3) {
            if (this[i] == 13.toByte() && this[i + 1] == 10.toByte() && this[i + 2] == 13.toByte() && this[i + 3] == 10.toByte()) return i
        }
        return -1
    }
}
