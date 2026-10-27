package com.countmymacros.data

import androidx.annotation.StringRes
import com.countmymacros.R
import java.text.DecimalFormatSymbols
import kotlin.math.roundToInt

/** The five meals Fitia plans a day around (breakfast, midMorning, lunch, midAfternoon, dinner). */
enum class Meal(@StringRes val label: Int) {
    BREAKFAST(R.string.meal_breakfast),
    MID_MORNING(R.string.meal_mid_morning),
    LUNCH(R.string.meal_lunch),
    SNACK(R.string.meal_snack),
    DINNER(R.string.meal_dinner),
}

data class Macros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(o: Macros) =
        Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)

    fun scaled(f: Double) = Macros(kcal * f, protein * f, carbs * f, fat * f)

    companion object {
        val ZERO = Macros()
    }
}

/** A named portion and its weight, e.g. ("1 huevo", 50 g). Liquids use ml ≈ g. */
data class Serving(val name: String, val grams: Double)

/** Grams is always available as a serving; this is its canonical form. */
val GRAMS = Serving("g", 1.0)

enum class FoodSource(@StringRes val label: Int) {
    BASE(R.string.source_base),
    OFF(R.string.source_off),
    CUSTOM(R.string.source_custom),
    AI(R.string.source_ai),
    QUICK(R.string.source_quick),
}

/**
 * A food with nutrients per 100 g. [key] is a stable identity across sources:
 * "base:<n>", "off:<barcode>", "custom:<uuid>", "ai:<uuid>".
 */
data class Food(
    val key: String,
    val name: String,
    val brand: String = "",
    val per100: Macros,
    val servings: List<Serving> = emptyList(),
    val source: FoodSource,
    val barcode: String? = null,
    val aliases: String = "",
) {
    /** Grams first, then the food's own servings. */
    val allServings: List<Serving> get() = listOf(GRAMS) + servings

    /** The serving a new entry starts with: the food's first named serving, else 100 g. */
    val defaultServing: Pair<Serving, Double>
        get() = servings.firstOrNull()?.let { it to 1.0 } ?: (GRAMS to 100.0)
}

/**
 * One logged item in the diary. Nutrients and the name are snapshotted (in the language
 * the app had when it was logged) so later edits or a language switch don't rewrite history.
 */
data class Entry(
    val id: Long = 0,
    val date: String, // yyyy-MM-dd, local time
    val meal: Meal,
    val foodKey: String,
    val name: String,
    val brand: String = "",
    val per100: Macros,
    val servingName: String,
    val servingGrams: Double,
    val quantity: Double,
    val source: FoodSource,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val grams: Double get() = servingGrams * quantity
    val macros: Macros get() = per100.scaled(grams / 100.0)

    fun asFood() = Food(
        key = foodKey,
        name = name,
        brand = brand,
        per100 = per100,
        servings = if (servingName == GRAMS.name) emptyList() else listOf(Serving(servingName, servingGrams)),
        source = source,
    )
}

data class Weight(val date: String, val kg: Double)

/** Short number for UI in the current locale: 12, 12.5 / 12,5, 0.3. */
fun fmt(v: Double): String {
    val r = (v * 10).roundToInt() / 10.0
    val s = if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    val sep = DecimalFormatSymbols.getInstance().decimalSeparator
    return if (sep == '.') s else s.replace('.', sep)
}

/** Parses user input with either decimal separator. */
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
