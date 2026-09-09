/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.handwriting

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.handwriting.HandwritingPinyin
import org.fcitx.fcitx5.android.data.handwriting.HandwritingRecognizer
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow
import splitties.dimensions.dp
import splitties.views.backgroundColor

/**
 * Handwriting input: strokes on the left, n-best characters on top, each
 * annotated with its most common reading.
 */
class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>() {

    private val service by manager.inputMethodService()
    private val theme by manager.theme()

    private var canvasView: HandwritingCanvasView? = null

    private var candidatesLayout: LinearLayout? = null

    private var classifyJob: Job? = null

    private val clearButton by lazy {
        ToolButton(context, R.drawable.ic_baseline_delete_24, theme).apply {
            contentDescription = context.getString(R.string.handwriting_clear)
            setOnClickListener { reset() }
        }
    }

    override fun onCreateView(): View {
        if (!HandwritingRecognizer.open()) {
            return TextView(context).apply {
                text = context.getString(R.string.handwriting_model_missing)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setTextColor(theme.keyTextColor)
            }
        }
        val candidates = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                candidates,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        val canvas = HandwritingCanvasView(context, theme).apply {
            onStrokesChanged = { strokes -> classify(strokes) }
        }
        candidatesLayout = candidates
        canvasView = canvas
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            backgroundColor = theme.keyboardColor
            addView(
                scroll,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            )
            addView(
                canvas,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
    }

    private fun classify(strokes: List<List<Int>>) {
        val canvas = canvasView ?: return
        val width = canvas.width
        val height = canvas.height
        if (width <= 0 || height <= 0) return
        // copy, the canvas keeps mutating its own lists
        val snapshot = strokes.map { it.toList() }
        classifyJob?.cancel()
        classifyJob = service.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                HandwritingRecognizer.classify(width, height, snapshot)
            }
            showCandidates(result)
        }
    }

    private fun showCandidates(candidates: List<String>) {
        val layout = candidatesLayout ?: return
        layout.removeAllViews()
        candidates.forEach { candidate ->
            layout.addView(candidateView(candidate))
        }
    }

    private fun candidateView(candidate: String): View {
        val charView = TextView(context).apply {
            text = candidate
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(theme.candidateTextColor)
        }
        val pinyinView = TextView(context).apply {
            text = HandwritingPinyin.of(candidate) ?: ""
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(theme.candidateCommentColor)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            addView(
                charView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                pinyinView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            setOnClickListener {
                service.lifecycleScope.launch { service.commitText(candidate) }
                reset()
            }
        }
    }

    private fun reset() {
        classifyJob?.cancel()
        classifyJob = null
        canvasView?.clear()
        candidatesLayout?.removeAllViews()
    }

    override fun onAttached() {}

    override fun onDetached() {
        reset()
    }

    override val title: String by lazy {
        context.getString(R.string.handwriting)
    }

    override fun onCreateBarExtension(): View = clearButton
}
