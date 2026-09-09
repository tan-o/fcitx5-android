/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp

/**
 * Collects pen strokes and hands them over as flat x, y lists whenever a
 * stroke ends, so candidates can be refreshed stroke by stroke.
 */
@SuppressLint("ViewConstructor")
class HandwritingCanvasView(context: Context, theme: Theme) : View(context) {

    var onStrokesChanged: ((List<List<Int>>) -> Unit)? = null

    private val strokes = mutableListOf<MutableList<Int>>()

    private val paths = mutableListOf<Path>()

    private var currentPath: Path? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = context.dp(4f)
        color = theme.keyTextColor
    }

    val isEmpty: Boolean
        get() = strokes.isEmpty()

    fun clear() {
        strokes.clear()
        paths.clear()
        currentPath = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                strokes.add(mutableListOf(x.toInt(), y.toInt()))
                currentPath = Path().apply { moveTo(x, y) }.also { paths.add(it) }
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = strokes.lastOrNull() ?: return true
                stroke.add(x.toInt())
                stroke.add(y.toInt())
                currentPath?.lineTo(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentPath = null
                onStrokesChanged?.invoke(strokes)
            }
            else -> return false
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        paths.forEach { canvas.drawPath(it, paint) }
    }

    override fun performClick(): Boolean = super.performClick()
}
