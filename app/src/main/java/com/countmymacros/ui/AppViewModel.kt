package com.countmymacros.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.countmymacros.R
import com.countmymacros.data.BackupException
import com.countmymacros.data.Burn
import com.countmymacros.data.Days
import com.countmymacros.data.Entry
import com.countmymacros.data.Equipment
import com.countmymacros.data.Exercise
import com.countmymacros.data.ExerciseType
import com.countmymacros.data.Food
import com.countmymacros.data.FoodSource
import com.countmymacros.data.Goals
import com.countmymacros.data.LabelResult
import com.countmymacros.data.LabelText
import com.countmymacros.data.Lang
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.MealText
import com.countmymacros.data.Muscle
import com.countmymacros.data.OffException
import com.countmymacros.data.Profile
import com.countmymacros.data.Records
import com.countmymacros.data.Repo
import com.countmymacros.data.Routine
import com.countmymacros.data.Serving
import com.countmymacros.data.Sessions
import com.countmymacros.data.SetRecord
import com.countmymacros.data.Targets
import com.countmymacros.data.Weight
import com.countmymacros.data.Workout
import com.countmymacros.data.WorkoutExercise
import com.countmymacros.data.WorkoutJson
import com.countmymacros.data.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Full-screen pages pushed over the tabs. */
sealed interface Screen {
    data class AddFood(val meal: Meal) : Screen
    data class Describe(val meal: Meal) : Screen
    data class CreateFood(val meal: Meal, val barcode: String? = null) : Screen
    data object EditRoutine : Screen
    data object PickExercises : Screen
    data object RunWorkout : Screen
    data class WorkoutSummary(val workout: Workout, val records: List<SetRecord>) : Screen
    data class ExerciseProgress(val exerciseId: String) : Screen
}

/** Where the exercise picker puts what the user selects. */
enum class PickerTarget { ROUTINE, SESSION }

/**
 * A workout being logged: [live] ones run from [startMs] until finished; the others are logged
 * after the fact with a chosen start and [durationMin].
 */
data class Session(
    val name: String,
    val routineId: Long?,
    val live: Boolean,
    val startMs: Long,
    val durationMin: Int = 60,
    val exercises: List<WorkoutExercise> = emptyList(),
    val restEndMs: Long = 0,
) {
    val date: LocalDate get() = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
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

private const val PERSIST_DELAY_MS = 800L

/** A message with an "Undo" button: [restore] puts back what was just deleted. */
class Undo(val message: UiText, val restore: () -> Unit)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)

    // --- diary ---
    /** The calendar's today, as of the last time the app came to the foreground. */
    var today by mutableStateOf(LocalDate.now()); private set
    var date by mutableStateOf(today); private set
    var entries by mutableStateOf<List<Entry>>(emptyList()); private set
    var profile by mutableStateOf(repo.prefs.profile); private set
    var profileSet by mutableStateOf(repo.prefs.profileSet); private set
    val targets: Targets get() = Goals.targets(profile)

    /** Bumped whenever saved foods change, so lists that show them re-read. */
    var foodsVersion by mutableIntStateOf(0); private set

    // --- training ---
    var exercises by mutableStateOf<List<Exercise>>(emptyList()); private set
    var routines by mutableStateOf<List<Routine>>(emptyList()); private set
    var recentWorkouts by mutableStateOf<List<Workout>>(emptyList()); private set

    /** Workouts finished on the diary's day. Their calories are shown, not added to the budget: the activity level already counts exercise. */
    var dayWorkouts by mutableStateOf<List<Workout>>(emptyList()); private set
    val burned: Double get() = dayWorkouts.sumOf { it.kcal }

    var routineDraft by mutableStateOf<Routine?>(null); private set
    var session by mutableStateOf<Session?>(null); private set
    var previous by mutableStateOf<Map<String, List<WorkoutSet>>>(emptyMap()); private set
    private var pickerTarget = PickerTarget.ROUTINE

    // --- navigation ---
    val stack = mutableStateListOf<Screen>()
    var sheet by mutableStateOf<FoodSheet?>(null)
    var toast by mutableStateOf<UiText?>(null)

    /** Set after a delete: the snackbar offers to take it back. */
    var undo by mutableStateOf<Undo?>(null)

    // Scroll positions live here so they survive leaving a tab or opening a pushed page
    // (the tabs leave the composition while a page is open).
    val diaryList = LazyListState()
    val trainList = LazyListState()
    val progressList = LazyListState()
    val profileScroll = ScrollState(0)

    init {
        reload()
        loadTraining()
        restoreSession()
    }

    fun reload() {
        val d = date
        viewModelScope.launch {
            entries = withContext(Dispatchers.IO) { repo.day(d) }
            dayWorkouts = withContext(Dispatchers.IO) { repo.workoutsOn(d) }
        }
    }

    fun setDay(d: LocalDate) {
        date = d
        reload()
    }

    /** Called when the app comes to the foreground: after midnight, a diary left on "today" moves to the new today. */
    fun refreshToday() {
        val now = LocalDate.now()
        if (now == today) return
        val shown = Days.follow(date, today, now)
        today = now
        if (shown != date) setDay(shown)
    }

    fun push(s: Screen) {
        if (s is Screen.CreateFood) {
            labelResult = null
            labelError = null
        }
        if (s is Screen.AddFood) {
            addedKeys.clear()
            addedCount = 0
            addedKcal = 0.0
        }
        stack.add(s)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    // --- entries ---

    /** What was added during the current visit to Add food: drives the ticks and the "Listo" bar. */
    val addedKeys = mutableStateListOf<String>()
    var addedCount by mutableIntStateOf(0); private set
    var addedKcal by mutableStateOf(0.0); private set

    /** [silent] skips the toast: the add screen shows a tick instead. */
    fun addFood(food: Food, meal: Meal, servingName: String, servingGrams: Double, quantity: Double, silent: Boolean = false) {
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
        addedKeys.add(food.key)
        addedCount++
        addedKcal += e.macros.kcal
        if (!silent) toast = uiText(R.string.toast_added, food.name, uiText(meal.label))
    }

    fun deleteFood(food: Food) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteFood(food.key) }
            foodsVersion++
        }
    }

    fun updateEntry(e: Entry) = io { repo.update(e) }

    fun deleteEntry(e: Entry) {
        io { repo.delete(e.id) }
        undo = Undo(uiText(R.string.toast_entry_deleted, e.name)) { io { repo.add(e.copy(id = 0)) } }
    }

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

    // --- training ---

    private val exercisesById by derivedStateOf { exercises.associateBy { it.id } }

    fun exercise(id: String): Exercise? = exercisesById[id]

    private fun loadTraining() {
        viewModelScope.launch {
            exercises = withContext(Dispatchers.IO) { repo.exercises() }
            routines = withContext(Dispatchers.IO) { repo.routines() }
            recentWorkouts = withContext(Dispatchers.IO) { repo.recentWorkouts() }
        }
    }

    fun newRoutine() {
        routineDraft = Routine(name = "")
        push(Screen.EditRoutine)
    }

    fun editRoutine(r: Routine) {
        routineDraft = r
        push(Screen.EditRoutine)
    }

    fun updateRoutine(r: Routine) {
        routineDraft = r
    }

    fun saveRoutine() {
        val r = routineDraft ?: return
        if (r.name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveRoutine(r.copy(name = r.name.trim())) }
            routines = withContext(Dispatchers.IO) { repo.routines() }
            routineDraft = null
            pop()
        }
    }

    fun deleteRoutine(r: Routine) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteRoutine(r.id) }
            routines = withContext(Dispatchers.IO) { repo.routines() }
        }
    }

    fun createExercise(name: String, type: ExerciseType, muscle: Muscle, equipment: Equipment, onDone: (Exercise) -> Unit) {
        viewModelScope.launch {
            val e = withContext(Dispatchers.IO) { repo.addCustomExercise(name.trim(), type, muscle, equipment) }
            exercises = exercises + e
            onDone(e)
        }
    }

    fun pickExercises(target: PickerTarget) {
        pickerTarget = target
        push(Screen.PickExercises)
    }

    /** Appends the picked exercises, each with three empty sets, to the routine or workout in progress. */
    fun addExercises(ids: List<String>) {
        val added = ids.map { WorkoutExercise(it, sets = List(3) { WorkoutSet() }) }
        if (pickerTarget == PickerTarget.ROUTINE) {
            routineDraft = routineDraft?.let { it.copy(exercises = it.exercises + added) }
        } else {
            session?.let { updateSession(it.copy(exercises = it.exercises + added)) }
            loadPrevious(ids)
        }
        pop()
    }

    private fun loadPrevious(ids: List<String>) {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { repo.previousSets(ids) }
            previous = previous + found
        }
    }

    /** Starts a live workout from [routine] (or a free one when null). */
    fun startWorkout(routine: Routine?, defaultName: String) {
        val exercises = routine?.exercises.orEmpty().map { e -> e.copy(sets = e.sets.map { it.copy(done = false) }) }
        session = Session(routine?.name ?: defaultName, routine?.id, live = true, startMs = System.currentTimeMillis(), exercises = exercises)
        persistJob?.cancel()
        persistSession()
        loadPrevious(exercises.map { it.exerciseId })
        push(Screen.RunWorkout)
    }

    /** Logs [routine] as already done: an hour ago by default, every set ticked, to be adjusted. */
    fun logWorkout(routine: Routine, minutes: Int = 60) {
        val start = System.currentTimeMillis() - minutes * 60_000L
        val done = routine.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(done = true) }) }
        session = Session(routine.name, routine.id, live = false, startMs = start, durationMin = minutes, exercises = done)
        push(Screen.RunWorkout)
    }

    fun updateSession(s: Session) {
        session = s
        if (s.live) schedulePersist()
    }

    /** Puts a removed set back where it was (undo). */
    fun reinsertSet(exerciseIndex: Int, setIndex: Int, set: WorkoutSet) {
        val s = session ?: return
        val e = s.exercises.getOrNull(exerciseIndex) ?: return
        val sets = e.sets.toMutableList().apply { add(setIndex.coerceIn(0, size), set) }
        updateSession(s.copy(exercises = s.exercises.replaced(exerciseIndex, e.copy(sets = sets))))
    }

    /** Puts a removed exercise back where it was (undo). */
    fun reinsertExercise(index: Int, item: WorkoutExercise) {
        val s = session ?: return
        updateSession(s.copy(exercises = s.exercises.toMutableList().apply { add(index.coerceIn(0, size), item) }))
    }

    fun discardSession() {
        persistJob?.cancel()
        session = null
        repo.prefs.activeWorkout = null
    }

    /**
     * Saves the workout, or says why not when nothing was completed. A live workout ends now unless
     * [durationMin] says how long it really lasted (for one that was forgotten open).
     */
    fun finishSession(durationMin: Int? = null) {
        val s = session ?: return
        val now = System.currentTimeMillis()
        val endMs = when {
            !s.live -> s.startMs + s.durationMin * 60_000L
            durationMin != null -> s.startMs + durationMin * 60_000L
            else -> now
        }
        val done = s.exercises
            .map { e -> e.copy(sets = e.sets.filter { it.done }) }
            .filter { it.sets.isNotEmpty() }
        if (done.isEmpty()) {
            toast = uiText(R.string.toast_no_sets)
            return
        }
        val kcal = Burn.kcal((endMs - s.startMs) / 1000, profile.weightKg)
        val w = Workout(
            date = s.date.toString(), startMs = s.startMs, endMs = endMs, name = s.name,
            routineId = s.routineId, kcal = kcal, exercises = done,
        )
        viewModelScope.launch {
            // Records are measured against what was saved before this workout.
            val history = withContext(Dispatchers.IO) { repo.recentWorkouts(60) }
            val records = Records.find(done, history)
            val id = withContext(Dispatchers.IO) { repo.addWorkout(w) }
            recentWorkouts = withContext(Dispatchers.IO) { repo.recentWorkouts() }
            reload()
            discardSession()
            stack.clear()
            push(Screen.WorkoutSummary(w.copy(id = id), records))
        }
    }

    fun deleteWorkout(w: Workout) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteWorkout(w.id) }
            recentWorkouts = withContext(Dispatchers.IO) { repo.recentWorkouts() }
            reload()
        }
    }

    private var persistJob: Job? = null

    /** Every keystroke edits the session; writing it out each time rewrote the whole prefs file per character. */
    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DELAY_MS)
            persistSession()
        }
    }

    /** Writes a pending edit right now: the app is being stopped and the delay may never elapse. */
    fun flushSession() {
        if (persistJob?.isActive != true) return
        persistJob?.cancel()
        persistSession()
    }

    private fun persistSession() {
        val s = session ?: return
        repo.prefs.activeWorkout = JSONObject()
            .put("name", s.name).put("routine", s.routineId ?: -1L).put("start", s.startMs)
            .put("rest", s.restEndMs).put("ex", WorkoutJson.encode(s.exercises)).toString()
    }

    private fun restoreSession() {
        val json = repo.prefs.activeWorkout ?: return
        session = runCatching {
            val o = JSONObject(json)
            val exercises = WorkoutJson.decode(o.getString("ex"))
            loadPrevious(exercises.map { it.exerciseId })
            Session(
                name = o.getString("name"),
                routineId = o.getLong("routine").takeIf { it >= 0 },
                live = true,
                startMs = o.getLong("start"),
                exercises = exercises,
                restEndMs = o.optLong("rest"),
            )
        }.getOrNull()
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
        undo = Undo(uiText(R.string.toast_weight_deleted)) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { repo.putWeight(w) }
                loadWeights()
            }
        }
    }

    // --- export / import ---

    fun exportTo(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            toast = try {
                withContext(Dispatchers.IO) {
                    val json = repo.exportJson()
                    val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("no output stream")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                uiText(R.string.toast_exported)
            } catch (e: Exception) {
                uiText(R.string.toast_export_failed)
            }
        }
    }

    /** Replaces all data with the backup file at [uri]. A bad file changes nothing. */
    fun importFrom(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            toast = try {
                withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: throw IOException("no input stream")
                    repo.importJson(input.use { it.readBytes().toString(Charsets.UTF_8) })
                }
                reloadAll()
                uiText(R.string.toast_imported)
            } catch (e: BackupException) {
                uiText(R.string.toast_import_invalid)
            } catch (e: Exception) {
                uiText(R.string.toast_import_failed)
            }
        }
    }

    private fun reloadAll() {
        profile = repo.prefs.profile
        profileSet = repo.prefs.profileSet
        foodsVersion++
        reload()
        loadTraining()
        loadWeights()
    }

    private fun io(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            reload()
        }
    }
}
