package com.countmymacros.data

import android.content.Context

/** Profile/goals in plain prefs; the API key in its own file, excluded from cloud backup. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secrets = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    var profileSet: Boolean
        get() = sp.getBoolean("profile_set", false)
        set(v) = sp.edit().putBoolean("profile_set", v).apply()

    var apiKey: String
        get() = secrets.getString("anthropic_api_key", "") ?: ""
        set(v) = secrets.edit().putString("anthropic_api_key", v.trim()).apply()

    /** In-app language before Android 13 ("" = follow the system). Android 13+ stores it itself. */
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) = sp.edit().putString("language", v).commit().let { }

    var profile: Profile
        get() {
            val d = Profile()
            return Profile(
                sex = enumOr(sp.getString("sex", null), d.sex),
                age = sp.getInt("age", d.age),
                heightCm = sp.getFloat("height", d.heightCm.toFloat()).toDouble(),
                weightKg = sp.getFloat("weight", d.weightKg.toFloat()).toDouble(),
                activity = enumOr(sp.getString("activity", null), d.activity),
                goal = enumOr(sp.getString("goal", null), d.goal),
                pace = sp.getFloat("pace", d.pace.toFloat()).toDouble(),
                preset = enumOr(sp.getString("preset", null), d.preset),
                custom = Macros(
                    kcal = sp.getFloat("c_kcal", d.custom.kcal.toFloat()).toDouble(),
                    protein = sp.getFloat("c_protein", d.custom.protein.toFloat()).toDouble(),
                    carbs = sp.getFloat("c_carbs", d.custom.carbs.toFloat()).toDouble(),
                    fat = sp.getFloat("c_fat", d.custom.fat.toFloat()).toDouble(),
                ),
            )
        }
        set(p) = sp.edit()
            .putString("sex", p.sex.name)
            .putInt("age", p.age)
            .putFloat("height", p.heightCm.toFloat())
            .putFloat("weight", p.weightKg.toFloat())
            .putString("activity", p.activity.name)
            .putString("goal", p.goal.name)
            .putFloat("pace", p.pace.toFloat())
            .putString("preset", p.preset.name)
            .putFloat("c_kcal", p.custom.kcal.toFloat())
            .putFloat("c_protein", p.custom.protein.toFloat())
            .putFloat("c_carbs", p.custom.carbs.toFloat())
            .putFloat("c_fat", p.custom.fat.toFloat())
            .apply()

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
