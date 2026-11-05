package com.countmymacros.data

import androidx.annotation.StringRes
import com.countmymacros.R
import org.json.JSONArray

/** What a set of this kind records. */
enum class SetField { KG, REPS, DURATION, DISTANCE }

/** Hevy's exercise types: they decide which columns a set has. */
enum class ExerciseType(val id: String, val fields: List<SetField>, @StringRes val label: Int) {
    WEIGHT_REPS("weight_reps", listOf(SetField.KG, SetField.REPS), R.string.type_weight_reps),
    REPS_ONLY("reps_only", listOf(SetField.REPS), R.string.type_reps_only),
    BODYWEIGHT_REPS("bodyweight_reps", listOf(SetField.KG, SetField.REPS), R.string.type_bodyweight_reps), // kg = added weight
    BODYWEIGHT_ASSISTED_REPS("bodyweight_assisted_reps", listOf(SetField.KG, SetField.REPS), R.string.type_bodyweight_assisted), // kg = assistance
    DURATION("duration", listOf(SetField.DURATION), R.string.type_duration),
    WEIGHT_DURATION("weight_duration", listOf(SetField.KG, SetField.DURATION), R.string.type_weight_duration),
    DISTANCE_DURATION("distance_duration", listOf(SetField.DISTANCE, SetField.DURATION), R.string.type_distance_duration),
    SHORT_DISTANCE_WEIGHT("short_distance_weight", listOf(SetField.KG, SetField.DISTANCE), R.string.type_short_distance_weight),
    FLOORS_DURATION("floors_duration", listOf(SetField.REPS, SetField.DURATION), R.string.type_floors_duration),
    STEPS_DURATION("steps_duration", listOf(SetField.REPS, SetField.DURATION), R.string.type_steps_duration);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: WEIGHT_REPS
    }
}

enum class Muscle(val id: String, @StringRes val label: Int) {
    ABDOMINALS("abdominals", R.string.muscle_abdominals),
    ABDUCTORS("abductors", R.string.muscle_abductors),
    ADDUCTORS("adductors", R.string.muscle_adductors),
    BICEPS("biceps", R.string.muscle_biceps),
    CALVES("calves", R.string.muscle_calves),
    CHEST("chest", R.string.muscle_chest),
    FOREARMS("forearms", R.string.muscle_forearms),
    FULL_BODY("full_body", R.string.muscle_full_body),
    GLUTES("glutes", R.string.muscle_glutes),
    HAMSTRINGS("hamstrings", R.string.muscle_hamstrings),
    LATS("lats", R.string.muscle_lats),
    LOWER_BACK("lower_back", R.string.muscle_lower_back),
    NECK("neck", R.string.muscle_neck),
    QUADRICEPS("quadriceps", R.string.muscle_quadriceps),
    SHOULDERS("shoulders", R.string.muscle_shoulders),
    TRAPS("traps", R.string.muscle_traps),
    TRICEPS("triceps", R.string.muscle_triceps),
    UPPER_BACK("upper_back", R.string.muscle_upper_back),
    CARDIO("cardio", R.string.muscle_cardio),
    OTHER("other", R.string.muscle_other);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

enum class Equipment(val id: String, @StringRes val label: Int) {
    BARBELL("barbell", R.string.equip_barbell),
    DUMBBELL("dumbbell", R.string.equip_dumbbell),
    MACHINE("machine", R.string.equip_machine),
    KETTLEBELL("kettlebell", R.string.equip_kettlebell),
    PLATE("plate", R.string.equip_plate),
    RESISTANCE_BAND("resistance_band", R.string.equip_band),
    SUSPENSION("suspension", R.string.equip_suspension),
    NONE("none", R.string.equip_none),
    OTHER("other", R.string.equip_other);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/**
 * One exercise of the catalog (assets/ejercicios.json, from tools/hevy_extract.py) or one the
 * user created ("custom:<n>"). [hasMale] / [hasFemale] say which thumbnails exist.
 */
data class Exercise(
    val id: String,
    val en: String,
    val es: String,
    val type: ExerciseType,
    val muscle: Muscle,
    val equipment: Equipment,
    val hasMale: Boolean = false,
    val hasFemale: Boolean = false,
) {
    fun name(lang: String) = if (lang == "es") es else en
    val isCustom get() = id.startsWith("custom:")

    /** Both names normalized once per instance: the picker filters the whole catalog on every keystroke. */
    internal val searchText: String by lazy { FoodSearch.normalize("$es $en") }
}

object ExerciseCatalog {
    fun parse(json: String): List<Exercise> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Exercise(
                id = o.getString("id"),
                en = o.getString("en"),
                es = o.getString("es"),
                type = ExerciseType.of(o.getString("type")),
                muscle = Muscle.of(o.getString("muscle")),
                equipment = Equipment.of(o.getString("equipment")),
                hasMale = o.optBoolean("m"),
                hasFemale = o.optBoolean("f"),
            )
        }
    }

    /** Every query word must appear in either language's name; the catalog's popularity order is kept. */
    fun search(
        exercises: List<Exercise>,
        query: String,
        muscle: Muscle? = null,
        equipment: Equipment? = null,
    ): List<Exercise> {
        val words = FoodSearch.normalize(query).split(' ').filter { it.isNotBlank() }
        return exercises.filter { e ->
            (muscle == null || e.muscle == muscle) && (equipment == null || e.equipment == equipment) &&
                words.all { e.searchText.contains(it) }
        }
    }
}
