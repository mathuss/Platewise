package com.countmymacros.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.countmymacros.data.Lang
import com.countmymacros.data.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbCache = LruCache<String, ImageBitmap>(120)

/** The exercise's photo (male or female variant, falling back to the other), or null while loading / when there is none. */
@Composable
private fun exerciseBitmap(exercise: Exercise?, female: Boolean): State<ImageBitmap?> {
    val context = LocalContext.current
    val variant = when {
        exercise == null -> null
        female && exercise.hasFemale -> "f"
        exercise.hasMale -> "m"
        exercise.hasFemale -> "f"
        else -> null
    }
    val key = exercise?.id + variant
    @Suppress("ProduceStateDoesNotAssignValue")
    return produceState<ImageBitmap?>(null, key) {
        value = if (exercise == null || variant == null) null else thumbCache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("ejercicios/${exercise.id}_$variant.jpg").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }?.also { thumbCache.put(key, it) }
    }
}

/**
 * The exercise's photo from assets/ejercicios. The images aren't in git: without them, or for the
 * user's own exercises, it's an icon. With [onClick] it shows a small magnifier, to open [ExercisePhotoDialog].
 */
@Composable
fun ExerciseThumb(
    exercise: Exercise?,
    female: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
) {
    val bitmap by exerciseBitmap(exercise, female)
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null && b != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(3.dp).size(18.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ZoomIn, null, Modifier.size(13.dp), tint = Color.White) }
        }
    }
}

/** The photo at a readable size with the exercise's name, so you can tell which exercise it is before picking it. */
@Composable
fun ExercisePhotoDialog(exercise: Exercise, female: Boolean, onDismiss: () -> Unit) {
    val bitmap by exerciseBitmap(exercise, female)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    val b = bitmap
                    if (b != null) Image(b, exercise.name(Lang.current), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Icon(Icons.Filled.FitnessCenter, null, Modifier.size(64.dp), tint = Color.Gray)
                }
                Text(
                    exercise.name(Lang.current),
                    Modifier.padding(top = 14.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
