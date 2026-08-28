package com.gatekeep.app.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.gatekeep.data.locale.LocalePreferences

object LocaleController {
    val supportedTags = listOf("en-GB", "de-AT", "es-ES")

    fun normalizeTag(tag: String?): String =
        tag?.takeIf { it in LocalePreferences.SUPPORTED_TAGS } ?: LocalePreferences.DEFAULT_TAG

    fun apply(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalizeTag(languageTag)),
        )
    }

    fun applyStored(context: Context) {
        apply(LocalePreferences.read(context))
    }

    fun flagForTag(tag: String): String = when (normalizeTag(tag)) {
        "de-AT" -> "🇦🇹"
        "es-ES" -> "🇪🇸"
        else -> "🇬🇧"
    }

    /** Native language name — always shown in that language, not translated. */
    fun nativeLabelForTag(tag: String): String = when (normalizeTag(tag)) {
        "de-AT" -> "Deutsch"
        "es-ES" -> "Español"
        else -> "English"
    }
}
