/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.handwriting

import org.fcitx.fcitx5.android.core.data.DataManager
import java.io.File

/**
 * Online handwriting recognition, backed by zinnia and the Tegaki
 * simplified-chinese model installed at [MODEL_PATH] under the data dir.
 */
object HandwritingRecognizer {

    const val MODEL_PATH = "handwriting/handwriting-zh_CN.model"

    init {
        System.loadLibrary("native-lib")
    }

    private var handle = 0L

    val modelFile: File
        get() = File(DataManager.dataDir, MODEL_PATH)

    @Synchronized
    fun open(): Boolean {
        if (handle != 0L) return true
        val model = modelFile
        if (!model.exists()) return false
        handle = nativeOpen(model.absolutePath)
        return handle != 0L
    }

    @Synchronized
    fun close() {
        if (handle == 0L) return
        nativeClose(handle)
        handle = 0L
    }

    /**
     * Classify [strokes], each of which is a flat list of x, y in canvas
     * coordinates. Returns the n-best characters, most confident first.
     */
    @Synchronized
    fun classify(
        width: Int,
        height: Int,
        strokes: List<List<Int>>,
        nbest: Int = 10
    ): List<String> {
        if (handle == 0L || strokes.isEmpty()) return emptyList()
        val flat = ArrayList<Int>(strokes.sumOf { it.size } + strokes.size + 1)
        flat.add(strokes.size)
        strokes.forEach {
            flat.add(it.size / 2)
            flat.addAll(it)
        }
        return nativeClassify(handle, width, height, flat.toIntArray(), nbest)
            ?.toList() ?: emptyList()
    }

    private external fun nativeOpen(model: String): Long

    private external fun nativeClose(handle: Long)

    private external fun nativeClassify(
        handle: Long,
        width: Int,
        height: Int,
        strokes: IntArray,
        nbest: Int
    ): Array<String>?
}
