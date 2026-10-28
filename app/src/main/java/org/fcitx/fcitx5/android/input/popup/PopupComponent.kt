/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.popup

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.broadcast.PunctuationComponent
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.KeyGestureActions
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.UniqueComponent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import java.util.LinkedList
import kotlin.math.ceil
import kotlin.math.max

class PopupComponent :
    UniqueComponent<PopupComponent>(), Dependent, ManagedHandler by managedHandler() {

    private val service by manager.inputMethodService()
    private val context by manager.context()
    private val theme by manager.theme()
    private val punctuation: PunctuationComponent by manager.must()

    private val showingEntryUi = HashMap<Int, PopupEntryUi>()
    private val dismissJobs = HashMap<Int, Job>()
    private val freeEntryUi = LinkedList<PopupEntryUi>()

    private val showingContainerUi = HashMap<Int, PopupContainerUi>()

    private val keyBottomMargin by lazy {
        context.dp(ThemeManager.prefs.keyVerticalMargin.getValue())
    }
    private val popupWidth by lazy {
        context.dp(38)
    }
    private val popupHeight by lazy {
        context.dp(116)
    }
    private val popupKeyHeight by lazy {
        context.dp(48)
    }
    private val popupRadius by lazy {
        context.dp(ThemeManager.prefs.keyRadius.getValue()).toFloat()
    }
    private val hideThreshold = 100L

    private val rootLocation = intArrayOf(0, 0)
    private val rootBounds: Rect = Rect()

    val root by lazy {
        context.frameLayout {
            // we want (0, 0) at top left
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isClickable = false
            isFocusable = false

            addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                val (x, y) = rootLocation.also { v.getLocationInWindow(it) }
                val width = right - left
                val height = bottom - top
                rootBounds.set(x, y, x + width, y + height)
            }
        }
    }

    private fun showPopup(viewId: Int, content: String, bounds: Rect, fitContent: Boolean = false) {
        dismissJobs.remove(viewId)?.cancel()
        val popup = (showingEntryUi[viewId] ?: freeEntryUi.poll()
            ?: PopupEntryUi(context, theme, popupKeyHeight, popupRadius)).apply {
            lastShowTime = System.currentTimeMillis()
            setText(content)
        }
        val padding = if (fitContent) context.dp(12) else 0
        popup.textView.apply {
            setPadding(padding, 0, padding, 0)
            scaleMode = if (fitContent) AutoScaleTextView.Mode.Proportional else AutoScaleTextView.Mode.None
        }
        val availableWidth = root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val width = if (fitContent) {
            max(popupWidth, ceil(popup.textView.paint.measureText(content)).toInt() + padding * 2)
                .coerceAtMost(availableWidth)
        } else popupWidth
        val height = if (fitContent) {
            max(popupKeyHeight, ceil(popup.textView.paint.fontSpacing).toInt() + context.dp(16))
        } else popupHeight
        popup.textView.layoutParams = popup.textView.layoutParams.apply {
            this.height = if (fitContent) height else popupKeyHeight
        }
        popup.root.layoutParams = FrameLayout.LayoutParams(width, height).apply {
            if (fitContent) {
                topMargin = (bounds.top - rootBounds.top - height - context.dp(8))
                    .coerceIn(0, (root.height - height).coerceAtLeast(0))
                leftMargin = ((bounds.left + bounds.right - width) / 2 - rootBounds.left)
                    .coerceIn(0, (availableWidth - width).coerceAtLeast(0))
            } else {
                // align popup bottom with key border bottom [^1]
                topMargin = bounds.bottom - popupHeight - keyBottomMargin
                leftMargin = (bounds.left + bounds.right - popupWidth) / 2
            }
        }
        // make sure that popup.root does not have parent view before adding it under root container
        // it's wired that on some devices it would have a parent view despite it was newly created
        // or just polled from freeEntryUi
        if (popup.root.parent == null) {
            root.addView(popup.root)
        } else if (popup.root.parent !== root) {
            (popup.root.parent as? ViewGroup)?.removeView(popup.root)
            root.addView(popup.root)
        }
        showingEntryUi[viewId] = popup
    }

    private fun updatePopup(viewId: Int, content: String) {
        showingEntryUi[viewId]?.setText(content)
    }

    private fun showKeyboard(viewId: Int, keyboard: KeyDef.Popup.Keyboard, bounds: Rect) {
        val items: Array<KeyDef.Popup.Keyboard.Explicit.Item>
        when (keyboard) {
            is KeyDef.Popup.Keyboard.Preset -> {
                val configured = if (keyboard.label.singleOrNull()?.isLetter() == true) {
                    KeyGestureActions.customLongPress(context, keyboard.label)
                } else null
                items = if (configured != null) {
                    configured.map { KeyDef.Popup.Keyboard.Explicit.Item(it.label, it.action) }.toTypedArray()
                } else {
                    val preset = PopupPreset[keyboard.label] ?: return
                    Array(preset.size) { index ->
                        val key = preset[index]
                        val label = if (keyboard.transformPunctuation && punctuation.enabled) {
                            punctuation.transform(key)
                        } else key
                        KeyDef.Popup.Keyboard.Explicit.Item(label, KeyAction.FcitxKeyAction(key))
                    }
                }
            }
            is KeyDef.Popup.Keyboard.Explicit -> {
                items = keyboard.items
            }
        }
        if (items.isEmpty()) return
        // clear popup preview text         OR create empty popup preview
        showingEntryUi[viewId]?.setText("") ?: showPopup(viewId, "", bounds)
        val keyboardUi = PopupKeyboardUi(
            context,
            theme,
            rootBounds,
            bounds,
            { dismissPopup(viewId) },
            popupRadius,
            popupWidth,
            popupKeyHeight,
            // position popup keyboard higher, because of [^1]
            popupHeight + keyBottomMargin,
            items
        )
        showPopupContainer(viewId, keyboardUi)
    }

    private fun showMenu(viewId: Int, menu: KeyDef.Popup.Menu, bounds: Rect) {
        showingEntryUi[viewId]?.let {
            dismissPopupEntry(viewId, it)
        }
        val menuUi = PopupMenuUi(
            context,
            theme,
            rootBounds,
            bounds,
            { dismissPopup(viewId) },
            menu.items,
        )
        showPopupContainer(viewId, menuUi)
    }

    private fun showPopupContainer(viewId: Int, ui: PopupContainerUi) {
        root.apply {
            add(ui.root, lParams {
                leftMargin = ui.triggerBounds.left + ui.offsetX - rootBounds.left
                topMargin = ui.triggerBounds.top + ui.offsetY - rootBounds.top
            })
        }
        showingContainerUi[viewId] = ui
    }

    private fun changeFocus(viewId: Int, x: Float, y: Float): Boolean {
        return showingContainerUi[viewId]?.changeFocus(x, y) ?: false
    }

    private fun triggerFocused(viewId: Int): KeyAction? {
        return showingContainerUi[viewId]?.onTrigger()
    }

    private fun dismissPopup(viewId: Int) {
        dismissPopupContainer(viewId)
        showingEntryUi[viewId]?.also {
            val timeLeft = it.lastShowTime + hideThreshold - System.currentTimeMillis()
            if (timeLeft <= 0L) {
                dismissPopupEntry(viewId, it)
            } else {
                dismissJobs[viewId] = service.lifecycleScope.launch {
                    delay(timeLeft)
                    dismissPopupEntry(viewId, it)
                    dismissJobs.remove(viewId)
                }
            }
        }
    }

    private fun dismissPopupContainer(viewId: Int) {
        showingContainerUi[viewId]?.also {
            showingContainerUi.remove(viewId)
            root.removeView(it.root)
        }
    }

    private fun dismissPopupEntry(viewId: Int, popup: PopupEntryUi) {
        showingEntryUi.remove(viewId)
        root.removeView(popup.root)
        freeEntryUi.add(popup)
    }

    fun dismissAll() {
        // avoid modifying collection while iterating
        dismissJobs.forEach { (_, job) ->
            job.cancel()
        }
        dismissJobs.clear()
        // too
        showingContainerUi.forEach { (_, container) ->
            root.removeView(container.root)
        }
        showingContainerUi.clear()
        // too too
        showingEntryUi.forEach { (_, entry) ->
            root.removeView(entry.root)
            freeEntryUi.add(entry)
        }
        showingEntryUi.clear()
    }

    val listener = PopupActionListener { action ->
        with(action) {
            when (this) {
                is PopupAction.ChangeFocusAction -> outResult = changeFocus(viewId, x, y)
                is PopupAction.DismissAction -> dismissPopup(viewId)
                is PopupAction.PreviewAction -> showPopup(viewId, content, bounds, fitContent)
                is PopupAction.PreviewUpdateAction -> updatePopup(viewId, content)
                is PopupAction.ShowKeyboardAction -> showKeyboard(viewId, keyboard, bounds)
                is PopupAction.ShowMenuAction -> showMenu(viewId, menu, bounds)
                is PopupAction.TriggerAction -> outAction = triggerFocused(viewId)
            }
        }
    }
}
