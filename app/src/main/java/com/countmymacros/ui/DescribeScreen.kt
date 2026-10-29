package com.countmymacros.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.GRAMS
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import kotlin.math.roundToInt

/** Review of what [MealText] recognized from the free-text description: pick, fix, add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescribeScreen(vm: AppViewModel, initialMeal: Meal) {
    var meal by remember { mutableStateOf(initialMeal) }
    val selected = vm.describeItems.filter { it.selected }
    val total = selected.fold(Macros.ZERO) { acc, it -> acc + it.food.per100.scaled(it.quantity * it.serving.grams / 100.0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_description)) },
                navigationIcon = {
                    IconButton(onClick = vm::pop) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            if (vm.describeItems.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.meal_label), style = MaterialTheme.typography.labelLarge)
                            MealPicker(meal, { meal = it })
                        }
                        Button(
                            onClick = { vm.addDescribed(meal) },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(
                                pluralStringResource(
                                    R.plurals.add_n_to, selected.size,
                                    selected.size, stringResource(meal.label), total.kcal.roundToInt(),
                                )
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Text("“${vm.describeText}”", fontStyle = FontStyle.Italic, modifier = Modifier.padding(16.dp))
                }
            }
            itemsIndexed(vm.describeItems) { i, item ->
                DescribeItemCard(item) { vm.describeItems[i] = it }
            }
            if (vm.describeUnmatched.isNotEmpty()) item {
                Text(
                    stringResource(R.string.describe_unmatched, vm.describeUnmatched.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.describeItems.isEmpty() && vm.describeUnmatched.isNotEmpty()) item {
                Column {
                    Text(stringResource(R.string.describe_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.describe_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DescribeItemCard(item: DescribeItem, onChange: (DescribeItem) -> Unit) {
    var qtyText by remember(item.food.key) { mutableStateOf(fmt(item.quantity)) }
    var menu by remember { mutableStateOf(false) }
    val grams = item.quantity * item.serving.grams
    val macros = item.food.per100.scaled(grams / 100.0)

    Card {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.selected, onCheckedChange = { onChange(item.copy(selected = it)) })
            Column(Modifier.weight(1f)) {
                Box {
                    Row(Modifier.clickable { menu = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(item.food.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (item.alternatives.isNotEmpty()) {
                            Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.change_food), Modifier.size(18.dp))
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        (listOf(item.food) + item.alternatives).forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.name) },
                                trailingIcon = if (f.key == item.food.key) {
                                    { Icon(Icons.Filled.Check, null) }
                                } else null,
                                onClick = {
                                    menu = false
                                    if (f.key != item.food.key) {
                                        val others = (listOf(item.food) + item.alternatives).filter { it.key != f.key }
                                        onChange(item.copy(food = f, serving = f.defaultServing.first, alternatives = others))
                                    }
                                },
                            )
                        }
                    }
                }
                Text(
                    if (item.serving == GRAMS) stringResource(R.string.amount_grams, fmt(item.quantity))
                    else stringResource(R.string.amount_servings, fmt(item.quantity), item.serving.name, fmt(grams)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.kcal_value, macros.kcal.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                MacroLine(macros)
            }
            NumberField(
                value = qtyText,
                onValue = { s ->
                    qtyText = s
                    parseNum(s)?.let { q -> onChange(item.copy(quantity = q)) }
                },
                label = stringResource(R.string.quantity),
                suffix = if (item.serving == GRAMS) "g" else null,
                modifier = Modifier.width(112.dp),
            )
        }
    }
}
