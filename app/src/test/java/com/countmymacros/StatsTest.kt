package com.countmymacros

import com.countmymacros.data.Entry
import com.countmymacros.data.FoodSource
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.Records
import com.countmymacros.data.SetType
import com.countmymacros.data.Stats
import com.countmymacros.data.Workout
import com.countmymacros.data.WorkoutExercise
import com.countmymacros.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private fun entry(date: String, kcal: Double) = Entry(
        date = date, meal = Meal.LUNCH, foodKey = "quick:$date$kcal", name = "x",
        per100 = Macros(kcal), servingName = "quick", servingGrams = 100.0, quantity = 1.0, source = FoodSource.QUICK,
    )

    @Test
    fun perDayAverageAndDaysOnTarget() {
        val perDay = Stats.kcalPerDay(
            listOf(entry("2026-11-01", 1000.0), entry("2026-11-01", 1000.0), entry("2026-11-02", 2700.0), entry("2026-11-03", 2300.0)),
        )
        assertEquals(2000.0, perDay[LocalDate.parse("2026-11-01")]!!, 0.01)
        assertEquals((2000.0 + 2700.0 + 2300.0) / 3, Stats.averageKcal(perDay), 0.01)
        // Target 2340 with 10 % tolerance: 2000 and 2300 are on target, 2700 is not.
        assertEquals(2 to 3, Stats.daysOnTarget(perDay, 2340.0))
        assertEquals(0.0, Stats.averageKcal(emptyMap()), 0.0)
        assertEquals(0 to 0, Stats.daysOnTarget(emptyMap(), 2340.0))
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val today = LocalDate.parse("2026-11-03")
        val d = { n: Long -> today.minusDays(n) }
        assertEquals(3, Stats.streak(setOf(d(0), d(1), d(2)), today))
        // Today is still empty: yesterday's streak is intact.
        assertEquals(2, Stats.streak(setOf(d(1), d(2)), today))
        // A gap ends it.
        assertEquals(1, Stats.streak(setOf(d(0), d(2), d(3)), today))
        assertEquals(0, Stats.streak(setOf(d(2), d(3)), today))
        assertEquals(0, Stats.streak(emptySet(), today))
    }

    private fun workout(vararg sets: WorkoutSet) = Workout(
        date = "2026-10-20", startMs = 0, endMs = 3_600_000, name = "A", kcal = 0.0,
        exercises = listOf(WorkoutExercise("bench", sets = sets.toList())),
    )

    @Test
    fun personalRecords() {
        val history = listOf(workout(WorkoutSet(kg = 60.0, reps = 8, done = true), WorkoutSet(kg = 100.0, reps = 1, done = true, type = SetType.WARMUP)))
        fun now(kg: Double, reps: Int, done: Boolean = true) =
            listOf(WorkoutExercise("bench", sets = listOf(WorkoutSet(kg = kg, reps = reps, done = done))))

        // Heavier beats it; so does the same weight with more reps.
        assertEquals(60.0 + 2.5, Records.find(now(62.5, 5), history).single().kg, 0.0)
        assertEquals(9, Records.find(now(60.0, 9), history).single().reps)
        // Equal, lighter, not done: no record. The warm-up's 100 kg doesn't count against it either.
        assertTrue(Records.find(now(60.0, 8), history).isEmpty())
        assertTrue(Records.find(now(55.0, 12), history).isEmpty())
        assertTrue(Records.find(now(80.0, 5, done = false), history).isEmpty())
        // The first time an exercise is done there is nothing to beat.
        assertTrue(Records.find(now(80.0, 5), emptyList()).isEmpty())
    }
}
