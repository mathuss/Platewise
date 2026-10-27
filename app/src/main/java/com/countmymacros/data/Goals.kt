package com.countmymacros.data

import androidx.annotation.StringRes
import com.countmymacros.R
import kotlin.math.roundToInt

enum class Sex(@StringRes val label: Int) { MALE(R.string.sex_male), FEMALE(R.string.sex_female) }

/** PAL multipliers (FAO/WHO style), applied to BMR to get TDEE. */
enum class Activity(@StringRes val label: Int, @StringRes val detail: Int, val factor: Double) {
    SEDENTARY(R.string.activity_sedentary, R.string.activity_sedentary_detail, 1.2),
    LIGHT(R.string.activity_light, R.string.activity_light_detail, 1.375),
    MODERATE(R.string.activity_moderate, R.string.activity_moderate_detail, 1.55),
    ACTIVE(R.string.activity_active, R.string.activity_active_detail, 1.725),
    VERY_ACTIVE(R.string.activity_very_active, R.string.activity_very_active_detail, 1.9),
}

/** Fitia models the goal as a signed calorie-deficit percentage on top of TDEE. */
enum class GoalType(@StringRes val label: Int) {
    LOSE(R.string.goal_lose),
    MAINTAIN(R.string.goal_maintain),
    GAIN(R.string.goal_gain),
}

/** Share of calories from protein / carbs / fat. Names follow Fitia's macrosDistributionType values. */
enum class MacroPreset(@StringRes val label: Int, val protein: Double, val carbs: Double, val fat: Double) {
    BALANCED(R.string.preset_balanced, 0.25, 0.45, 0.30),
    HIGH_PROTEIN(R.string.preset_high_protein, 0.35, 0.35, 0.30),
    LOW_CARB(R.string.preset_low_carb, 0.30, 0.25, 0.45),
    LOW_FAT(R.string.preset_low_fat, 0.25, 0.55, 0.20),
    KETO(R.string.preset_keto, 0.25, 0.05, 0.70),
    CUSTOM(R.string.preset_custom, 0.0, 0.0, 0.0),
}

data class Profile(
    val sex: Sex = Sex.MALE,
    val age: Int = 30,
    val heightCm: Double = 175.0,
    val weightKg: Double = 75.0,
    val activity: Activity = Activity.LIGHT,
    val goal: GoalType = GoalType.MAINTAIN,
    /** Deficit (LOSE) or surplus (GAIN) as a fraction of TDEE, e.g. 0.20. Ignored for MAINTAIN. */
    val pace: Double = 0.15,
    val preset: MacroPreset = MacroPreset.BALANCED,
    /** Used only with [MacroPreset.CUSTOM]: the user's own daily targets. */
    val custom: Macros = Macros(2000.0, 150.0, 200.0, 67.0),
)

data class Targets(val bmr: Double, val tdee: Double, val daily: Macros)

object Goals {
    /** Mifflin-St Jeor. Fitia's own formula lives in obfuscated code we didn't decompile. */
    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun targets(p: Profile): Targets {
        val bmr = bmr(p)
        val tdee = bmr * p.activity.factor
        if (p.preset == MacroPreset.CUSTOM) return Targets(bmr, tdee, p.custom)

        val factor = when (p.goal) {
            GoalType.LOSE -> 1 - p.pace
            GoalType.MAINTAIN -> 1.0
            GoalType.GAIN -> 1 + p.pace
        }
        // Never prescribe below BMR-ish floors; aggressive deficits are the user's call via CUSTOM.
        val floor = if (p.sex == Sex.MALE) 1500.0 else 1200.0
        val kcal = (tdee * factor).coerceAtLeast(minOf(floor, tdee)).roundTo(10)
        val daily = Macros(
            kcal = kcal,
            protein = (kcal * p.preset.protein / 4).roundTo(1),
            carbs = (kcal * p.preset.carbs / 4).roundTo(1),
            fat = (kcal * p.preset.fat / 9).roundTo(1),
        )
        return Targets(bmr, tdee, daily)
    }

    private fun Double.roundTo(step: Int) = (this / step).roundToInt() * step.toDouble()
}
