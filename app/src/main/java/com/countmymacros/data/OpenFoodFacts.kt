package com.countmymacros.data

import androidx.annotation.StringRes
import com.countmymacros.R
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** An Open Food Facts failure to show the user: a string resource plus an optional argument. */
class OffException(@StringRes val messageRes: Int, val arg: Any? = null) : IOException("OFF error $messageRes")

/**
 * Open Food Facts: free, open (ODbL) database of packaged products. Stands in for Fitia's
 * private food backend (Elastic/Algolia search and `searchProductsByBarcodeAndCountryV2`),
 * which isn't shipped in the APK and isn't ours to use.
 *
 * OFF asks clients to identify themselves and limits full-text search to ~10 requests/min,
 * so the UI only searches here on demand.
 */
object OpenFoodFacts {
    private const val BASE = "https://world.openfoodfacts.org"
    private const val UA = "Platewise/1.0 (Android; personal macro tracker)"
    private const val FIELDS = "code,product_name,product_name_es,product_name_en,generic_name," +
        "generic_name_es,generic_name_en,brands,nutriments,serving_size,serving_quantity,countries_tags"

    /** Product for an EAN/UPC barcode, or null when OFF doesn't have it (or has no kcal for it). */
    fun byBarcode(code: String, lang: String): Food? {
        val clean = code.filter { it.isDigit() }
        if (clean.isEmpty()) return null
        val json = get("$BASE/api/v2/product/$clean.json?fields=$FIELDS") ?: return null
        val o = JSONObject(json)
        if (o.optInt("status") != 1) return null
        return parseProduct(o.optJSONObject("product") ?: return null, clean, lang)
    }

    /**
     * Full-text search. The world database is dominated by European products, so products
     * sold in [countryTag] (e.g. "en:argentina") come first.
     */
    fun search(query: String, lang: String, countryTag: String?): List<Food> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get(
            "$BASE/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1" +
                "&page_size=40&lc=$lang&fields=$FIELDS"
        ) ?: return emptyList()
        val arr = JSONObject(json).optJSONArray("products") ?: return emptyList()
        val products = (0 until arr.length()).map { arr.getJSONObject(it) }
        val local = products.filter { p -> countryTag != null && p.countries().contains(countryTag) }
        return (local + (products - local.toSet()))
            .mapNotNull { parseProduct(it, null, lang) }
            .distinctBy { it.key }
    }

    fun parseProduct(p: JSONObject, barcode: String?, lang: String): Food? {
        val code = barcode ?: p.optString("code").ifBlank { return null }
        val other = if (lang == "es") "en" else "es"
        val name = listOf("product_name_$lang", "product_name", "generic_name_$lang", "product_name_$other", "generic_name")
            .map { p.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() } ?: return null
        val n = p.optJSONObject("nutriments") ?: return null
        val kcal = n.num("energy-kcal_100g") ?: n.num("energy_100g")?.let { it / 4.184 } ?: return null
        val per100 = Macros(
            kcal = kcal,
            protein = n.num("proteins_100g") ?: 0.0,
            carbs = n.num("carbohydrates_100g") ?: 0.0,
            fat = n.num("fat_100g") ?: 0.0,
        )
        val servingGrams = p.optDouble("serving_quantity", Double.NaN)
        val servingLabel = p.optString("serving_size").trim()
        val servings = if (!servingGrams.isNaN() && servingGrams > 0) {
            listOf(Serving(servingLabel.ifEmpty { "1 × ${servingGrams.toInt()} g" }, servingGrams))
        } else emptyList()
        return Food(
            key = "off:$code",
            name = name,
            brand = p.optString("brands").split(',').first().trim(),
            per100 = per100,
            servings = servings,
            source = FoodSource.OFF,
            barcode = code,
        )
    }

    private fun JSONObject.countries(): List<String> {
        val arr = optJSONArray("countries_tags") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun JSONObject.num(key: String): Double? =
        if (has(key)) optDouble(key, Double.NaN).takeUnless { it.isNaN() } else null

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            return when (val code = conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                429 -> throw OffException(R.string.off_rate_limited)
                else -> throw OffException(R.string.off_error, code)
            }
        } finally {
            conn.disconnect()
        }
    }
}
