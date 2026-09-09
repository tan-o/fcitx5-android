/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.handwriting

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionContext
import com.google.mlkit.vision.digitalink.WritingArea
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One recognizer per handwriting window; recognition stays on device. */
class HandwritingRecognizer : AutoCloseable {
    data class Point(val x: Float, val y: Float, val time: Long)

    private val model = DigitalInkRecognitionModel.builder(
        checkNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hans"))
    ).build()
    private val modelManager = RemoteModelManager.getInstance()
    private val client = DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).build()
    )

    suspend fun isReady(): Boolean = modelManager.isModelDownloaded(model).awaitResult()

    suspend fun download() {
        modelManager.download(model, DownloadConditions.Builder().build()).awaitResult()
    }

    suspend fun classify(width: Int, height: Int, strokes: List<List<Point>>): List<String> {
        if (width <= 0 || height <= 0 || strokes.isEmpty()) return emptyList()
        val ink = Ink.builder().apply {
            strokes.filter { it.isNotEmpty() }.forEach { points ->
                addStroke(Ink.Stroke.builder().apply {
                    points.forEach { addPoint(Ink.Point.create(it.x, it.y, it.time)) }
                }.build())
            }
        }.build()
        val context = RecognitionContext.builder()
            .setWritingArea(WritingArea(width.toFloat(), height.toFloat()))
            .build()
        return client.recognize(ink, context).awaitResult().candidates
            .map { it.text }.filter { it.isNotBlank() }.distinct().take(10)
    }

    override fun close() = client.close()

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
