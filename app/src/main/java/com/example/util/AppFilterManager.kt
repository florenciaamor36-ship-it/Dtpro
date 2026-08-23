package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

object AppFilterManager {
    private const val PREFS_NAME = "dtunnel_app_filter_prefs"
    private const val KEY_FILTER_ENABLED = "key_filter_enabled"
    private const val KEY_FILTER_MODE = "key_filter_mode" // "INCLUDE" o "EXCLUDE"
    private const val KEY_SELECTED_APPS = "key_selected_apps"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFilterEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FILTER_ENABLED, false)
    }

    fun setFilterEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FILTER_ENABLED, enabled).apply()
    }

    fun getFilterMode(context: Context): String {
        return getPrefs(context).getString(KEY_FILTER_MODE, "EXCLUDE") ?: "EXCLUDE"
    }

    fun setFilterMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_FILTER_MODE, mode).apply()
    }

    fun getSelectedApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }

    fun setSelectedApps(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_SELECTED_APPS, packages).apply()
    }

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<InstalledAppInfo>()

        for (app in apps) {
            // Ignorar nuestra propia app
            if (app.packageName == context.packageName) continue

            val appName = pm.getApplicationLabel(app).toString()
            val isSys = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            result.add(
                InstalledAppInfo(
                    packageName = app.packageName,
                    appName = appName,
                    isSystemApp = isSys
                )
            )
        }

        return result.sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
    }
}
