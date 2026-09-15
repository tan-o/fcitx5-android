/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.core

data class CandidateWord @JvmOverloads constructor(
    val label: String,
    val text: String,
    val comment: String,
    val spaceBetweenComment: Boolean = true
) {
    val radical: String?
        get() = RadicalMetadata.find(comment)?.groupValues?.get(1)

    fun visibleComment(): String = RadicalMetadata.replace(comment, "").trimEnd()

    fun textWithComment(): String {
        return buildString {
            append(text)
            val visibleComment = visibleComment()
            if (visibleComment.isNotBlank()) {
                if (spaceBetweenComment) {
                    append(" ")
                }
                append(visibleComment)
            }
        }
    }

    companion object {
        private val RadicalMetadata = Regex("\u2063fcitx-radical:([^\u2063]+)\u2063")
        val Empty = CandidateWord("", "", "", false)
    }
}
