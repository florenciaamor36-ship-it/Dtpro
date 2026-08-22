package com.example.ui

import android.app.Application
import android.content.Context
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ConnectionStatus
import com.example.data.model.LogEntry
import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import com.example.data.model.TunnelState
import com.example.service.DTunnelVpnService
import com.example.service.TunnelEngine
import com.example.util.ConfigExporter
import com.example.util.NetworkDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TunnelViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val dao = database.tunnelConfigDao()
    private val engine = TunnelEngine.instance

    val allConfigs: StateFlow<List<TunnelConfig>> = dao.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tunnelState: StateFlow<TunnelState> = engine.tunnelState
    val logs: StateFlow<List<LogEntry>> = engine.logs

    private val _selectedConfig = MutableStateFlow<TunnelConfig?>(null)
    val selectedConfig: StateFlow<TunnelConfig?> = _selectedConfig.asStateFlow()

    // Dialog & Sheet states
    val showConfigEditor = MutableStateFlow(false)
    val editingConfig = MutableStateFlow<TunnelConfig?>(null)

    val showServersSheet = MutableStateFlow(false)
    val showPayloadGenerator = MutableStateFlow(false)
    val showToolsDialog = MutableStateFlow(false)
    val showSettingsDialog = MutableStateFlow(false)
    val showImportExportDialog = MutableStateFlow(false)

    // Diagnostics State
    val testPingResult = MutableStateFlow<String?>(null)
    val isTestingPing = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Inicializar configs si está vacío
            AppDatabase.populateInitialConfigs(dao)
            val def = dao.getDefaultConfig()
            if (def != null) {
                _selectedConfig.value = def
            }
        }
    }

    fun selectConfig(config: TunnelConfig) {
        _selectedConfig.value = config
        viewModelScope.launch(Dispatchers.IO) {
            dao.resetDefaults()
            dao.setDefault(config.id)
        }
    }

    fun toggleConnection(context: Context) {
        val currentStatus = tunnelState.value.status
        if (currentStatus is ConnectionStatus.Connected || currentStatus is ConnectionStatus.Connecting || currentStatus is ConnectionStatus.Authenticating || currentStatus is ConnectionStatus.Reconnecting) {
            disconnect(context)
        } else {
            val config = _selectedConfig.value ?: allConfigs.value.firstOrNull()
            if (config != null) {
                connect(context, config)
            }
        }
    }

    fun connect(context: Context, config: TunnelConfig) {
        engine.startTunnel(context, config)
        DTunnelVpnService.startService(context, config)
    }

    fun getDeviceHwid(): String {
        return com.example.util.HwidManager.getHwid(getApplication())
    }

    fun copyDeviceHwid(context: Context): String {
        return com.example.util.HwidManager.copyHwidToClipboard(context)
    }

    fun disconnect(context: Context) {
        DTunnelVpnService.stopService(context)
        engine.stopTunnel()
    }

    fun clearLogs() {
        engine.clearLogs()
    }

    fun saveConfig(config: TunnelConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            if (config.id == 0L) {
                val newId = dao.insertConfig(config)
                _selectedConfig.value = config.copy(id = newId)
            } else {
                dao.updateConfig(config)
                if (_selectedConfig.value?.id == config.id) {
                    _selectedConfig.value = config
                }
            }
        }
    }

    fun deleteConfig(config: TunnelConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteConfig(config)
            if (_selectedConfig.value?.id == config.id) {
                _selectedConfig.value = allConfigs.value.firstOrNull { it.id != config.id }
            }
        }
    }

    fun importConfigDetailed(text: String): ConfigExporter.ImportResult {
        val result = ConfigExporter.importConfigDetailed(text)
        if (result is ConfigExporter.ImportResult.Success) {
            saveConfig(result.config)
            _selectedConfig.value = result.config
        }
        return result
    }

    fun importConfigFromString(text: String): Boolean {
        val result = importConfigDetailed(text)
        return result is ConfigExporter.ImportResult.Success
    }

    fun exportCurrentConfig(): String {
        val config = _selectedConfig.value ?: return ""
        return ConfigExporter.exportConfig(config)
    }

    fun exportConfigCustom(config: TunnelConfig): String {
        return ConfigExporter.exportConfig(config)
    }

    fun runHostPing(host: String, port: Int) {
        viewModelScope.launch {
            isTestingPing.value = true
            testPingResult.value = "Probando conexión con $host:$port..."
            val result = NetworkDiagnostics.checkRealPing(host, port, 4000)
            isTestingPing.value = false
            testPingResult.value = if (result >= 0) {
                "✓ Éxito: Latencia $result ms hacia $host:$port"
            } else {
                "✗ Error: No se pudo conectar a $host:$port (Timeout o cerrado)"
            }
        }
    }

    fun refreshPublicIp() {
        viewModelScope.launch {
            val ipInfo = NetworkDiagnostics.fetchPublicIpInfo()
            engine.log("IP actualizada: ${ipInfo.ip} (${ipInfo.region})", com.example.data.model.LogLevel.INFO)
        }
    }
}
