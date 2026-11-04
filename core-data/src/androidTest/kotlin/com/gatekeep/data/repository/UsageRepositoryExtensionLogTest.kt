package com.gatekeep.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gatekeep.data.local.GatekeepDatabase
import com.gatekeep.domain.ExtensionDisplayLogic
import com.gatekeep.domain.model.AppLimit
import com.gatekeep.domain.model.UsageSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Log fixture: usageDailyMs=11_776_964, first +15m explicit cap 12_720_000;
 * second +15m while usage below cap must raise displayCapAfterMs (was stuck at 12_720_000).
 */
@RunWith(AndroidJUnit4::class)
class UsageRepositoryExtensionLogTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: GatekeepDatabase
    private lateinit var repository: UsageRepository

    private val profileId = 1L
    private val packageName = "app.revanced.android.youtube"
    private val dayStartMs = 0L
    private val usageMs = 11_776_964L
    private val extensionMs = 15 * 60_000L
    private val baseDailyMs = 40 * 60_000L
    private val firstExplicitCapMs = 12_720_000L
    private val secondExplicitCapMs = firstExplicitCapMs + extensionMs

    private val limit = AppLimit(
        profileId = profileId,
        packageName = packageName,
        enabled = true,
        dailyLimitMs = baseDailyMs,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, GatekeepDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = UsageRepository(
            db.usageSessionDao(),
            db.usageAggregateDao(),
            db.sessionStateDao(),
            db.pauseDao(),
            db.overrideEventDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun logExtensionOverride_secondGrantRaisesDisplayCapAfterWhenUsageBelowPriorExplicitCap() = runBlocking {
        val usage = UsageSnapshot(dailyMs = usageMs)
        val anchors = ExtensionDisplayLogic.anchorsAtGrant(usage, limit)

        val first = repository.logExtensionOverride(
            packageName = packageName,
            profileId = profileId,
            extensionMs = extensionMs,
            anchors = anchors,
            usage = usage,
            limit = limit,
            dayStartMs = dayStartMs,
            hourStartMs = dayStartMs,
            weekStartMs = dayStartMs,
            sharedPool = false,
        )
        assertEquals(firstExplicitCapMs, first.displayCapAfterMs)

        val second = repository.logExtensionOverride(
            packageName = packageName,
            profileId = profileId,
            extensionMs = extensionMs,
            anchors = anchors,
            usage = usage,
            limit = limit,
            dayStartMs = dayStartMs,
            hourStartMs = dayStartMs,
            weekStartMs = dayStartMs,
            sharedPool = false,
        )
        assertEquals(firstExplicitCapMs, second.displayCapBeforeMs)
        assertEquals(secondExplicitCapMs, second.displayCapAfterMs)
        assertTrue(second.displayCapAfterMs!! > second.displayCapBeforeMs!!)
        assertEquals(secondExplicitCapMs, second.dailyMaxExplicitCapMs)
    }
}
