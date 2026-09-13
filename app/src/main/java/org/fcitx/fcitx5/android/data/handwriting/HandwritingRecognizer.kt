package org.fcitx.fcitx5.android.data.handwriting

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.work.WorkManager
import androidx.work.getWorkInfosForUniqueWorkFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.download.ModelDownloadWorker
import org.fcitx.fcitx5.android.utils.appContext
import java.nio.FloatBuffer

/** PP-OCRv6 weights with local ONNX inference; no vendor analytics SDK. */
class HandwritingRecognizer : AutoCloseable {
    data class Point(val x: Float, val y: Float, val time: Long)
    private var session: OrtSession? = null
    private var closed = false
    private val environment = OrtEnvironment.getEnvironment().apply { setTelemetry(false) }
    suspend fun isReady() = ModelDownloadWorker.handwritingFile.isFile
    suspend fun download(onProgress: (received: Long, total: Long, text: String) -> Unit) {
        withContext(Dispatchers.IO) {
            ModelDownloadWorker.enqueue(ModelDownloadWorker.HAND).result.get()
        }
        val infos = WorkManager.getInstance(appContext).getWorkInfosForUniqueWorkFlow("model-handwriting")
            .first { rows ->
                rows.firstOrNull { !it.state.isFinished }?.progress?.let { data ->
                    onProgress(
                        data.getLong("received", 0),
                        data.getLong("total", 0),
                        data.getString("text").orEmpty()
                    )
                }
                rows.isNotEmpty() && rows.all { it.state.isFinished }
            }
        check(isReady()) { infos.lastOrNull()?.outputData?.getString("text") ?: "下载未完成，点击可续传" }
    }
    suspend fun classify(width: Int, height: Int, strokes: List<List<Point>>): List<String> = withContext(Dispatchers.Default) {
        synchronized(this@HandwritingRecognizer) {
            if (closed || width <= 0 || height <= 0 || strokes.flatten().isEmpty()) return@synchronized emptyList()
            val current = session ?: OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                environment.createSession(ModelDownloadWorker.handwritingFile.absolutePath, options)
            }.also { session = it }
            val chars = listOf("") + current.metadata.customMetadata["character"]!!.split('\n') + listOf(" ")
            val points = strokes.flatten()
            val left = points.minOf { it.x }; val top = points.minOf { it.y }
            val spanX = points.maxOf { it.x } - left; val spanY = points.maxOf { it.y } - top
            val scale = 216f / maxOf(spanX, spanY, 1f)
            val offsetX = (256f - spanX * scale) / 2f; val offsetY = (256f - spanY * scale) / 2f
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            strokes.filter { it.isNotEmpty() }.forEach { stroke ->
                val path = Path()
                stroke.forEachIndexed { index, p ->
                    val x = (p.x - left) * scale + offsetX; val y = (p.y - top) * scale + offsetY
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (stroke.size == 1) canvas.drawPoint((stroke[0].x - left) * scale + offsetX, (stroke[0].y - top) * scale + offsetY, pen)
                else canvas.drawPath(path, pen)
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            val pixels = IntArray(48 * 48)
            scaled.getPixels(pixels, 0, 48, 0, 0, 48, 48)
            scaled.recycle(); bitmap.recycle()
            val data = FloatArray(3 * 48 * 320)
            for (channel in 0..2) for (y in 0 until 48) for (x in 0 until 48)
                data[channel * 48 * 320 + y * 320 + x] = Color.red(pixels[y * 48 + x]) / 127.5f - 1f
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, 48, 320)).use { input ->
                current.run(mapOf(current.inputNames.first() to input)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val frames = (result[0].value as Array<Array<FloatArray>>)[0]
                    check(frames.first().size == chars.size) { "手写模型词表不匹配" }
                    var previous = -1
                    val greedy = buildString {
                        frames.forEach { frame ->
                            val best = frame.indices.maxBy { frame[it] }
                            if (best != 0 && best != previous) append(chars[best])
                            previous = best
                        }
                    }.trim()
                    // CTC probability for each single-character path (blank*, char+, blank*).
                    val candidates = chars.indices.filter { chars[it].length == 1 && chars[it][0] in '\u3400'..'\u9fff' }
                        .sortedByDescending { token -> frames.maxOf { it[token] } }.take(64)
                        .map { token ->
                            var before = 1.0; var character = 0.0; var after = 0.0
                            frames.forEach { frame ->
                                after = (character + after) * frame[0]
                                character = (before + character) * frame[token]
                                before *= frame[0]
                            }
                            chars[token] to character + after
                        }.sortedByDescending { it.second }.map { it.first }
                    (listOf(greedy) + candidates).filter { it.isNotBlank() }.distinct().take(20)
                }
            }
        }
    }
    @Synchronized override fun close() { closed = true; session?.close(); session = null }
}
