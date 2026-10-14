package com.gatekeep.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                sessionRefillOverrideMs = 480_000,
                periodContinueInheritSessionCap = false,
                periodContinueDurationMs = 900_000,
                openFrictionDifficulty = "easy",
                periodFrictionDifficulty = "medium",
                sessionFrictionDifficulty = "hard",
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
        assertEquals(480_000L, profile.sessionRefillOverrideMs)
        assertEquals(false, profile.periodContinueInheritSessionCap)
        assertEquals(900_000L, profile.periodContinueDurationMs)
        assertEquals("easy", profile.openFrictionDifficulty)
        assertEquals("medium", profile.periodFrictionDifficulty)
        assertEquals("hard", profile.sessionFrictionDifficulty)
    }

    @Test
    fun `entity backup restore round trip preserves new grant fields`() {
        val entity = com.gatekeep.data.local.entity.ProfileEntity(
            id = 4,
            name = "Work",
            isActive = true,
            sessionLimitMs = 1_200_000,
            sessionRefillOverrideMs = 600_000,
            periodContinueInheritSessionCap = false,
            periodContinueDurationMs = 900_000,
            openFrictionDifficulty = "easy",
            periodFrictionDifficulty = "hard",
            sessionFrictionDifficulty = "medium",
            defaultFrictionDifficulty = "medium",
        )
        val json = BackupManager.export(
            listOf(
                BackupManager.profileToBackup(
                    profile = entity,
                    apps = emptyList(),
                    limits = emptyList(),
                    segments = emptyList(),
                    windows = emptyList(),
                ),
            ),
        )
        val restored = BackupManager.backupToProfile(BackupManager.import(json).profiles.first().profile)
        assertEquals(entity.sessionRefillOverrideMs, restored.sessionRefillOverrideMs)
        assertEquals(entity.periodContinueInheritSessionCap, restored.periodContinueInheritSessionCap)
        assertEquals(entity.periodContinueDurationMs, restored.periodContinueDurationMs)
        assertEquals("easy", restored.openFrictionDifficulty)
        assertEquals("hard", restored.periodFrictionDifficulty)
        assertEquals("medium", restored.sessionFrictionDifficulty)
        assertNull(BackupManager.backupToProfile(
            BackupManager.import(json).profiles.first().profile.copy(sessionRefillOverrideMs = null),
        ).sessionRefillOverrideMs)
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
        val profile = restored.profiles.first().profile
        assertEquals("perApp", profile.limitUsageScope)
        assertEquals(null, profile.sessionRefillOverrideMs)
        assertEquals(null, profile.periodContinueInheritSessionCap)
        assertEquals(900_000L, profile.periodContinueDurationMs)
        val entity = BackupManager.backupToProfile(profile)
        assertEquals(true, entity.periodContinueInheritSessionCap)
        assertEquals("easy", entity.openFrictionDifficulty)
        assertEquals("easy", entity.periodFrictionDifficulty)
        assertEquals("easy", entity.sessionFrictionDifficulty)
    }

    @Test
    fun `legacy backup with session cap restores continue inherit on`() {
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
                  "gradualTighteningPercentPerWeek": 5,
                  "sessionLimitMs": 900000
                },
                "monitoredApps": [],
                "limits": [],
                "scheduleWindows": []
              }]
            }
        """.trimIndent()

        val entity = BackupManager.backupToProfile(
            BackupManager.import(legacyJson).profiles.first().profile,
        )
        assertEquals(true, entity.periodContinueInheritSessionCap)
        assertEquals(900_000L, entity.sessionLimitMs)
    }
}
