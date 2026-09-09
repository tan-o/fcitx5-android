/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.gravityCenter

enum class ClipboardTab {
    Recent,
    Pinned
}

/**
 * A two-tab strip on top of the clipboard window, switching between recently
 * copied entries and pinned ones.
 */
class ClipboardTabsUi(override val ctx: Context, private val theme: Theme) : Ui {

    var onTabSelected: ((ClipboardTab) -> Unit)? = null

    var activeTab: ClipboardTab = ClipboardTab.Recent
        private set

    private val recentTab = tabView(R.string.clipboard_tab_recent)

    private val pinnedTab = tabView(R.string.clipboard_tab_pinned)

    override val root = horizontalLayout {
        addView(recentTab, LinearLayout.LayoutParams(0, matchParent, 1f))
        addView(pinnedTab, LinearLayout.LayoutParams(0, matchParent, 1f))
    }

    init {
        recentTab.setOnClickListener { selectTab(ClipboardTab.Recent) }
        pinnedTab.setOnClickListener { selectTab(ClipboardTab.Pinned) }
        updateActivated()
    }

    private fun tabView(@StringRes label: Int): TextView = textView {
        setText(label)
        textSize = 14f
        isSingleLine = true
        gravity = gravityCenter
        setTextColor(theme.keyTextColor)
        background = indicatorDrawable()
    }

    private fun indicatorDrawable(): Drawable {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_activated),
            intArrayOf()
        )
        val colors = intArrayOf(
            theme.genericActiveBackgroundColor,
            theme.altKeyBackgroundColor
        )
        return LayerDrawable(
            arrayOf(
                ShapeDrawable(RectShape()).apply {
                    setTintList(ColorStateList(states, colors))
                },
                pressHighlightDrawable(theme.keyPressHighlightColor)
            )
        ).apply {
            setLayerSize(0, ctx.dp(32), ctx.dp(2))
            setLayerGravity(0, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayerInsetBottom(0, ctx.dp(4))
        }
    }

    private fun selectTab(tab: ClipboardTab) {
        if (tab == activeTab) return
        activeTab = tab
        updateActivated()
        onTabSelected?.invoke(tab)
    }

    private fun updateActivated() {
        recentTab.isActivated = activeTab == ClipboardTab.Recent
        pinnedTab.isActivated = activeTab == ClipboardTab.Pinned
    }
}
