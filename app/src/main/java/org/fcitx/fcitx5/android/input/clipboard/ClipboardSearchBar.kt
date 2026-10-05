/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.clipboard.ClipboardSearch
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.handwriting.HandwritingPinyin
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.translation.KeyboardTextTarget
import org.fcitx.fcitx5.android.utils.clipboardManager
import splitties.dimensions.dp

/**
 * Clipboard search shown above the regular keyboard, so the keyboard keeps its full size and
 * the query can be typed with the active input method (pinyin included).
 */
class ClipboardSearchBar(
    private val service: FcitxInputMethodService,
    private val theme: Theme,
    private val onLayoutChanged: () -> Unit,
    private val onBack: () -> Unit
) {
    companion object {
        private const val MaxResults = 50
    }

    private val prefs = AppPrefs.getInstance().clipboard
    private val returnAfterPaste by prefs.clipboardReturnAfterPaste
    private val maskSensitive by prefs.clipboardMaskSensitive

    private var target: KeyboardTextTarget? = null
    private var openJob: Job? = null
    private var searchJob: Job? = null
    private var pasteJob: Job? = null

    private val closeCallback: () -> Unit = { close() }

    private val editor = EditText(service).apply {
        hint = service.getString(R.string.clipboard_search_hint)
        setTextColor(theme.keyTextColor)
        setHintTextColor(theme.altKeyTextColor)
        textSize = 15f
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT
        showSoftInputOnFocus = false
        background = null
        // hold to search for the current system clipboard content
        setOnLongClickListener {
            val clip = service.clipboardManager.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(service) else null
            if (!text.isNullOrEmpty()) {
                setText(text)
                setSelection(length())
            }
            true
        }
        doAfterTextChanged { if (isOpen) search(it?.toString().orEmpty()) }
    }

    private val back = ToolButton(service, R.drawable.ic_baseline_arrow_back_24, theme).apply {
        contentDescription = service.getString(R.string.clipboard)
        setOnClickListener { returnToClipboard() }
    }

    private val clear = TextView(service).apply {
        text = "×"
        gravity = Gravity.CENTER
        textSize = 18f
        setTextColor(theme.altKeyTextColor)
        setOnClickListener { editor.text.clear() }
    }

    private val header = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(back, LinearLayout.LayoutParams(service.dp(40), service.dp(40)))
        addView(editor, LinearLayout.LayoutParams(0, -1, 1f))
        addView(clear, LinearLayout.LayoutParams(service.dp(40), -1))
    }

    private val results = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(service.dp(4), service.dp(4), service.dp(4), service.dp(6))
    }

    private val resultsScroll = HorizontalScrollView(service).apply {
        isHorizontalScrollBarEnabled = false
        addView(results, FrameLayout.LayoutParams(-2, -1))
    }

    private val empty = TextView(service).apply {
        setText(R.string.clipboard_no_results)
        gravity = Gravity.CENTER
        setTextColor(theme.altKeyTextColor)
        visibility = View.GONE
    }

    val root = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setBackgroundColor(theme.keyboardColor)
        addView(header, LinearLayout.LayoutParams(-1, service.dp(44)))
        addView(FrameLayout(service).apply {
            addView(resultsScroll, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(-1, service.dp(58)))
    }

    val isOpen: Boolean get() = root.visibility == View.VISIBLE

    fun open() {
        if (isOpen) return
        editor.text.clear()
        root.visibility = View.VISIBLE
        onLayoutChanged()
        service.closeKeyboardPanel = closeCallback
        editor.requestFocus()
        search("")
        service.finishComposing()
        openJob = service.lifecycleScope.launch {
            // drop composing state that belongs to the application's editor
            service.postFcitxJob { focusOutIn() }.join()
            if (!isOpen) return@launch
            target = KeyboardTextTarget(editor, onReturn = ::returnToClipboard)
                .also { service.keyboardTextTarget = it }
        }
    }

    fun close() {
        if (!isOpen && target == null) return
        openJob?.cancel()
        searchJob?.cancel()
        pasteJob?.cancel()
        if (service.closeKeyboardPanel === closeCallback) service.closeKeyboardPanel = null
        root.visibility = View.GONE
        results.removeAllViews()
        onLayoutChanged()
        val previous = target ?: return
        target = null
        service.lifecycleScope.launch {
            service.postFcitxJob { focusOutIn() }.join()
            if (service.keyboardTextTarget === previous) service.keyboardTextTarget = null
        }
    }

    private fun returnToClipboard() {
        close()
        onBack()
    }

    private fun search(query: String) {
        searchJob?.cancel()
        searchJob = service.lifecycleScope.launch {
            delay(120)
            ClipboardManager.observeEntries().collectLatest { entries ->
                val matches = withContext(Dispatchers.Default) {
                    entries.asSequence()
                        .filter { ClipboardSearch.matches(it.text, query, HandwritingPinyin::of) }
                        .take(MaxResults)
                        .toList()
                }
                showResults(matches)
            }
        }
    }

    private fun showResults(entries: List<ClipboardEntry>) {
        results.removeAllViews()
        resultsScroll.scrollTo(0, 0)
        empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        entries.forEach { entry ->
            results.addView(TextView(service).apply {
                text = ClipboardAdapter.excerptText(
                    entry.text, entry.sensitive && maskSensitive, lines = 2, chars = 64
                )
                textSize = 13f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = service.dp(180)
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(theme.keyTextColor)
                setPadding(service.dp(10), 0, service.dp(10), 0)
                background = GradientDrawable().apply {
                    cornerRadius = service.dp(6).toFloat()
                    setColor(theme.keyBackgroundColor)
                }
                setOnClickListener { paste(entry) }
            }, LinearLayout.LayoutParams(-2, -1).apply { marginEnd = service.dp(6) })
        }
    }

    private fun paste(entry: ClipboardEntry) {
        if (pasteJob?.isActive == true) return
        pasteJob = service.lifecycleScope.launch {
            val current = target ?: return@launch
            // finish the half-typed query before writing to the application
            service.postFcitxJob { focusOutIn() }.join()
            if (service.keyboardTextTarget !== current) return@launch
            service.keyboardTextTarget = null
            service.commitText(entry.text)
            if (returnAfterPaste) {
                target = null
                close()
            } else {
                service.keyboardTextTarget = current
            }
        }
    }
}
