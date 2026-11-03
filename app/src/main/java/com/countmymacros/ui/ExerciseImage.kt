package com.countmymacros.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.countmymacros.data.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbCache = LruCache<String, ImageBitmap>(120)

/**
 * The exercise's photo from assets/ejercicios (male or female variant, falling back to the
 * other). The images aren't in git: without them, or for the user's own exercises, it's an icon.
 */
@Composable
fun ExerciseThumb(exercise: Exercise?, female: Boolean, modifier: Modifier = Modifier, size: Dp = 48.dp) {
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
    val bitmap by produceState<ImageBitmap?>(null, key) {
        value = if (exercise == null || variant == null) null else thumbCache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("ejercicios/${exercise.id}_$variant.jpg").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }?.also { thumbCache.put(key, it) }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
