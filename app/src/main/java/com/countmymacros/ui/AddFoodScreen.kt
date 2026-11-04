package com.countmymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Entry
import com.countmymacros.data.Food
import com.countmymacros.data.FoodSource
import com.countmymacros.data.Lang
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.Serving
import com.countmymacros.data.parseNum
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private sealed interface Online {
    data object Idle : Online
    data object Loading : Online
    data class Done(val query: String, val foods: List<Food>) : Online
    data class Failed(val message: UiText) : Online
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFoodScreen(vm: AppViewModel, initialMeal: Meal) {
    var meal by remember { mutableStateOf(initialMeal) }
    var query by remember { mutableStateOf("") }
    var online by remember { mutableStateOf<Online>(Online.Idle) }
    var quickDialog by remember { mutableStateOf(false) }
    var describeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // Re-read after anything is logged or saved so recents/my foods stay current.
    // (The lint suppressions are for a K2 false positive: these lambdas do assign `value`.)
    @Suppress("ProduceStateDoesNotAssignValue")
    val recents by produceState(emptyList<Entry>(), vm.entries) {
        value = withContext(Dispatchers.IO) { vm.repo.recentFoods() }
    }
    @Suppress("ProduceStateDoesNotAssignValue")
    val myFoods by produceState(emptyList<Food>(), vm.foodsVersion) {
        value = withContext(Dispatchers.IO) { vm.repo.myFoods() }
    }
    @Suppress("ProduceStateDoesNotAssignValue")
    val local by produceState(emptyList<Food>(), query, myFoods) {
        value = if (query.isBlank()) emptyList() else withContext(Dispatchers.IO) { vm.repo.searchLocal(query, Lang.current) }
    }

    fun searchOnline() {
        val q = query.trim()
        if (q.length < 2) return
        // Hide the keyboard but keep focus in the field: clearing focus here moved it to the
        // back button, and a hardware Enter's key-up then "clicked" it and closed the screen.
        keyboard?.hide()
        online = Online.Loading
        val lang = Lang.current
        scope.launch {
            online = try {
                Online.Done(q, withContext(Dispatchers.IO) { vm.repo.searchOnline(q, lang) })
            } catch (e: Exception) {
                Online.Failed(errorText(e))
            }
        }
    }

    fun scanBarcode() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { vm.lookupBarcode(it, meal) } }
            .addOnFailureListener { vm.toast = uiText(R.string.toast_scanner_error) }
    }

    fun open(food: Food, preset: Pair<Serving, Double>? = null) {
        focus.clearFocus()
        vm.sheet = FoodSheet(food, meal, preset = preset)
    }

    /** The "+" button: adds the amount used last time for this food, or its default serving. */
    fun quickAdd(food: Food) {
        val last = recents.firstOrNull { it.foodKey == food.key }
        val (sv, qty) = if (last != null) Serving(last.servingName, last.servingGrams) to last.quantity else food.defaultServing
        vm.addFood(food, meal, sv.name, sv.grams, qty, silent = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_food_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            if (vm.addedCount > 0) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                pluralStringResource(R.plurals.added_summary, vm.addedCount, vm.addedCount),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.added_kcal_in, vm.addedKcal.roundToInt(), stringResource(meal.label)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = vm::pop) { Text(stringResource(R.string.done_action)) }
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Meal.entries.forEach { m ->
                            FilterChip(
                                selected = m == meal,
                                onClick = { meal = m },
                                label = { Text(stringResource(m.label)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; online = Online.Idle },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; online = Online.Idle }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.clear))
                                }
                            } else {
                                IconButton(onClick = ::scanBarcode) {
                                    Icon(Icons.Filled.QrCodeScanner, stringResource(R.string.chip_barcode), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchOnline() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        QuickAction(Icons.Filled.QrCodeScanner, stringResource(R.string.qa_scan), ::scanBarcode)
                        QuickAction(Icons.AutoMirrored.Filled.ShortText, stringResource(R.string.chip_describe)) { describeDialog = true }
                        QuickAction(Icons.Filled.Bolt, stringResource(R.string.chip_quick)) { quickDialog = true }
                        QuickAction(Icons.Filled.EditNote, stringResource(R.string.qa_create)) { vm.push(Screen.CreateFood(meal)) }
                    }
                    if (vm.barcodeLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.looking_up_product), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            if (query.isBlank()) {
                if (recents.isNotEmpty()) {
                    item { Header(stringResource(R.string.recents)) }
                    items(recents, key = { "r" + it.id }) { e ->
                        val food = e.asFood()
                        FoodRow(
                            food, e.macros, amountLabel(e), e.macros.kcal,
                            added = food.key in vm.addedKeys,
                            addDescription = stringResource(R.string.add_to_meal, stringResource(meal.label)),
                            onAdd = { quickAdd(food) },
                        ) { open(food, Serving(e.servingName, e.servingGrams) to e.quantity) }
                    }
                }
                if (myFoods.isNotEmpty()) {
                    item { Header(stringResource(R.string.my_foods)) }
                    items(myFoods, key = { "m" + it.key }) { f ->
                        FoodRow(
                            f, f.per100, per100Label(f), f.per100.kcal,
                            added = f.key in vm.addedKeys,
                            addDescription = stringResource(R.string.add_to_meal, stringResource(meal.label)),
                            onAdd = { quickAdd(f) },
                            onDelete = { vm.deleteFood(f) },
                        ) { open(f) }
                    }
                }
                if (recents.isEmpty() && myFoods.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.empty_add_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(local, key = { "l" + it.key }) { f ->
                    FoodRow(
                        f, f.per100, per100Label(f), f.per100.kcal,
                        added = f.key in vm.addedKeys,
                        addDescription = stringResource(R.string.add_to_meal, stringResource(meal.label)),
                        onAdd = { quickAdd(f) },
                    ) { open(f) }
                }
                item {
                    Column(Modifier.padding(16.dp)) {
                        when (val o = online) {
                            Online.Idle -> OutlinedButton(onClick = ::searchOnline, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    if (local.isEmpty()) stringResource(R.string.search_off, query)
                                    else stringResource(R.string.more_off)
                                )
                            }
                            Online.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.searching_off), Modifier.padding(start = 8.dp))
                            }
                            is Online.Failed -> Text(o.message.text(), color = MaterialTheme.colorScheme.error)
                            is Online.Done -> if (o.foods.isEmpty()) {
                                Text(stringResource(R.string.no_off_results))
                                TextButton(onClick = { vm.push(Screen.CreateFood(meal)) }) { Text(stringResource(R.string.chip_create)) }
                            } else Header(stringResource(R.string.source_off), inset = false)
                        }
                    }
                }
                val o = online
                if (o is Online.Done) items(o.foods, key = { "o" + it.key }) { f ->
                    FoodRow(
                        f, f.per100, per100Label(f), f.per100.kcal,
                        added = f.key in vm.addedKeys,
                        addDescription = stringResource(R.string.add_to_meal, stringResource(meal.label)),
                        onAdd = { quickAdd(f) },
                    ) { open(f) }
                }
            }
        }
    }

    if (quickDialog) {
        QuickAddDialog(onDismiss = { quickDialog = false }) { name, macros ->
            quickDialog = false
            vm.quickAdd(meal, name, macros)
        }
    }
    if (describeDialog) {
        DescribeDialog(onDismiss = { describeDialog = false }) { text ->
            describeDialog = false
            vm.describe(text, meal)
        }
    }
    vm.barcodeMissing?.let { code ->
        AlertDialog(
            onDismissRequest = { vm.barcodeMissing = null },
            title = { Text(stringResource(R.string.not_found_title)) },
            text = { Text(stringResource(R.string.not_found_body, code)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.barcodeMissing = null
                    vm.push(Screen.CreateFood(meal, code))
                }) { Text(stringResource(R.string.chip_create)) }
            },
            dismissButton = { TextButton(onClick = { vm.barcodeMissing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Header(text: String, inset: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = if (inset) Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp) else Modifier,
    )
}

/** "100 g · Marca · Open Food Facts": what a search result says about itself. */
@Composable
private fun per100Label(f: Food): String {
    val source = if (f.source == FoodSource.BASE) "" else stringResource(f.source.label)
    return listOf(stringResource(R.string.per_100g_short), f.brand, source).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FoodRow(
    f: Food,
    macros: Macros,
    subtitle: String,
    kcal: Double,
    added: Boolean,
    addDescription: String,
    onAdd: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val strong = MaterialTheme.colorScheme.onSurface
    val kcalText = stringResource(R.string.kcal_value, kcal.roundToInt())
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Restaurant, size = 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(f.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    append("$subtitle · ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(kcalText) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MacroLine(macros, whole = true)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.remove_from_my_foods)) }
        }
        AddCircleButton(added, onAdd, addDescription)
    }
}

/** Fitia's QuickItemModel: just a name and totals, no food behind it. */
@Composable
private fun QuickAddDialog(onDismiss: () -> Unit, onAdd: (String, Macros) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    val protein = parseNum(p) ?: 0.0
    val carbs = parseNum(c) ?: 0.0
    val fat = parseNum(f) ?: 0.0
    val derived = protein * 4 + carbs * 4 + fat * 9
    val calories = parseNum(kcal) ?: derived
    val defaultName = stringResource(R.string.quick_default_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.quick_name)) }, singleLine = true)
                NumberField(kcal, { kcal = it }, stringResource(R.string.calories), suffix = "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(p, { p = it }, stringResource(R.string.prot_short), Modifier.weight(1f), "g")
                    NumberField(c, { c = it }, stringResource(R.string.carbs_short), Modifier.weight(1f), "g")
                    NumberField(f, { f = it }, stringResource(R.string.fat_short), Modifier.weight(1f), "g")
                }
                if (kcal.isBlank() && derived > 0) {
                    Text(stringResource(R.string.kcal_from_macros, derived.roundToInt()), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.ifBlank { defaultName }, Macros(calories, protein, carbs, fat)) }, enabled = calories > 0) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Free text → foods from the local catalog, matched by rules (no AI; see MealText). */
@Composable
private fun DescribeDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.describe_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.describe_example),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.describe_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
