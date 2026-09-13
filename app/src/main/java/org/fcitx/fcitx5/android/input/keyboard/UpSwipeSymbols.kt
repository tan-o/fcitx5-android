/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import androidx.preference.PreferenceManager

object UpSwipeSymbols {
    val defaults = linkedMapOf(
        "q" to "~", "w" to "`", "e" to "€", "r" to "₹", "t" to "™",
        "y" to "¥", "u" to "_", "i" to "|", "o" to "[", "p" to "]",
        "a" to "&", "s" to "%", "d" to "\$", "f" to "^", "g" to "<",
        "h" to ">", "j" to "{", "k" to "}", "l" to ";",
        "z" to "…", "x" to "×", "c" to "©", "v" to "✓", "b" to "•",
        "n" to "—", "m" to "§"
    )

    fun preferenceKey(key: String) = "up_swipe_symbol_${key.lowercase()}"

    fun get(context: Context, key: String): String {
        val normalized = key.lowercase()
        val fallback = defaults[normalized].orEmpty()
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(preferenceKey(normalized), fallback)
            .orEmpty()
    }
}
