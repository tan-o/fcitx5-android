/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.candidates.expanded

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui

/**
 * How the expanded candidates are grouped. [None] is the plain, unfiltered
 * list; the others group candidates by their first character.
 */
enum class CandidateFilterMode {
    None,
    Single;

    val labelRes: Int
        get() = when (this) {
            None -> R.string.candidate_filter_none
            Single -> R.string.candidate_filter_single

        }


}

/**
 * The bar on top of the expanded candidate list: a button cycling through the
 * grouping modes, followed by one chip per group.
 */
class CandidateFilterUi(override val ctx: Context, private val theme: Theme) : Ui {


    /** null means "clear the filter". */
    var onChipClick: ((String?) -> Unit)? = null

    private var activeChip: String? = null

    var onModeSelected: ((CandidateFilterMode) -> Unit)? = null
    private val modeButtons = CandidateFilterMode.entries.associateWith { mode ->
        chipView().apply {
            setText(mode.labelRes)
            setTextColor(theme.candidateTextColor)
            setOnClickListener { onModeSelected?.invoke(mode) }
        }
    }
    val modes = LinearLayout(ctx).apply {
        id = View.generateViewId()
        modeButtons.values.forEach { addView(it, LinearLayout.LayoutParams(0, -1, 1f)) }
    }

    private val chipsLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private val chipsScroll = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
        addView(
            chipsLayout,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override val root = LinearLayout(ctx).apply {
        id = View.generateViewId()
        orientation = LinearLayout.HORIZONTAL
        addView(
            chipsScroll,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )
    }

    fun setMode(mode: CandidateFilterMode) {
        modeButtons.forEach { (key, button) -> button.isActivated = key == mode }
        root.visibility = if (mode == CandidateFilterMode.None) View.GONE else View.VISIBLE
    }

    fun setChips(chips: List<String>) {
        activeChip = null
        chipsLayout.removeAllViews()
        chipsScroll.scrollTo(0, 0)
        chips.forEach { chip ->
            val view = chipView().apply {
                text = chip
                setTextColor(theme.candidateTextColor)
                setOnClickListener {
                    val selected = if (activeChip == chip) null else chip
                    activeChip = selected
                    updateActivated()
                    onChipClick?.invoke(selected)
                }
            }
            chipsLayout.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun updateActivated() {
        for (i in 0 until chipsLayout.childCount) {
            val child = chipsLayout.getChildAt(i) as? TextView ?: continue
            child.isActivated = child.text == activeChip
        }
    }

    private fun chipView(): TextView = TextView(ctx).apply {
        textSize = 14f
        isSingleLine = true
        gravity = Gravity.CENTER
        setPadding(ctx.dp(12), 0, ctx.dp(12), 0)
        background = chipBackground()
    }

    private fun chipBackground(): Drawable {
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
            setLayerSize(0, ctx.dp(24), ctx.dp(2))
            setLayerGravity(0, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayerInsetBottom(0, ctx.dp(3))
        }
    }
}
