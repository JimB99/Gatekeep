package com.gatekeep.app.util

import android.content.Context
import android.os.LocaleList
import com.gatekeep.data.locale.LocalePreferences
import java.util.Locale

fun Context.withAppLocale(): Context {
    val tag = LocalePreferences.read(this)
    val locale = Locale.forLanguageTag(tag)
    val config = resources.configuration
    config.setLocales(LocaleList(locale))
    return createConfigurationContext(config)
}

fun Context.appLocale(): Locale = Locale.forLanguageTag(LocalePreferences.read(this))
