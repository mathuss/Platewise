package com.countmymacros.data

import android.content.res.Resources
import java.util.Locale

/** The language the app is showing right now, as used for data (AI output, food names). */
object Lang {
    /** "es" or "en": the two languages the app ships. Anything else falls back to English. */
    val current: String
        get() = if (Locale.getDefault().language == "es") "es" else "en"

    /**
     * Open Food Facts country tag for the phone's region (from the system locale, not the
     * app language), e.g. "en:argentina". Null when the region is unknown.
     */
    fun offCountryTag(): String? {
        val country = Resources.getSystem().configuration.locales[0].country
        if (country.isBlank()) return null
        val name = Locale("", country).getDisplayCountry(Locale.ENGLISH)
        return "en:" + name.lowercase(Locale.ROOT).replace(' ', '-')
    }
}
