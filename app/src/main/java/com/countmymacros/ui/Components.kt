package com.countmymacros.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.countmymacros.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.countmymacros.data.Macros
import com.countmymacros.data.Meal
import com.countmymacros.data.fmt
import com.countmymacros.ui.theme.macroTextColors
import kotlin.math.roundToInt

@Composable
fun CalorieRing(consumed: Double, target: Double, size: Dp = 132.dp) {
    val over = consumed > target && target > 0
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val fraction = if (target > 0) (consumed / target).toFloat().coerceIn(0f, 1f) else 0f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke.width, this.size.height - stroke.width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = stroke)
            drawArc(fill, 135f, 270f * fraction, false, topLeft, arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val remaining = (target - consumed).roundToInt()
            Text(
                if (remaining >= 0) "$remaining" else "+${-remaining}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(if (remaining >= 0) R.string.kcal_left else R.string.kcal_over),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MacroBar(label: String, value: Double, target: Double, color: Color, textColor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = textColor, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.grams_of, value.roundToInt(), target.roundToInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp),
            color = if (value > target * 1.1 && target > 0) MaterialTheme.colorScheme.error else color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}

/** "P 20 · C 35 · F 10" (G in Spanish) with each letter in its macro color. */
@Composable
fun MacroLine(m: Macros, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (proteinColor, carbsColor, fatColor) = macroTextColors()
    val p = stringResource(R.string.letter_protein)
    val c = stringResource(R.string.letter_carbs)
    val f = stringResource(R.string.letter_fat)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = proteinColor, fontWeight = FontWeight.SemiBold)) { append("$p ") }
            withStyle(SpanStyle(color = muted)) { append(fmt(m.protein)) }
            withStyle(SpanStyle(color = muted)) { append("  ·  ") }
            withStyle(SpanStyle(color = carbsColor, fontWeight = FontWeight.SemiBold)) { append("$c ") }
            withStyle(SpanStyle(color = muted)) { append(fmt(m.carbs)) }
            withStyle(SpanStyle(color = muted)) { append("  ·  ") }
            withStyle(SpanStyle(color = fatColor, fontWeight = FontWeight.SemiBold)) { append("$f ") }
            withStyle(SpanStyle(color = muted)) { append(fmt(m.fat)) }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/** Compact meal selector: a dropdown button instead of a scrolling chip row that clips at the edge. */
@Composable
fun MealPicker(selected: Meal, onSelect: (Meal) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val changeMealLabel = stringResource(R.string.change_meal)
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 44.dp)) {
            Text(stringResource(selected.label))
            Icon(Icons.Filled.ArrowDropDown, changeMealLabel)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Meal.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(stringResource(m.label)) },
                    trailingIcon = if (m == selected) {
                        { Icon(Icons.Filled.Check, null) }
                    } else null,
                    onClick = { open = false; onSelect(m) },
                )
            }
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onValue(s.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}
