package com.phonecontroller.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app is in the foreground and, if it's on [blockedPackages],
 * backs out to the home screen. This is the extension point for real
 * app-limit / blocking logic (e.g. only block after a time budget is used up).
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        // In-memory for now; swap for a persisted store (DataStore/Room) once
        // there's a UI to edit this list.
        val blockedPackages = mutableSetOf<String>()

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expectedComponent = "${context.packageName}/${AppBlockerAccessibilityService::class.java.name}"
            return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foregroundPackage = event.packageName?.toString() ?: return

        if (foregroundPackage in blockedPackages) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        // Required override; nothing to clean up.
    }
}
