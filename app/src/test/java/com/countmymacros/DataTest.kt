package com.countmymacros

import com.countmymacros.data.Activity
import com.countmymacros.data.BaseFoods
import com.countmymacros.data.Burn
import com.countmymacros.data.ExerciseCatalog
import com.countmymacros.data.ExerciseType
import com.countmymacros.data.Muscle
import com.countmymacros.data.SetType
import com.countmymacros.data.Workout
import com.countmymacros.data.WorkoutExercise
import com.countmymacros.data.WorkoutJson
import com.countmymacros.data.WorkoutSet
import com.countmymacros.data.Entry
import com.countmymacros.data.Food
import com.countmymacros.data.FoodSearch
import com.countmymacros.data.FoodSource
import com.countmymacros.data.GRAMS
import com.countmymacros.data.GoalType
import com.countmymacros.data.Goals
import com.countmymacros.data.LabelText
import com.countmymacros.data.MacroPreset
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.MealText
import com.countmymacros.data.OcrLine
import com.countmymacros.data.OpenFoodFacts
import com.countmymacros.data.Profile
import com.countmymacros.data.Sex
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class DataTest {
    private val savedLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun mifflinTargets() {
        val p = Profile(
            sex = Sex.MALE, age = 30, heightCm = 180.0, weightKg = 80.0,
            activity = Activity.MODERATE, goal = GoalType.LOSE, pace = 0.20, preset = MacroPreset.BALANCED,
        )
        val t = Goals.targets(p)
        assertEquals(1780.0, t.bmr, 0.01) // 10·80 + 6.25·180 − 5·30 + 5
        assertEquals(2759.0, t.tdee, 0.01) // × 1.55
        assertEquals(2210.0, t.daily.kcal, 0.0) // −20 %, rounded to 10
        assertEquals(138.0, t.daily.protein, 0.0) // 25 % / 4
        assertEquals(249.0, t.daily.carbs, 0.0) // 45 % / 4
        assertEquals(74.0, t.daily.fat, 0.0) // 30 % / 9
    }

    @Test
    fun customTargetsPassThrough() {
        val custom = Macros(1800.0, 160.0, 150.0, 60.0)
        assertEquals(custom, Goals.targets(Profile(preset = MacroPreset.CUSTOM, custom = custom)).daily)
    }

    @Test
    fun entryScalesPer100() {
        val e = Entry(
            date = "2026-10-24", meal = Meal.LUNCH, foodKey = "base:x", name = "Huevo",
            per100 = Macros(143.0, 12.6, 0.7, 9.5), servingName = "1 huevo", servingGrams = 50.0,
            quantity = 2.0, source = FoodSource.BASE,
        )
        assertEquals(100.0, e.grams, 0.0)
        assertEquals(143.0, e.macros.kcal, 1e-9)
    }

    @Test
    fun numbersFollowLocale() {
        Locale.setDefault(Locale.forLanguageTag("es-AR"))
        assertEquals("12,5", fmt(12.5))
        assertEquals("12", fmt(12.0))
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("12.5", fmt(12.5))
        // Input accepts either separator regardless of language.
        assertEquals(7.5, parseNum("7,5")!!, 0.0)
        assertEquals(7.5, parseNum("7.5")!!, 0.0)
    }

    private fun food(name: String, aliases: String = "") =
        Food(key = name, name = name, per100 = Macros.ZERO, source = FoodSource.BASE, aliases = aliases)

    @Test
    fun searchIgnoresAccentsAndUsesAliases() {
        val foods = listOf(food("Sandía"), food("Palta", "aguacate avocado"), food("Pechuga de pollo cocida"), food("Pollo asado con piel"))
        assertEquals(listOf("Sandía"), FoodSearch.search("SANDIA", foods).map { it.name })
        assertEquals(listOf("Palta"), FoodSearch.search("aguacate", foods).map { it.name })
        // The other language's name is an alias, so an English query finds a Spanish-named food.
        assertEquals(listOf("Palta"), FoodSearch.search("avocado", foods).map { it.name })
        assertEquals(listOf("Pechuga de pollo cocida"), FoodSearch.search("pollo cocida", foods).map { it.name })
        // Name starting with the query ranks first.
        assertEquals("Pollo asado con piel", FoodSearch.search("pollo", foods).first().name)
    }

    @Test
    fun bundledFoodsAreConsistentAndBilingual() {
        val arr = JSONArray(File("src/main/assets/alimentos.json").readText())
        assertTrue(arr.length() >= 100)
        val names = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val n = o.getString("n")
            assertTrue("duplicate $n", names.add(n))
            assertTrue("$n has no English name", o.getString("e").isNotBlank())
            val servings = o.getJSONArray("s")
            for (j in 0 until servings.length()) {
                val s = servings.getJSONArray(j)
                assertEquals("$n serving $j must be [es, en, grams]", 3, s.length())
                assertTrue(s.getString(0).isNotBlank() && s.getString(1).isNotBlank() && s.getDouble(2) > 0)
            }
            val kcal = o.getDouble("k")
            val fromMacros = 4 * o.getDouble("p") + 4 * o.getDouble("c") + 9 * o.getDouble("f")
            // USDA uses food-specific Atwater factors (fiber in fruit/veg yields < 4 kcal/g), so
            // light foods drift further from 4/4/9; alcohol isn't in the macros at all.
            val drink = n.startsWith("Cerveza") || n.startsWith("Vino")
            val tolerance = if (kcal >= 100) 0.15 else 0.35
            if (!drink && kcal > 20) {
                assertTrue("$n: $kcal kcal vs $fromMacros from macros", kotlin.math.abs(kcal - fromMacros) / kcal < tolerance)
            }
        }
    }

    private val baseEs by lazy { BaseFoods.parse(File("src/main/assets/alimentos.json").readText(), "es") }
    private val baseEn by lazy { BaseFoods.parse(File("src/main/assets/alimentos.json").readText(), "en") }

    @Test
    fun describeParsesQuantitiesAndUnits() {
        val meal = MealText.parse("2 empanadas de carne y una coca chica", baseEs)
        assertEquals(2, meal.items.size)
        assertTrue(meal.items[0].food.name.contains("Empanada de carne"))
        assertEquals(2.0, meal.items[0].quantity, 0.0)
        assertEquals("1 empanada", meal.items[0].serving.name)
        assertTrue(meal.items[1].food.name.contains("Gaseosa"))
        assertEquals(1.0, meal.items[1].quantity, 0.0)

        val withPure = MealText.parse("media milanesa con puré", baseEs)
        assertEquals(2, withPure.items.size)
        assertTrue(withPure.items[0].food.name.contains("Milanesa"))
        assertEquals(0.5, withPure.items[0].quantity, 0.0)
        assertTrue(withPure.items[1].food.name.contains("Puré"))

        val grams = MealText.parse("200 g de arroz", baseEs).items.single()
        assertTrue(grams.food.name.contains("Arroz"))
        assertEquals(GRAMS, grams.serving)
        assertEquals(200.0, grams.quantity, 0.0)

        val cup = MealText.parse("una taza de arroz cocido", baseEs).items.single()
        assertEquals("Arroz blanco cocido", cup.food.name)
        assertEquals("1 taza", cup.serving.name)

        // "coca" alone is ambiguous between regular and diet soda: either can be the match,
        // the other should show up as an alternative to switch to.
        val can = MealText.parse("una lata de coca", baseEs).items.single()
        assertTrue(can.food.name.startsWith("Gaseosa"))
        assertEquals("1 lata", can.serving.name)
        assertTrue((listOf(can.food) + can.alternatives).size > 1)

        assertTrue(MealText.parse("xyzzy", baseEs).items.isEmpty())
        assertEquals(listOf("xyzzy"), MealText.parse("xyzzy", baseEs).unmatched)

        val eggs = MealText.parse("2 eggs", baseEn).items.single()
        assertEquals("Egg", eggs.food.name)
        assertEquals(2.0, eggs.quantity, 0.0)
    }

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrLine(text, left, top, right, bottom)

    @Test
    fun labelTextParsesCommonLayouts() {
        // Mercosur-style: name/value/%VD columns, one row per nutrient, per serving (not per 100 g).
        // "Grasas saturadas" is listed before "Grasas totales" on purpose, to check it's skipped.
        val mercosur = listOf(
            line("Porción", 10, 100, 90, 120), line("30 g", 200, 100, 260, 120),
            line("Valor energético", 10, 140, 160, 160), line("150 kcal", 200, 140, 280, 160), line("7%", 300, 140, 330, 160),
            line("Grasas saturadas", 10, 170, 150, 190), line("2 g", 200, 170, 230, 190),
            line("Grasas totales", 10, 230, 130, 250), line("5 g", 200, 230, 230, 250), line("9%", 300, 230, 330, 250),
            line("Proteínas", 10, 260, 90, 280), line("3 g", 200, 260, 230, 280),
            line("Carbohidratos", 10, 290, 110, 310), line("20 g", 200, 290, 260, 310),
        )
        val m = LabelText.parse(mercosur)!!
        assertEquals(150.0, m.kcal!!, 0.0)
        assertEquals(3.0, m.protein!!, 0.0)
        assertEquals(20.0, m.carbs!!, 0.0)
        assertEquals(5.0, m.fat!!, 0.0) // not the 2 g from "Grasas saturadas"
        assertEquals(30.0, m.servingGrams!!, 0.0)
        assertFalse(m.per100)

        // Per 100 g, name and value OCR'd together on one line.
        val per100 = listOf(
            line("Información nutricional", 10, 10, 200, 30),
            line("Por 100 g", 10, 40, 100, 60),
            line("Valor energético 420 kcal", 10, 80, 220, 100),
            line("Proteínas 9,5 g", 10, 110, 150, 130),
            line("Hidratos de carbono 70 g", 10, 140, 200, 160),
            line("Grasas totales 11 g", 10, 170, 170, 190),
        )
        val p = LabelText.parse(per100)!!
        assertEquals(420.0, p.kcal!!, 0.0)
        assertEquals(9.5, p.protein!!, 0.0) // comma decimal
        assertEquals(70.0, p.carbs!!, 0.0)
        assertEquals(11.0, p.fat!!, 0.0)
        assertNull(p.servingGrams)
        assertTrue(p.per100)

        // Only kJ given for energy; converts to kcal. Missing fat is fine (kcal + 2 macros).
        val kjOnly = listOf(
            line("Energia 1757 kJ", 10, 10, 160, 30),
            line("Proteinas 9 g", 10, 40, 120, 60),
            line("Carbohidratos 70 g", 10, 70, 150, 90),
        )
        val kj = LabelText.parse(kjOnly)!!
        assertEquals(419.9, kj.kcal!!, 0.5)
        assertNull(kj.fat)

        assertNull(LabelText.parse(listOf(line("Ingredientes: harina, agua, sal.", 10, 10, 200, 30))))
    }

    @Test
    fun exerciseCatalogIsConsistentAndSearchable() {
        val json = File("src/main/assets/ejercicios.json").readText()
        val all = ExerciseCatalog.parse(json)
        assertTrue(all.size >= 400)
        assertEquals("duplicate ids", all.size, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.en.isNotBlank() && it.es.isNotBlank() })
        // Spanish or English names find the same exercise, with accents optional.
        assertTrue(ExerciseCatalog.search(all, "press de banca").any { it.id == "79D0BB3A" })
        assertTrue(ExerciseCatalog.search(all, "bench press").any { it.id == "79D0BB3A" })
        val chestBarbell = ExerciseCatalog.search(all, "", Muscle.CHEST, com.countmymacros.data.Equipment.BARBELL)
        assertTrue(chestBarbell.isNotEmpty() && chestBarbell.all { it.muscle == Muscle.CHEST })
        assertEquals(ExerciseType.WEIGHT_REPS, all.first { it.id == "79D0BB3A" }.type)
    }

    @Test
    fun workoutVolumeKcalAndJsonRoundTrip() {
        val sets = listOf(
            WorkoutSet(SetType.WARMUP, kg = 20.0, reps = 10, done = true),
            WorkoutSet(kg = 80.0, reps = 8, done = true),
            WorkoutSet(kg = 80.0, reps = 6, done = true),
            WorkoutSet(kg = 80.0, reps = 6, done = false),
        )
        val exercises = listOf(WorkoutExercise("79D0BB3A", 120, "pausa", sets))
        // Warm-ups and sets not done don't count: 80 x 8 + 80 x 6.
        assertEquals(1120.0, Workout.volumeKg(exercises), 0.0)
        assertEquals(exercises, WorkoutJson.decode(WorkoutJson.encode(exercises)))

        // 60 min at 75 kg: MET 5.0 x 75 x 1 h; no time or no weight burns nothing.
        assertEquals(375.0, Burn.kcal(3600, 75.0), 1e-9)
        assertEquals(0.0, Burn.kcal(0, 75.0), 0.0)
        assertEquals(0.0, Burn.kcal(3600, 0.0), 0.0)
    }

    @Test
    fun openFoodFactsProductParsing() {
        val p = JSONObject(
            """{"code":"7790070318213","product_name":"Galletitas","product_name_en":"Crackers","brands":"Marca A, Marca B",
                "serving_size":"3 galletitas (30 g)","serving_quantity":30,
                "nutriments":{"energy_100g":1757,"proteins_100g":9,"carbohydrates_100g":70,"fat_100g":11}}"""
        )
        val es = OpenFoodFacts.parseProduct(p, null, "es")!!
        assertEquals("off:7790070318213", es.key)
        assertEquals("Galletitas", es.name)
        assertEquals("Marca A", es.brand)
        assertEquals(420.0, es.per100.kcal, 0.5) // kJ → kcal
        assertEquals(30.0, es.servings.single().grams, 0.0)
        assertEquals("Crackers", OpenFoodFacts.parseProduct(p, null, "en")!!.name)
        assertNull(OpenFoodFacts.parseProduct(JSONObject("""{"code":"1","product_name":"Sin datos"}"""), null, "es"))
    }
}
