package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.TunnelConfig
import java.io.File
import java.io.InputStream

object FileHandlerHelper {

    /**
     * Exporta la configuración a un archivo físico .dtun y genera un Intent para compartirlo por WhatsApp, Telegram, etc.
     */
    fun shareConfigFile(context: Context, config: TunnelConfig): Intent? {
        return try {
            val content = ConfigExporter.exportConfig(config)
            val cleanName = config.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val fileName = "$cleanName.dtun"

            val configsDir = File(context.cacheDir, "configs").apply { mkdirs() }
            val file = File(configsDir, fileName)
            file.writeText(content, Charsets.UTF_8)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Perfil DTunnel: ${config.name}")
                putExtra(Intent.EXTRA_TEXT, "Configuración DTunnel (${config.name})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lee el contenido de un archivo a partir de un Uri (por ejemplo, desde un Intent de WhatsApp o Telegram).
     */
    fun readConfigFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
}
