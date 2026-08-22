package com.example.service

import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.util.PayloadGenerator
import com.jcraft.jsch.SocketFactory
import kotlinx.coroutines.CoroutineScope
import okhttp3.WebSocket
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PushbackInputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLParameters
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

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

                // Enviar el payload completo sin alteraciones
                if (config.customPayload.isNotBlank()) {
                    val parsedPayload = PayloadGenerator.parsePayload(config.customPayload, targetHost, targetPort)
                    logCallback("Enviando Payload HTTP completo (${parsedPayload.length} caracteres)...")
                    val output = socket.getOutputStream()
                    output.write(parsedPayload.toByteArray(Charsets.ISO_8859_1))
                    output.flush()
                    logCallback("✓ Payload enviado al Host Frontal. Esperando respuesta...")
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
        }
    }

    private fun consumeHttpResponseIfNeeded(pushbackIn: PushbackInputStream, socket: Socket) {
        val checkBuf = ByteArray(7)
        val readBytes = pushbackIn.read(checkBuf, 0, 7)
        if (readBytes <= 0) return

        val prefix = String(checkBuf, 0, readBytes, Charsets.ISO_8859_1)
        if (prefix.startsWith("HTTP/", ignoreCase = true)) {
            // El proxy o frontal respondió con cabeceras HTTP. Leemos la línea de estado y los headers.
            val statusLineSb = StringBuilder(prefix)
            var b: Int
            while (pushbackIn.read().also { b = it } != -1) {
                statusLineSb.append(b.toChar())
                if (b == '\n'.code) break
            }
            val statusLine = statusLineSb.toString().trim()
            logCallback("Respuesta HTTP del Frontal: $statusLine")

            // Consumir el resto de las cabeceras hasta \r\n\r\n o \n\n
            var lineBreakCount = 0
            while (pushbackIn.read().also { b = it } != -1) {
                if (b == '\n'.code) {
                    lineBreakCount++
                    if (lineBreakCount >= 2) break
                } else if (b != '\r'.code) {
                    lineBreakCount = 0
                }
            }
            logCallback("✓ Encabezados HTTP procesados. Canal listo para autenticación SSH.")
        } else {
            // No es HTTP (por ejemplo, el servidor SSH ya envió directamente 'SSH-2.0...').
            // Reintroducimos los bytes leídos en el stream para que JSch los lea completos.
            pushbackIn.unread(checkBuf, 0, readBytes)
            logCallback("Canal TCP listo. Iniciando intercambio SSH...")
        }
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()

    class PayloadSocketWrapper(
        private val underlyingSocket: Socket,
        private val inStream: InputStream
    ) : Socket() {
        override fun getInputStream(): InputStream = inStream
        override fun getOutputStream(): OutputStream = underlyingSocket.getOutputStream()
        override fun close() = underlyingSocket.close()
        override fun isConnected(): Boolean = underlyingSocket.isConnected
        override fun isClosed(): Boolean = underlyingSocket.isClosed
        override fun setSoTimeout(timeout: Int) { underlyingSocket.soTimeout = timeout }
        override fun getSoTimeout(): Int = underlyingSocket.soTimeout
        override fun setTcpNoDelay(on: Boolean) { underlyingSocket.tcpNoDelay = on }
        override fun getTcpNoDelay(): Boolean = underlyingSocket.tcpNoDelay
    }

    class VirtualWebSocketSocket(
        private val webSocket: WebSocket,
        scope: CoroutineScope
    ) : Socket() {
        private val inPipe = PipedInputStream(65536)
        private val outPipeSink = PipedOutputStream(inPipe)
        private val bridge = WebSocketByteBridge(webSocket, scope)

        init {
            bridge.attachOutputStream(outPipeSink)
        }

        fun onIncomingBytes(bytes: ByteString) {
            bridge.onBinaryMessageReceived(bytes)
        }

        override fun getInputStream(): InputStream = inPipe

        override fun getOutputStream(): OutputStream {
            return object : OutputStream() {
                override fun write(b: Int) {
                    val arr = byteArrayOf(b.toByte())
                    webSocket.send(arr.toByteString(0, 1))
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (len > 0) {
                        webSocket.send(b.toByteString(off, len))
                    }
                }
            }
        }

        override fun close() {
            try { inPipe.close() } catch (_: Exception) {}
            try { outPipeSink.close() } catch (_: Exception) {}
            bridge.stop()
        }

        override fun isConnected(): Boolean = true
        override fun isClosed(): Boolean = false
    }
}

