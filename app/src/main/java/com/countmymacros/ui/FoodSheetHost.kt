package com.countmymacros.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.countmymacros.R
import com.countmymacros.data.FoodSource
import com.countmymacros.data.GRAMS
import com.countmymacros.data.Serving
import com.countmymacros.data.fmt
import com.countmymacros.data.parseNum
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSheetHost(vm: AppViewModel) {
    val s = vm.sheet ?: return
    ModalBottomSheet(
        onDismissRequest = { vm.sheet = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        FoodSheetContent(s, vm)
    }
}

@Composable
private fun FoodSheetContent(s: FoodSheet, vm: AppViewModel) {
    val food = s.food
    val entry = s.entry
    val quick = food.source == FoodSource.QUICK
    val start: Pair<Serving, Double> = remember(s) {
        when {
            entry != null -> Serving(entry.servingName, entry.servingGrams) to entry.quantity
            s.preset != null -> s.preset
            else -> food.defaultServing
        }
    }
    // A quick add has no food behind it: its one "serving" is the only unit that makes sense.
    val servings = remember(s) {
        if (quick) listOf(start.first) else (food.allServings + start.first).distinctBy { it.name }
    }
    var serving by remember(s) { mutableStateOf(start.first) }
    var qtyText by remember(s) { mutableStateOf(fmt(start.second)) }
    var meal by remember(s) { mutableStateOf(s.meal) }

    val qty = parseNum(qtyText) ?: 0.0
    val grams = qty * serving.grams
    val m = food.per100.scaled(grams / 100.0)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp)
    ) {
        Text(food.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            listOf(food.brand, stringResource(food.source.label)).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                value = qtyText,
                onValue = { qtyText = it },
                label = stringResource(R.string.quantity),
                suffix = if (serving == GRAMS) "g" else null,
                modifier = Modifier.width(140.dp),
            )
            Spacer(Modifier.width(12.dp))
            if (serving != GRAMS && !quick) {
                Text(
                    stringResource(R.string.equals_grams, fmt(grams)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            servings.forEach { sv ->
                FilterChip(
                    selected = sv.name == serving.name,
                    onClick = {
                        if (sv.name != serving.name) {
                            // Keep the amount sensible when switching units.
                            qtyText = if (sv == GRAMS) fmt(grams) else "1"
                            serving = sv
                        }
                    },
                    label = {
                        Text(
                            when {
                                sv == GRAMS -> stringResource(R.string.grams_chip)
                                quick -> stringResource(R.string.quick_serving)
                                else -> stringResource(R.string.serving_chip, sv.name, fmt(sv.grams))
                            }
                        )
                    },
                )
            }
        }

        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.Bottom) {
            Text("${m.kcal.roundToInt()}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(" kcal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
        }
        MacroLine(m)
        if (!quick) {
            Text(
                stringResource(
                    R.string.per100_line,
                    food.per100.kcal.roundToInt(),
                    fmt(food.per100.protein),
                    fmt(food.per100.carbs),
                    fmt(food.per100.fat),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text(stringResource(R.string.meal_label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
        MealPicker(meal, { meal = it }, Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(16.dp))
        if (entry == null) {
            Button(
                onClick = {
                    vm.addFood(food, meal, serving.name, serving.grams, qty)
                    vm.sheet = null
                },
                enabled = qty > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.add_to_meal, stringResource(meal.label))) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vm.deleteEntry(entry); vm.sheet = null },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.delete)) }
                Button(
                    onClick = {
                        vm.updateEntry(
                            entry.copy(
                                meal = meal,
                                servingName = serving.name,
                                servingGrams = serving.grams,
                                quantity = qty,
                            )
                        )
                        vm.sheet = null
                    },
                    enabled = qty > 0,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
