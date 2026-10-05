/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard

import java.text.Normalizer
import java.util.Locale

/**
 * Literal substring, then untoned pinyin/full initials. No SQL wildcards.
 * Syllable separators typed in pinyin queries (spaces, apostrophes) are ignored and
 * `v` may stand for `ü`.
 */
object ClipboardSearch {
    private val separators = setOf(' ', '\'', '\u2019')

    fun matches(text: String, query: String, reading: (String) -> String?): Boolean {
        if (text.contains(query, ignoreCase = true)) return true
        val needle = query.lowercase(Locale.ROOT).filterNot { it in separators }
        if (needle.isEmpty() || needle.any { it !in 'a'..'z' }) return false
        val full = StringBuilder()
        val initials = StringBuilder()
        var offset = 0
        while (offset < text.length) {
            val cp = text.codePointAt(offset)
            val character = String(Character.toChars(cp))
            val syllable = reading(character)?.let {
                Normalizer.normalize(it, Normalizer.Form.NFD)
                    .filter { c -> Character.getType(c) != Character.NON_SPACING_MARK.toInt() }
                    .lowercase(Locale.ROOT)
            }
            if (syllable != null) {
                full.append(syllable)
                initials.append(syllable.firstOrNull() ?: ' ')
            } else {
                full.append(character.lowercase(Locale.ROOT))
                initials.append(character.lowercase(Locale.ROOT))
            }
            offset += Character.charCount(cp)
        }
        val alternative = needle.replace('v', 'u')
        return needle in full || needle in initials ||
            (alternative != needle && (alternative in full || alternative in initials))
    }
}
