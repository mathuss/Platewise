package com.countmymacros.ui

import android.graphics.BitmapFactory
import com.countmymacros.data.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text on a nutrition label with ML Kit, on the device: the recognizer model
 * downloads via Play services on first use, no key and no network call of our own.
 */
suspend fun readLabelLines(jpeg: ByteArray): List<OcrLine> {
    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return emptyList()
    val image = InputImage.fromBitmap(bitmap, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val text = try {
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    } finally {
        recognizer.close() // a new client per scan: leaving it open keeps the native recognizer alive
    }
    return text.textBlocks.flatMap { block ->
        block.lines.map { line ->
            val box = line.boundingBox
            OcrLine(line.text, box?.left ?: 0, box?.top ?: 0, box?.right ?: 0, box?.bottom ?: 0)
        }
    }
}
