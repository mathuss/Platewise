package com.countmymacros.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.Food
import com.countmymacros.data.FoodSource
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.Serving
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFoodScreen(vm: AppViewModel, meal: Meal, barcode: String?) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var code by remember { mutableStateOf(barcode.orEmpty()) }
    var perServing by remember { mutableStateOf(false) }
    var servingName by remember { mutableStateOf("") }
    var servingGrams by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var photoMenu by remember { mutableStateOf(false) }
    val defaultServing = stringResource(R.string.default_serving)

    val photos = rememberPhotoSource(onJpeg = vm::scanLabel, onError = { vm.toast = it })

    // Fill the form from a scanned label (values come back per 100 g).
    LaunchedEffect(vm.labelResult) {
        val l = vm.labelResult ?: return@LaunchedEffect
        if (l.name.isNotBlank()) name = l.name
        if (l.brand.isNotBlank()) brand = l.brand
        perServing = false
        if (l.servingGrams != 100.0 || l.servingName != "100 g") {
            servingName = l.servingName
            servingGrams = fmt(l.servingGrams)
        }
        kcal = fmt(l.per100.kcal)
        p = fmt(l.per100.protein)
        c = fmt(l.per100.carbs)
        f = fmt(l.per100.fat)
    }

    val sGrams = parseNum(servingGrams) ?: 0.0
    val protein = parseNum(p) ?: 0.0
    val carbs = parseNum(c) ?: 0.0
    val fat = parseNum(f) ?: 0.0
    val derived = protein * 4 + carbs * 4 + fat * 9
    val calories = parseNum(kcal) ?: derived
    val toPer100 = if (perServing) (if (sGrams > 0) 100.0 / sGrams else 0.0) else 1.0
    val valid = name.isNotBlank() && calories > 0 && toPer100 > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_food_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                FilledTonalButton(
                    onClick = { photoMenu = true },
                    enabled = !vm.labelLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (vm.labelLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  " + stringResource(R.string.reading_label))
                    } else {
                        Icon(Icons.Filled.DocumentScanner, null)
                        Text("  " + stringResource(R.string.scan_label))
                    }
                }
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
            vm.labelError?.let { Text(it.text(), color = MaterialTheme.colorScheme.error) }
            vm.labelResult?.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.brand_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumberField(code, { code = it.filter(Char::isDigit) }, stringResource(R.string.barcode_optional), Modifier.fillMaxWidth())

            SectionTitle(stringResource(R.string.serving_optional))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    servingName, { servingName = it },
                    label = { Text(stringResource(R.string.name)) },
                    placeholder = { Text(stringResource(R.string.serving_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                NumberField(servingGrams, { servingGrams = it }, stringResource(R.string.weight), Modifier.weight(0.7f), "g")
            }

            SectionTitle(stringResource(R.string.nutrition_info))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !perServing,
                    onClick = { perServing = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.per_100g)) }
                SegmentedButton(
                    selected = perServing,
                    onClick = { perServing = true },
                    enabled = sGrams > 0,
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.per_serving)) }
            }
            NumberField(kcal, { kcal = it }, stringResource(R.string.calories), Modifier.fillMaxWidth(), "kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(p, { p = it }, stringResource(R.string.protein), Modifier.weight(1f), "g")
                NumberField(c, { c = it }, stringResource(R.string.carbs), Modifier.weight(1f), "g")
                NumberField(f, { f = it }, stringResource(R.string.fat), Modifier.weight(1f), "g")
            }
            if (kcal.isBlank() && derived > 0) {
                Text(stringResource(R.string.kcal_from_macros, derived.roundToInt()), style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val per100 = Macros(calories, protein, carbs, fat).scaled(toPer100)
                    val servings = if (sGrams > 0) listOf(Serving(servingName.ifBlank { defaultServing }, sGrams)) else emptyList()
                    vm.saveCustomFood(
                        Food(
                            key = vm.repo.newCustomKey(),
                            name = name.trim(),
                            brand = brand.trim(),
                            per100 = per100,
                            servings = servings,
                            source = FoodSource.CUSTOM,
                            barcode = code.ifBlank { null },
                        ),
                        meal,
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) { Text(stringResource(R.string.save_and_pick)) }
        }
    }
}
