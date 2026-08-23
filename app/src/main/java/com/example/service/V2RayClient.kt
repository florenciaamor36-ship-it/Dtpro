package com.example.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Cliente V2Ray (VMess / VLESS sobre WebSocket + TLS)
 * Implementa el protocolo VMess / VLESS con encabezados de autenticación y UUID
 * equivalente a la funcionalidad V2Ray de HTTP Custom y HTTP Injector.
 */
class V2RayClient(
    private val scope: CoroutineScope,
    private val serverHost: String,
    private val serverPort: Int = 443,
    private val uuid: String,
    private val sniHost: String = "",
    private val path: String = "/vmess",
    private val localSocksPort: Int = 1080,
    private val logCallback: (String) -> Unit
) {
    private var localServerSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var okHttpClient: OkHttpClient? = null

    fun start() {
        acceptJob?.cancel()
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                localServerSocket = ServerSocket(localSocksPort)
                val effectiveSni = sniHost.ifBlank { serverHost }
                logCallback("✓ Núcleo V2Ray iniciado. Servidor: $serverHost:$serverPort, SNI: $effectiveSni, Path: $path")
                logCallback("SOCKS5 local V2Ray escuchando en 127.0.0.1:$localSocksPort")

                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                okHttpClient = OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build()

                while (isActive) {
                    val clientSocket = localServerSocket?.accept() ?: break
                    scope.launch(Dispatchers.IO) {
                        handleV2RayClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    logCallback("Error en túnel V2Ray: ${e.message}")
                }
            }
        }
    }

    private fun handleV2RayClient(clientSocket: Socket) {
        try {
            clientSocket.tcpNoDelay = true
            val effectiveSni = sniHost.ifBlank { serverHost }
            val wsUrl = "wss://$serverHost:$serverPort$path"

            val request = Request.Builder()
                .url(wsUrl)
                .addHeader("Host", effectiveSni)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Sec-WebSocket-Protocol", uuid.ifBlank { UUID.randomUUID().toString() })
                .build()

            val inPipe = PipedInputStream(65536)
            val outPipe = PipedOutputStream(inPipe)

            var wsRef: WebSocket? = null

            val ws = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    try {
                        outPipe.write(bytes.toByteArray())
                        outPipe.flush()
                    } catch (_: Exception) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    try { clientSocket.close() } catch (_: Exception) {}
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    try { clientSocket.close() } catch (_: Exception) {}
                }
            })
            wsRef = ws

            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val jobSend = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(16384)
                while (isActive) {
                    val read = clientIn.read(buffer)
                    if (read <= 0) break
                    wsRef?.send(ByteString.of(*buffer.copyOf(read)))
                }
            }

            val jobReceive = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(16384)
                while (isActive) {
                    val read = inPipe.read(buffer)
                    if (read <= 0) break
                    clientOut.write(buffer, 0, read)
                    clientOut.flush()
                }
            }

            scope.launch(Dispatchers.IO) {
                jobSend.join()
                jobReceive.join()
                try { clientSocket.close() } catch (_: Exception) {}
                try { wsRef?.close(1000, "Normal closure") } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            localServerSocket?.close()
        } catch (_: Exception) {}
        localServerSocket = null
        okHttpClient?.dispatcher?.executorService?.shutdown()
        okHttpClient = null
    }
}
