package com.countmymacros.data

import java.time.LocalDate

/** Small calculations behind the Progress screen. Pure, so they can be tested without a device. */
object Stats {
    /** Calories eaten on each day that has something logged. */
    fun kcalPerDay(entries: List<Entry>): Map<LocalDate, Double> =
        entries.groupBy { LocalDate.parse(it.date) }.mapValues { (_, list) -> list.sumOf { it.macros.kcal } }

    /** Average calories over the days that have something logged; 0 when none do. */
    fun averageKcal(perDay: Map<LocalDate, Double>): Double {
        val logged = perDay.values.filter { it > 0 }
        return if (logged.isEmpty()) 0.0 else logged.sum() / logged.size
    }

    /**
     * (days within the target, logged days). A day is "on target" when it does not go over the
     * target by more than [tolerance] (10 % by default): eating a bit under is not a miss.
     */
    fun daysOnTarget(perDay: Map<LocalDate, Double>, target: Double, tolerance: Double = 0.10): Pair<Int, Int> {
        val logged = perDay.values.filter { it > 0 }
        return logged.count { it <= target * (1 + tolerance) } to logged.size
    }

    /**
     * Consecutive days with something logged, counting back from [today]. A day that is still
     * empty does not break the streak until it is over, so counting starts from yesterday then.
     */
    fun streak(logged: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in logged) today else today.minusDays(1)
        var n = 0
        while (day in logged) {
            n++
            day = day.minusDays(1)
        }
        return n
    }
}

/** A personal best set: the heaviest weight, and at equal weight the most reps. */
data class SetRecord(val exerciseId: String, val kg: Double, val reps: Int)

object Records {
    private fun beats(a: WorkoutSet, b: WorkoutSet) = a.kg > b.kg || (a.kg == b.kg && a.reps > b.reps)

    /** The best completed working set (warm-ups don't count). */
    fun best(sets: List<WorkoutSet>): WorkoutSet? =
        sets.filter { it.done && it.type != SetType.WARMUP && (it.kg > 0 || it.reps > 0) }
            .reduceOrNull { a, b -> if (beats(b, a)) b else a }

    /** Exercises of [current] whose best set is better than anything in [history]. A first time is not a record. */
    fun find(current: List<WorkoutExercise>, history: List<Workout>): List<SetRecord> =
        current.mapNotNull { e ->
            val now = best(e.sets) ?: return@mapNotNull null
            val before = history.flatMap { w -> w.exercises.filter { it.exerciseId == e.exerciseId } }
                .mapNotNull { best(it.sets) }
                .reduceOrNull { a, b -> if (beats(b, a)) b else a }
            if (before != null && beats(now, before)) SetRecord(e.exerciseId, now.kg, now.reps) else null
        }.distinctBy { it.exerciseId }
}
