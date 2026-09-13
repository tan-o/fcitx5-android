/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.input.keyboard.UpSwipeSymbols
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment

class UpSwipeSettingsFragment : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(PreferenceCategory(ctx).apply {
                title = ctx.getString(R.string.up_swipe_symbols)
                UpSwipeSymbols.defaults.forEach { (keyName, default) ->
                    addPreference(EditTextPreference(ctx).apply {
                        key = UpSwipeSymbols.preferenceKey(keyName)
                        title = ctx.getString(R.string.up_swipe_key, keyName.uppercase())
                        setDefaultValue(default)
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        setOnBindEditTextListener {
                            it.inputType = InputType.TYPE_CLASS_TEXT or
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            it.filters = arrayOf(InputFilter.LengthFilter(8))
                            it.isSingleLine = true
                            it.selectAll()
                        }
                    })
                }
            })
        }
    }
}
