package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ConnectionStatus
import com.example.data.model.TunnelConfig
import com.example.util.AppFilterManager
import com.example.util.BatteryManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DTunnelVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var stateObserverJob: Job? = null

    companion object {
        const val ACTION_CONNECT = "com.example.service.DTunnelVpnService.CONNECT"
        const val ACTION_DISCONNECT = "com.example.service.DTunnelVpnService.DISCONNECT"
        const val CHANNEL_ID = "dtunnel_vpn_status_channel"
        const val NOTIFICATION_ID = 101

        fun startService(context: Context, config: TunnelConfig) {
            val intent = Intent(context, DTunnelVpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DTunnelVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                BatteryManagerHelper.acquireWakeLock(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification("Iniciando conexión...", "DTunnel"),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification("Iniciando conexión...", "DTunnel"))
                }
                observeTunnelState()
                establishVpn()
            }
            ACTION_DISCONNECT -> {
                stopVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun establishVpn() {
        try {
            val currentConfig = TunnelEngine.instance.tunnelState.value.currentConfig
            val dns1 = currentConfig?.dnsPrimary?.ifBlank { "8.8.8.8" } ?: "8.8.8.8"
            val dns2 = currentConfig?.dnsSecondary?.ifBlank { "8.8.4.4" } ?: "8.8.4.4"

            val builder = Builder()
                .setSession("DTunnel VPN")
                .setMtu(1500)
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(dns1)

            try {
                builder.addDnsServer(dns2)
            } catch (_: Exception) {}

            // Aplicar Split Tunneling / Filtro de Aplicaciones
            if (AppFilterManager.isFilterEnabled(this)) {
                val selectedApps = AppFilterManager.getSelectedApps(this)
                val mode = AppFilterManager.getFilterMode(this)

                if (selectedApps.isNotEmpty()) {
                    for (pkg in selectedApps) {
                        try {
                            if (mode == "INCLUDE") {
                                builder.addAllowedApplication(pkg)
                            } else {
                                builder.addDisallowedApplication(pkg)
                            }
                        } catch (e: Exception) {
                            TunnelEngine.instance.log("Aviso en filtro de app ($pkg): ${e.message}", com.example.data.model.LogLevel.WARNING)
                        }
                    }
                    val actionName = if (mode == "INCLUDE") "Enrutando exclusivamente" else "Excluyendo del túnel"
                    TunnelEngine.instance.log("✓ Split Tunneling activo: $actionName ${selectedApps.size} apps.", com.example.data.model.LogLevel.INFO)
                }
            }

            vpnInterface = builder.establish()
            TunnelEngine.instance.log("Interfaz TUN VPN establecida (10.0.0.2/24)", com.example.data.model.LogLevel.SUCCESS)
        } catch (e: Exception) {
            TunnelEngine.instance.log("Aviso en interfaz VPN: ${e.message}", com.example.data.model.LogLevel.WARNING)
        }
    }

    private fun observeTunnelState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            var lastNotifTime = 0L
            TunnelEngine.instance.tunnelState.collectLatest { state ->
                val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val now = System.currentTimeMillis()
                val isStatusChange = state.status !is ConnectionStatus.Connected

                if (isStatusChange || (now - lastNotifTime >= 2000L)) {
                    lastNotifTime = now
                    val text = when (state.status) {
                        is ConnectionStatus.Connected -> {
                            val downKb = state.downloadSpeedBps / 1024
                            val upKb = state.uploadSpeedBps / 1024
                            "Conectado • ↓ ${downKb} KB/s  ↑ ${upKb} KB/s"
                        }
                        is ConnectionStatus.Connecting -> "Conectando al servidor..."
                        is ConnectionStatus.Authenticating -> "Autenticando SSH..."
                        is ConnectionStatus.Reconnecting -> "Reconectando..."
                        is ConnectionStatus.Error -> "Error: ${(state.status as ConnectionStatus.Error).message}"
                        is ConnectionStatus.Disconnected -> "Desconectado"
                    }
                    val title = state.currentConfig?.name ?: "DTunnel Manager"
                    notifManager.notify(NOTIFICATION_ID, buildNotification(text, title))
                }

                if (state.status is ConnectionStatus.Disconnected) {
                    stopSelf()
                }
            }
        }
    }

    private fun stopVpn() {
        BatteryManagerHelper.releaseWakeLock()
        TunnelEngine.instance.stopTunnel()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DTunnel VPN Estado",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado activo de la conexión DTunnel"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String, title: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, DTunnelVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desconectar", pendingDisconnect)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        BatteryManagerHelper.releaseWakeLock()
        stateObserverJob?.cancel()
        serviceJob.cancel()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        super.onDestroy()
    }
}
