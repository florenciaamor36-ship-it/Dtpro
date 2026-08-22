package com.example.service

import com.example.data.model.ConnectionStatus
import com.example.data.model.LogLevel
import com.example.data.model.LogEntry
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.data.model.TunnelState
import com.example.util.BatteryManagerHelper
import com.example.util.NetworkDiagnostics
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
    private var statsJob: Job? = null
    private var reconnectAttempts = 0
    private var isUserInitiatedStop = false

    private var lastContext: android.content.Context? = null

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

    fun startTunnel(context: android.content.Context? = null, config: TunnelConfig) {
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
                    val err = "Error de configuración: Debes ingresar el Host del servidor SSH o el Host frontal."
                    log("⛔ $err", LogLevel.ERROR)
                    _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(err))
                    return@launch
                }
                if (config.username.trim().isBlank()) {
                    val err = "Error de configuración: Debes ingresar el Usuario SSH."
                    log("⛔ $err", LogLevel.ERROR)
                    _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(err))
                    return@launch
                }

                // 1. Verificación de Expiración (Fecha y Hora)
                val expiryCheck = com.example.util.HwidManager.checkExpiry(config)
                if (!expiryCheck.first) {
                    log("⛔ ERROR DE SEGURIDAD: ${expiryCheck.second}", LogLevel.ERROR)
                    _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(expiryCheck.second))
                    return@launch
                } else if (config.expiryTimestamp > 0) {
                    log("✓ Archivo de configuración válido (${expiryCheck.second})", LogLevel.SUCCESS)
                }

                // 2. Verificación de HWID Autorizado
                if (context != null) {
                    val myHwid = com.example.util.HwidManager.getHwid(context)
                    log("HWID del Dispositivo: $myHwid", LogLevel.INFO)

                    val hwidCheck = com.example.util.HwidManager.checkHwidPermission(context, config)
                    if (!hwidCheck.first) {
                        log("⛔ ACCESO DENEGADO: ${hwidCheck.second}", LogLevel.ERROR)
                        _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(hwidCheck.second))
                        return@launch
                    }

                    // 3. Verificación Remota en Servidor VPS (si está configurada)
                    if (config.vpsAuthUrl.isNotBlank()) {
                        log("Consultando autorización en VPS remota...", LogLevel.INFO)
                        val vpsCheck = com.example.util.HwidManager.checkVpsValidation(context, config)
                        if (!vpsCheck.first) {
                            log("⛔ BLOQUEO VPS: ${vpsCheck.second}", LogLevel.ERROR)
                            _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(vpsCheck.second))
                            return@launch
                        }
                        log("✓ Autorización confirmada por VPS: ${vpsCheck.second}", LogLevel.SUCCESS)
                    }
                }

                if (config.creatorNote.isNotBlank()) {
                    log("Nota del Creador: ${config.creatorNote}", LogLevel.INFO)
                }

                if (config.mode == TunnelMode.SSH_WEBSOCKET || config.mode == TunnelMode.SSH_WEBSOCKET_SSL) {
                    startWebSocketTunnel(config)
                } else {
                    startSshDirectOrSslOrPayload(config)
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
                    log("Aviso en configuración de puertos: ${e.message}", LogLevel.WARNING)
                }

                jschSession = session
                reconnectAttempts = 0

                // Iniciar Proxy local para conteo de estadísticas de tráfico
                startLocalProxy(socksPort)

                _tunnelState.value = _tunnelState.value.copy(
                    status = ConnectionStatus.Connected,
                    connectedSinceTimestamp = System.currentTimeMillis(),
                    localSocksPort = socksPort
                )
                log("✓ ¡TÚNEL CONECTADO Y OPERATIVO AL 100%!", LogLevel.SUCCESS)

                startMonitoringLoop(config)

            } catch (e: Exception) {
                log("Error de conexión SSH: ${e.message}", LogLevel.ERROR)
                handleConnectionFailure(config, e.message ?: "Fallo de conexión SSH")
            }
        }
    }

    private fun startLocalProxy(remoteSocksPort: Int) {
        localProxy?.stop()
        val proxy = LocalProxyServer(
            scope = scope,
            localPort = 8080,
            remoteSocksPort = remoteSocksPort,
            onBytesTransferred = { bytesIn, bytesOut ->
                val current = _tunnelState.value
                _tunnelState.value = current.copy(
                    bytesIn = current.bytesIn + bytesIn,
                    bytesOut = current.bytesOut + bytesOut
                )
            },
            logCallback = { msg -> log(msg, LogLevel.DEBUG) }
        )
        proxy.start()
        localProxy = proxy
    }

    private fun startMonitoringLoop(config: TunnelConfig) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var lastIn = _tunnelState.value.bytesIn
            var lastOut = _tunnelState.value.bytesOut

            // Obtener IP pública real y geolocalización
            scope.launch {
                val ipInfo = NetworkDiagnostics.fetchPublicIpInfo()
                _tunnelState.value = _tunnelState.value.copy(
                    publicIp = ipInfo.ip,
                    ipLocation = ipInfo.region
                )
                log("IP Pública del Túnel: ${ipInfo.ip} (${ipInfo.region})", LogLevel.INFO)
            }

            while (isActive && jschSession?.isConnected == true) {
                val isSaver = lastContext?.let { BatteryManagerHelper.isBatterySaverEnabled(it) } ?: false
                val pollInterval = if (isSaver) 3000L else 1000L
                delay(pollInterval)

                val currentIn = _tunnelState.value.bytesIn
                val currentOut = _tunnelState.value.bytesOut

                val speedDown = ((currentIn - lastIn) * 1000L) / pollInterval
                val speedUp = ((currentOut - lastOut) * 1000L) / pollInterval
                lastIn = currentIn
                lastOut = currentOut

                // Ping periódico
                val ping = if (!isSaver) NetworkDiagnostics.checkRealPing("1.1.1.1", 53, 2000) else _tunnelState.value.pingMs

                _tunnelState.value = _tunnelState.value.copy(
                    downloadSpeedBps = speedDown,
                    uploadSpeedBps = speedUp,
                    pingMs = ping
                )
            }

            if (!isUserInitiatedStop && _tunnelState.value.status is ConnectionStatus.Connected) {
                log("Conexión perdida con el servidor.", LogLevel.WARNING)
                handleConnectionFailure(config, "Conexión SSH perdida.")
            }
        }
    }

    private suspend fun handleConnectionFailure(config: TunnelConfig, reason: String) {
        cleanup()
        if (config.autoReconnect && !isUserInitiatedStop && reconnectAttempts < 5) {
            reconnectAttempts++
            val delaySeconds = reconnectAttempts * 2
            log("Reintentando conexión automática en $delaySeconds segundos (Intento $reconnectAttempts/5)...", LogLevel.WARNING)
            _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Reconnecting)
            delay(delaySeconds * 1000L)
            if (!isUserInitiatedStop) {
                startTunnel(lastContext, config)
            }
        } else {
            _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Error(reason))
        }
    }

    fun stopTunnel() {
        isUserInitiatedStop = true
        log("Deteniendo túnel a petición del usuario...", LogLevel.INFO)
        _tunnelState.value = _tunnelState.value.copy(status = ConnectionStatus.Disconnected)
        cleanup()
        lastContext?.let { BatteryManagerHelper.releaseWakeLock() }
        log("Túnel desconectado.", LogLevel.INFO)
    }

    private fun cleanup() {
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

        localProxy?.stop()
        localProxy = null
    }
}
