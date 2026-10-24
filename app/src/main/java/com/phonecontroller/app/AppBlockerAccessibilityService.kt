package com.phonecontroller.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app is in the foreground. If that app has a daily limit set
 * (via [AppLimitsStore]) and today's usage has already reached it, backs out
 * to the home screen -- both immediately on switching to it, and on a
 * recurring check while it stays in the foreground (so staying inside one
 * app past the limit still gets caught).
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val RECHECK_INTERVAL_MS = 15_000L

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expectedComponent = "${context.packageName}/${AppBlockerAccessibilityService::class.java.name}"
            return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentForegroundPackage: String? = null

    private val recheckRunnable = object : Runnable {
        override fun run() {
            currentForegroundPackage?.let { enforceLimit(it) }
            handler.postDelayed(this, RECHECK_INTERVAL_MS)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == currentForegroundPackage) return

        currentForegroundPackage = foregroundPackage
        handler.removeCallbacks(recheckRunnable)
        enforceLimit(foregroundPackage)

        if (AppLimitsStore.getLimitMinutes(this, foregroundPackage) != null) {
            handler.postDelayed(recheckRunnable, RECHECK_INTERVAL_MS)
        }
    }

    private fun enforceLimit(packageName: String) {
        val limitMinutes = AppLimitsStore.getLimitMinutes(this, packageName) ?: return
        val usedMillis = UsageStatsHelper.getTodayUsage(this)
            .firstOrNull { it.packageName == packageName }
            ?.foregroundMillis ?: 0L

        if (usedMillis >= limitMinutes * 60_000L) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.removeCallbacks(recheckRunnable)
        }
    }

    override fun onInterrupt() {
        // Required override; nothing to clean up.
    }
}
