package com.countmymacros.data

import org.json.JSONArray

/** Parses the offline starter catalog (assets/alimentos.json), named in [lang]. */
object BaseFoods {
    /**
     * The other language's name goes into the aliases, so "pollo" and "chicken" both find
     * it either way.
     */
    fun parse(json: String, lang: String): List<Food> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val es = o.getString("n")
            val en = o.getString("e")
            val s = o.getJSONArray("s")
            Food(
                // Keyed by the Spanish name so the key is the same in both languages.
                key = "base:" + FoodSearch.normalize(es).replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                name = if (lang == "es") es else en,
                per100 = Macros(o.getDouble("k"), o.getDouble("p"), o.getDouble("c"), o.getDouble("f")),
                servings = (0 until s.length()).map {
                    val p = s.getJSONArray(it)
                    Serving(if (lang == "es") p.getString(0) else p.getString(1), p.getDouble(2))
                },
                source = FoodSource.BASE,
                aliases = o.optString("a") + " " + (if (lang == "es") en else es),
            )
        }
    }
}
