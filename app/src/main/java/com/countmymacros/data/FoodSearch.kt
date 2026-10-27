package com.countmymacros.data

import java.text.Normalizer

/** Accent- and case-insensitive local search: every query word must appear in the name, brand or aliases. */
object FoodSearch {
    private val MARKS = Regex("\\p{Mn}+")

    fun normalize(s: String): String =
        MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    fun search(query: String, foods: List<Food>, limit: Int = 40): List<Food> {
        val words = normalize(query).split(' ', ',').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return foods.mapNotNull { f ->
            val name = normalize(f.name)
            val hay = "$name ${normalize(f.brand)} ${normalize(f.aliases)}"
            if (!words.all { hay.contains(it) }) return@mapNotNull null
            val first = words.first()
            val score = when {
                name.startsWith(first) -> 0
                name.split(' ', '(', '/').any { it.startsWith(first) } -> 1
                name.contains(first) -> 2
                else -> 3 // matched only via brand/aliases
            }
            Triple(f, score, name.length)
        }
            .sortedWith(compareBy({ it.second }, { it.third }))
            .take(limit)
            .map { it.first }
    }
}
