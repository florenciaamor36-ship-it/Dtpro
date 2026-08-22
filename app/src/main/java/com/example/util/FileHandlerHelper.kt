package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.model.TunnelConfig
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedConfigFile(
    val file: File,
    val name: String,
    val formattedDate: String,
    val sizeKb: String,
    val uri: Uri
)

object FileHandlerHelper {

    private fun getConfigsDir(context: Context): File {
        val dir = File(context.filesDir, "dtunnel_configs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Guarda la configuración de manera permanente en el almacenamiento del dispositivo.
     */
    fun saveConfigFileToStorage(context: Context, config: TunnelConfig): Pair<Boolean, String> {
        return try {
            val content = ConfigExporter.exportConfig(config)
            val cleanName = config.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "config" }
            val fileName = "$cleanName.dtun"

            val dir = getConfigsDir(context)
            val file = File(dir, fileName)
            file.writeText(content, Charsets.UTF_8)
            Pair(true, file.absolutePath)
        } catch (e: Exception) {
            Pair(false, e.message ?: "Error desconocido al guardar")
        }
    }

    /**
     * Obtiene la lista de todos los archivos .dtun guardados en el almacenamiento del dispositivo.
     */
    fun getSavedConfigsList(context: Context): List<SavedConfigFile> {
        return try {
            val dir = getConfigsDir(context)
            val files = dir.listFiles { _, name -> name.endsWith(".dtun", ignoreCase = true) } ?: emptyArray()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            files.sortedByDescending { it.lastModified() }.map { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val sizeKb = String.format(Locale.US, "%.1f KB", file.length() / 1024.0)
                SavedConfigFile(
                    file = file,
                    name = file.name,
                    formattedDate = dateFormat.format(Date(file.lastModified())),
                    sizeKb = sizeKb,
                    uri = uri
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Elimina un archivo guardado.
     */
    fun deleteSavedFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exporta la configuración a un archivo físico .dtun y genera un Intent para compartirlo.
     */
    fun shareConfigFile(context: Context, config: TunnelConfig): Intent? {
        return try {
            val content = ConfigExporter.exportConfig(config)
            val cleanName = config.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "config" }
            val fileName = "$cleanName.dtun"

            val dir = getConfigsDir(context)
            val file = File(dir, fileName)
            file.writeText(content, Charsets.UTF_8)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            createShareIntent(context, uri, config.name)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Comparte un archivo .dtun existente de la lista guardada.
     */
    fun shareExistingFile(context: Context, savedFile: SavedConfigFile): Intent {
        return createShareIntent(context, savedFile.uri, savedFile.name.removeSuffix(".dtun"))
    }

    private fun createShareIntent(context: Context, uri: Uri, title: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Perfil DTunnel: $title")
            putExtra(Intent.EXTRA_TEXT, "Configuración DTunnel ($title)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Lee el contenido de un archivo a partir de un Uri.
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
