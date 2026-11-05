package com.countmymacros.data

/** One item recognized from free text, with alternatives the user can switch to instead. */
data class ParsedItem(
    val food: Food,
    val serving: Serving,
    val quantity: Double,
    val alternatives: List<Food>,
)

data class ParsedMeal(val items: List<ParsedItem>, val unmatched: List<String>)

/**
 * Turns a free-text meal description into foods from the local catalog: no AI, just
 * quantity/unit parsing plus [FoodSearch]. Handles direct phrases ("2 empanadas de carne",
 * "una taza de arroz"), not open-ended descriptions ("a bit of cheese on top, not much").
 */
object MealText {
    private val FRAGMENT_SPLIT = Regex("[,;+\\n]+|\\s+(?:y|e|and)\\s+", RegexOption.IGNORE_CASE)
    private val CON_SPLIT = Regex("\\s+(?:con|with)\\s+")
    private val FRACTION = Regex("^(\\d+)\\s*/\\s*(\\d+)\\s*")
    private val NUMBER = Regex("^(\\d+(?:[.,]\\d+)?)\\s*")
    private val PAIR = Regex("^(un par de|a pair of)\\s*")
    // Weight and volume units, longest spellings first. Volume counts as grams (ml ≈ g).
    private val UNIT_GRAMS = Regex(
        "^(kilogramos|kilogramo|kilos|kilo|kgs|kg|gramos|gramo|grs|gr|g|mililitros|mililitro|ml|" +
            "litros|litro|lt|l|onzas|onza|oz|libras|libra|lbs|lb)\\b\\s*(?:de\\s+)?"
    )

    /** Grams in one of the units [UNIT_GRAMS] accepts. */
    private fun gramsPerUnit(unit: String): Double = when (unit) {
        "kilogramos", "kilogramo", "kilos", "kilo", "kgs", "kg", "litros", "litro", "lt", "l" -> 1000.0
        "onzas", "onza", "oz" -> 28.35
        "libras", "libra", "lbs", "lb" -> 453.6
        else -> 1.0
    }

    private val NUMBER_WORDS = mapOf(
        "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0,
        "cinco" to 5.0, "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0,
        "once" to 11.0, "doce" to 12.0, "trece" to 13.0, "catorce" to 14.0, "quince" to 15.0,
        "dieciseis" to 16.0, "diecisiete" to 17.0, "dieciocho" to 18.0, "diecinueve" to 19.0, "veinte" to 20.0,
        "medio" to 0.5, "media" to 0.5,
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
        "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "eleven" to 11.0, "twelve" to 12.0, "thirteen" to 13.0, "fourteen" to 14.0, "fifteen" to 15.0,
        "sixteen" to 16.0, "seventeen" to 17.0, "eighteen" to 18.0, "nineteen" to 19.0, "twenty" to 20.0,
        "half" to 0.5,
    )

    // Harmless to drop: FoodSearch only requires each remaining word to appear in the food's
    // name/aliases, so one fewer required word never hides a real match.
    private val FILLER_WORDS = setOf(
        "de", "del", "el", "la", "los", "las", "of",
        "chico", "chica", "grande", "mediano", "mediana", "small", "large", "medium",
    )
    private val PORTION_WORDS = setOf(
        "taza", "plato", "vaso", "lata", "porcion", "rodaja", "feta", "unidad", "cucharada",
        "cucharadita", "punado", "pieza", "pedazo", "tazon",
        "cup", "glass", "can", "slice", "serving", "portion",
        "tbsp", "tsp", "tablespoon", "teaspoon", "handful", "piece", "bowl",
    )

    fun parse(text: String, foods: List<Food>): ParsedMeal {
        val items = mutableListOf<ParsedItem>()
        val unmatched = mutableListOf<String>()
        for (raw in text.split(FRAGMENT_SPLIT)) {
            val fragment = raw.trim()
            if (fragment.isBlank()) continue
            val found = parseFragment(fragment, foods)
            if (found.isEmpty()) unmatched += fragment else items += found
        }
        return ParsedMeal(items, unmatched)
    }

    private fun parseFragment(fragment: String, foods: List<Food>): List<ParsedItem> {
        var rest = FoodSearch.normalize(fragment).trim().replace(Regex("\\s+"), " ")

        var quantity = 1.0
        val fraction = FRACTION.find(rest)
        val number = NUMBER.find(rest)
        val pair = PAIR.find(rest)
        when {
            fraction != null -> {
                val n = fraction.groupValues[1].toDouble()
                val d = fraction.groupValues[2].toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
                quantity = n / d
                rest = rest.substring(fraction.range.last + 1)
            }
            number != null -> {
                quantity = number.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 1.0
                rest = rest.substring(number.range.last + 1)
            }
            pair != null -> {
                quantity = 2.0
                rest = rest.substring(pair.range.last + 1)
            }
            else -> NUMBER_WORDS[rest.substringBefore(' ')]?.let {
                quantity = it
                rest = rest.substringAfter(' ', "")
            }
        }
        rest = rest.trim()

        var grams = false
        var portionWord: String? = null
        val unit = UNIT_GRAMS.find(rest)
        if (unit != null) {
            grams = true
            quantity *= gramsPerUnit(unit.groupValues[1])
            rest = rest.substring(unit.range.last + 1).trim()
        } else {
            val firstWord = singularize(rest.substringBefore(' '))
            if (firstWord in PORTION_WORDS) {
                portionWord = firstWord
                rest = rest.substringAfter(' ', "").trim().removePrefix("de ").trim()
            }
        }

        rest = rest.split(' ').filter { it.isNotBlank() && it !in FILLER_WORDS }.joinToString(" ")
        if (rest.isBlank()) return emptyList()

        if (grams) {
            val candidates = searchAsIsOrSingular(rest, foods) ?: searchWithWordDrop(rest, foods)
            return candidates?.let { listOf(toItem(it, GRAMS, quantity)) } ?: emptyList()
        }

        searchAsIsOrSingular(rest, foods)?.let { candidates ->
            return listOf(toItem(candidates, pickServing(candidates.first(), portionWord), quantity))
        }

        val conSplit = CON_SPLIT.find(rest)
        if (conSplit != null) {
            val left = rest.substring(0, conSplit.range.first).trim()
            val right = rest.substring(conSplit.range.last + 1).trim()
            val leftItem = searchWithWordDrop(left, foods)
                ?.let { toItem(it, pickServing(it.first(), portionWord), quantity) }
            val rightItems = if (right.isBlank()) emptyList() else parseFragment(right, foods)
            val combined = listOfNotNull(leftItem) + rightItems
            if (combined.isNotEmpty()) return combined
        }

        return searchWithWordDrop(rest, foods)
            ?.let { listOf(toItem(it, pickServing(it.first(), portionWord), quantity)) }
            ?: emptyList()
    }

    /** The match plus up to 4 alternatives, trying the words as-is, then singularized. */
    private fun searchAsIsOrSingular(text: String, foods: List<Food>): List<Food>? {
        if (text.isBlank()) return null
        FoodSearch.search(text, foods, limit = 5).takeIf { it.isNotEmpty() }?.let { return it }
        val words = text.split(' ').filter { it.isNotBlank() }
        val singular = words.joinToString(" ") { singularize(it) }
        if (singular != text) {
            FoodSearch.search(singular, foods, limit = 5).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    /** Drops trailing words one at a time (each try as-is, then singularized) until something matches. */
    private fun searchWithWordDrop(text: String, foods: List<Food>): List<Food>? {
        var words = text.split(' ').filter { it.isNotBlank() }
        while (words.isNotEmpty()) {
            searchAsIsOrSingular(words.joinToString(" "), foods)?.let { return it }
            words = words.dropLast(1)
        }
        return null
    }

    private fun pickServing(food: Food, portionWord: String?): Serving {
        if (portionWord != null) {
            food.servings.firstOrNull { FoodSearch.normalize(it.name).contains(portionWord) }?.let { return it }
        }
        return food.defaultServing.first
    }

    private fun toItem(candidates: List<Food>, serving: Serving, quantity: Double) =
        ParsedItem(candidates.first(), serving, quantity, candidates.drop(1))

    private fun singularize(w: String): String = when {
        w.length > 4 && w.endsWith("es") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") -> w.dropLast(1)
        else -> w
    }
}
