package com.countmymacros

import com.countmymacros.data.Activity
import com.countmymacros.data.Backup
import com.countmymacros.data.BackupException
import com.countmymacros.data.Days
import com.countmymacros.data.ExerciseStats
import com.countmymacros.data.Food
import com.countmymacros.data.FoodSource
import com.countmymacros.data.GRAMS
import com.countmymacros.data.GoalType
import com.countmymacros.data.MacroPreset
import com.countmymacros.data.Macros
import com.countmymacros.data.MealText
import com.countmymacros.data.Profile
import com.countmymacros.data.Serving
import com.countmymacros.data.Sessions
import com.countmymacros.data.Sex
import com.countmymacros.data.SetType
import com.countmymacros.data.Workout
import com.countmymacros.data.WorkoutExercise
import com.countmymacros.data.WorkoutSet
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class ImprovementsTest {
    // --- the diary follows midnight ---

    @Test
    fun diaryOnTodayFollowsTheNewDay() {
        val yesterday = LocalDate.of(2026, 11, 4)
        val today = LocalDate.of(2026, 11, 5)
        assertEquals(today, Days.follow(shown = yesterday, oldToday = yesterday, newToday = today))
    }

    @Test
    fun diaryOnAnotherDayStaysThere() {
        val shown = LocalDate.of(2026, 10, 1)
        assertEquals(shown, Days.follow(shown, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5)))
    }

    // --- a workout forgotten open ---

    @Test
    fun staleOnlyAfterSixHours() {
        val start = 1_000_000L
        assertTrue(!Sessions.isStale(start, start + 5 * 3_600_000L))
        assertTrue(Sessions.isStale(start, start + 6 * 3_600_000L + 1))
    }

    // --- strength progress ---

    private fun set(kg: Double, reps: Int, done: Boolean = true, type: SetType = SetType.NORMAL) =
        WorkoutSet(type = type, kg = kg, reps = reps, done = done)

    private fun workout(day: Int, id: String, vararg sets: WorkoutSet) = Workout(
        date = "2026-11-%02d".format(day),
        startMs = day * 86_400_000L,
        endMs = day * 86_400_000L + 3_600_000L,
        name = "w",
        kcal = 0.0,
        exercises = listOf(WorkoutExercise(id, sets = sets.toList())),
    )

    @Test
    fun seriesHasOnePointPerWorkoutOldestFirst() {
        val history = listOf(
            workout(3, "bench", set(80.0, 5)),
            workout(1, "bench", set(60.0, 8), set(40.0, 10, type = SetType.WARMUP)),
            workout(2, "squat", set(100.0, 5)),
            workout(4, "bench", set(90.0, 3, done = false)), // nothing completed: no point
        )
        val s = ExerciseStats.series(history, "bench")
        assertEquals(listOf("2026-11-01", "2026-11-03"), s.map { it.date })
        assertEquals(60.0, s[0].bestKg, 1e-9)
        assertEquals(8, s[0].bestReps)
        assertEquals(480.0, s[0].volume, 1e-9) // the warm-up is not counted
        assertEquals(80.0, s[1].bestKg, 1e-9)
    }

    @Test
    fun oneRepMaxUsesEpley() {
        assertEquals(100.0, ExerciseStats.e1rm(100.0, 1), 1e-9)
        assertEquals(100.0 * (1 + 5 / 30.0), ExerciseStats.e1rm(100.0, 5), 1e-9)
        assertEquals(0.0, ExerciseStats.e1rm(0.0, 10), 1e-9) // bodyweight: no estimate
    }

    @Test
    fun recentExercisesAreNewestFirstAndDistinct() {
        val history = listOf(
            workout(1, "squat", set(100.0, 5)),
            workout(3, "bench", set(80.0, 5)),
            workout(2, "squat", set(105.0, 5)),
        )
        assertEquals(listOf("bench", "squat"), ExerciseStats.recentExerciseIds(history, 5))
        assertEquals(listOf("bench"), ExerciseStats.recentExerciseIds(history, 1))
    }

    // --- describing a meal: more numbers and units ---

    private fun food(name: String, vararg servings: Serving) = Food(
        key = "base:$name", name = name, per100 = Macros(100.0, 10.0, 10.0, 10.0),
        servings = servings.toList(), source = FoodSource.BASE,
    )

    private val catalog = listOf(food("arroz"), food("almendras"), food("chicken"), food("leche"), food("azucar"))

    @Test
    fun describeUnderstandsKilosOuncesAndLiters() {
        val kilo = MealText.parse("1 kg de arroz", catalog).items.single()
        assertEquals(GRAMS, kilo.serving)
        assertEquals(1000.0, kilo.quantity, 1e-6)

        val ounces = MealText.parse("8 oz chicken", catalog).items.single()
        assertEquals(8 * 28.35, ounces.quantity, 1e-6)

        val liter = MealText.parse("2 litros de leche", catalog).items.single()
        assertEquals(2000.0, liter.quantity, 1e-6)

        // Plain grams still work, with or without a space.
        assertEquals(150.0, MealText.parse("150g de arroz", catalog).items.single().quantity, 1e-6)
    }

    @Test
    fun describeUnderstandsNumbersUpToTwenty() {
        assertEquals(12.0, MealText.parse("doce almendras", catalog).items.single().quantity, 1e-6)
        assertEquals(20.0, MealText.parse("veinte almendras", catalog).items.single().quantity, 1e-6)
        assertEquals(15.0, MealText.parse("fifteen almendras", catalog).items.single().quantity, 1e-6)
    }

    @Test
    fun describeUnderstandsMoreMeasures() {
        val teaspoon = food("azucar", Serving("1 cucharadita", 4.0), Serving("1 cucharada", 12.0))
        val item = MealText.parse("dos cucharaditas de azucar", listOf(teaspoon)).items.single()
        assertEquals(2.0, item.quantity, 1e-6)
        assertEquals(Serving("1 cucharadita", 4.0), item.serving)
    }

    // --- backup file ---

    @Test
    fun backupKeepsProfileAndTables() {
        val profile = Profile(
            sex = Sex.FEMALE, age = 41, heightCm = 168.0, weightKg = 62.5, activity = Activity.ACTIVE,
            goal = GoalType.LOSE, pace = 0.2, preset = MacroPreset.CUSTOM, custom = Macros(1800.0, 130.0, 180.0, 60.0),
        )
        val tables = JSONObject("""{"weights":[{"date":"2026-11-05","kg":62.5}]}""")
        val (readTables, readProfile) = Backup.decode(Backup.encode(tables, profile, 123L))
        assertEquals(profile, readProfile)
        assertEquals(62.5, readTables.getJSONArray("weights").getJSONObject(0).getDouble("kg"), 1e-9)
    }

    @Test
    fun backupRejectsOtherFilesAndNewerFormats() {
        fun rejects(json: String) {
            try {
                Backup.decode(json)
                fail("should have been rejected: $json")
            } catch (_: BackupException) {
            }
        }
        rejects("not json at all")
        rejects("""{"hello":"world"}""")
        rejects("""{"app":"platewise","format":99,"tables":{}}""")
        rejects("""{"app":"platewise","format":1}""")
    }

    @Test
    fun backupWithMissingProfileFieldsFallsBackToDefaults() {
        val (_, profile) = Backup.decode("""{"app":"platewise","format":1,"tables":{},"profile":{"age":50}}""")
        assertEquals(50, profile.age)
        assertEquals(Profile().heightCm, profile.heightCm, 1e-9)
    }
}
