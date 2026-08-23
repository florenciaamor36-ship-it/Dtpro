package com.example.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor Hotshare / Tethering (Compartir VPN vía WiFi)
 * Permite que otros dispositivos (PC, consolas, celulares) conectados a la zona Wi-Fi
 * naveguen a través del túnel VPN DTunnel sin requerir configuración extra en el router.
 */
class HotshareServer(
    private val scope: CoroutineScope,
    private val listenPort: Int = 8080,
    private val upstreamSocksPort: Int = 1080,
    private val logCallback: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var listenJob: Job? = null
    val isRunning = AtomicBoolean(false)

    fun start() {
        if (isRunning.get()) return
        listenJob?.cancel()
        listenJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(listenPort)
                isRunning.set(true)
                logCallback("✓ Hotshare / Tethering activo en puerto :$listenPort (Comparte tu VPN a tu PC/WiFi)")

                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    scope.launch(Dispatchers.IO) {
                        handleTetherClient(client)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    logCallback("Hotshare detenido o error: ${e.message}")
                }
            } finally {
                isRunning.set(false)
            }
        }
    }

    private fun handleTetherClient(clientSocket: Socket) {
        try {
            clientSocket.tcpNoDelay = true
            val socksSocket = Socket()
            socksSocket.connect(InetSocketAddress("127.0.0.1", upstreamSocksPort), 5000)
            socksSocket.tcpNoDelay = true

            val cIn = clientSocket.getInputStream()
            val cOut = clientSocket.getOutputStream()
            val sIn = socksSocket.getInputStream()
            val sOut = socksSocket.getOutputStream()

            val job1 = scope.launch(Dispatchers.IO) { pipe(cIn, sOut) }
            val job2 = scope.launch(Dispatchers.IO) { pipe(sIn, cOut) }

            scope.launch(Dispatchers.IO) {
                job1.join()
                job2.join()
                try { clientSocket.close() } catch (_: Exception) {}
                try { socksSocket.close() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        try {
            while (true) {
                val r = input.read(buffer)
                if (r <= 0) break
                output.write(buffer, 0, r)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning.set(false)
        listenJob?.cancel()
        listenJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
