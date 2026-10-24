package com.phonecontroller.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long
)

object UsageStatsHelper {

    /** True once the user has granted "Usage access" for this app in system settings. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Per-app foreground time since local midnight today, sorted by most used first. */
    fun getTodayUsage(context: Context): List<AppUsageInfo> {
        if (!hasUsageAccess(context)) return emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startOfDay,
            now
        ) ?: return emptyList()

        val packageManager = context.packageManager

        return stats
            .filter { it.totalTimeInForeground > 0 }
            .mapNotNull { usageStat ->
                val label = try {
                    val appInfo = packageManager.getApplicationInfo(usageStat.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    usageStat.packageName
                }
                AppUsageInfo(
                    packageName = usageStat.packageName,
                    label = label,
                    foregroundMillis = usageStat.totalTimeInForeground
                )
            }
            .sortedByDescending { it.foregroundMillis }
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
