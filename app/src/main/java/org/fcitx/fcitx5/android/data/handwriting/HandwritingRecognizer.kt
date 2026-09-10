/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.handwriting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.utils.appContext
import java.io.File

/** Local Zinnia recognition with a bundled model; no network or telemetry SDK. */
class HandwritingRecognizer : AutoCloseable {
    data class Point(val x: Float, val y: Float, val time: Long)
    private var handle = 0L
    private var closed = false

    init { System.loadLibrary("native-lib") }

    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@HandwritingRecognizer) {
            if (closed) return@synchronized false
            if (handle != 0L) return@synchronized true
            val model = File(appContext.noBackupFilesDir, "handwriting-zh_CN.model")
            if (!model.isFile) {
                val temporary = File.createTempFile("handwriting-", ".tmp", appContext.noBackupFilesDir)
                try {
                    appContext.assets.open("handwriting/handwriting-zh_CN.model").use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    check(temporary.renameTo(model)) { "Cannot install bundled handwriting model" }
                } finally {
                    temporary.delete()
                }
            }
            handle = nativeOpen(model.absolutePath)
            check(handle != 0L) { "Cannot open bundled handwriting model" }
            true
        }
    }

    suspend fun classify(width: Int, height: Int, strokes: List<List<Point>>): List<String> =
        withContext(Dispatchers.Default) {
            synchronized(this@HandwritingRecognizer) {
                if (closed || handle == 0L || width <= 0 || height <= 0 || strokes.isEmpty()) {
                    return@synchronized emptyList()
                }
                val nonEmpty = strokes.filter { it.isNotEmpty() }
                if (nonEmpty.isEmpty()) return@synchronized emptyList()
                val points = nonEmpty.flatten()
                val left = points.minOf { it.x }
                val top = points.minOf { it.y }
                val right = points.maxOf { it.x }
                val bottom = points.maxOf { it.y }
                // Zinnia expects a square character. The keyboard canvas is wide;
                // scaling by its dimensions distorts every character differently.
                val side = maxOf(right - left, bottom - top, 1f)
                val scale = 800f / side
                val centerX = (left + right) / 2f
                val centerY = (top + bottom) / 2f
                val flat = ArrayList<Int>()
                flat.add(nonEmpty.size)
                nonEmpty.forEach { stroke ->
                    flat.add(stroke.size)
                    stroke.forEach { point ->
                        flat.add(((point.x - centerX) * scale + 500f).toInt())
                        flat.add(((point.y - centerY) * scale + 500f).toInt())
                    }
                }
                nativeClassify(handle, 1000, 1000, flat.toIntArray(), 20)
                    ?.filter { it.isNotBlank() }?.distinct().orEmpty()
            }
        }

    @Synchronized
    override fun close() {
        closed = true
        if (handle != 0L) nativeClose(handle)
        handle = 0L
    }

    private external fun nativeOpen(model: String): Long
    private external fun nativeClose(handle: Long)
    private external fun nativeClassify(
        handle: Long, width: Int, height: Int, strokes: IntArray, nbest: Int
    ): Array<String>?
}
