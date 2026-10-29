package com.countmymacros.data

import android.content.Context
import java.time.LocalDate
import java.util.UUID

class Repo(private val context: Context) {
    private val db = Db(context)
    val prefs = Prefs(context)

    private val baseJson: String by lazy {
        context.assets.open("alimentos.json").bufferedReader().use { it.readText() }
    }
    private val baseCache = HashMap<String, List<Food>>()

    @Synchronized
    fun baseFoods(lang: String): List<Food> = baseCache.getOrPut(lang) { BaseFoods.parse(baseJson, lang) }

    /** My foods plus the base catalog, my own foods taking priority on a key clash. */
    fun localFoods(lang: String): List<Food> {
        val mine = db.foods()
        val mineKeys = mine.map { it.key }.toSet()
        return mine + baseFoods(lang).filter { it.key !in mineKeys }
    }

    // --- diary ---

    fun day(date: LocalDate): List<Entry> = db.entries(date.toString())

    fun range(from: LocalDate, to: LocalDate): List<Entry> = db.entries(from.toString(), to.toString())

    fun add(e: Entry): Long = db.insert(e)

    fun update(e: Entry) = db.update(e)

    fun delete(id: Long) = db.deleteEntry(id)

    /** Fitia's "copy from yesterday" for one meal. Returns how many items were copied. */
    fun copyMeal(from: LocalDate, to: LocalDate, meal: Meal): Int {
        val items = db.entries(from.toString()).filter { it.meal == meal }
        val now = System.currentTimeMillis()
        items.forEachIndexed { i, e -> db.insert(e.copy(id = 0, date = to.toString(), createdAt = now + i)) }
        return items.size
    }

    // --- foods ---

    fun recentFoods(limit: Int = 30): List<Entry> = db.recentEntries(limit)

    fun myFoods(): List<Food> = db.foods()

    fun saveFood(f: Food) = db.upsert(f)

    fun deleteFood(key: String) = db.deleteFood(key)

    fun newCustomKey() = "custom:" + UUID.randomUUID()

    fun searchLocal(query: String, lang: String): List<Food> = FoodSearch.search(query, localFoods(lang))

    /** Saved foods first (instant, offline), then Open Food Facts. Found products are cached. */
    fun barcode(code: String, lang: String): Food? =
        db.foodByBarcode(code) ?: OpenFoodFacts.byBarcode(code, lang)?.also { db.upsert(it) }

    fun searchOnline(query: String, lang: String): List<Food> =
        OpenFoodFacts.search(query, lang, Lang.offCountryTag())

    // --- weight ---

    fun weights(): List<Weight> = db.weights()

    fun putWeight(w: Weight) = db.putWeight(w)

    fun deleteWeight(date: String) = db.deleteWeight(date)
}
