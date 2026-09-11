package org.fcitx.fcitx5.android.data.handwriting

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.tasks.await

/** Recognizes the actual pen trajectory, including continuous cursive strokes. */
class HandwritingRecognizer : AutoCloseable {
    data class Point(val x: Float, val y: Float, val time: Long)
    private val model = DigitalInkRecognitionModel.builder(
        DigitalInkRecognitionModelIdentifier.ZH_HANI_CN
    ).build()
    private val models = RemoteModelManager.getInstance()
    private val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
    suspend fun isReady(): Boolean = models.isModelDownloaded(model).await()
    suspend fun download() { models.download(model, DownloadConditions.Builder().build()).await() }

    suspend fun classify(width: Int, height: Int, strokes: List<List<Point>>): List<String> {
        if (width <= 0 || height <= 0 || strokes.isEmpty()) return emptyList()
        val points = strokes.flatten()
        if (points.isEmpty()) return emptyList()
        val left = points.minOf { it.x }
        val top = points.minOf { it.y }
        val inkWidth = maxOf(points.maxOf { it.x } - left, 1f)
        val inkHeight = maxOf(points.maxOf { it.y } - top, 1f)
        val padding = maxOf(inkWidth, inkHeight) * .1f
        val ink = Ink.builder().apply {
            strokes.filter { it.isNotEmpty() }.forEach { stroke ->
                addStroke(Ink.Stroke.builder().apply {
                    stroke.forEach { p -> addPoint(Ink.Point.create(p.x - left + padding, p.y - top + padding, p.time)) }
                }.build())
            }
        }.build()
        val context = RecognitionContext.builder()
            .setWritingArea(WritingArea(inkWidth + 2 * padding, inkHeight + 2 * padding)).build()
        return recognizer.recognize(ink, context).await().candidates
            .map { it.text.trim() }.filter { it.isNotEmpty() }.distinct().take(20)
    }
    override fun close() = recognizer.close()
}
