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
import android.widget.ProgressBar
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
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import org.fcitx.fcitx5.android.input.wm.InputWindow
import splitties.dimensions.dp
import splitties.views.backgroundColor
import timber.log.Timber

class HandwritingWindow : InputWindow.ExtendedInputWindow<HandwritingWindow>() {
    private val windowManager: InputWindowManager by manager.must()
    override val showTitle = false
    private val service by manager.inputMethodService()
    private val theme by manager.theme()
    private var recognizer: HandwritingRecognizer? = null
    private lateinit var canvas: HandwritingCanvasView
    private lateinit var candidates: LinearLayout
    private lateinit var status: TextView
    private lateinit var downloadProgress: ProgressBar
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
        downloadProgress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            backgroundColor = theme.keyboardColor
            addView(status, LinearLayout.LayoutParams(-1, context.dp(40)))
            addView(downloadProgress, LinearLayout.LayoutParams(-1, context.dp(4)))
            addView(canvas, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun prepareModel(download: Boolean = false) {
        if (modelJob?.isActive == true) return
        status.visibility = View.VISIBLE
        status.setText(R.string.handwriting_model_checking)
        status.setOnClickListener(null)
        modelJob = service.lifecycleScope.launch {
            try {
                val engine = recognizer ?: HandwritingRecognizer().also { recognizer = it }
                if (download) {
                    status.text = "正在下载中文手写模型（约 20 MB）…"
                    downloadProgress.visibility = View.VISIBLE
                    engine.download()
                }
                if (!attached) return@launch
                if (!engine.isReady()) {
                    status.text = "点击下载中文手写模型（约 20 MB）"
                    status.setOnClickListener { prepareModel(true) }
                    return@launch
                }
                ready = true
                canvas.isEnabled = true
                status.visibility = View.GONE
                status.setOnClickListener(null)
                classify(canvas.snapshot())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Handwriting model unavailable")
                status.visibility = View.VISIBLE
                status.text = "${e.localizedMessage ?: "手写模型不可用"}；点击重试"
                status.setOnClickListener { prepareModel(true) }
            } finally {
                downloadProgress.visibility = View.GONE
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
                    val item = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.BOTTOM
                        setPadding(context.dp(10), 0, context.dp(10), context.dp(3))
                        contentDescription = "$text $pinyin"
                        addView(TextView(context).apply {
                            this.text = text
                            textSize = 24f
                            gravity = Gravity.CENTER
                            includeFontPadding = false
                            setTextColor(theme.candidateTextColor)
                        }, LinearLayout.LayoutParams(-2, -1))
                        addView(TextView(context).apply {
                            this.text = pinyin.replace(',', ' ')
                            textSize = 10f
                            includeFontPadding = false
                            setPadding(context.dp(3), 0, 0, 0)
                            setTextColor(theme.candidateTextColor)
                        }, LinearLayout.LayoutParams(-2, -2))
                        setOnClickListener {
                            if (token == generation && attached) {
                                service.commitText(text)
                                reset()
                            }
                        }
                    }
                    candidates.addView(item, LinearLayout.LayoutParams(-2, -1))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Handwriting recognition failed")
                if (token == generation) {
                    status.visibility = View.VISIBLE
                    status.setText(R.string.handwriting_recognition_retry)
                }
            }
        }
    }

    private fun reset() {
        generation++
        classifyJob?.cancel()
        canvas.clear()
        candidates.removeAllViews()
        if (ready) status.visibility = View.GONE
    }

    override fun onAttached() {
        attached = true
        ready = false
        canvas.isEnabled = false
        prepareModel()
    }

    override fun onDetached() {
        attached = false
        reset()
        modelJob?.cancel()
        modelJob = null
        val closing = recognizer
        recognizer = null
        service.lifecycleScope.launch(Dispatchers.IO) { closing?.close() }
        ready = false
    }

    override val title: String by lazy { context.getString(R.string.handwriting) }

    override fun onCreateBarExtension(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(ToolButton(context, R.drawable.ic_baseline_arrow_back_24, theme).apply {
            contentDescription = context.getString(R.string.back_to_keyboard)
            setOnClickListener { windowManager.attachWindow(KeyboardWindow) }
        }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)))
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(candidates, ViewGroup.LayoutParams(-2, -1))
        }, LinearLayout.LayoutParams(0, -1, 1f))
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
