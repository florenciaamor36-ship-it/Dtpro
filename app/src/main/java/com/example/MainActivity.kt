package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.ui.TunnelViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ConfigExporter
import com.example.util.FileHandlerHelper

class MainActivity : ComponentActivity() {

    private val viewModel: TunnelViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val conf = viewModel.selectedConfig.value ?: viewModel.allConfigs.value.firstOrNull()
            if (conf != null) {
                viewModel.connect(this, conf)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onPrepareVpn = { prepareVpn() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val action = incomingIntent.action
        val data: Uri? = incomingIntent.data

        if (Intent.ACTION_VIEW == action && data != null) {
            val scheme = data.scheme
            if (scheme == "dtunnel") {
                val fullUri = data.toString()
                viewModel.importConfigFromString(fullUri)
            } else if (scheme == "file" || scheme == "content") {
                val fileContent = FileHandlerHelper.readConfigFromUri(this, data)
                if (!fileContent.isNullOrBlank()) {
                    viewModel.importConfigFromString(fileContent.trim())
                }
            }
        }
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        }
    }
}
