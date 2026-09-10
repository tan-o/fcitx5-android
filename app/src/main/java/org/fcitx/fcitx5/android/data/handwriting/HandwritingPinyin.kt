/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.handwriting

import org.fcitx.fcitx5.android.utils.appContext

/**
 * All recorded readings of each of the 8105 chars of 通用规范汉字表, used to
 * annotate handwriting candidates. Data from mozillazg/pinyin-data.
 */
object HandwritingPinyin {

    private const val ASSET_PATH = "handwriting/pinyin.txt"

    private val table: Map<Int, String> by lazy {
        runCatching {
            appContext.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                buildMap {
                    lines.forEach { line ->
                        val sep = line.indexOf('\t')
                        if (sep > 0) put(line.codePointAt(0), line.substring(sep + 1))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun ofText(text: String): String = buildList {
        var offset = 0
        while (offset < text.length) {
            val cp = text.codePointAt(offset)
            add(table[cp] ?: String(Character.toChars(cp)))
            offset += Character.charCount(cp)
        }
    }.joinToString(" ")

    fun of(char: String): String? {
        if (char.isEmpty()) return null
        return table[char.codePointAt(0)]?.substringBefore(',')
    }
}
