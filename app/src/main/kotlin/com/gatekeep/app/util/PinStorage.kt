package com.gatekeep.app.util

import android.content.Context
import android.content.SharedPreferences
import android.security.KeyStoreException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = openEncryptedPrefs(context)

    fun getAppPin(): String? = prefs.getString(KEY_APP_PIN, null)?.takeIf { it.isNotBlank() }

    fun setAppPin(pin: String) {
        prefs.edit().putString(KEY_APP_PIN, pin).apply()
    }

    fun clearAppPin() {
        prefs.edit().remove(KEY_APP_PIN).apply()
    }

    fun getProfilePin(profileId: Long): String? =
        prefs.getString(profileKey(profileId), null)?.takeIf { it.isNotBlank() }

    fun setProfilePin(profileId: Long, pin: String) {
        prefs.edit().putString(profileKey(profileId), pin).apply()
    }

    fun clearProfilePin(profileId: Long) {
        prefs.edit().remove(profileKey(profileId)).apply()
    }

    private fun profileKey(profileId: Long) = "profile_pin_$profileId"

    companion object {
        private const val TAG = "PinStorage"
        private const val PREFS_FILE = "gatekeep_pins"
        private const val KEY_APP_PIN = "app_pin"

        private fun openEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                createEncryptedPrefs(context)
            } catch (e: Exception) {
                if (!isRecoverableCryptoFailure(e)) throw e
                Log.w(TAG, "Encrypted pin prefs unreadable; clearing and recreating", e)
                context.deleteSharedPreferences(PREFS_FILE)
                createEncryptedPrefs(context)
            }
        }

        private fun createEncryptedPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        private fun isRecoverableCryptoFailure(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                when (current) {
                    is KeyStoreException,
                    is AEADBadTagException,
                    is GeneralSecurityException,
                    -> return true
                }
                current = current.cause
            }
            return false
        }
    }
}
