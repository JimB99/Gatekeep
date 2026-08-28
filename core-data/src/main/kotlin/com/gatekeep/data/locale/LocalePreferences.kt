package com.gatekeep.data.locale

import android.content.Context

object LocalePreferences {
    const val DEFAULT_TAG = "en-GB"

    private const val PREFS = "gatekeep_locale"
    private const val KEY = "language_tag"

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, DEFAULT_TAG)
            ?.takeIf { it in SUPPORTED_TAGS }
            ?: DEFAULT_TAG

    fun write(context: Context, languageTag: String) {
        val normalized = normalizeTag(languageTag)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, normalized)
            .commit()
    }

    fun normalizeTag(tag: String?): String =
        tag?.takeIf { it in SUPPORTED_TAGS } ?: DEFAULT_TAG

    val SUPPORTED_TAGS = setOf("en-GB", "de-AT", "es-ES")
}
