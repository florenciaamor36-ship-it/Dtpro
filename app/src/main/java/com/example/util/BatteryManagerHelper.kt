package com.example.util

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

object BatteryManagerHelper {

    private const val PREFS_NAME = "dtunnel_power_prefs"
    private const val KEY_WAKELOCK_ENABLED = "pref_wakelock_enabled"
    private const val KEY_BATTERY_SAVER_ENABLED = "pref_battery_saver_enabled"

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun isWakeLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WAKELOCK_ENABLED, true)
    }

    fun setWakeLockEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WAKELOCK_ENABLED, enabled).apply()
        if (enabled) {
            acquireWakeLock(context)
        } else {
            releaseWakeLock()
        }
    }

    fun isBatterySaverEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BATTERY_SAVER_ENABLED, false)
    }

    fun setBatterySaverEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BATTERY_SAVER_ENABLED, enabled).apply()
    }

    @Synchronized
    fun acquireWakeLock(context: Context) {
        if (!isWakeLockEnabled(context)) return
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "DTunnel::VpnConnectionWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 horas máximo de salvaguarda
            }

            // Adquirir WifiLock para evitar caídas de sockets en reposo
            if (wifiLock == null) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "DTunnel::VpnWifiLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
        wifiLock = null
    }
}
