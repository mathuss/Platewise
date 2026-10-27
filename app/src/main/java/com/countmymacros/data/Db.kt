package com.countmymacros.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/**
 * Everything the user records lives here, on the device. Mirrors the parts of Fitia's Room
 * schema that matter for counting macros: diary entries (FoodModel/QuickItemModel), the
 * user's own foods (custom + scanned), and weight (WeightModel).
 */
class Db(context: Context) : SQLiteOpenHelper(context, "macros.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                meal INTEGER NOT NULL,
                food_key TEXT NOT NULL,
                name TEXT NOT NULL,
                brand TEXT NOT NULL,
                kcal REAL NOT NULL, protein REAL NOT NULL, carbs REAL NOT NULL, fat REAL NOT NULL,
                serving_name TEXT NOT NULL,
                serving_grams REAL NOT NULL,
                quantity REAL NOT NULL,
                source TEXT NOT NULL,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX entries_date ON entries(date)")
        db.execSQL(
            """CREATE TABLE foods (
                key TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                brand TEXT NOT NULL,
                kcal REAL NOT NULL, protein REAL NOT NULL, carbs REAL NOT NULL, fat REAL NOT NULL,
                servings TEXT NOT NULL,
                source TEXT NOT NULL,
                barcode TEXT,
                updated_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX foods_barcode ON foods(barcode)")
        db.execSQL("CREATE TABLE weights (date TEXT PRIMARY KEY, kg REAL NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // --- entries ---

    fun entries(fromDate: String, toDate: String = fromDate): List<Entry> =
        readableDatabase.query(
            "entries", null, "date BETWEEN ? AND ?", arrayOf(fromDate, toDate),
            null, null, "date, meal, created_at",
        ).use { c -> c.map { it.toEntry() } }

    fun insert(e: Entry): Long = writableDatabase.insert("entries", null, e.values())

    fun update(e: Entry) {
        writableDatabase.update("entries", e.values(), "id = ?", arrayOf(e.id.toString()))
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
    }

    /** Most recently logged distinct foods, newest first. */
    fun recentEntries(limit: Int): List<Entry> =
        readableDatabase.rawQuery(
            """SELECT * FROM entries WHERE id IN (
                   SELECT MAX(id) FROM entries GROUP BY food_key)
               ORDER BY created_at DESC LIMIT ?""",
            arrayOf(limit.toString()),
        ).use { c -> c.map { it.toEntry() } }

    private fun Entry.values() = ContentValues().apply {
        put("date", date)
        put("meal", meal.ordinal)
        put("food_key", foodKey)
        put("name", name)
        put("brand", brand)
        put("kcal", per100.kcal)
        put("protein", per100.protein)
        put("carbs", per100.carbs)
        put("fat", per100.fat)
        put("serving_name", servingName)
        put("serving_grams", servingGrams)
        put("quantity", quantity)
        put("source", source.name)
        put("created_at", createdAt)
    }

    private fun Cursor.toEntry() = Entry(
        id = long("id"),
        date = str("date"),
        meal = Meal.entries.getOrElse(int("meal")) { Meal.SNACK },
        foodKey = str("food_key"),
        name = str("name"),
        brand = str("brand"),
        per100 = Macros(dbl("kcal"), dbl("protein"), dbl("carbs"), dbl("fat")),
        servingName = str("serving_name"),
        servingGrams = dbl("serving_grams"),
        quantity = dbl("quantity"),
        source = sourceOf(str("source")),
        createdAt = long("created_at"),
    )

    // --- saved foods ---

    fun foods(): List<Food> =
        readableDatabase.query("foods", null, null, null, null, null, "name COLLATE NOCASE")
            .use { c -> c.map { it.toFood() } }

    fun foodByBarcode(code: String): Food? =
        readableDatabase.query("foods", null, "barcode = ?", arrayOf(code), null, null, null, "1")
            .use { c -> c.map { it.toFood() }.firstOrNull() }

    fun upsert(f: Food) {
        val v = ContentValues().apply {
            put("key", f.key)
            put("name", f.name)
            put("brand", f.brand)
            put("kcal", f.per100.kcal)
            put("protein", f.per100.protein)
            put("carbs", f.per100.carbs)
            put("fat", f.per100.fat)
            put("servings", JSONArray(f.servings.map { JSONArray(listOf(it.name, it.grams)) }).toString())
            put("source", f.source.name)
            put("barcode", f.barcode)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("foods", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteFood(key: String) {
        writableDatabase.delete("foods", "key = ?", arrayOf(key))
    }

    private fun Cursor.toFood(): Food {
        val arr = JSONArray(str("servings"))
        return Food(
            key = str("key"),
            name = str("name"),
            brand = str("brand"),
            per100 = Macros(dbl("kcal"), dbl("protein"), dbl("carbs"), dbl("fat")),
            servings = (0 until arr.length()).map {
                val s = arr.getJSONArray(it)
                Serving(s.getString(0), s.getDouble(1))
            },
            source = sourceOf(str("source")),
            barcode = getString(getColumnIndexOrThrow("barcode")),
        )
    }

    // --- weights ---

    fun weights(): List<Weight> =
        readableDatabase.query("weights", null, null, null, null, null, "date DESC")
            .use { c -> c.map { Weight(it.str("date"), it.dbl("kg")) } }

    fun putWeight(w: Weight) {
        val v = ContentValues().apply { put("date", w.date); put("kg", w.kg) }
        writableDatabase.insertWithOnConflict("weights", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteWeight(date: String) {
        writableDatabase.delete("weights", "date = ?", arrayOf(date))
    }

    // --- cursor helpers ---

    private inline fun <T> Cursor.map(f: (Cursor) -> T): List<T> {
        val out = ArrayList<T>(count)
        while (moveToNext()) out += f(this)
        return out
    }

    private fun Cursor.str(col: String) = getString(getColumnIndexOrThrow(col))
    private fun Cursor.dbl(col: String) = getDouble(getColumnIndexOrThrow(col))
    private fun Cursor.long(col: String) = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.int(col: String) = getInt(getColumnIndexOrThrow(col))

    private fun sourceOf(name: String) =
        FoodSource.entries.firstOrNull { it.name == name } ?: FoodSource.CUSTOM
}
