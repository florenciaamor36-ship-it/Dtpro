package com.example.util

import com.example.data.model.TunnelConfig
import com.example.data.model.TunnelMode
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object ConfigExporter {

    private const val AES_KEY_STRING = "DTUNNEL_SECRET_AES_KEY_2026_MGR"

    sealed class ImportResult {
        data class Success(
            val config: TunnelConfig,
            val message: String
        ) : ImportResult()

        data class Error(val reason: String) : ImportResult()
    }

    /**
     * Exporta un perfil en formato dtunnel:// con opciones avanzadas de bloqueo, expiración y HWID.
     */
    fun exportConfig(config: TunnelConfig): String {
        val rootJson = JSONObject().apply {
            put("v", 3)
            put("name", config.name)
            put("mode", config.mode.name)
            put("serverHost", config.serverHost)
            put("serverPort", config.serverPort)
            put("isLocked", config.isLocked)
            put("expiryTimestamp", config.expiryTimestamp)
            put("allowedHwids", config.allowedHwids)
            put("vpsAuthUrl", config.vpsAuthUrl)
            put("creatorNote", config.creatorNote)
            put("isUdpForwarding", config.isUdpForwarding)
            put("dnsPrimary", config.dnsPrimary)
            put("dnsSecondary", config.dnsSecondary)
            put("autoReconnect", config.autoReconnect)

            // Bloqueos Adicionales
            put("blockRoot", config.blockRoot)
            put("allowedCarriers", config.allowedCarriers)
            put("blockWifi", config.blockWifi)
            put("blockMobileData", config.blockMobileData)
            put("blockSniffers", config.blockSniffers)
            put("blockHotshare", config.blockHotshare)
            put("lockPassword", config.lockPassword)
            put("showToastOnConnect", config.showToastOnConnect)

            if (config.isLocked) {
                // Encriptar credenciales y payload si el archivo está cerrado
                val sensitiveJson = JSONObject().apply {
                    put("username", config.username)
                    put("password", config.password)
                    put("customPayload", config.customPayload)
                    put("sniHost", config.sniHost)
                    put("proxyHost", config.proxyHost)
                    put("proxyPort", config.proxyPort)
                }
                val encrypted = encryptAes(sensitiveJson.toString())
                put("secPayload", encrypted)
            } else {
                put("username", config.username)
                put("password", config.password)
                put("customPayload", config.customPayload)
                put("sniHost", config.sniHost)
                put("proxyHost", config.proxyHost)
                put("proxyPort", config.proxyPort)
            }
        }

        val encoded = Base64.getEncoder().encodeToString(rootJson.toString().toByteArray(Charsets.UTF_8))
        return "dtunnel://$encoded"
    }

    /**
     * Importa y valida la estructura de un archivo de configuración.
     */
    fun importConfigDetailed(rawText: String): ImportResult {
        try {
            val clean = rawText.trim()
                .removePrefix("dtunnel://")
                .removePrefix("dtunnel:")
                .trim()

            if (clean.isBlank()) {
                return ImportResult.Error("El texto o archivo está vacío.")
            }

            val decodedBytes = try {
                Base64.getDecoder().decode(clean)
            } catch (_: Exception) {
                return ImportResult.Error("Formato de codificación inválido (no es Base64 válido).")
            }

            val jsonString = String(decodedBytes, Charsets.UTF_8)
            val rootJson = JSONObject(jsonString)

            val name = rootJson.optString("name", "Configuración Importada")
            val modeName = rootJson.optString("mode", TunnelMode.SSH_DIRECT.name)
            val mode = try { TunnelMode.valueOf(modeName) } catch (_: Exception) { TunnelMode.SSH_DIRECT }

            val serverHost = rootJson.optString("serverHost", "")
            val serverPort = rootJson.optInt("serverPort", 22)
            val isLocked = rootJson.optBoolean("isLocked", false)
            val expiryTimestamp = rootJson.optLong("expiryTimestamp", 0L)
            val allowedHwids = rootJson.optString("allowedHwids", "")
            val vpsAuthUrl = rootJson.optString("vpsAuthUrl", "")
            val creatorNote = rootJson.optString("creatorNote", "")
            val isUdpForwarding = rootJson.optBoolean("isUdpForwarding", true)
            val dnsPrimary = rootJson.optString("dnsPrimary", "8.8.8.8")
            val dnsSecondary = rootJson.optString("dnsSecondary", "8.8.4.4")
            val autoReconnect = rootJson.optBoolean("autoReconnect", true)

            // Bloqueos Adicionales
            val blockRoot = rootJson.optBoolean("blockRoot", false)
            val allowedCarriers = rootJson.optString("allowedCarriers", "")
            val blockWifi = rootJson.optBoolean("blockWifi", false)
            val blockMobileData = rootJson.optBoolean("blockMobileData", false)
            val blockSniffers = rootJson.optBoolean("blockSniffers", false)
            val blockHotshare = rootJson.optBoolean("blockHotshare", false)
            val lockPassword = rootJson.optString("lockPassword", "")
            val showToastOnConnect = rootJson.optString("showToastOnConnect", "")

            var username = ""
            var password = ""
            var customPayload = ""
            var sniHost = ""
            var proxyHost = ""
            var proxyPort = 8080

            if (isLocked && rootJson.has("secPayload")) {
                val encrypted = rootJson.getString("secPayload")
                val decryptedJson = decryptAes(encrypted)
                if (decryptedJson != null) {
                    val secObj = JSONObject(decryptedJson)
                    username = secObj.optString("username", "")
                    password = secObj.optString("password", "")
                    customPayload = secObj.optString("customPayload", "")
                    sniHost = secObj.optString("sniHost", "")
                    proxyHost = secObj.optString("proxyHost", "")
                    proxyPort = secObj.optInt("proxyPort", 8080)
                } else {
                    return ImportResult.Error("No se pudo descifrar el contenido protegido del archivo.")
                }
            } else {
                username = rootJson.optString("username", "")
                password = rootJson.optString("password", "")
                customPayload = rootJson.optString("customPayload", "")
                sniHost = rootJson.optString("sniHost", "")
                proxyHost = rootJson.optString("proxyHost", "")
                proxyPort = rootJson.optInt("proxyPort", 8080)
            }

            val config = TunnelConfig(
                name = name,
                mode = mode,
                serverHost = serverHost,
                serverPort = serverPort,
                username = username,
                password = password,
                customPayload = customPayload,
                sniHost = sniHost,
                proxyHost = proxyHost,
                proxyPort = proxyPort,
                isUdpForwarding = isUdpForwarding,
                dnsPrimary = dnsPrimary,
                dnsSecondary = dnsSecondary,
                autoReconnect = autoReconnect,
                isLocked = isLocked,
                expiryTimestamp = expiryTimestamp,
                allowedHwids = allowedHwids,
                vpsAuthUrl = vpsAuthUrl,
                creatorNote = creatorNote,
                blockRoot = blockRoot,
                allowedCarriers = allowedCarriers,
                blockWifi = blockWifi,
                blockMobileData = blockMobileData,
                blockSniffers = blockSniffers,
                blockHotshare = blockHotshare,
                lockPassword = lockPassword,
                showToastOnConnect = showToastOnConnect
            )

            val statusDetails = buildString {
                append("✓ Perfil '$name' importado correctamente.")
                if (isLocked) append(" [🔒 Bloqueado]")
                if (blockRoot) append(" [🚫 No Root]")
                if (allowedCarriers.isNotBlank()) append(" [📶 $allowedCarriers]")
                if (blockWifi) append(" [📱 Solo Datos]")
                if (blockMobileData) append(" [📡 Solo Wi-Fi]")
                if (expiryTimestamp > 0) {
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    append(" Expira: ${sdf.format(Date(expiryTimestamp))}")
                }
                if (creatorNote.isNotBlank()) {
                    append("\nMensaje: $creatorNote")
                }
            }

            return ImportResult.Success(config, statusDetails)

        } catch (e: Exception) {
            return ImportResult.Error("Error al procesar el perfil: ${e.message ?: "Estructura desconocida"}")
        }
    }

    /**
     * Mantiene retrocompatibilidad con la firma clásica TunnelConfig?
     */
    fun importConfig(rawText: String): TunnelConfig? {
        return when (val res = importConfigDetailed(rawText)) {
            is ImportResult.Success -> res.config
            is ImportResult.Error -> null
        }
    }

    private fun encryptAes(plainText: String): String {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(AES_KEY_STRING.toByteArray(Charsets.UTF_8))
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(encryptedBytes)
        } catch (_: Exception) {
            Base64.getEncoder().encodeToString(plainText.toByteArray(Charsets.UTF_8))
        }
    }

    private fun decryptAes(cipherText: String): String? {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(AES_KEY_STRING.toByteArray(Charsets.UTF_8))
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.getDecoder().decode(cipherText)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                // Fallback a Base64 estándar si no tenía clave AES
                val fallbackBytes = Base64.getDecoder().decode(cipherText)
                String(fallbackBytes, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }
}
