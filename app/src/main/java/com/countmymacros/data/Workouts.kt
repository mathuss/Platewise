package com.countmymacros.data

import androidx.annotation.StringRes
import com.countmymacros.R
import org.json.JSONArray
import org.json.JSONObject

enum class SetType(val id: String, @StringRes val label: Int) {
    NORMAL("normal", R.string.set_normal),
    WARMUP("warmup", R.string.set_warmup),
    DROPSET("dropset", R.string.set_dropset),
    FAILURE("failure", R.string.set_failure);

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

/** Weight is always kg, duration seconds and distance meters; the UI converts. */
data class WorkoutSet(
    val type: SetType = SetType.NORMAL,
    val kg: Double = 0.0,
    val reps: Int = 0,
    val durationS: Int = 0,
    val distanceM: Double = 0.0,
    val done: Boolean = false,
)

data class WorkoutExercise(
    val exerciseId: String,
    val restSeconds: Int = 90,
    val notes: String = "",
    val sets: List<WorkoutSet> = listOf(WorkoutSet()),
)

data class Routine(
    val id: Long = 0,
    val name: String,
    val notes: String = "",
    val exercises: List<WorkoutExercise> = emptyList(),
)

/** A finished workout. [kcal] is what [Burn] estimated when it was saved. */
data class Workout(
    val id: Long = 0,
    val date: String, // yyyy-MM-dd, local time of the start
    val startMs: Long,
    val endMs: Long,
    val name: String,
    val routineId: Long? = null,
    val kcal: Double,
    val exercises: List<WorkoutExercise>,
) {
    val durationMin: Int get() = ((endMs - startMs) / 60_000).toInt()
    val volumeKg: Double get() = volumeKg(exercises)
    val setCount: Int get() = exercises.sumOf { e -> e.sets.count { it.done } }

    companion object {
        /** Weight × reps of completed sets; warm-ups don't count. */
        fun volumeKg(exercises: List<WorkoutExercise>): Double =
            exercises.sumOf { e -> e.sets.filter { it.done && it.type != SetType.WARMUP }.sumOf { it.kg * it.reps } }
    }
}

/** Stores a list of exercises (with their sets) as JSON in a TEXT column or in prefs. */
object WorkoutJson {
    fun encode(exercises: List<WorkoutExercise>): String = JSONArray(exercises.map { e ->
        JSONObject()
            .put("id", e.exerciseId)
            .put("rest", e.restSeconds)
            .put("notes", e.notes)
            .put("sets", JSONArray(e.sets.map { s ->
                JSONObject()
                    .put("t", s.type.id).put("kg", s.kg).put("r", s.reps)
                    .put("d", s.durationS).put("m", s.distanceM).put("ok", s.done)
            }))
    }).toString()

    fun decode(json: String): List<WorkoutExercise> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sets = o.getJSONArray("sets")
            WorkoutExercise(
                exerciseId = o.getString("id"),
                restSeconds = o.optInt("rest", 90),
                notes = o.optString("notes"),
                sets = (0 until sets.length()).map { j ->
                    val s = sets.getJSONObject(j)
                    WorkoutSet(
                        type = SetType.of(s.optString("t")),
                        kg = s.optDouble("kg", 0.0),
                        reps = s.optInt("r"),
                        durationS = s.optInt("d"),
                        distanceM = s.optDouble("m", 0.0),
                        done = s.optBoolean("ok"),
                    )
                },
            )
        }
    }
}

/**
 * Calories burned by a workout, from its length and the user's weight: `MET × kg × hours`.
 * Hevy estimates 5.6 kcal per minute whatever you weigh (see docs/ANALISIS_HEVY.md); that is MET
 * 5.0 at about 67 kg, so this keeps its shape and scales it to the actual user.
 */
object Burn {
    const val MET = 5.0

    fun kcal(durationSeconds: Long, weightKg: Double): Double =
        if (durationSeconds <= 0 || weightKg <= 0) 0.0 else MET * weightKg * durationSeconds / 3600.0
}
