/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ViewAnimator
import androidx.transition.Fade
import androidx.transition.TransitionManager
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.coordinatorlayout.coordinatorLayout
import splitties.views.dsl.coordinatorlayout.defaultLParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.view
import splitties.views.dsl.recyclerview.recyclerView
import timber.log.Timber

class ClipboardUi(override val ctx: Context, private val theme: Theme) : Ui {

    val recyclerView = recyclerView {
        addItemDecoration(SpacesItemDecoration(dp(4)))
    }

    val enableUi = ClipboardInstructionUi.Enable(ctx, theme)

    val emptyUi = ClipboardInstructionUi.Empty(ctx, theme)

    val viewAnimator =  view(::ViewAnimator) {
        add(recyclerView, lParams(matchParent, matchParent))
        add(emptyUi.root, lParams(matchParent, matchParent))
        add(enableUi.root, lParams(matchParent, matchParent))
    }

    val tabsUi = ClipboardTabsUi(ctx, theme)

    val searchBar = textView {
        textSize = 14f
        isSingleLine = true
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(theme.keyTextColor)
        visibility = View.GONE
    }

    val searchKeyboard = TextKeyboard(ctx, theme).apply {
        visibility = View.GONE
    }

    private var searching = false

    private val keyBorder by ThemeManager.prefs.keyBorder
    private val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation

    private val content = verticalLayout {
        addView(tabsUi.root, LinearLayout.LayoutParams(matchParent, dp(36)))
        addView(searchBar, LinearLayout.LayoutParams(matchParent, dp(36)))
        addView(viewAnimator, LinearLayout.LayoutParams(matchParent, 0, 1f))
        addView(searchKeyboard, LinearLayout.LayoutParams(matchParent, 0, 1.6f))
    }

    override val root = coordinatorLayout {
        if (!keyBorder) {
            backgroundColor = theme.barColor
        }
        add(content, defaultLParams(matchParent, matchParent))
    }

    val deleteAllButton = ToolButton(ctx, R.drawable.ic_baseline_delete_sweep_24, theme).apply {
        contentDescription = ctx.getString(R.string.delete_all)
    }

    val searchButton = ToolButton(ctx, R.drawable.ic_baseline_search_24, theme).apply {
        contentDescription = ctx.getString(R.string.clipboard_search)
    }

    val extension = horizontalLayout {
        add(searchButton, lParams(dp(40), dp(40)))
        add(deleteAllButton, lParams(dp(40), dp(40)))
    }

    fun setSearchMode(on: Boolean) {
        searching = on
        tabsUi.root.visibility = if (on) View.GONE else View.VISIBLE
        searchBar.visibility = if (on) View.VISIBLE else View.GONE
        searchKeyboard.visibility = if (on) View.VISIBLE else View.GONE
    }

    fun updateSearchQuery(query: String) {
        searchBar.text = query.ifEmpty { ctx.getString(R.string.clipboard_search) }
    }

    private fun setDeleteButtonShown(enabled: Boolean) {
        deleteAllButton.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    private fun setTabsShown(shown: Boolean) {
        if (searching) return
        tabsUi.root.visibility = if (shown) View.VISIBLE else View.GONE
    }

    fun switchUiByState(state: ClipboardStateMachine.State) {
        Timber.d("Switch clipboard to $state")
        if (!disableAnimation)
            TransitionManager.beginDelayedTransition(root, Fade().apply { duration = 100L })
        when (state) {
            ClipboardStateMachine.State.Normal -> {
                viewAnimator.displayedChild = 0
                setDeleteButtonShown(true)
                setTabsShown(true)
            }
            ClipboardStateMachine.State.AddMore -> {
                viewAnimator.displayedChild = 1
                setDeleteButtonShown(false)
                setTabsShown(true)
            }
            ClipboardStateMachine.State.EnableListening -> {
                viewAnimator.displayedChild = 2
                setDeleteButtonShown(false)
                setTabsShown(false)
            }
        }
    }
}