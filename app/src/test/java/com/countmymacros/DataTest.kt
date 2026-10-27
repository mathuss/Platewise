package com.countmymacros

import com.countmymacros.data.Activity
import com.countmymacros.data.Entry
import com.countmymacros.data.Food
import com.countmymacros.data.FoodSearch
import com.countmymacros.data.FoodSource
import com.countmymacros.data.GoalType
import com.countmymacros.data.Goals
import com.countmymacros.data.MacroPreset
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.OpenFoodFacts
import com.countmymacros.data.Profile
import com.countmymacros.data.Sex
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
