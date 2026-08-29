package com.gatekeep.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledAppEntry(val packageName: String, val label: String)

@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val _apps = MutableStateFlow<List<InstalledAppEntry>>(emptyList())
    val apps: StateFlow<List<InstalledAppEntry>> = _apps.asStateFlow()

    suspend fun loadIfNeeded(force: Boolean = false) {
        if (!force && _apps.value.isNotEmpty()) return
        mutex.withLock {
            if (!force && _apps.value.isNotEmpty()) return
            _apps.value = withContext(Dispatchers.IO) { queryInstalledApps() }
        }
    }

    suspend fun refresh() = loadIfNeeded(force = true)

    private fun isUserSelectableApp(packageName: String, pm: PackageManager): Boolean {
        if (pm.getLaunchIntentForPackage(packageName) == null) return false
        if (packageName == context.packageName) return false
        if (packageName.startsWith("com.android.")) return false
        if (packageName.startsWith("com.google.android.gms")) return false
        if (packageName.startsWith("com.google.android.gsf")) return false
        if (packageName.startsWith("com.samsung.android.app.telephonyui")) return false
        return true
    }

    private fun queryInstalledApps(): List<InstalledAppEntry> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { info ->
                val isUpdatedSystem = info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                val isUserApp = info.flags and ApplicationInfo.FLAG_SYSTEM == 0
                (isUserApp || isUpdatedSystem) && isUserSelectableApp(info.packageName, pm)
            }
            .map { InstalledAppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    fun filterVisibleApps(
        installed: List<InstalledAppEntry>,
        monitoredPackageNames: Set<String>,
    ): List<InstalledAppEntry> {
        val pm = context.packageManager
        return installed.filter { entry ->
            entry.packageName in monitoredPackageNames ||
                isUserSelectableApp(entry.packageName, pm)
        }
    }
}
