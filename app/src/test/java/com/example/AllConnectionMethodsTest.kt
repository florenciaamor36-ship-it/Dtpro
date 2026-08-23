package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.service.CustomSshSocketFactory
import com.example.service.LocalProxyServer
import com.example.service.UdpGwClient
import com.example.service.V2RayClient
import com.example.service.VirtualWebSocketSocket
import com.example.util.AppFilterManager
import com.example.util.PayloadGenerator
import com.example.util.SoundEffectHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AllConnectionMethodsTest {

    private lateinit var testScope: CoroutineScope
    private lateinit var context: Context

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.IO)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    /**
     * 1. Test Método SSH DIRECT:
     * Verifica que el SocketFactory abra un socket TCP directo real hacia el host y puerto especificados.
     */
    @Test
    fun testMethod_SshDirect_TcpSocketCreation() = runBlocking {
        val server = ServerSocket(0)
        val assignedPort = server.localPort
        val clientConnected = AtomicBoolean(false)

        testScope.launch {
            val sock = server.accept()
            clientConnected.set(true)
            sock.close()
            server.close()
        }

        val config = TunnelConfig(
            name = "SSH Direct Test",
            mode = TunnelMode.SSH_DIRECT,
            serverHost = "127.0.0.1",
            serverPort = assignedPort,
            username = "sshuser",
            password = "sshpassword"
        )

        val factory = CustomSshSocketFactory(config, testScope) {}
        val clientSocket = factory.createSocket("127.0.0.1", assignedPort)

        assertNotNull(clientSocket)
        assertTrue(clientSocket.isConnected)
        clientSocket.close()
        delay(100)
        assertTrue(clientConnected.get())
    }

    /**
     * 2. Test Método SSH PAYLOAD (HTTP Proxy & Inyección):
     * Verifica la inyección del payload HTTP personalizado, reemplazo de marcadores [host] y consumo de cabeceras HTTP.
     */
    @Test
    fun testMethod_SshPayload_InjectionAndHeadersConsuming() = runBlocking {
        val mockProxyServer = ServerSocket(0)
        val proxyPort = mockProxyServer.localPort
        val receivedPayload = StringBuilder()
        val latch = CountDownLatch(1)

        testScope.launch {
            val client = mockProxyServer.accept()
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(client.getOutputStream()), true)

            // Leer líneas del payload enviado por el cliente
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                receivedPayload.append(line).append("\n")
                if (line.isNullOrBlank()) break
            }

            // Responder HTTP 200 Connection Established (comportamiento estándar de proxy HTTP)
            writer.print("HTTP/1.1 200 Connection Established\r\n\r\n")
            writer.flush()

            // Enviar banner SSH
            writer.print("SSH-2.0-OpenSSH_8.9p1 Ubuntu\r\n")
            writer.flush()

            latch.countDown()
            client.close()
            mockProxyServer.close()
        }

        val config = TunnelConfig(
            name = "Payload Tunnel Test",
            mode = TunnelMode.SSH_PAYLOAD,
            serverHost = "ssh.myserver.com",
            serverPort = 22,
            proxyHost = "127.0.0.1",
            proxyPort = proxyPort,
            customPayload = "CONNECT [host_port] HTTP/1.1[crlf]Host: [host][crlf]X-Online-Host: [host][crlf]Connection: Keep-Alive[crlf][crlf]"
        )

        val logs = mutableListOf<String>()
        val factory = CustomSshSocketFactory(config, testScope) { logs.add(it) }
        val socket = factory.createSocket("ssh.myserver.com", 22)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(socket)
        assertTrue(socket.isConnected)

        // Verificar que el payload recibido por el servidor proxy sustituyó correctamente las etiquetas
        val payloadStr = receivedPayload.toString()
        assertTrue("Debe contener CONNECT ssh.myserver.com:22", payloadStr.contains("CONNECT ssh.myserver.com:22"))
        assertTrue("Debe contener Host: ssh.myserver.com", payloadStr.contains("Host: ssh.myserver.com"))
        assertTrue("Debe contener X-Online-Host", payloadStr.contains("X-Online-Host"))

        socket.close()
    }

    /**
     * 3. Test Método SSH WEBSOCKET & WEBSOCKET+SSL:
     * Verifica el encapsulado de tráfico de red a través de un WebSocket virtual bidireccional.
     */
    @Test
    fun testMethod_SshWebSocket_VirtualSocketPiping() = runBlocking {
        val bytesSent = mutableListOf<ByteString>()

        val fakeWs = object : WebSocket {
            override fun request(): Request = Request.Builder().url("http://localhost").build()
            override fun queueSize(): Long = 0
            override fun send(text: String): Boolean = true
            override fun send(bytes: ByteString): Boolean {
                bytesSent.add(bytes)
                return true
            }
            override fun close(code: Int, reason: String?): Boolean = true
            override fun cancel() {}
        }

        val virtualSocket = VirtualWebSocketSocket(fakeWs, testScope)
        assertTrue(virtualSocket.isConnected)
        assertFalse(virtualSocket.isClosed)

        // 1. Probar transmisión desde SSH hacia WebSocket
        val testData = "SSH-2.0-ClientTest\r\n".toByteArray()
        virtualSocket.outputStream.write(testData)
        virtualSocket.outputStream.flush()

        assertEquals(1, bytesSent.size)
        assertEquals("SSH-2.0-ClientTest\r\n", bytesSent[0].utf8())

        // 2. Probar recepción desde WebSocket hacia SSH
        val responseBytes = "SSH-2.0-ServerResponse\r\n".encodeUtf8()
        virtualSocket.onIncomingBytes(responseBytes)

        val buffer = ByteArray(24)
        val readBytes = virtualSocket.inputStream.read(buffer)
        assertEquals(24, readBytes)
        assertEquals("SSH-2.0-ServerResponse\r\n", String(buffer, 0, readBytes))

        virtualSocket.close()
    }

    /**
     * 4. Test Método V2RAY / VMESS / VLESS:
     * Verifica la inicialización del cliente V2Ray y apertura del puerto local SOCKS5.
     */
    @Test
    fun testMethod_V2RayClient_LocalSocksLifecycle() = runBlocking {
        val freePort = 19182
        val logs = mutableListOf<String>()
        val v2rayClient = V2RayClient(
            scope = testScope,
            serverHost = "v2ray.myserver.com",
            serverPort = 443,
            uuid = "b831381d-6324-4d53-ad4f-8cda48b30811",
            sniHost = "bug.whatsapp.com",
            path = "/vmess-ws",
            localSocksPort = freePort
        ) { msg ->
            logs.add(msg)
        }

        v2rayClient.start()
        delay(200)

        // Verificar que el puerto SOCKS5 esté escuchando
        val testSocket = Socket()
        testSocket.connect(InetSocketAddress("127.0.0.1", freePort), 2000)
        assertTrue(testSocket.isConnected)
        testSocket.close()

        v2rayClient.stop()
        delay(100)

        assertTrue(logs.any { it.contains("Núcleo V2Ray iniciado") })
        assertTrue(logs.any { it.contains("SOCKS5 local V2Ray escuchando") })
    }

    /**
     * 5. Test Método UDP / BadVPN / Hysteria:
     * Verifica el cliente UDPGW para retransmisión de datagramas UDP con baja latencia.
     */
    @Test
    fun testMethod_UdpGw_DatagramRelay() = runBlocking {
        val listenPort = 17305
        val remotePort = 17306

        val mockRemoteUdpServer = DatagramSocket(remotePort)
        val receivedPacketsCount = AtomicInteger(0)

        testScope.launch {
            val buf = ByteArray(1024)
            val packet = DatagramPacket(buf, buf.size)
            mockRemoteUdpServer.receive(packet)
            receivedPacketsCount.incrementAndGet()
            mockRemoteUdpServer.close()
        }

        val logs = mutableListOf<String>()
        val udpClient = UdpGwClient(
            scope = testScope,
            remoteServer = "127.0.0.1",
            remoteUdpPort = remotePort,
            localListenPort = listenPort
        ) { logs.add(it) }

        udpClient.start()
        delay(150)

        // Enviar un paquete UDP al cliente local
        val localSender = DatagramSocket()
        val dataToSend = "PING-TEST-UDP-PAYLOAD".toByteArray()
        val packetToSend = DatagramPacket(dataToSend, dataToSend.size, InetAddress.getByName("127.0.0.1"), listenPort)
        localSender.send(packetToSend)
        localSender.close()

        delay(300)
        assertEquals(1, receivedPacketsCount.get())

        udpClient.stop()
        assertTrue(logs.any { it.contains("Reenvío UDP / BadVPN activo") })
    }

    /**
     * 6. Test Proxy Local y Contador de Transferencia de Datos:
     * Verifica el cálculo de bytes subidos y descargados.
     */
    @Test
    fun testLocalProxyServer_BytesAccounting() = runBlocking {
        val mockTargetServer = ServerSocket(0)
        val targetPort = mockTargetServer.localPort

        testScope.launch {
            val sock = mockTargetServer.accept()
            val inS = sock.getInputStream()
            val outS = sock.getOutputStream()
            val buf = ByteArray(1024)
            val read = inS.read(buf)
            if (read > 0) {
                outS.write(buf, 0, read) // Eco
                outS.flush()
            }
            delay(50)
            sock.close()
            mockTargetServer.close()
        }

        var inBytesRecorded = 0L
        var outBytesRecorded = 0L

        val proxyServer = LocalProxyServer(
            scope = testScope,
            localPort = 18880,
            remoteSocksPort = targetPort,
            onBytesTransferred = { inB, outB ->
                inBytesRecorded += inB
                outBytesRecorded += outB
            }
        ) {}

        proxyServer.start()
        delay(200)

        val client = Socket()
        client.connect(InetSocketAddress("127.0.0.1", 18880), 2000)
        client.outputStream.write("HOLA MUNDO PROXY".toByteArray())
        client.outputStream.flush()

        val respBuf = ByteArray(64)
        val read = client.inputStream.read(respBuf)
        client.close()
        delay(200)

        assertTrue(read > 0)
        assertEquals("HOLA MUNDO PROXY", String(respBuf, 0, read))
        assertTrue("Debe registrar bytes de subida", proxyServer.totalBytesOut.get() > 0)
        assertTrue("Debe registrar bytes de bajada", proxyServer.totalBytesIn.get() > 0)

        proxyServer.stop()
    }

    /**
     * 7. Test de Efectos de Sonido y Preferencias:
     */
    @Test
    fun testSoundEffects_PreferencesAndExecution() {
        SoundEffectHelper.setSoundEnabled(context, true)
        assertTrue(SoundEffectHelper.isSoundEnabled(context))

        SoundEffectHelper.playConnectSound(context)
        SoundEffectHelper.playDisconnectSound(context)
        SoundEffectHelper.playErrorSound(context)

        SoundEffectHelper.setSoundEnabled(context, false)
        assertFalse(SoundEffectHelper.isSoundEnabled(context))
    }

    /**
     * 8. Test de Split Tunneling / AppFilterManager:
     */
    @Test
    fun testAppFilterManager_ModesAndSelection() {
        AppFilterManager.setFilterEnabled(context, true)
        assertTrue(AppFilterManager.isFilterEnabled(context))

        AppFilterManager.setFilterMode(context, "EXCLUDE")
        assertEquals("EXCLUDE", AppFilterManager.getFilterMode(context))

        val testApps = setOf("com.whatsapp", "com.instagram.android")
        AppFilterManager.setSelectedApps(context, testApps)
        val selected = AppFilterManager.getSelectedApps(context)
        assertEquals(2, selected.size)
        assertTrue(selected.contains("com.whatsapp"))
    }
}
