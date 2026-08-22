package com.example.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.WebSocket
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class WebSocketByteBridge(
    private val webSocket: WebSocket,
    private val scope: CoroutineScope
) {
    private var readJob: Job? = null
    private var outputStream: OutputStream? = null

    fun attachOutputStream(output: OutputStream) {
        this.outputStream = output
    }

    fun onBinaryMessageReceived(bytes: ByteString) {
        try {
            outputStream?.let { out ->
                out.write(bytes.toByteArray())
                out.flush()
            }
        } catch (_: IOException) {}
    }

    fun startForwarding(input: InputStream, bufferSize: Int = 16384) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            try {
                while (isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        val byteString = buffer.toByteString(0, bytesRead)
                        val sent = webSocket.send(byteString)
                        if (!sent) break
                    }
                }
            } catch (_: IOException) {
            } finally {
                withContext(Dispatchers.IO) {
                    try { input.close() } catch (_: Exception) {}
                    try { outputStream?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        try { outputStream?.close() } catch (_: Exception) {}
    }
}
