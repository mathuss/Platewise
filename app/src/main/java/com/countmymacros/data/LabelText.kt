package com.countmymacros.data

/** One line of OCR'd text with its bounding box in image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

data class LabelResult(
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val servingGrams: Double?,
    val per100: Boolean,
)

/**
 * Pulls kcal/protein/carbs/fat out of a photographed nutrition facts label from its OCR'd
 * lines: no AI, just keyword and geometry matching. Works well on sharp, front-on labels;
 * gives up (returns null) rather than guess on blurry photos or unusual layouts.
 */
object LabelText {
    private val ENERGY_KEYS = listOf("valor energetico", "energia", "calorias", "energy", "calories")
    private val PROTEIN_KEYS = listOf("proteinas", "proteina", "protein")
    private val CARBS_KEYS = listOf("hidratos de carbono", "carbohidratos", "carbohidrato", "carbohydrate")
    private val FAT_KEYS = listOf("grasas totales", "grasa total", "total fat", "grasas", "fat")
    private val FAT_SKIP = listOf("satura", "trans", "mono", "poli", "unsaturat")
    private val SERVING_KEYS = listOf("tamano de la porcion", "tamano de porcion", "tamano por porcion", "porcion", "serving size")
    private val PER_100_MARK = listOf("100 g", "100g", "100 ml", "100ml", "por 100", "per 100")

    private const val NUM = "(\\d+(?:[.,]\\d+)?)"
    private fun unitRegex(unit: String) = Regex("$NUM\\s*$unit\\b", RegexOption.IGNORE_CASE)
    private val PLAIN_NUM = Regex("^$NUM$")
    private val PERCENT_ONLY = Regex("^$NUM\\s*%$")

    fun parse(lines: List<OcrLine>): LabelResult? {
        val rows = lines.map { Row(it, FoodSearch.normalize(it.text)) }

        val per100Header = rows.any { r -> PER_100_MARK.any { r.norm.contains(it) } }
        val servingGrams = findServingGrams(rows)

        val kcal = findNumber(rows, ENERGY_KEYS, emptyList(), ::energyFrom)
        val protein = findNumber(rows, PROTEIN_KEYS, emptyList(), ::gramsFrom)
        val carbs = findNumber(rows, CARBS_KEYS, emptyList(), ::gramsFrom)
        val fat = findNumber(rows, FAT_KEYS, FAT_SKIP, ::gramsFrom)

        if (kcal == null || listOfNotNull(protein, carbs, fat).size < 2) return null
        return LabelResult(kcal, protein, carbs, fat, servingGrams, per100Header || servingGrams == null)
    }

    private class Row(val line: OcrLine, val norm: String)

    private fun energyFrom(text: String): Double? {
        unitRegex("kcal").find(text)?.let { return toDouble(it.groupValues[1]) }
        unitRegex("kj").find(text)?.let { return toDouble(it.groupValues[1]) / 4.184 }
        return null
    }

    private fun gramsFrom(text: String): Double? = unitRegex("g").find(text)?.let { toDouble(it.groupValues[1]) }

    private fun toDouble(s: String) = s.replace(',', '.').toDoubleOrNull() ?: 0.0

    /** The keyword line's own text first, else the nearest non-percent column to its right. */
    private fun findNumber(rows: List<Row>, keys: List<String>, skip: List<String>, extract: (String) -> Double?): Double? {
        val key = rows.firstOrNull { r -> skip.none { r.norm.contains(it) } && keys.any { r.norm.contains(it) } } ?: return null
        extract(key.line.text)?.let { return it }
        for (candidate in sameRowToTheRight(rows, key)) {
            val t = candidate.line.text.trim()
            if (PERCENT_ONLY.matches(t)) continue
            extract(t)?.let { return it }
            if (PLAIN_NUM.matches(t)) return toDouble(t)
        }
        return null
    }

    private fun findServingGrams(rows: List<Row>): Double? {
        val key = rows.firstOrNull { r -> SERVING_KEYS.any { r.norm.contains(it) } } ?: return null
        fun amount(text: String) = unitRegex("g").find(text) ?: unitRegex("ml").find(text)
        amount(key.line.text)?.let { return toDouble(it.groupValues[1]) }
        for (candidate in sameRowToTheRight(rows, key)) {
            amount(candidate.line.text)?.let { return toDouble(it.groupValues[1]) }
        }
        return null
    }

    private fun sameRowToTheRight(rows: List<Row>, key: Row): List<Row> =
        rows.filter { it !== key && it.line.left >= key.line.right && verticalOverlap(it.line, key.line) }
            .sortedBy { it.line.left }

    private fun verticalOverlap(a: OcrLine, keyLine: OcrLine): Boolean =
        (a.top + a.bottom) / 2 in keyLine.top..keyLine.bottom
}
