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
import java.util.concurrent.atomic.AtomicLong

class LocalProxyServer(
    private val scope: CoroutineScope,
    private val localPort: Int = 1080,
    private val remoteSocksPort: Int = 1080,
    private val onBytesTransferred: (Long, Long) -> Unit,
    private val logCallback: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    val totalBytesIn = AtomicLong(0)
    val totalBytesOut = AtomicLong(0)

    fun start() {
        acceptJob?.cancel()
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(localPort)
                logCallback("Proxy local escuchando en 127.0.0.1:$localPort")
                
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    logCallback("Error en servidor proxy local: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.tcpNoDelay = true
            val forwardSocket = Socket()
            forwardSocket.connect(InetSocketAddress("127.0.0.1", remoteSocksPort), 5000)
            forwardSocket.tcpNoDelay = true

            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()
            val forwardIn = forwardSocket.getInputStream()
            val forwardOut = forwardSocket.getOutputStream()

            // Iniciar relay bidireccional
            val job1 = scope.launch(Dispatchers.IO) {
                relayStream(clientIn, forwardOut, isUpload = true)
            }
            val job2 = scope.launch(Dispatchers.IO) {
                relayStream(forwardIn, clientOut, isUpload = false)
            }

            scope.launch(Dispatchers.IO) {
                job1.join()
                job2.join()
                try { clientSocket.close() } catch (_: Exception) {}
                try { forwardSocket.close() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun relayStream(input: InputStream, output: OutputStream, isUpload: Boolean) {
        val buffer = ByteArray(16384)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
                if (isUpload) {
                    totalBytesOut.addAndGet(read.toLong())
                    onBytesTransferred(0, read.toLong())
                } else {
                    totalBytesIn.addAndGet(read.toLong())
                    onBytesTransferred(read.toLong(), 0)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }
}
