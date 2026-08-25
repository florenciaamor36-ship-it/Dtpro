package com.example.service

import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.jcraft.jsch.SocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okio.ByteString
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PushbackInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class VirtualWebSocketSocket(
    private val webSocket: WebSocket,
    private val scope: CoroutineScope
) : Socket() {
    private val inPipe = PipedInputStream(65536)
    private val outPipe = PipedOutputStream(inPipe)

    private val virtualOutputStream = object : OutputStream() {
        override fun write(b: Int) {
            val arr = byteArrayOf(b.toByte())
            webSocket.send(ByteString.of(*arr))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len > 0) {
                val copy = b.copyOfRange(off, off + len)
                webSocket.send(ByteString.of(*copy))
            }
        }
    }

    fun onIncomingBytes(bytes: ByteString) {
        try {
            outPipe.write(bytes.toByteArray())
            outPipe.flush()
        } catch (_: Exception) {}
    }

    override fun getInputStream(): InputStream = inPipe
    override fun getOutputStream(): OutputStream = virtualOutputStream
    override fun isConnected(): Boolean = true
    override fun isClosed(): Boolean = false
    override fun close() {
        try { inPipe.close() } catch (_: Exception) {}
        try { outPipe.close() } catch (_: Exception) {}
        try { webSocket.close(1000, "Closed") } catch (_: Exception) {}
    }
}

class CustomSshSocketFactory(
    private val config: TunnelConfig,
    private val scope: CoroutineScope,
    private val logCallback: (String) -> Unit
) : SocketFactory {

    private var virtualWsSocket: VirtualWebSocketSocket? = null
    private var lastCreatedSocket: Socket? = null

    fun getVirtualSocket(): VirtualWebSocketSocket? = virtualWsSocket

    fun bindWebSocket(ws: WebSocket): VirtualWebSocketSocket {
        val socket = VirtualWebSocketSocket(ws, scope)
        this.virtualWsSocket = socket
        return socket
    }

    override fun createSocket(host: String?, port: Int): Socket {
        val targetHost = host?.ifBlank { null } ?: config.serverHost
        val targetPort = if (port > 0) port else config.serverPort

        when (config.mode) {
            TunnelMode.SSH_DIRECT -> {
                if (targetHost.isBlank()) {
                    throw IllegalArgumentException("El host del servidor SSH no puede estar vacío.")
                }
                logCallback("Conectando TCP Directo a $targetHost:$targetPort...")
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.soTimeout = 20000
                socket.connect(InetSocketAddress(targetHost, targetPort), 15000)
                lastCreatedSocket = socket
                return socket
            }

            TunnelMode.SSH_SSL -> {
                if (targetHost.isBlank()) {
                    throw IllegalArgumentException("El host del servidor SSH no puede estar vacío.")
                }
                val effectiveSni = config.sniHost.trim().ifBlank { targetHost }
                logCallback("Iniciando conexión SSL/TLS con SNI '$effectiveSni' a $targetHost:$targetPort...")
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket(targetHost, targetPort) as SSLSocket
                
                if (effectiveSni.isNotBlank()) {
                    val sslParams = SSLParameters()
                    sslParams.serverNames = listOf(SNIHostName(effectiveSni))
                    sslSocket.sslParameters = sslParams
                }
                
                sslSocket.soTimeout = 20000
                sslSocket.startHandshake()
                logCallback("✓ Handshake SSL completado con éxito.")
                lastCreatedSocket = sslSocket
                return sslSocket
            }

            TunnelMode.SSH_PAYLOAD -> {
                // Modo SSH + HTTP Payload: Socket TCP directo al Host Frontal / Proxy (normalmente puerto 80)
                val frontHost = config.proxyHost.trim().ifBlank { targetHost }
                val frontPort = if (config.proxyPort > 0) config.proxyPort else 80

                if (frontHost.isBlank()) {
                    throw IllegalArgumentException("El Host frontal o Proxy no puede estar vacío.")
                }
                if (targetHost.isBlank()) {
                    throw IllegalArgumentException("El Host real del servidor SSH no puede estar vacío.")
                }

                logCallback("Conectando socket TCP al Host Frontal/Proxy: $frontHost:$frontPort...")
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.soTimeout = 20000
                socket.connect(InetSocketAddress(frontHost, frontPort), 15000)
                logCallback("✓ Socket TCP conectado a $frontHost:$frontPort")

                // Enviar payload con soporte de inyección partida [split] y retardos [delay_split]
                if (config.customPayload.isNotBlank()) {
                    val payloadBlocks = PayloadCodec.expandBlocks(
                        config.customPayload, targetHost, targetPort, "Dtpro/1.0"
                    )
                    val output = socket.getOutputStream()
                    if (payloadBlocks.size > 1) {
                        logCallback("Iniciando inyección partida (${payloadBlocks.size} bloques)...")
                    }
                    for ((index, block) in payloadBlocks.withIndex()) {
                        logCallback("Enviando bloque ${index + 1}/${payloadBlocks.size} (${block.size} bytes)...")
                        output.write(block)
                        output.flush()
                    }
                    logCallback("✓ Payload completado y enviado al Host Frontal. Esperando respuesta...")
                }

                // Leer y verificar respuesta inicial (Consumir encabezados HTTP 200/101 o pasar stream limpio si es banner SSH)
                val pushbackIn = PushbackInputStream(socket.getInputStream(), 4096)
                consumeHttpResponseIfNeeded(pushbackIn, socket)

                val wrappedSocket = PayloadSocketWrapper(socket, pushbackIn)
                lastCreatedSocket = wrappedSocket
                return wrappedSocket
            }

            TunnelMode.SSH_WEBSOCKET, TunnelMode.SSH_WEBSOCKET_SSL -> {
                return virtualWsSocket ?: throw IllegalStateException("Transporte WebSocket no inicializado.")
            }

            TunnelMode.DIRECT_PROXY -> {
                val pHost = config.proxyHost.trim().ifBlank { targetHost }
                val pPort = if (config.proxyPort > 0) config.proxyPort else 8080
                if (pHost.isBlank()) {
                    throw IllegalArgumentException("El Host del Proxy no puede estar vacío.")
                }
                logCallback("Conectando a Proxy Directo $pHost:$pPort...")
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.soTimeout = 20000
                socket.connect(InetSocketAddress(pHost, pPort), 15000)
                lastCreatedSocket = socket
                return socket
            }

            else -> {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.soTimeout = 20000
                socket.connect(InetSocketAddress(targetHost, targetPort), 15000)
                lastCreatedSocket = socket
                return socket
            }
        }
    }

    private fun consumeHttpResponseIfNeeded(pushbackIn: PushbackInputStream, socket: Socket) {
        val checkBuf = ByteArray(7)
        val readBytes = pushbackIn.read(checkBuf, 0, 7)
        if (readBytes <= 0) return

        val prefix = String(checkBuf, 0, readBytes, Charsets.ISO_8859_1)
        if (prefix.startsWith("HTTP/", ignoreCase = true)) {
            logCallback("Respuesta HTTP detectada del Proxy/Servidor. Consumiendo cabeceras...")
            // Consumir hasta encontrar CRLF CRLF (\r\n\r\n o \n\n)
            var state = 0
            while (true) {
                val b = pushbackIn.read()
                if (b == -1) break
                if (b == '\r'.code) {
                    if (state == 0 || state == 2) state++ else state = 1
                } else if (b == '\n'.code) {
                    if (state == 1) state = 2
                    else if (state == 3 || state == 0) break
                    else state = 0
                } else {
                    state = 0
                }
            }
            logCallback("✓ Cabeceras HTTP consumidas. Canal directo establecido para SSH.")
        } else {
            // No es HTTP (ej: Banner SSH-2.0 directo), devolver los bytes leídos al stream
            pushbackIn.unread(checkBuf, 0, readBytes)
            logCallback("Respuesta de socket transparente (Banner SSH directo).")
        }
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
}

/**
 * Wrapper de Socket para conservar el PushbackInputStream una vez consumidas las cabeceras HTTP
 */
class PayloadSocketWrapper(
    private val rawSocket: Socket,
    private val pushbackInputStream: PushbackInputStream
) : Socket() {
    override fun getInputStream(): InputStream = pushbackInputStream
    override fun getOutputStream(): OutputStream = rawSocket.getOutputStream()
    override fun isConnected(): Boolean = rawSocket.isConnected
    override fun isClosed(): Boolean = rawSocket.isClosed
    override fun close() = rawSocket.close()
    override fun getInetAddress(): InetAddress = rawSocket.inetAddress
    override fun getPort(): Int = rawSocket.port
    override fun getLocalPort(): Int = rawSocket.localPort
}
