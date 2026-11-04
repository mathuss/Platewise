package com.countmymacros.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.countmymacros.data.Meal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Shared building blocks: tonal surfaces instead of outlined boxes, one look for chips and numbers. */

fun mealIcon(meal: Meal): ImageVector = when (meal) {
    Meal.BREAKFAST -> Icons.Filled.WbSunny
    Meal.MID_MORNING -> Icons.Filled.Eco
    Meal.LUNCH -> Icons.Filled.Restaurant
    Meal.SNACK -> Icons.Filled.LocalCafe
    Meal.DINNER -> Icons.Filled.Bedtime
}

/** A card with a tonal fill and rounded corners, no border. Fills the width of its parent. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, content = content)
    else Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, content = content)
}

/** The highlighted card of a screen: a soft green gradient with its own content color. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brush = Brush.linearGradient(listOf(cs.primaryContainer, cs.surfaceContainerHigh))
    val shape = RoundedCornerShape(24.dp)
    CompositionLocalProvider(LocalContentColor provides cs.onPrimaryContainer) {
        val inner: @Composable () -> Unit = { Column(Modifier.background(brush).fillMaxWidth(), content = content) }
        val colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = cs.onPrimaryContainer)
        if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { inner() }
        else Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { inner() }
    }
}

/** A big number with a small caption underneath, in its own tonal tile. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                textAlign = TextAlign.Center,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) { body() }
    else Card(modifier = modifier, shape = shape, colors = colors) { body() }
}

enum class TagTone { NEUTRAL, PRIMARY, WARM }

/** A small label: muscle, equipment, minutes, kcal. */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier, tone: TagTone = TagTone.NEUTRAL, icon: ImageVector? = null) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        TagTone.NEUTRAL -> cs.surfaceContainerHighest to cs.onSurfaceVariant
        TagTone.PRIMARY -> cs.primaryContainer to cs.onPrimaryContainer
        TagTone.WARM -> cs.tertiaryContainer to cs.onTertiaryContainer
    }
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(12.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

/** A rounded square with an icon: meal icons, the workout row. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 36.dp, warm: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(if (warm) cs.tertiaryContainer else cs.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = if (warm) cs.onTertiaryContainer else cs.onPrimaryContainer)
    }
}

/** The round "+" that adds a food in one tap; it turns into a tick once added. */
@Composable
fun AddCircleButton(added: Boolean, onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (added) cs.primary else cs.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (added) Icons.Filled.Check else Icons.Filled.Add,
            description,
            Modifier.size(22.dp),
            tint = if (added) cs.onPrimary else cs.onPrimaryContainer,
        )
    }
}

/** The week of [selected], Monday first. The selected day is filled; [marked] days get a dot. */
@Composable
fun DayStrip(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    marked: Set<LocalDate> = emptySet(),
) {
    val cs = MaterialTheme.colorScheme
    val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val today = LocalDate.now()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).map { monday.plusDays(it.toLong()) }.forEach { d ->
            val on = d == selected
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(min = 40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) cs.primary else cs.surfaceContainer)
                    .clickable { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                )
                Text(
                    d.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (d == today) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (on) cs.onPrimary else cs.onSurface,
                )
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (d in marked) (if (on) cs.onPrimary else cs.primary) else Color.Transparent),
                )
            }
        }
    }
}

/** A trend line through [values] (oldest first) with a dot on the latest one. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val pad = 6.dp.toPx()
        if (values.isEmpty()) return@Canvas
        if (values.size == 1) {
            drawCircle(color, 4.dp.toPx(), Offset(size.width - pad, size.height / 2))
            return@Canvas
        }
        val lo = values.min()
        val range = (values.max() - lo).takeIf { it > 1e-6 } ?: 1.0
        val pts = values.mapIndexed { i, v ->
            Offset(
                pad + i * (size.width - 2 * pad) / (values.size - 1),
                pad + (1 - ((v - lo) / range)).toFloat() * (size.height - 2 * pad),
            )
        }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 4.dp.toPx(), pts.last())
    }
}
