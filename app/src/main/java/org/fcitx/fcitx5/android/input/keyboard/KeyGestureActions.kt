/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import androidx.preference.PreferenceManager
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupPreset

object KeyGestureActions {
    data class Entry(val label: String, val action: KeyAction)

    val keys = "qwertyuiopasdfghjklzxcvbnm".map(Char::toString)

    private val defaultUpSwipe = linkedMapOf(
        "q" to "~", "w" to "`", "e" to "€", "r" to "₹", "t" to "™",
        "y" to "¥", "u" to "_", "i" to "|", "o" to "[", "p" to "]",
        "a" to "&", "s" to "%", "d" to "\$", "f" to "^", "g" to "<",
        "h" to ">", "j" to "{", "k" to "}", "l" to ";",
        "z" to "…", "x" to "×", "c" to "©", "v" to "✓", "b" to "•",
        "n" to "—", "m" to "§"
    )

    fun upSwipePreferenceKey(key: String) = "up_swipe_action_${key.lowercase()}"
    fun longPressPreferenceKey(key: String) = "long_press_actions_${key.lowercase()}"

    fun defaultUpSwipeSpec(key: String) = defaultUpSwipe[key.lowercase()].orEmpty()

    fun defaultLongPressSpec(key: String): String =
        PopupPreset[key.lowercase()].orEmpty().joinToString("\n")

    fun upSwipe(context: Context, key: String): Entry? {
        val normalized = key.lowercase()
        val spec = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(upSwipePreferenceKey(normalized), defaultUpSwipeSpec(normalized))
            .orEmpty()
        return parse(spec)
    }

    fun longPress(context: Context, key: String): List<Entry> {
        val normalized = key.lowercase()
        val stored = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(longPressPreferenceKey(normalized), null)
        val lines = (stored ?: defaultLongPressSpec(key)).lineSequence()
        return lines.mapNotNull(::parse).toList()
    }

    /**
     * One action per line. A label may be placed before '='.
     * Plain text commits itself. Supported commands:
     * text:, datetime:, lua:function[:argument], fcitx:quickphrase,
     * fcitx:unicode, fcitx:emoji, fcitx:symbols and fcitx:next-ime.
     */
    fun parse(line: String): Entry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val separator = trimmed.indexOf('=')
        val explicitLabel = separator > 0
        val spec = if (explicitLabel) trimmed.substring(separator + 1).trim() else trimmed
        if (spec.isEmpty()) return null
        val action = when {
            spec.startsWith("text:", ignoreCase = true) ->
                KeyAction.CommitAction(spec.substringAfter(':'))
            spec.startsWith("datetime:", ignoreCase = true) ->
                KeyAction.DateTimeAction(spec.substringAfter(':'))
            spec.startsWith("lua:", ignoreCase = true) -> {
                val invocation = spec.substringAfter(':')
                val function = invocation.substringBefore(':').trim()
                if (function.isEmpty()) return null
                KeyAction.LuaAction(function, invocation.substringAfter(':', ""))
            }
            spec.equals("fcitx:quickphrase", ignoreCase = true) -> KeyAction.QuickPhraseAction
            spec.equals("fcitx:unicode", ignoreCase = true) -> KeyAction.UnicodeAction
            spec.equals("fcitx:emoji", ignoreCase = true) ->
                KeyAction.PickerSwitchAction(PickerWindow.Key.Emoji)
            spec.equals("fcitx:symbols", ignoreCase = true) ->
                KeyAction.PickerSwitchAction(PickerWindow.Key.Symbol)
            spec.equals("fcitx:next-ime", ignoreCase = true) -> KeyAction.LangSwitchAction
            else -> KeyAction.CommitAction(spec)
        }
        val label = if (explicitLabel) trimmed.substring(0, separator).trim() else when (action) {
            is KeyAction.CommitAction -> action.text
            else -> spec
        }
        if (label.isEmpty()) return null
        return Entry(label, action)
    }
}
