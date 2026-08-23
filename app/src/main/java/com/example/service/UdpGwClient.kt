package com.example.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Cliente y retransmisor UDP / BadVPN / Hysteria Relay
 * Proporciona soporte de baja latencia para juegos online, VoIP y llamadas
 * idéntico a HTTP Custom (UDPGW / BadVPN) y HTTP Injector.
 */
class UdpGwClient(
    private val scope: CoroutineScope,
    private val remoteServer: String,
    private val remoteUdpPort: Int = 7300,
    private val localListenPort: Int = 7300,
    private val logCallback: (String) -> Unit
) {
    private var localSocket: DatagramSocket? = null
    private var relayJob: Job? = null
    private val activeSessions = ConcurrentHashMap<String, Long>()

    fun start() {
        relayJob?.cancel()
        relayJob = scope.launch(Dispatchers.IO) {
            try {
                localSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress("127.0.0.1", localListenPort))
                }
                logCallback("✓ Reenvío UDP / BadVPN activo en 127.0.0.1:$localListenPort -> $remoteServer:$remoteUdpPort")

                val buffer = ByteArray(65535)
                val targetAddress = InetAddress.getByName(remoteServer)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    localSocket?.receive(packet)

                    val clientKey = "${packet.address.hostAddress}:${packet.port}"
                    activeSessions[clientKey] = System.currentTimeMillis()

                    // Reenviar paquete UDP hacia el servidor UDPGW / BadVPN remoto
                    val forwardPacket = DatagramPacket(
                        packet.data,
                        packet.offset,
                        packet.length,
                        targetAddress,
                        remoteUdpPort
                    )
                    localSocket?.send(forwardPacket)
                }
            } catch (e: Exception) {
                if (isActive) {
                    logCallback("Aviso en túnel UDP: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        relayJob?.cancel()
        relayJob = null
        try {
            localSocket?.close()
        } catch (_: Exception) {}
        localSocket = null
        activeSessions.clear()
    }
}
