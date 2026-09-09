/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.candidates

import org.fcitx.fcitx5.android.utils.appContext

/**
 * Radical (部首) and total stroke count of each character, used to group
 * candidates. Data from pwxcoo/chinese-xinhua (MIT); characters missing from
 * it simply cannot be grouped.
 */
object HanziIndex {

    private const val ASSET_PATH = "candidates/hanzi-index.txt"

    data class Entry(val radical: String, val strokes: Int)

    private val table: Map<Int, Entry> by lazy {
        runCatching {
            appContext.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                buildMap {
                    lines.forEach { line ->
                        val parts = line.split('\t')
                        if (parts.size != 3) return@forEach
                        val strokes = parts[2].toIntOrNull() ?: return@forEach
                        put(parts[0].codePointAt(0), Entry(parts[1], strokes))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    /**
     * A candidate is grouped by its first character, which is what one is
     * looking at when hunting for a particular 字.
     */
    fun of(candidate: String): Entry? {
        if (candidate.isEmpty()) return null
        return table[candidate.codePointAt(0)]
    }

    /**
     * Stroke count of a radical itself, so radical chips can be ordered the
     * way a 部首表 is.
     */
    fun radicalStrokes(radical: String): Int =
        of(radical)?.strokes ?: Int.MAX_VALUE
}
