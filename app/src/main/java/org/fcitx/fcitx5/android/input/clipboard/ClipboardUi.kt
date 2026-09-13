/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.widget.ViewAnimator
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.keyboard.NumberKeyboard
import org.fcitx.fcitx5.android.input.keyboard.BaseKeyboard
import org.fcitx.fcitx5.android.input.keyboard.SymbolKey
import org.fcitx.fcitx5.android.input.keyboard.LayoutSwitchKey
import org.fcitx.fcitx5.android.input.keyboard.BackspaceKey
import org.fcitx.fcitx5.android.input.keyboard.ReturnKey
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
        add(textView {
            setText(R.string.clipboard_no_results)
            gravity = Gravity.CENTER
            setTextColor(theme.keyTextColor)
        }, lParams(matchParent, matchParent))
    }

    val tabsUi = ClipboardTabsUi(ctx, theme)

    private val categoryRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val categoryScroll = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        addView(categoryRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, matchParent))
        visibility = View.GONE
    }

    val searchBar = textView {
        textSize = 14f
        isSingleLine = true
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(theme.keyTextColor)
        visibility = View.GONE
    }

    val searchKeyboard = TextKeyboard(ctx, theme)
    val searchNumberKeyboard = NumberKeyboard(ctx, theme)
    val searchSymbolKeyboard = object : BaseKeyboard(ctx, theme, listOf(
        "!@#$%^&*()".map { SymbolKey(it.toString()) },
        "[]{}<>_=+~".map { SymbolKey(it.toString()) },
        listOf(LayoutSwitchKey("ABC", TextKeyboard.Name, 0.2f)) +
            ":;?/.,".map { SymbolKey(it.toString()) } +
            listOf(BackspaceKey(), ReturnKey())
    )) {}
    val searchKeyboards = listOf(searchKeyboard, searchNumberKeyboard, searchSymbolKeyboard)
    private val keyboardContainer = FrameLayout(ctx).apply {
        searchKeyboards.forEach { addView(it, FrameLayout.LayoutParams(matchParent, matchParent)) }
        visibility = View.GONE
    }

    fun switchSearchKeyboard(name: String) {
        val selected = when (name) {
            TextKeyboard.Name -> searchKeyboard
            "Symbol" -> searchSymbolKeyboard
            else -> searchNumberKeyboard
        }
        searchKeyboards.forEach { it.visibility = if (it === selected) View.VISIBLE else View.GONE }
    }

    private var searching = false

    private val keyBorder by ThemeManager.prefs.keyBorder

    private val content = verticalLayout {
        addView(tabsUi.root, LinearLayout.LayoutParams(matchParent, dp(36)))
        addView(categoryScroll, LinearLayout.LayoutParams(matchParent, dp(34)))
        addView(searchBar, LinearLayout.LayoutParams(matchParent, dp(36)))
        addView(viewAnimator, LinearLayout.LayoutParams(matchParent, 0, 1f))
        addView(keyboardContainer, LinearLayout.LayoutParams(matchParent, 0, 1.6f))
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
        if (on) categoryScroll.visibility = View.GONE
        searchBar.visibility = if (on) View.VISIBLE else View.GONE
        keyboardContainer.visibility = if (on) View.VISIBLE else View.GONE
        if (on) switchSearchKeyboard(TextKeyboard.Name)
    }

    fun showCategories(labels: List<String>, selected: String?, onSelect: (String?) -> Unit) {
        categoryRow.removeAllViews()
        val values = listOf<String?>(null) + labels
        values.forEach { value ->
            categoryRow.addView(TextView(ctx).apply {
                text = value ?: ctx.getString(R.string.clipboard_tag_all)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (value == selected) theme.genericActiveForegroundColor else theme.keyTextColor)
                setPadding(dp(12), 0, dp(12), 0)
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (value == selected) theme.genericActiveBackgroundColor else theme.altKeyBackgroundColor)
                }
                setOnClickListener { onSelect(value) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)).apply {
                marginStart = dp(4)
            })
        }
        categoryScroll.visibility = if (!searching && labels.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun hideCategories() {
        categoryScroll.visibility = View.GONE
    }

    fun updateSearchQuery(query: String) {
        searchBar.text = query.ifEmpty { ctx.getString(R.string.clipboard_search_hint) }
    }

    fun updateTagQuery(query: String) {
        searchBar.text = if (query.isEmpty()) {
            ctx.getString(R.string.clipboard_tag_hint)
        } else {
            ctx.getString(R.string.clipboard_tag_value, query)
        }
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
        when (state) {
            ClipboardStateMachine.State.Normal -> {
                viewAnimator.displayedChild = 0
                setDeleteButtonShown(true)
                setTabsShown(true)
            }
            ClipboardStateMachine.State.AddMore -> {
                viewAnimator.displayedChild = if (searching) 3 else 1
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
