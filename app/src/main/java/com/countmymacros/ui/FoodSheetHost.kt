package com.countmymacros.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.ui.theme.MacroColors
import com.countmymacros.ui.theme.macroTextColors
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
    val cs = MaterialTheme.colorScheme

    // What the day looks like with this food in it (an entry being edited does not count twice).
    val target = vm.targets.daily.kcal
    val consumed = vm.entries.filter { it.id != (entry?.id ?: -1L) }.sumOf { it.macros.kcal }
    val after = consumed + m.kcal

    fun setQty(v: Double) {
        qtyText = fmt(((v.coerceAtLeast(0.0)) * 10).roundToInt() / 10.0)
    }
    val step = if (serving == GRAMS) 10.0 else 0.5

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Restaurant, size = 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (food.brand.isNotBlank()) TagChip(food.brand)
                    TagChip(stringResource(food.source.label))
                }
            }
        }

        // Quantity: minus, the number (still editable by keyboard), plus.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainer).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(Icons.Filled.Remove, stringResource(R.string.less), filled = false) { setQty(qty - step) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = qtyText,
                    onValueChange = { t -> qtyText = t.filter { it.isDigit() || it == '.' || it == ',' } },
                    textStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = cs.onSurface, textAlign = TextAlign.Center),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.width(130.dp),
                )
                Text(
                    when {
                        serving == GRAMS -> stringResource(R.string.grams_chip)
                        quick -> stringResource(R.string.quick_serving)
                        else -> serving.name + " " + stringResource(R.string.equals_grams, fmt(grams))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            StepButton(Icons.Filled.Add, stringResource(R.string.more), filled = true) { setQty(qty + step) }
        }

        if (!quick) {
            val shortcuts = if (serving == GRAMS) listOf(50.0, 100.0, 150.0, 200.0) else listOf(0.5, 1.0, 1.5, 2.0)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { v ->
                    FilterChip(
                        selected = qty == v,
                        onClick = { qtyText = fmt(v) },
                        label = {
                            Text(
                                when {
                                    serving == GRAMS -> fmt(v) + " g"
                                    v == 0.5 -> "½"
                                    v == 1.5 -> "1½"
                                    else -> fmt(v)
                                }
                            )
                        },
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // Where the calories come from, and the three macros.
        val (proteinText, carbsText, fatText) = macroTextColors()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroDonut(m, Modifier.size(104.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatTile(fmt(m.protein), stringResource(R.string.prot_short), Modifier.weight(1f), proteinText)
                StatTile(fmt(m.carbs), stringResource(R.string.carbs_short), Modifier.weight(1f), carbsText)
                StatTile(fmt(m.fat), stringResource(R.string.fat_short), Modifier.weight(1f), fatText)
            }
        }
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
                color = cs.onSurfaceVariant,
            )
        }

        if (target > 0) DayEffect(consumed, m.kcal, target)

        Text(stringResource(R.string.meal_label), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Meal.entries.forEach { mm ->
                FilterChip(selected = mm == meal, onClick = { meal = mm }, label = { Text(stringResource(mm.label)) })
            }
        }

        if (entry == null) {
            Button(
                onClick = {
                    vm.addFood(food, meal, serving.name, serving.grams, qty)
                    vm.sheet = null
                },
                enabled = qty > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.add_with_kcal, m.kcal.roundToInt())) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vm.deleteEntry(entry); vm.sheet = null },
                    modifier = Modifier.weight(1f).height(52.dp),
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
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, filled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) cs.primary else cs.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (filled) cs.onPrimary else cs.onSurface) }
}

/** The share of calories from protein, carbs and fat (4 / 4 / 9 kcal per gram) as a ring, with the total inside. */
@Composable
private fun MacroDonut(m: Macros, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val parts = listOf(m.protein * 4 to MacroColors.Protein, m.carbs * 4 to MacroColors.Carbs, m.fat * 9 to MacroColors.Fat)
    val sum = parts.sumOf { it.first }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Butt)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
            if (sum > 0) {
                var from = -90f
                parts.forEach { (kcal, color) ->
                    val sweep = (kcal / sum * 360).toFloat()
                    if (sweep > 0.5f) drawArc(color, from, (sweep - 2f).coerceAtLeast(0.5f), false, topLeft, arcSize, style = stroke)
                    from += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${m.kcal.roundToInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** How much of today is left, and how much of it this food takes. Turns amber when it goes past the target. */
@Composable
private fun DayEffect(consumed: Double, adding: Double, target: Double) {
    val cs = MaterialTheme.colorScheme
    val after = consumed + adding
    val over = after > target
    val consumedFrac = (consumed / target).toFloat().coerceIn(0f, 1f)
    val afterFrac = (after / target).toFloat().coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.left_today), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.kcal_value, (target - after).roundToInt().coerceAtLeast(0)),
                fontWeight = FontWeight.Bold,
            )
            Text(" / " + stringResource(R.string.kcal_value, target.roundToInt()), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(afterFrac).height(8.dp).background(if (over) cs.tertiary else cs.primary.copy(alpha = 0.55f)))
            Box(Modifier.fillMaxWidth(consumedFrac).height(8.dp).background(cs.primary))
        }
        Text(
            if (over) stringResource(R.string.over_line, (after - target).roundToInt())
            else stringResource(R.string.after_line, after.roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = if (over) cs.tertiary else cs.onSurfaceVariant,
        )
    }
}
