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
import org.fcitx.fcitx5.android.data.handwriting.HandwritingRecognizer.Point
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp

@SuppressLint("ViewConstructor")
class HandwritingCanvasView(context: Context, theme: Theme) : View(context) {
    var onStrokesChanged: ((List<List<Point>>) -> Unit)? = null
    var onStrokeStarted: (() -> Unit)? = null
    private val strokes = mutableListOf<MutableList<Point>>()
    private val paths = mutableListOf<Path>()
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = context.dp(4f)
        color = theme.keyTextColor
    }

    val isEmpty: Boolean get() = strokes.isEmpty()
    fun snapshot(): List<List<Point>> = strokes.map { it.toList() }

    fun clear() {
        strokes.clear()
        paths.clear()
        pointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    fun undo() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.lastIndex)
        paths.removeAt(paths.lastIndex)
        pointerId = MotionEvent.INVALID_POINTER_ID
        invalidate()
        onStrokesChanged?.invoke(snapshot())
    }

    private fun addPoint(x: Float, y: Float, time: Long) {
        val point = Point(x.coerceIn(0f, width.toFloat()), y.coerceIn(0f, height.toFloat()), time)
        strokes.last().add(point)
        paths.last().lineTo(point.x, point.y)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pointerId = event.getPointerId(0)
                onStrokeStarted?.invoke()
                strokes.add(mutableListOf(Point(event.x, event.y, event.eventTime)))
                paths.add(Path().apply { moveTo(event.x, event.y) })
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0 || strokes.isEmpty()) return true
                for (h in 0 until event.historySize) {
                    addPoint(event.getHistoricalX(index, h), event.getHistoricalY(index, h), event.getHistoricalEventTime(h))
                }
                addPoint(event.getX(index), event.getY(index), event.eventTime)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    pointerId = MotionEvent.INVALID_POINTER_ID
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    onStrokesChanged?.invoke(snapshot())
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pointerId != MotionEvent.INVALID_POINTER_ID) undo()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEachIndexed { i, points ->
            if (points.size == 1) canvas.drawPoint(points[0].x, points[0].y, paint)
            else canvas.drawPath(paths[i], paint)
        }
    }

    override fun performClick(): Boolean = super.performClick()
}
