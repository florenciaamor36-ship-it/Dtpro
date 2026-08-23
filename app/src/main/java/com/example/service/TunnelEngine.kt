package com.example.service

import android.content.Context
import com.example.data.model.ConnectionStatus
import com.example.data.model.LogLevel
import com.example.data.model.LogEntry
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.data.model.TunnelState
import com.example.util.BatteryManagerHelper
import com.example.util.HapticFeedbackHelper
import com.example.util.NetworkDiagnostics
import com.example.util.SoundEffectHelper
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TunnelEngine private constructor() {
    private val engineJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + engineJob)

    private val _tunnelState = MutableStateFlow(TunnelState())
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var jschSession: Session? = null
    private var wsTransport: WebSocketTransport? = null
    private var localProxy: LocalProxyServer? = null
    private var v2rayClient: V2RayClient? = null
    private var udpGwClient: UdpGwClient? = null
    private var hotshareServer: HotshareServer? = null
    private var statsJob: Job? = null
    private var pingerJob: Job? = null
    private var reconnectAttempts = 0
    private var isUserInitiatedStop = false

    private var lastContext: Context? = null

    companion object {
        val instance: TunnelEngine by lazy { TunnelEngine() }
    }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(level = level, message = message)
        val current = _logs.value
        val updated = if (current.size >= 100) {
            current.drop(current.size - 99) + entry
        } else {
            current + entry
        }
        _logs.value = updated
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun startTunnel(context: Context? = null, config: TunnelConfig) {
        isUserInitiatedStop = false
        if (context != null) {
            lastContext = context.applicationContext
            BatteryManagerHelper.acquireWakeLock(context)
        }
        _tunnelState.value = _tunnelState.value.copy(
            status = ConnectionStatus.Connecting,
            currentConfig = config
        )
        log("=== Iniciando Túnel: ${config.name} (${config.mode.title}) ===", LogLevel.INFO)

        scope.launch {
            try {
                // Validación básica de campos indispensables
                val targetHost = config.serverHost.trim()
                if (targetHost.isBlank() && config.proxyHost.isBlank()) {
                    val err = "Error de configuración: Debes ingresar el Host del servidor o el Host frontal."
                    log("⛔ $err", LogLevel.ERROR)
                    _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(err))
                    return@launch
                }

                if (config.mode != TunnelMode.V2RAY_VMESS && config.mode != TunnelMode.UDP_HYSTERIA && config.username.trim().isBlank()) {
                    val err = "Error de configuración: Debes ingresar el Usuario SSH."
                    log("⛔ $err", LogLevel.ERROR)
                    _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(err))
                    return@launch
                }

                // 1. Verificación Integral de Seguridad y Bloqueos de Archivo
                if (context != null) {
                    val myHwid = com.example.util.HwidManager.getHwid(context)
                    log("HWID del Dispositivo: $myHwid", LogLevel.INFO)

                    val securityCheck = com.example.util.SecurityValidator.validateAll(context, config)
                    if (!securityCheck.first) {
                        log("⛔ SEGURIDAD / BLOQUEO: ${securityCheck.second}", LogLevel.ERROR)
                        _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(securityCheck.second))
                        return@launch
                    }
                    if (config.blockRoot) {
                        log("✓ Dispositivo libre de Root (Verificado)", LogLevel.SUCCESS)
                    }
                    if (config.allowedCarriers.isNotBlank()) {
                        log("✓ Bloqueo por operadora validado (${config.allowedCarriers})", LogLevel.SUCCESS)
                    }
                    if (config.expiryTimestamp > 0) {
                        log("✓ Archivo de configuración vigente", LogLevel.SUCCESS)
                    }
                } else {
                    val expiryCheck = com.example.util.HwidManager.checkExpiry(config)
                    if (!expiryCheck.first) {
                        log("⛔ ERROR DE SEGURIDAD: ${expiryCheck.second}", LogLevel.ERROR)
                        _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(expiryCheck.second))
                        return@launch
                    }
                }

                if (config.creatorNote.isNotBlank()) {
                    log("Nota del Creador: ${config.creatorNote}", LogLevel.INFO)
                }

                when (config.mode) {
                    TunnelMode.SSH_WEBSOCKET, TunnelMode.SSH_WEBSOCKET_SSL -> {
                        startWebSocketTunnel(config)
                    }
                    TunnelMode.V2RAY_VMESS -> {
                        startV2RayTunnel(config)
                    }
                    TunnelMode.UDP_HYSTERIA -> {
                        startUdpTunnel(config)
                    }
                    else -> {
                        startSshDirectOrSslOrPayload(config)
                    }
                }
            } catch (e: Exception) {
                val readableError = getReadableErrorMessage(e)
                log("⛔ $readableError", LogLevel.ERROR)
                handleConnectionFailure(config, readableError)
            }
        }
    }

    private fun getReadableErrorMessage(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            e is java.net.UnknownHostException || msg.contains("Unable to resolve host", ignoreCase = true) -> {
                "Error DNS: No se pudo resolver el host '${msg.substringAfter("host ").substringBefore(":")}'. Revisa tu conexión de red o dominio."
            }
            e is java.net.ConnectException || msg.contains("Connection refused", ignoreCase = true) -> {
                "Error de conexión: Conexión rechazada. Verifica que el host frontal/servidor y el puerto estén activos."
            }
            e is java.net.SocketTimeoutException || msg.contains("timeout", ignoreCase = true) -> {
                "Tiempo de espera agotado: El servidor o proxy no respondió a tiempo."
            }
            msg.contains("Auth fail", ignoreCase = true) -> {
                "Error de autenticación SSH: Usuario o contraseña incorrectos."
            }
            msg.contains("SSH_MSG_DISCONNECT", ignoreCase = true) -> {
                "El servidor SSH cerró la conexión."
            }
            msg.contains("Host frontal", ignoreCase = true) || msg.contains("Usuario SSH", ignoreCase = true) -> {
                msg
            }
            else -> "Error en conexión: ${msg.ifBlank { e.javaClass.simpleName }}"
        }
    }

    private suspend fun startV2RayTunnel(config: TunnelConfig) {
        withContext(Dispatchers.IO) {
            log("Iniciando túnel V2Ray (VMess/VLESS sobre WS + TLS)...", LogLevel.INFO)
            val client = V2RayClient(
                scope = scope,
                serverHost = config.serverHost,
                serverPort = if (config.serverPort > 0) config.serverPort else 443,
                uuid = config.password.ifBlank { config.username },
                sniHost = config.sniHost.ifBlank { config.serverHost },
                path = if (config.customPayload.isNotBlank() && config.customPayload.startsWith("/")) config.customPayload else "/vmess",
                localSocksPort = 1080
            ) { msg ->
                log(msg, LogLevel.DEBUG)
            }
            v2rayClient = client
            client.start()

            // Iniciar reenvío UDP opcional si está activado
            if (config.isUdpForwarding) {
                startUdpGwIfNeeded(config)
            }

            // Iniciar Proxy local
            startLocalProxy(1080)
            reconnectAttempts = 0
            _tunnelState.value = _tunnelState.value.copy(
                status = ConnectionStatus.Connected,
                connectedSinceTimestamp = System.currentTimeMillis()
            )
            log("✓ Conexión V2Ray establecida con éxito.", LogLevel.SUCCESS)
            lastContext?.let { 
                HapticFeedbackHelper.vibrateSuccess(it)
                SoundEffectHelper.playConnectSound(it)
            }
            startStatsMonitoring()
            startKeepAlivePinger()
            refreshPublicIp()
        }
    }

    private suspend fun startUdpTunnel(config: TunnelConfig) {
        withContext(Dispatchers.IO) {
            log("Iniciando túnel UDP / BadVPN / Hysteria...", LogLevel.INFO)
            startUdpGwIfNeeded(config)
            startLocalProxy(1080)
            reconnectAttempts = 0
            _tunnelState.value = _tunnelState.value.copy(
                status = ConnectionStatus.Connected,
                connectedSinceTimestamp = System.currentTimeMillis()
            )
            log("✓ Túnel UDP conectado con éxito.", LogLevel.SUCCESS)
            lastContext?.let { 
                HapticFeedbackHelper.vibrateSuccess(it)
                SoundEffectHelper.playConnectSound(it)
            }
            startStatsMonitoring()
            startKeepAlivePinger()
            refreshPublicIp()
        }
    }

    private fun startUdpGwIfNeeded(config: TunnelConfig) {
        val targetHost = config.serverHost.ifBlank { config.proxyHost }
        if (targetHost.isNotBlank()) {
            val udpClient = UdpGwClient(
                scope = scope,
                remoteServer = targetHost,
                remoteUdpPort = 7300,
                localListenPort = 7300
            ) { msg ->
                log(msg, LogLevel.DEBUG)
            }
            udpGwClient = udpClient
            udpClient.start()
        }
    }

    private suspend fun startWebSocketTunnel(config: TunnelConfig) {
        val proto = if (config.mode == TunnelMode.SSH_WEBSOCKET_SSL) "wss://" else "ws://"
        val wsUrl = "$proto${config.serverHost}:${config.serverPort}/"
        log("Estableciendo transporte WebSocket: $wsUrl", LogLevel.INFO)

        val socketFactory = CustomSshSocketFactory(config, scope) { msg ->
            log(msg, LogLevel.DEBUG)
        }

        val transport = WebSocketTransport(
            url = wsUrl,
            sniHost = config.sniHost.ifBlank { config.serverHost },
            onOpenCallback = { ws ->
                log("WebSocket abierto correctamente. Vinculando Socket SSH...", LogLevel.SUCCESS)
                socketFactory.bindWebSocket(ws)
                scope.launch {
                    connectJSchSession(config, socketFactory)
                }
            },
            onBinaryMessage = { bytes ->
                socketFactory.getVirtualSocket()?.onIncomingBytes(bytes)
            },
            onFailureCallback = { err, response ->
                log("Fallo en transporte WebSocket: ${err.message}", LogLevel.ERROR)
                scope.launch {
                    handleConnectionFailure(config, "Fallo en WebSocket: ${err.localizedMessage}")
                }
            },
            onClosedCallback = { code, reason ->
                log("WebSocket cerrado: $code - $reason", LogLevel.WARNING)
            }
        )
        wsTransport = transport
        transport.connect()
    }

    private suspend fun startSshDirectOrSslOrPayload(config: TunnelConfig) {
        val socketFactory = CustomSshSocketFactory(config, scope) { msg ->
            log(msg, LogLevel.DEBUG)
        }
        connectJSchSession(config, socketFactory)
    }

    private suspend fun connectJSchSession(config: TunnelConfig, socketFactory: CustomSshSocketFactory) {
        withContext(Dispatchers.IO) {
            try {
                log("Autenticando SSH con usuario '${config.username}'...", LogLevel.INFO)
                _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Authenticating)

                val jsch = JSch()
                val session = jsch.getSession(config.username, config.serverHost, config.serverPort)
                session.setPassword(config.password)
                session.setSocketFactory(socketFactory)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                session.setConfig("compression.s2c", "zlib@openssh.com,none")
                session.setConfig("compression.c2s", "zlib@openssh.com,none")

                session.connect(20000)
                log("Sesión SSH autenticada con éxito.", LogLevel.SUCCESS)

                val socksPort = 1080
                try {
                    val dMethod = session.javaClass.methods.firstOrNull { 
                        it.name == "setPortForwardingD" && it.parameterTypes.size == 1 
                    }
                    if (dMethod != null) {
                        dMethod.invoke(session, socksPort)
                        log("Dynamic Port Forwarding (SOCKS5) activo en 127.0.0.1:$socksPort", LogLevel.SUCCESS)
                    } else {
                        val dMethod2 = session.javaClass.methods.firstOrNull { 
                            it.name == "setPortForwardingD" && it.parameterTypes.size == 2 
                        }
                        if (dMethod2 != null) {
                            dMethod2.invoke(session, "127.0.0.1", socksPort)
                            log("Dynamic Port Forwarding (SOCKS5) activo en 127.0.0.1:$socksPort", LogLevel.SUCCESS)
                        } else {
                            session.setPortForwardingL(socksPort, "127.0.0.1", socksPort)
                            log("Port Forwarding (L) activo en 127.0.0.1:$socksPort", LogLevel.SUCCESS)
                        }
                    }
                } catch (e: Exception) {
                    log("Aviso de Port Forwarding: ${e.message}", LogLevel.WARNING)
                }

                jschSession = session

                if (config.isUdpForwarding) {
                    startUdpGwIfNeeded(config)
                }

                startLocalProxy(socksPort)
                reconnectAttempts = 0
                _tunnelState.value = _tunnelState.value.copy(
                    status = ConnectionStatus.Connected,
                    connectedSinceTimestamp = System.currentTimeMillis()
                )
                log("✓ Conexión establecida con éxito. Enrutando tráfico a través del túnel.", LogLevel.SUCCESS)
                lastContext?.let { ctx ->
                    HapticFeedbackHelper.vibrateSuccess(ctx)
                    SoundEffectHelper.playConnectSound(ctx)
                    if (config.showToastOnConnect.isNotBlank()) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            android.widget.Toast.makeText(ctx, config.showToastOnConnect, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                startStatsMonitoring()
                startKeepAlivePinger()
                refreshPublicIp()

            } catch (e: Exception) {
                val readableError = getReadableErrorMessage(e)
                log("⛔ $readableError", LogLevel.ERROR)
                handleConnectionFailure(config, readableError)
            }
        }
    }

    private fun startLocalProxy(remoteSocksPort: Int) {
        localProxy?.stop()
        localProxy = LocalProxyServer(
            scope = scope,
            localPort = 8080,
            remoteSocksPort = remoteSocksPort,
            onBytesTransferred = { inBytes, outBytes ->
                val current = _tunnelState.value
                _tunnelState.value = current.copy(
                    bytesIn = current.bytesIn + inBytes,
                    bytesOut = current.bytesOut + outBytes
                )
            },
            logCallback = { msg ->
                log(msg, LogLevel.DEBUG)
            }
        )
        localProxy?.start()
    }

    private fun startKeepAlivePinger() {
        pingerJob?.cancel()
        pingerJob = scope.launch {
            while (isActive) {
                delay(30000) // Pinger cada 30 segundos
                if (_tunnelState.value.status is ConnectionStatus.Connected) {
                    try {
                        val pingMs = NetworkDiagnostics.checkRealPing("8.8.8.8", 53, 2000)
                        if (pingMs > 0) {
                            _tunnelState.value = _tunnelState.value.copy(pingMs = pingMs)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun startStatsMonitoring() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var prevIn = 0L
            var prevOut = 0L
            while (isActive) {
                delay(1000)
                val curIn = _tunnelState.value.bytesIn
                val curOut = _tunnelState.value.bytesOut
                val downSpeed = (curIn - prevIn).coerceAtLeast(0)
                val upSpeed = (curOut - prevOut).coerceAtLeast(0)
                prevIn = curIn
                prevOut = curOut

                _tunnelState.value = _tunnelState.value.copy(
                    downloadSpeedBps = downSpeed,
                    uploadSpeedBps = upSpeed
                )
            }
        }
    }

    private fun refreshPublicIp() {
        scope.launch {
            try {
                val ipInfo = NetworkDiagnostics.fetchPublicIpInfo()
                if (ipInfo.ip.isNotBlank() && ipInfo.ip != "---") {
                    _tunnelState.value = _tunnelState.value.copy(
                        publicIp = ipInfo.ip,
                        ipLocation = ipInfo.region
                    )
                    log("IP Pública Asignada: ${ipInfo.ip} (${ipInfo.region})", LogLevel.INFO)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun handleConnectionFailure(config: TunnelConfig, reason: String) {
        cleanup()
        lastContext?.let { SoundEffectHelper.playErrorSound(it) }
        if (config.autoReconnect && !isUserInitiatedStop && reconnectAttempts < 5) {
            reconnectAttempts++
            val delaySec = reconnectAttempts * 3
            log("Reintentando conexión automática en $delaySec s (Intento $reconnectAttempts/5)...", LogLevel.WARNING)
            _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Reconnecting)
            delay(delaySec * 1000L)
            startTunnel(lastContext, config)
        } else {
            _tunnelState.value = _tunnelState.value.copy(
                status = ConnectionStatus.Error(reason)
            )
        }
    }

    fun stopTunnel() {
        isUserInitiatedStop = true
        reconnectAttempts = 0
        log("Deteniendo túnel y liberando recursos...", LogLevel.INFO)
        cleanup()
        _tunnelState.value = _tunnelState.value.copy(
            status = ConnectionStatus.Disconnected,
            downloadSpeedBps = 0L,
            uploadSpeedBps = 0L
        )
        lastContext?.let { 
            HapticFeedbackHelper.vibrateDisconnect(it)
            SoundEffectHelper.playDisconnectSound(it)
        }
        log("Túnel desconectado.", LogLevel.INFO)
    }

    fun toggleHotshare(enable: Boolean, listenPort: Int = 8080) {
        if (enable) {
            hotshareServer?.stop()
            val server = HotshareServer(scope, listenPort = listenPort, upstreamSocksPort = 1080) { msg ->
                log(msg, LogLevel.INFO)
            }
            hotshareServer = server
            server.start()
        } else {
            hotshareServer?.stop()
            hotshareServer = null
            log("Hotshare / Tethering detenido.", LogLevel.INFO)
        }
    }

    fun isHotshareActive(): Boolean = hotshareServer?.isRunning?.get() ?: false

    private fun cleanup() {
        pingerJob?.cancel()
        pingerJob = null
        statsJob?.cancel()
        statsJob = null
        try {
            jschSession?.disconnect()
        } catch (_: Exception) {}
        jschSession = null

        try {
            wsTransport?.close()
        } catch (_: Exception) {}
        wsTransport = null

        try {
            v2rayClient?.stop()
        } catch (_: Exception) {}
        v2rayClient = null

        try {
            udpGwClient?.stop()
        } catch (_: Exception) {}
        udpGwClient = null

        try {
            localProxy?.stop()
        } catch (_: Exception) {}
        localProxy = null

        BatteryManagerHelper.releaseWakeLock()
    }
}
