package com.example.service

import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.util.PayloadGenerator
import com.jcraft.jsch.SocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import okio.ByteString
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
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

    fun getVirtualSocket(): VirtualWebSocketSocket? = virtualWsSocket

    fun bindWebSocket(ws: WebSocket): VirtualWebSocketSocket {
        val socket = VirtualWebSocketSocket(ws, scope)
        this.virtualWsSocket = socket
        return socket
    }

    override fun createSocket(host: String?, port: Int): Socket {
        val targetHost = host ?: config.serverHost
        val targetPort = if (port > 0) port else config.serverPort

        when (config.mode) {
            TunnelMode.SSH_DIRECT -> {
                logCallback("Conectando TCP Directo a $targetHost:$targetPort...")
                val socket = Socket()
                socket.connect(InetSocketAddress(targetHost, targetPort), 15000)
                socket.tcpNoDelay = true
                return socket
            }

            TunnelMode.SSH_SSL -> {
                logCallback("Iniciando SSL/TLS con SNI '${config.sniHost.ifBlank { targetHost }}' a $targetHost:$targetPort...")
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket(targetHost, targetPort) as SSLSocket
                
                if (config.sniHost.isNotBlank()) {
                    val sslParams = SSLParameters()
                    sslParams.serverNames = listOf(SNIHostName(config.sniHost.trim()))
                    sslSocket.sslParameters = sslParams
                }
                
                sslSocket.startHandshake()
                logCallback("Handshake SSL completado con éxito.")
                return sslSocket
            }

            TunnelMode.SSH_PAYLOAD -> {
                val proxyHost = config.proxyHost.ifBlank { targetHost }
                val proxyPort = if (config.proxyPort > 0) config.proxyPort else 8080
                logCallback("Conectando a Proxy HTTP $proxyHost:$proxyPort...")
                
                val socket = Socket()
                socket.connect(InetSocketAddress(proxyHost, proxyPort), 15000)
                socket.tcpNoDelay = true
                
                logCallback("Inyectando Custom Payload HTTP...")
                val rawPayload = config.customPayload.ifBlank {
                    "[method] [host_port] [protocol][crlf]Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]"
                }
                val parsed = PayloadGenerator.parsePayload(rawPayload, targetHost, targetPort)
                
                val output = socket.getOutputStream()
                output.write(parsed.toByteArray(Charsets.ISO_8859_1))
                output.flush()
                
                logCallback("Payload enviado. Esperando respuesta del Proxy...")
                return socket
            }

            TunnelMode.SSH_WEBSOCKET, TunnelMode.SSH_WEBSOCKET_SSL -> {
                return virtualWsSocket ?: throw IllegalStateException("WebSocket aún no está abierto.")
            }

            TunnelMode.DIRECT_PROXY -> {
                val pHost = config.proxyHost.ifBlank { targetHost }
                val pPort = if (config.proxyPort > 0) config.proxyPort else 8080
                logCallback("Conectando a Proxy Directo $pHost:$pPort...")
                val socket = Socket()
                socket.connect(InetSocketAddress(pHost, pPort), 15000)
                return socket
            }
        }
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()

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
                    webSocket.send(ByteString.of(*arr))
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (len > 0) {
                        webSocket.send(ByteString.of(b, off, len))
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
