package com.countmymacros.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

private val BACKUP_TABLES = listOf("entries", "foods", "weights", "routines", "workouts", "custom_exercises")

/**
 * Everything the user records lives here, on the device. Mirrors the parts of Fitia's Room
 * schema that matter for counting macros: diary entries (FoodModel/QuickItemModel), the
 * user's own foods (custom + scanned), and weight (WeightModel). Version 2 adds training:
 * routines, finished workouts and the user's own exercises.
 */
class Db(context: Context) : SQLiteOpenHelper(context, "macros.db", null, 3) {

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
        createFoodKeyIndex(db)
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
        createTrainingTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createTrainingTables(db)
        if (oldVersion < 3) createFoodKeyIndex(db)
    }

    /** "Recent foods" groups the whole diary by food_key; the index keeps that from scanning the table. */
    private fun createFoodKeyIndex(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS entries_food_key ON entries(food_key)")
    }

    private fun createTrainingTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE routines (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                notes TEXT NOT NULL,
                position INTEGER NOT NULL,
                data TEXT NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE workouts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                name TEXT NOT NULL,
                routine_id INTEGER,
                kcal REAL NOT NULL,
                data TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX workouts_date ON workouts(date)")
        db.execSQL(
            """CREATE TABLE custom_exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                muscle TEXT NOT NULL,
                equipment TEXT NOT NULL)"""
        )
    }

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

    // --- routines ---

    fun routines(): List<Routine> =
        readableDatabase.query("routines", null, null, null, null, null, "position, id")
            .use { c ->
                c.map {
                    Routine(it.long("id"), it.str("name"), it.str("notes"), WorkoutJson.decode(it.str("data")))
                }
            }

    /** Inserts when [r] has no id yet, otherwise updates it. Returns the id. */
    fun saveRoutine(r: Routine): Long {
        val v = ContentValues().apply {
            put("name", r.name)
            put("notes", r.notes)
            put("data", WorkoutJson.encode(r.exercises))
        }
        if (r.id != 0L) {
            writableDatabase.update("routines", v, "id = ?", arrayOf(r.id.toString()))
            return r.id
        }
        v.put("position", routines().size)
        return writableDatabase.insert("routines", null, v)
    }

    fun deleteRoutine(id: Long) {
        writableDatabase.delete("routines", "id = ?", arrayOf(id.toString()))
    }

    // --- workouts ---

    fun workouts(fromDate: String, toDate: String = fromDate): List<Workout> =
        readableDatabase.query(
            "workouts", null, "date BETWEEN ? AND ?", arrayOf(fromDate, toDate), null, null, "start_ms",
        ).use { c -> c.map { it.toWorkout() } }

    fun recentWorkouts(limit: Int): List<Workout> =
        readableDatabase.query("workouts", null, null, null, null, null, "start_ms DESC", limit.toString())
            .use { c -> c.map { it.toWorkout() } }

    fun insertWorkout(w: Workout): Long {
        val v = ContentValues().apply {
            put("date", w.date)
            put("start_ms", w.startMs)
            put("end_ms", w.endMs)
            put("name", w.name)
            put("routine_id", w.routineId)
            put("kcal", w.kcal)
            put("data", WorkoutJson.encode(w.exercises))
        }
        return writableDatabase.insert("workouts", null, v)
    }

    fun deleteWorkout(id: Long) {
        writableDatabase.delete("workouts", "id = ?", arrayOf(id.toString()))
    }

    private fun Cursor.toWorkout() = Workout(
        id = long("id"),
        date = str("date"),
        startMs = long("start_ms"),
        endMs = long("end_ms"),
        name = str("name"),
        routineId = getLong(getColumnIndexOrThrow("routine_id")).takeIf { !isNull(getColumnIndexOrThrow("routine_id")) },
        kcal = dbl("kcal"),
        exercises = WorkoutJson.decode(str("data")),
    )

    // --- the user's own exercises ---

    fun customExercises(): List<Exercise> =
        readableDatabase.query("custom_exercises", null, null, null, null, null, "name COLLATE NOCASE")
            .use { c ->
                c.map {
                    val name = it.str("name")
                    Exercise(
                        id = "custom:" + it.long("id"),
                        en = name,
                        es = name,
                        type = ExerciseType.of(it.str("type")),
                        muscle = Muscle.of(it.str("muscle")),
                        equipment = Equipment.of(it.str("equipment")),
                    )
                }
            }

    fun insertCustomExercise(name: String, type: ExerciseType, muscle: Muscle, equipment: Equipment): Long {
        val v = ContentValues().apply {
            put("name", name)
            put("type", type.id)
            put("muscle", muscle.id)
            put("equipment", equipment.id)
        }
        return writableDatabase.insert("custom_exercises", null, v)
    }

    // --- backup ---

    /** Every row of every table as JSON, column names as keys. Schema-agnostic on purpose. */
    fun dumpAll(): JSONObject {
        val out = JSONObject()
        for (table in BACKUP_TABLES) {
            val rows = JSONArray()
            readableDatabase.query(table, null, null, null, null, null, null).use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> row.put(name, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> row.put(name, c.getDouble(i))
                            else -> row.put(name, c.getString(i))
                        }
                    }
                    rows.put(row)
                }
            }
            out.put(table, rows)
        }
        return out
    }

    /** Replaces everything with [tables] (as made by [dumpAll]) in one transaction: it all lands or none of it does. */
    fun restoreAll(tables: JSONObject) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in BACKUP_TABLES) db.delete(table, null, null)
            for (table in BACKUP_TABLES) {
                val rows = tables.optJSONArray(table) ?: continue
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val values = ContentValues()
                    for (key in row.keys()) {
                        when (val v = row.get(key)) {
                            JSONObject.NULL -> values.putNull(key)
                            is Int -> values.put(key, v.toLong())
                            is Long -> values.put(key, v)
                            is Double -> values.put(key, v)
                            else -> values.put(key, v.toString())
                        }
                    }
                    db.insertOrThrow(table, null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
