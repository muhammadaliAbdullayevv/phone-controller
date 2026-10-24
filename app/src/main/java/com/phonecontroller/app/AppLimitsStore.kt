package com.phonecontroller.app

import android.content.Context

/** Persists "packageName -> daily limit in minutes" across app restarts. */
object AppLimitsStore {
    private const val PREFS_NAME = "app_limits"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllLimits(context: Context): Map<String, Int> {
        val prefs = prefs(context)
        return prefs.all.mapNotNull { (packageName, minutes) ->
            (minutes as? Int)?.let { packageName to it }
        }.toMap()
    }

    fun getLimitMinutes(context: Context, packageName: String): Int? {
        val prefs = prefs(context)
        return if (prefs.contains(packageName)) prefs.getInt(packageName, 0) else null
    }

    fun setLimitMinutes(context: Context, packageName: String, minutes: Int) {
        prefs(context).edit().putInt(packageName, minutes).apply()
    }

    fun removeLimit(context: Context, packageName: String) {
        prefs(context).edit().remove(packageName).apply()
    }
}
