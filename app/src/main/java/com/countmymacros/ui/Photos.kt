package com.countmymacros.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.countmymacros.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PhotoSource(val takePhoto: () -> Unit, val pickFromGallery: () -> Unit)

/**
 * Camera (via the system camera app, no CAMERA permission) and the system photo picker
 * (no storage permission). Either way [onJpeg] gets a downscaled JPEG ready for Claude.
 */
@Composable
fun rememberPhotoSource(onJpeg: (ByteArray) -> Unit, onError: (UiText) -> Unit = {}): PhotoSource {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shotUri = remember { photoUri(context) }
    val currentOnJpeg by rememberUpdatedState(onJpeg)
    val currentOnError by rememberUpdatedState(onError)

    fun deliver(uri: Uri) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { toJpeg(context, uri) }.getOrNull() }
            if (bytes != null) currentOnJpeg(bytes) else currentOnError(uiText(R.string.toast_photo_error))
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) deliver(shotUri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(uri)
    }
    return remember {
        PhotoSource(
            takePhoto = {
                runCatching { camera.launch(shotUri) }.onFailure { currentOnError(uiText(R.string.toast_no_camera)) }
            },
            pickFromGallery = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

private fun photoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "shot.jpg"))
}

/**
 * Decodes (applying EXIF rotation) and downsizes to ~1.15 MP / ≤1568 px long side: the size
 * Claude's vision works at, so bigger uploads only add latency.
 */
private fun toJpeg(context: Context, uri: Uri): ByteArray {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = min(1.0, min(1568.0 / max(w, h), sqrt(1_150_000.0 / (w.toDouble() * h))))
        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        out.toByteArray()
    }
}
