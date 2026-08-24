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

    private fun queryInstalledApps(): List<InstalledAppEntry> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter {
                it.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                    it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            }
            .map { InstalledAppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}
