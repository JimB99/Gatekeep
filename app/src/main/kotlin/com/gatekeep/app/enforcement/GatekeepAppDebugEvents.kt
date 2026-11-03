package com.gatekeep.app.enforcement

import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI- and settings-origin debug trace events. Keeps [GatekeepDebugTrace] I/O out of ViewModels.
 */
@Singleton
class GatekeepAppDebugEvents @Inject constructor(
    private val debugTrace: GatekeepDebugTrace,
) {
    fun permissionBannerShown(missingKeys: String) {
        debugTrace.record(
            GatekeepDebugTrace.PERMISSION_BANNER_SHOWN,
            mapOf("missing" to missingKeys),
        )
    }

    fun profileSaved(profileId: Long) {
        debugTrace.record(
            GatekeepDebugTrace.PROFILE_SAVE,
            mapOf("profileId" to profileId.toString()),
        )
    }

    fun extensionReset(profileId: Long, dayStartMs: Long) {
        debugTrace.record(
            GatekeepDebugTrace.EXTENSION_RESET,
            mapOf(
                "profileId" to profileId.toString(),
                "dayStartMs" to dayStartMs.toString(),
            ),
        )
    }
}
