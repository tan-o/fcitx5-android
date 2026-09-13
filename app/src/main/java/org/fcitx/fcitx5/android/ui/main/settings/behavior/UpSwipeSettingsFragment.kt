/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.keyboard.KeyGestureActions
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment

class UpSwipeSettingsFragment : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(PreferenceCategory(ctx).apply {
                title = ctx.getString(R.string.gesture_action_syntax_title)
                summary = ctx.getString(R.string.gesture_action_syntax)
            })
            addPreference(PreferenceCategory(ctx).apply {
                title = ctx.getString(R.string.up_swipe_symbols)
                KeyGestureActions.keys.forEach { keyName ->
                    addPreference(EditTextPreference(ctx).apply {
                        key = KeyGestureActions.upSwipePreferenceKey(keyName)
                        title = ctx.getString(R.string.up_swipe_key, keyName.uppercase())
                        setDefaultValue(KeyGestureActions.defaultUpSwipeSpec(keyName))
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        setOnBindEditTextListener {
                            it.inputType = InputType.TYPE_CLASS_TEXT or
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            it.isSingleLine = true
                            it.selectAll()
                        }
                    })
                }
            })
            addPreference(PreferenceCategory(ctx).apply {
                title = ctx.getString(R.string.long_press_actions)
                KeyGestureActions.keys.forEach { keyName ->
                    addPreference(EditTextPreference(ctx).apply {
                        key = KeyGestureActions.longPressPreferenceKey(keyName)
                        title = ctx.getString(R.string.long_press_key, keyName.uppercase())
                        setDefaultValue(KeyGestureActions.defaultLongPressSpec(keyName))
                        summary = ctx.getString(R.string.long_press_action_summary)
                        setOnBindEditTextListener {
                            it.inputType = InputType.TYPE_CLASS_TEXT or
                                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            it.isSingleLine = false
                            it.minLines = 5
                            it.setHorizontallyScrolling(false)
                        }
                    })
                }
            })
        }
    }
}
