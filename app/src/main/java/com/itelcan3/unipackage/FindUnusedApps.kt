package com.itelcan3.unipackage

import android.Manifest
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import androidx.annotation.RequiresPermission

data class LeastUsedApp(
    val packageName: String,
    val appName: String,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long,
    val appSize: Long
)

class LeastUsedAppsReader(
    private val context: Context
) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    
    private val storageStatsManager =
        context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager

    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    fun getLeastUsedApps(
        days: Int = 180,
        AppListlimit: Int = 15
    ): List<LeastUsedApp> {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - days.toLong() * 24 * 60 * 60 * 1000

        // Get usage stats for the period
        val statsMap: Map<String, UsageStats> =
            usageStatsManager.queryAndAggregateUsageStats(
                startTime,
                endTime
            )

        // Get all installed apps
        val installedApps = context.packageManager.getInstalledApplications(0)

        return installedApps
            .filter { appInfo ->
                // Filter out system apps and the app itself
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isSelf = appInfo.packageName == context.packageName
                !isSystem && !isSelf
            }
            .mapNotNull { appInfo ->
                try {
                    val packageName = appInfo.packageName
                    val usageStat = statsMap[packageName]
                    
                    val totalTime = usageStat?.totalTimeInForeground ?: 0L
                    val lastUsed = usageStat?.lastTimeUsed ?: 0L

                    val appName = context.packageManager
                        .getApplicationLabel(appInfo)
                        .toString()

                    val storageStats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        packageName,
                        Process.myUserHandle()
                    )
                    
                    val totalSize = storageStats.appBytes + storageStats.dataBytes + storageStats.cacheBytes

                    LeastUsedApp(
                        packageName = packageName,
                        appName = appName,
                        totalTimeInForeground = totalTime,
                        lastTimeUsed = lastUsed,
                        appSize = totalSize
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedWith(compareBy({ it.totalTimeInForeground }, { it.lastTimeUsed }))
            .take(AppListlimit)
    }
}