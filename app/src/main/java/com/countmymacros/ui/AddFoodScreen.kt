package com.countmymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(vm: AppViewModel, initialMeal: Meal) {
    var meal by remember { mutableStateOf(initialMeal) }
    var query by remember { mutableStateOf("") }
    var online by remember { mutableStateOf<Online>(Online.Idle) }
    var photoMenu by remember { mutableStateOf(false) }
    var quickDialog by remember { mutableStateOf(false) }
    var describeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val photos = rememberPhotoSource(onJpeg = { vm.analyzePhoto(it, meal) }, onError = { vm.toast = it })

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_food_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.add_to_prefix),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        MealPicker(meal, { meal = it })
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
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchOnline() }),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            AssistChip(
                                onClick = { photoMenu = true },
                                label = { Text(stringResource(R.string.chip_photo)) },
                                leadingIcon = { Icon(Icons.Filled.CameraAlt, null, Modifier.size(18.dp)) },
                            )
                            DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.take_photo)) },
                                    leadingIcon = { Icon(Icons.Filled.CameraAlt, null) },
                                    onClick = { photoMenu = false; photos.takePhoto() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.pick_gallery)) },
                                    leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                                    onClick = { photoMenu = false; photos.pickFromGallery() },
                                )
                            }
                        }
                        AssistChip(
                            onClick = ::scanBarcode,
                            label = { Text(stringResource(R.string.chip_barcode)) },
                            leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp)) },
                        )
                        AssistChip(
                            onClick = { describeDialog = true },
                            label = { Text(stringResource(R.string.chip_describe)) },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)) },
                        )
                        AssistChip(
                            onClick = { quickDialog = true },
                            label = { Text(stringResource(R.string.chip_quick)) },
                            leadingIcon = { Icon(Icons.Filled.Bolt, null, Modifier.size(18.dp)) },
                        )
                        AssistChip(
                            onClick = { vm.push(Screen.CreateFood(meal)) },
                            label = { Text(stringResource(R.string.chip_create)) },
                            leadingIcon = { Icon(Icons.Filled.EditNote, null, Modifier.size(18.dp)) },
                        )
                    }
                    if (vm.barcodeLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
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
                        FoodRow(e.asFood(), subtitle = amountLabel(e), kcal = e.macros.kcal) {
                            open(e.asFood(), Serving(e.servingName, e.servingGrams) to e.quantity)
                        }
                    }
                }
                if (myFoods.isNotEmpty()) {
                    item { Header(stringResource(R.string.my_foods)) }
                    items(myFoods, key = { "m" + it.key }) { f ->
                        FoodRow(f, onDelete = { vm.deleteFood(f) }) { open(f) }
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
                items(local, key = { "l" + it.key }) { f -> FoodRow(f) { open(f) } }
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
                                TextButton(onClick = { describeDialog = true }) { Text(stringResource(R.string.estimate_with_ai)) }
                            } else Header(stringResource(R.string.source_off), inset = false)
                        }
                    }
                }
                val o = online
                if (o is Online.Done) items(o.foods, key = { "o" + it.key }) { f -> FoodRow(f) { open(f) } }
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
        DescribeDialog(initial = query, onDismiss = { describeDialog = false }) { text ->
            describeDialog = false
            vm.analyzeText(text, meal)
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

@Composable
private fun FoodRow(
    f: Food,
    subtitle: String? = null,
    kcal: Double? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val sourceLabel = stringResource(f.source.label)
    val per100 = stringResource(R.string.per_100g)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = subtitle
                    ?: listOf(f.brand, if (f.source == FoodSource.BASE) "" else sourceLabel)
                        .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { per100 }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (kcal == null) MacroLine(f.per100)
            }
            Text(
                if (kcal != null) stringResource(R.string.kcal_value, kcal.roundToInt())
                else stringResource(R.string.kcal_per_100, f.per100.kcal.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.remove_from_my_foods)) }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
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

/** Text → foods with AI, like Fitia's "Senku" text recognition. */
@Composable
private fun DescribeDialog(initial: String, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
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
            TextButton(onClick = { onSend(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.estimate)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
