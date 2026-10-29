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
import com.countmymacros.data.Entry
import com.countmymacros.data.Food
import com.countmymacros.data.FoodSource
import com.countmymacros.data.Goals
import com.countmymacros.data.LabelResult
import com.countmymacros.data.LabelText
import com.countmymacros.data.Lang
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.MealText
import com.countmymacros.data.OffException
import com.countmymacros.data.Profile
import com.countmymacros.data.Repo
import com.countmymacros.data.Serving
import com.countmymacros.data.Targets
import com.countmymacros.data.Weight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

/** Full-screen pages pushed over the tabs. */
sealed interface Screen {
    data class AddFood(val meal: Meal) : Screen
    data class Describe(val meal: Meal) : Screen
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

/** One item [MealText] recognized from the description, while the user reviews it. */
data class DescribeItem(
    val food: Food,
    val serving: Serving,
    val quantity: Double,
    val alternatives: List<Food>,
    val selected: Boolean = true,
)

/** Maps any failure to something the user can read. */
fun errorText(e: Throwable): UiText = when (e) {
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

    fun saveCustomFood(food: Food, meal: Meal) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveFood(food) }
            foodsVersion++
        }
        pop()
        sheet = FoodSheet(food, meal)
    }

    // --- describe (free text, no AI: MealText + the local catalog) ---

    val describeItems = mutableStateListOf<DescribeItem>()
    var describeText by mutableStateOf(""); private set
    var describeUnmatched by mutableStateOf<List<String>>(emptyList()); private set

    fun describe(text: String, meal: Meal) {
        describeItems.clear()
        describeText = text
        describeUnmatched = emptyList()
        push(Screen.Describe(meal))
        val lang = Lang.current
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) { MealText.parse(text, repo.localFoods(lang)) }
            describeItems.addAll(parsed.items.map { DescribeItem(it.food, it.serving, it.quantity, it.alternatives) })
            describeUnmatched = parsed.unmatched
        }
    }

    fun addDescribed(meal: Meal) {
        val chosen = describeItems.filter { it.selected && it.quantity > 0 }
        val now = System.currentTimeMillis()
        io {
            chosen.forEachIndexed { i, d ->
                repo.add(
                    Entry(
                        date = date.toString(),
                        meal = meal,
                        foodKey = d.food.key,
                        name = d.food.name,
                        brand = d.food.brand,
                        per100 = d.food.per100,
                        servingName = d.serving.name,
                        servingGrams = d.serving.grams,
                        quantity = d.quantity,
                        source = d.food.source,
                        createdAt = now + i,
                    )
                )
            }
        }
        toast = UiText.Plural(R.plurals.toast_added_n, chosen.size, listOf(chosen.size, uiText(meal.label)))
        stack.clear()
    }

    // --- nutrition label (OCR, no AI: ML Kit text recognition + LabelText rules) ---

    var labelLoading by mutableStateOf(false); private set
    var labelResult by mutableStateOf<LabelResult?>(null)
    var labelError by mutableStateOf<UiText?>(null)

    fun scanLabel(jpeg: ByteArray) {
        labelError = null
        labelResult = null
        labelLoading = true
        viewModelScope.launch {
            try {
                val lines = withContext(Dispatchers.IO) { readLabelLines(jpeg) }
                labelResult = LabelText.parse(lines) ?: run { labelError = uiText(R.string.label_failed); null }
            } catch (e: Exception) {
                labelError = errorText(e)
            } finally {
                labelLoading = false
            }
        }
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
