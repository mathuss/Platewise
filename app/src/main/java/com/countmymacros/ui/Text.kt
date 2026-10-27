package com.countmymacros.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.countmymacros.R
import com.countmymacros.data.Entry
import com.countmymacros.data.FoodSource
import com.countmymacros.data.GRAMS
import com.countmymacros.data.Macros
import com.countmymacros.data.Prefs
import com.countmymacros.data.fmt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Text produced outside the UI (ViewModel messages, errors) as resource ids, resolved when
 * shown, so it always comes out in the language the screen is in. Args may be UiText too.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any>) : UiText

    fun resolve(context: Context): String {
        fun arg(a: Any): Any = if (a is UiText) a.resolve(context) else a
        return when (this) {
            is Res -> context.getString(id, *args.map(::arg).toTypedArray())
            is Plural -> context.resources.getQuantityString(id, count, *args.map(::arg).toTypedArray())
        }
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

@Composable
fun UiText.text(): String = resolve(LocalContext.current)

/**
 * The in-app language choice: "" (system), "en" or "es". Android 13+ handles it natively
 * (per-app language, also in system Settings); older versions get a context wrapper.
 */
object AppLanguage {
    fun get(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) "" else locales[0].language
        } else Prefs(context).language

    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            Prefs(activity).language = tag
            activity.recreate()
        }
    }

    /** For Activity.attachBaseContext before Android 13. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = Prefs(base).language
        val locale = if (tag.isEmpty()) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}

@Composable
fun macroSummary(m: Macros): String =
    stringResource(R.string.macro_summary, fmt(m.protein), fmt(m.carbs), fmt(m.fat))

@Composable
fun amountLabel(e: Entry): String = when {
    e.servingName == GRAMS.name -> stringResource(R.string.amount_grams, fmt(e.quantity))
    e.source == FoodSource.QUICK -> stringResource(R.string.amount_quick, fmt(e.quantity))
    else -> stringResource(R.string.amount_servings, fmt(e.quantity), e.servingName, fmt(e.grams))
}

@Composable
fun fullDate(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern), Locale.getDefault()))
        .replaceFirstChar { it.uppercase() }

/** True for the relative labels (Today/Yesterday/Tomorrow), where [dayLabel] hides the actual date. */
fun isRelativeDay(d: LocalDate): Boolean {
    val today = LocalDate.now()
    return d == today || d == today.minusDays(1) || d == today.plusDays(1)
}

@Composable
fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> fullDate(d)
    }
}
