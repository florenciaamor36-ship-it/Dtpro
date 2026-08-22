package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.data.model.TunnelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object HwidManager {

    private const val PREFS_NAME = "dtunnel_device_prefs"
    private const val KEY_CACHED_HWID = "cached_unique_hwid"

    /**
     * Obtiene o genera un HWID único, persistente y determinista por dispositivo.
     */
    fun getHwid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHED_HWID, null)
        if (!cached.isNullOrBlank()) {
            return cached
        }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) {
            ""
        }

        val hardwareFingerprint = buildString {
            append(androidId)
            append("|").append(Build.BOARD)
            append("|").append(Build.BRAND)
            append("|").append(Build.DEVICE)
            append("|").append(Build.HARDWARE)
            append("|").append(Build.MANUFACTURER)
            append("|").append(Build.MODEL)
            append("|").append(Build.PRODUCT)
        }

        val generatedHwid = try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(hardwareFingerprint.toByteArray(Charsets.UTF_8))
            val hex = hashBytes.joinToString("") { "%02X".format(it) }
            // Formatear los primeros 16 caracteres en grupos de 4: DT-XXXX-XXXX-XXXX
            val chunk1 = hex.substring(0, 4)
            val chunk2 = hex.substring(4, 8)
            val chunk3 = hex.substring(8, 12)
            val chunk4 = hex.substring(12, 16)
            "DT-$chunk1-$chunk2-$chunk3-$chunk4"
        } catch (_: Exception) {
            val random = UUID.randomUUID().toString().replace("-", "").uppercase().take(12)
            "DT-${random.substring(0, 4)}-${random.substring(4, 8)}-${random.substring(8, 12)}"
        }

        prefs.edit().putString(KEY_CACHED_HWID, generatedHwid).apply()
        return generatedHwid
    }

    /**
     * Copia el HWID al portapapeles del sistema y muestra confirmación al usuario.
     */
    fun copyHwidToClipboard(context: Context): String {
        val hwid = getHwid(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DTunnel HWID", hwid)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "HWID copiado: $hwid", Toast.LENGTH_SHORT).show()
        return hwid
    }

    /**
     * Valida si el archivo ha expirado por fecha y hora.
     */
    fun checkExpiry(config: TunnelConfig): Pair<Boolean, String> {
        if (config.expiryTimestamp <= 0L) {
            return Pair(true, "Sin límite de tiempo")
        }

        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val expiryDateStr = sdf.format(Date(config.expiryTimestamp))

        return if (now > config.expiryTimestamp) {
            Pair(false, "Este perfil expiró el $expiryDateStr. Contacta al administrador.")
        } else {
            Pair(true, "Vence el: $expiryDateStr")
        }
    }

    /**
     * Valida si el HWID del dispositivo está en la lista de HWIDs permitidos del archivo.
     */
    fun checkHwidPermission(context: Context, config: TunnelConfig): Pair<Boolean, String> {
        if (config.allowedHwids.isBlank()) {
            return Pair(true, "Acceso para todos los dispositivos")
        }

        val myHwid = getHwid(context).trim().uppercase()
        val allowedList = config.allowedHwids.split(",", ";", "\n", " ")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }

        if (allowedList.isEmpty()) {
            return Pair(true, "Acceso permitido")
        }

        return if (allowedList.contains(myHwid)) {
            Pair(true, "Dispositivo HWID autorizado")
        } else {
            Pair(false, "Dispositivo no autorizado. Tu HWID ($myHwid) no está en la lista permitida.")
        }
    }

    /**
     * Valida el HWID contra la VPS remota si la URL de validación está configurada.
     */
    suspend fun checkVpsValidation(context: Context, config: TunnelConfig): Pair<Boolean, String> {
        if (config.vpsAuthUrl.isBlank()) {
            return Pair(true, "Validación VPS no requerida")
        }

        val myHwid = getHwid(context)
        val url = if (config.vpsAuthUrl.contains("?")) {
            "${config.vpsAuthUrl}&hwid=$myHwid"
        } else {
            "${config.vpsAuthUrl}?hwid=$myHwid"
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(6, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "DTunnel-Validator/2.0")
                    .header("X-Device-HWID", myHwid)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()?.trim() ?: ""

                if (response.isSuccessful) {
                    val upperBody = body.uppercase()
                    if (upperBody.contains("ACTIVE") || upperBody.contains("TRUE") || upperBody.contains("OK") || upperBody.contains("ALLOW") || upperBody.contains("SUCCESS")) {
                        Pair(true, "HWID validado correctamente en la VPS")
                    } else if (upperBody.contains("EXPIRED")) {
                        Pair(false, "Tu cuenta o HWID ha expirado en la VPS.")
                    } else if (upperBody.contains("DENIED") || upperBody.contains("DISABLED") || upperBody.contains("UNAUTHORIZED")) {
                        Pair(false, "Acceso denegado: HWID no registrado o bloqueado en la VPS.")
                    } else {
                        // Si la VPS respondió 200 con contenido genérico, considerarlo exitoso
                        Pair(true, "Validación VPS recibida ($body)")
                    }
                } else if (response.code == 403 || response.code == 401) {
                    Pair(false, "Acceso denegado por la VPS (Código ${response.code}). HWID no registrado.")
                } else {
                    Pair(false, "Error al validar con VPS: Servidor respondió código ${response.code}")
                }
            } catch (e: Exception) {
                Pair(false, "No se pudo contactar a la VPS de autenticación: ${e.message ?: "Sin conexión"}")
            }
        }
    }
}
