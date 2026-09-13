/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.keyboard.KeyGestureActions
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment

class UpSwipeSettingsFragment : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(PreferenceCategory(ctx).apply {
                title = ctx.getString(R.string.gesture_action_syntax_title)
                summary = ctx.getString(R.string.gesture_action_syntax)
            })
            addPreference(PreferenceCategory(ctx).apply {
                title = ctx.getString(R.string.key_gesture_actions)
                KeyGestureActions.keys.forEach { keyName ->
                    addPreference(Preference(ctx).apply {
                        title = ctx.getString(R.string.up_swipe_key, keyName.uppercase())
                        summary = summaryFor(keyName)
                        setOnPreferenceClickListener {
                            showEditor(keyName) { summary = summaryFor(keyName) }
                            true
                        }
                    })
                }
            })
        }
    }

    private fun summaryFor(key: String): String {
        val ctx = requireContext()
        val up = KeyGestureActions.upSwipeSpec(ctx, key).ifBlank { "关闭" }
        val count = KeyGestureActions.longPress(ctx, key).size
        return "上划：$up · 长按：${count} 项"
    }

    private fun showEditor(key: String, onSaved: () -> Unit) {
        val ctx = requireContext()
        fun label(text: String) = TextView(ctx).apply {
            this.text = text
            setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 4)
        }
        val upSwipe = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            setText(KeyGestureActions.upSwipeSpec(ctx, key))
        }
        val longPress = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(false)
            minLines = 7
            setText(KeyGestureActions.longPressSpec(ctx, key))
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(label("上划动作"))
            addView(upSwipe, LinearLayout.LayoutParams(-1, -2))
            addView(label("长按候选（每行一项）"))
            addView(longPress, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(ctx)
            .setTitle("${key.uppercase()} 键")
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("保存") { _, _ ->
                KeyGestureActions.save(ctx, key, upSwipe.text.toString(), longPress.text.toString())
                onSaved()
            }
            .show()
    }
}
