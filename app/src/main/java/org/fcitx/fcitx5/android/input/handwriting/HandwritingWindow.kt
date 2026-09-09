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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.handwriting.HandwritingPinyin
import org.fcitx.fcitx5.android.data.handwriting.HandwritingRecognizer
import org.fcitx.fcitx5.android.data.handwriting.HandwritingRecognizer.Point
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow
import splitties.dimensions.dp
import splitties.views.backgroundColor
import timber.log.Timber

class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>() {
    private val service by manager.inputMethodService()
    private val theme by manager.theme()
    private var recognizer: HandwritingRecognizer? = null
    private lateinit var canvas: HandwritingCanvasView
    private lateinit var candidates: LinearLayout
    private lateinit var status: TextView
    private var classifyJob: Job? = null
    private var modelJob: Job? = null
    private var attached = false
    private var ready = false
    private var generation = 0

    override fun onCreateView(): View {
        candidates = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        status = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.keyTextColor)
            setPadding(context.dp(12), 0, context.dp(12), 0)
        }
        canvas = HandwritingCanvasView(context, theme).apply {
            contentDescription = context.getString(R.string.handwriting)
            onStrokeStarted = {
                generation++
                classifyJob?.cancel()
                candidates.removeAllViews()
            }
            onStrokesChanged = { classify(it) }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            backgroundColor = theme.keyboardColor
            addView(status, LinearLayout.LayoutParams(-1, context.dp(40)))
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(candidates, ViewGroup.LayoutParams(-2, -1))
            }, LinearLayout.LayoutParams(-1, context.dp(52)))
            addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun prepareModel(download: Boolean) {
        if (modelJob?.isActive == true) return
        status.setText(R.string.handwriting_model_checking)
        status.setOnClickListener(null)
        modelJob = service.lifecycleScope.launch {
            try {
                val engine = recognizer ?: HandwritingRecognizer().also { recognizer = it }
                if (!engine.isReady()) {
                    if (!download) {
                        status.setText(R.string.handwriting_model_download)
                        status.setOnClickListener { prepareModel(true) }
                        return@launch
                    }
                    status.setText(R.string.handwriting_model_downloading)
                    engine.download()
                }
                ready = true
                canvas.isEnabled = true
                status.setText(R.string.handwriting_hint)
                status.setOnClickListener(null)
                classify(canvas.snapshot())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Handwriting model unavailable")
                status.setText(R.string.handwriting_model_retry)
                status.setOnClickListener { prepareModel(true) }
            }
        }
    }

    private fun classify(strokes: List<List<Point>>) {
        val token = ++generation
        classifyJob?.cancel()
        candidates.removeAllViews()
        if (!ready || !attached || strokes.isEmpty()) return
        val engine = recognizer ?: return
        val width = canvas.width
        val height = canvas.height
        classifyJob = service.lifecycleScope.launch {
            try {
                delay(100)
                val result = engine.classify(width, height, strokes)
                val annotated = withContext(Dispatchers.Default) {
                    result.map { it to HandwritingPinyin.ofText(it) }
                }
                if (token != generation || !attached) return@launch
                annotated.forEach { (text, pinyin) ->
                    candidates.addView(TextView(context).apply {
                        this.text = if (pinyin.isEmpty()) text else "$text\n$pinyin"
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setTextColor(theme.candidateTextColor)
                        setPadding(context.dp(14), 0, context.dp(14), 0)
                        layoutParams = LinearLayout.LayoutParams(-2, -1)
                        setOnClickListener {
                            if (token == generation && attached) {
                                service.commitText(text)
                                reset()
                            }
                        }
                    })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Handwriting recognition failed")
                if (token == generation) status.setText(R.string.handwriting_recognition_retry)
            }
        }
    }

    private fun reset() {
        generation++
        classifyJob?.cancel()
        canvas.clear()
        candidates.removeAllViews()
        if (ready) status.setText(R.string.handwriting_hint)
    }

    override fun onAttached() {
        attached = true
        ready = false
        canvas.isEnabled = false
        prepareModel(false)
    }

    override fun onDetached() {
        attached = false
        reset()
        modelJob?.cancel()
        modelJob = null
        recognizer?.close()
        recognizer = null
        ready = false
    }

    override val title: String by lazy { context.getString(R.string.handwriting) }

    override fun onCreateBarExtension(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(ToolButton(context, R.drawable.ic_baseline_backspace_24, theme).apply {
            contentDescription = context.getString(R.string.handwriting_undo)
            setOnClickListener {
                if (canvas.isEmpty) service.sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL) else canvas.undo()
            }
        }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
        addView(ToolButton(context, R.drawable.ic_baseline_delete_24, theme).apply {
            contentDescription = context.getString(R.string.handwriting_clear)
            setOnClickListener { reset() }
        }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
    }
}
