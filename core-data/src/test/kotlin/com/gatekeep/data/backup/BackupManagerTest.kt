package com.gatekeep.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupManagerTest {

    @Test
    fun `export import round trip preserves v3 profile fields`() {
        val backup = ProfileBackup(
            profile = ProfileBackupData(
                name = "Work",
                lockEnabled = true,
                autoScheduleEnabled = false,
                defaultFrictionMethod = "math",
                defaultFrictionDifficulty = "medium",
                delayOpenSeconds = 0,
                gradualTighteningEnabled = false,
                gradualTighteningTargetDailyMs = null,
                gradualTighteningPercentPerWeek = 5,
                dailyLimitMs = 3_600_000,
                hourlyLimitMs = 1_800_000,
                weeklyLimitMs = 25_200_000,
                sessionLimitMs = 900_000,
                breakDurationMs = 300_000,
                limitBreakDurationMs = 600_000,
                openWaitDurationSeconds = 30,
                sessionWaitDurationSeconds = 45,
                limitWaitDurationSeconds = 60,
                limitUsageScope = "sharedPool",
            ),
            monitoredApps = listOf(
                MonitoredAppBackup("com.test", "Test", "other", false),
            ),
            limits = listOf(
                AppLimitBackup("com.test", 3_600_000, null, null, null, null, true, null, null, 0),
            ),
            scheduleSegments = listOf(
                ScheduleSegmentBackup(localId = 1, label = "Focus", mode = "block", sortOrder = 0),
            ),
            scheduleWindows = listOf(
                ScheduleWindowBackup(segmentLocalId = 1, packageName = null, dayOfWeek = 1, startMinute = 540, endMinute = 1020, isProfileAutoSwitch = false),
            ),
        )

        val json = BackupManager.export(listOf(backup))
        val restored = BackupManager.import(json)

        assertEquals(3, restored.version)
        assertEquals(1, restored.profiles.size)
        val profile = restored.profiles.first().profile
        assertEquals("Work", profile.name)
        assertEquals(3_600_000L, profile.dailyLimitMs)
        assertEquals("sharedPool", profile.limitUsageScope)
        assertEquals(600_000L, profile.limitBreakDurationMs)
    }

    @Test
    fun `v2 backup decodes with defaults for new fields`() {
        val legacyJson = """
            {
              "version": 2,
              "profiles": [{
                "profile": {
                  "name": "Legacy",
                  "lockEnabled": false,
                  "autoScheduleEnabled": false,
                  "defaultFrictionMethod": "none",
                  "defaultFrictionDifficulty": "easy",
                  "delayOpenSeconds": 0,
                  "gradualTighteningEnabled": false,
                  "gradualTighteningTargetDailyMs": null,
                  "gradualTighteningPercentPerWeek": 5
                },
                "monitoredApps": [],
                "limits": [],
                "scheduleWindows": []
              }]
            }
        """.trimIndent()

        val restored = BackupManager.import(legacyJson)
        assertEquals("perApp", restored.profiles.first().profile.limitUsageScope)
    }
}
