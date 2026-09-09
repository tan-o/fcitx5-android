/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard

import java.text.Normalizer
import java.util.Locale

/** Literal substring, then untoned pinyin/full initials. No SQL wildcards. */
object ClipboardSearch {
    fun matches(text: String, query: String, reading: (String) -> String?): Boolean {
        if (text.contains(query, ignoreCase = true)) return true
        val needle = query.lowercase(Locale.ROOT)
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
        return needle in full || needle in initials
    }
}
