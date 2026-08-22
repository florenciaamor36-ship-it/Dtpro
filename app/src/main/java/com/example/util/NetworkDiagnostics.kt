package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class IpInfo(
    val ip: String,
    val country: String = "",
    val region: String = "",
    val org: String = ""
)

object NetworkDiagnostics {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun checkRealPing(host: String, port: Int = 80, timeoutMs: Int = 3000): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        }
    }

    suspend fun fetchPublicIpInfo(): IpInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", "DTunnel-Client/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val ip = json.optString("ip", "---")
                    val country = json.optString("country_name", "")
                    val city = json.optString("city", "")
                    val org = json.optString("org", "")
                    val loc = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
                    return@withContext IpInfo(ip = ip, country = country, region = loc, org = org)
                }
            }
        } catch (_: Exception) {}

        try {
            // Fallback rápido con api.ipify.org
            val request = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val ip = json.optString("ip", "---")
                    return@withContext IpInfo(ip = ip, country = "Online", region = "Direct", org = "ISP")
                }
            }
        } catch (_: Exception) {}

        IpInfo(ip = "---", country = "Desconectado", region = "Local", org = "")
    }
}
