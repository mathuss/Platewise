package com.countmymacros.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.countmymacros.R
import com.countmymacros.data.AiException
import com.countmymacros.data.AiLabel
import com.countmymacros.data.AiMeal
import com.countmymacros.data.Entry
import com.countmymacros.data.Food
import com.countmymacros.data.FoodAi
import com.countmymacros.data.FoodSearch
import com.countmymacros.data.FoodSource
import com.countmymacros.data.GRAMS
import com.countmymacros.data.Goals
import com.countmymacros.data.Lang
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.OffException
import com.countmymacros.data.Profile
import com.countmymacros.data.Repo
import com.countmymacros.data.Serving
import com.countmymacros.data.Targets
import com.countmymacros.data.Weight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Full-screen pages pushed over the tabs. */
sealed interface Screen {
    data class AddFood(val meal: Meal) : Screen
    data class AiReview(val meal: Meal) : Screen
    data class CreateFood(val meal: Meal, val barcode: String? = null) : Screen
}

/**
 * The quantity picker: adding [food] to [meal], or editing an existing [entry].
 * [preset] is the starting serving and quantity (e.g. what was used last time).
 */
data class FoodSheet(
    val food: Food,
    val meal: Meal,
    val entry: Entry? = null,
    val preset: Pair<Serving, Double>? = null,
)

/** One AI-detected item while the user reviews it. Macros scale with [grams]. */
data class ReviewItem(
    val name: String,
    val grams: Double,
    val per100: Macros,
    val confidence: String,
    val selected: Boolean = true,
) {
    val macros: Macros get() = per100.scaled(grams / 100.0)
}

/** Maps any failure to something the user can read. */
fun errorText(e: Throwable): UiText = when (e) {
    is AiException -> UiText.Res(e.messageRes, listOfNotNull(e.arg))
    is OffException -> UiText.Res(e.messageRes, listOfNotNull(e.arg))
    is IOException -> uiText(R.string.offline)
    else -> uiText(R.string.generic_error, e.message ?: e.javaClass.simpleName)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)

    // --- diary ---
    var date by mutableStateOf(LocalDate.now()); private set
    var entries by mutableStateOf<List<Entry>>(emptyList()); private set
    var profile by mutableStateOf(repo.prefs.profile); private set
    var profileSet by mutableStateOf(repo.prefs.profileSet); private set
    val targets: Targets get() = Goals.targets(profile)

    /** Bumped whenever saved foods change, so lists that show them re-read. */
    var foodsVersion by mutableIntStateOf(0); private set

    // --- navigation ---
    val stack = mutableStateListOf<Screen>()
    var sheet by mutableStateOf<FoodSheet?>(null)
    var toast by mutableStateOf<UiText?>(null)

    // --- AI meal review (photo or text) ---
    var aiImage by mutableStateOf<ByteArray?>(null); private set
    var aiText by mutableStateOf<String?>(null); private set
    var aiLoading by mutableStateOf(false); private set
    var aiError by mutableStateOf<UiText?>(null); private set
    var aiNotes by mutableStateOf(""); private set
    val aiItems = mutableStateListOf<ReviewItem>()
    private var aiJob: Job? = null

    // --- nutrition label scan (create food) ---
    var labelLoading by mutableStateOf(false); private set
    var labelResult by mutableStateOf<AiLabel?>(null)
    var labelError by mutableStateOf<UiText?>(null)

    var apiKey by mutableStateOf(repo.prefs.apiKey); private set
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    private var ai: FoodAi? = null

    init {
        reload()
    }

    fun reload() {
        val d = date
        viewModelScope.launch {
            entries = withContext(Dispatchers.IO) { repo.day(d) }
        }
    }

    fun setDay(d: LocalDate) {
        date = d
        reload()
    }

    /** The meal that fits the current time of day, like Fitia's per-country meal hours. */
    fun mealForNow(): Meal {
        val t = LocalTime.now()
        return when {
            t < LocalTime.of(10, 0) -> Meal.BREAKFAST
            t < LocalTime.of(12, 0) -> Meal.MID_MORNING
            t < LocalTime.of(16, 0) -> Meal.LUNCH
            t < LocalTime.of(20, 0) -> Meal.SNACK
            else -> Meal.DINNER
        }
    }

    fun push(s: Screen) {
        if (s is Screen.CreateFood) {
            labelResult = null
            labelError = null
        }
        stack.add(s)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun closeAll() = stack.clear()

    // --- entries ---

    fun addFood(food: Food, meal: Meal, servingName: String, servingGrams: Double, quantity: Double) {
        val e = Entry(
            date = date.toString(),
            meal = meal,
            foodKey = food.key,
            name = food.name,
            brand = food.brand,
            per100 = food.per100,
            servingName = servingName,
            servingGrams = servingGrams,
            quantity = quantity,
            source = food.source,
        )
        io {
            // Keep scanned/searched products available offline next time.
            if (food.source == FoodSource.OFF) repo.saveFood(food)
            repo.add(e)
        }
        if (food.source == FoodSource.OFF) foodsVersion++
        toast = uiText(R.string.toast_added, food.name, uiText(meal.label))
    }

    fun deleteFood(food: Food) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteFood(food.key) }
            foodsVersion++
        }
    }

    fun updateEntry(e: Entry) = io { repo.update(e) }

    fun deleteEntry(e: Entry) = io { repo.delete(e.id) }

    /** Fitia's QuickItemModel: one 100 g "serving" carries exactly these totals. */
    fun quickAdd(meal: Meal, name: String, macros: Macros) {
        val e = Entry(
            date = date.toString(),
            meal = meal,
            foodKey = "quick:" + UUID.randomUUID(),
            name = name,
            per100 = macros,
            servingName = "quick",
            servingGrams = 100.0,
            quantity = 1.0,
            source = FoodSource.QUICK,
        )
        io { repo.add(e) }
        toast = uiText(R.string.toast_added_meal, uiText(meal.label))
    }

    fun copyFromYesterday(meal: Meal) {
        val today = date
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { repo.copyMeal(today.minusDays(1), today, meal) }
            toast = if (n == 0) uiText(R.string.toast_nothing_yesterday, uiText(meal.label))
            else UiText.Plural(R.plurals.toast_copied, n, listOf(n))
            reload()
        }
    }

    // --- profile & settings ---

    fun saveProfile(p: Profile) {
        profile = p
        repo.prefs.profile = p
        if (!profileSet) {
            profileSet = true
            repo.prefs.profileSet = true
        }
    }

    fun saveApiKey(key: String) {
        repo.prefs.apiKey = key
        apiKey = key.trim()
        ai = null
    }

    private fun aiClient(): FoodAi =
        ai ?: FoodAi(apiKey).also { ai = it }

    // --- AI meal estimation ---

    fun analyzePhoto(jpeg: ByteArray, meal: Meal) {
        aiImage = jpeg
        aiText = null
        if (stack.lastOrNull() !is Screen.AiReview) push(Screen.AiReview(meal))
        val lang = Lang.current
        runAi { aiClient().estimatePhoto(jpeg, lang = lang) }
    }

    fun analyzeText(text: String, meal: Meal) {
        aiImage = null
        aiText = text
        if (stack.lastOrNull() !is Screen.AiReview) push(Screen.AiReview(meal))
        val lang = Lang.current
        runAi { aiClient().estimateText(text, lang) }
    }

    /** Re-run with a correction from the user ("half a portion", "no oil"). */
    fun reanalyze(note: String) {
        val image = aiImage
        val text = aiText
        val lang = Lang.current
        when {
            image != null -> runAi { aiClient().estimatePhoto(image, note, lang) }
            text != null -> runAi { aiClient().estimateText(if (note.isBlank()) text else "$text. $note", lang) }
        }
    }

    private fun runAi(call: () -> AiMeal) {
        aiJob?.cancel()
        aiItems.clear()
        aiNotes = ""
        aiError = null
        if (!hasApiKey) {
            aiError = uiText(R.string.need_key)
            return
        }
        aiLoading = true
        aiJob = viewModelScope.launch {
            try {
                val meal = withContext(Dispatchers.IO) { call() }
                aiNotes = meal.notes
                aiItems.addAll(meal.items.map {
                    val grams = if (it.grams > 0) it.grams else 100.0
                    ReviewItem(it.name, grams, it.macros.scaled(100.0 / grams), it.confidence)
                })
                if (meal.items.isEmpty() && meal.notes.isBlank()) aiError = uiText(R.string.no_food_detected)
            } catch (e: Exception) {
                aiError = errorText(e)
            } finally {
                aiLoading = false
            }
        }
    }

    fun addReviewed(meal: Meal) {
        val chosen = aiItems.filter { it.selected && it.grams > 0 }
        val now = System.currentTimeMillis()
        io {
            chosen.forEachIndexed { i, it ->
                repo.add(
                    Entry(
                        date = date.toString(),
                        meal = meal,
                        foodKey = aiKey(it.name),
                        name = it.name,
                        per100 = it.per100,
                        servingName = GRAMS.name,
                        servingGrams = GRAMS.grams,
                        quantity = it.grams,
                        source = FoodSource.AI,
                        createdAt = now + i,
                    )
                )
            }
        }
        toast = UiText.Plural(R.plurals.toast_added_n, chosen.size, listOf(chosen.size, uiText(meal.label)))
        closeAll()
    }

    // --- nutrition label ---

    fun scanLabel(jpeg: ByteArray) {
        labelError = null
        labelResult = null
        if (!hasApiKey) {
            labelError = uiText(R.string.need_key_label)
            return
        }
        labelLoading = true
        val lang = Lang.current
        viewModelScope.launch {
            try {
                labelResult = withContext(Dispatchers.IO) { aiClient().readLabel(jpeg, lang) }
            } catch (e: Exception) {
                labelError = errorText(e)
            } finally {
                labelLoading = false
            }
        }
    }

    fun saveCustomFood(food: Food, meal: Meal) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveFood(food) }
            foodsVersion++
        }
        pop()
        sheet = FoodSheet(food, meal)
    }

    // --- barcode ---

    var barcodeLoading by mutableStateOf(false); private set
    var barcodeMissing by mutableStateOf<String?>(null)

    fun lookupBarcode(code: String, meal: Meal) {
        barcodeLoading = true
        val lang = Lang.current
        viewModelScope.launch {
            try {
                val food = withContext(Dispatchers.IO) { repo.barcode(code, lang) }
                if (food != null) {
                    foodsVersion++ // found products are cached in saved foods
                    sheet = FoodSheet(food, meal)
                } else barcodeMissing = code
            } catch (e: Exception) {
                toast = errorText(e)
            } finally {
                barcodeLoading = false
            }
        }
    }

    // --- weight ---

    var weights by mutableStateOf<List<Weight>>(emptyList()); private set

    fun loadWeights() = viewModelScope.launch {
        weights = withContext(Dispatchers.IO) { repo.weights() }
    }

    fun addWeight(kg: Double) {
        val w = Weight(LocalDate.now().toString(), kg)
        // The latest weight also drives the calorie targets.
        saveProfile(profile.copy(weightKg = kg))
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.putWeight(w) }
            loadWeights()
        }
    }

    fun deleteWeight(w: Weight) = viewModelScope.launch {
        withContext(Dispatchers.IO) { repo.deleteWeight(w.date) }
        loadWeights()
    }

    private fun io(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            reload()
        }
    }
}

/** AI items group in "Recent" by normalized name. */
private fun aiKey(name: String) = "ai:" + FoodSearch.normalize(name).replace(Regex("[^a-z0-9]+"), "-")
