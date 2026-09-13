/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.clipboard

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.utils.appContext

/** User labels for pinned entries, with deterministic labels for common structured text. */
object ClipboardTags {
    private val changes = MutableStateFlow(0)
    private val preferences by lazy {
        appContext.getSharedPreferences("clipboard_tags", Context.MODE_PRIVATE)
    }
    private val email = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    private val url = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    private val phoneChars = Regex("^[+()\\d\\s.-]+$")

    private fun automatic(text: String): String {
        val value = text.trim()
        return when {
            email.matches(value) -> "邮箱"
            url.matches(value) -> "链接"
            phoneChars.matches(value) && value.count(Char::isDigit) in 7..15 -> "电话"
            else -> "其他"
        }
    }

    fun label(entry: ClipboardEntry): String =
        preferences.getString(entry.id.toString(), null)?.takeIf(String::isNotBlank)
            ?: automatic(entry.text)

    fun customLabel(entry: ClipboardEntry): String =
        preferences.getString(entry.id.toString(), null).orEmpty()

    fun set(id: Int, label: String) {
        val value = label.trim().take(16)
        preferences.edit().apply {
            if (value.isEmpty()) remove(id.toString()) else putString(id.toString(), value)
        }.apply()
        changes.value += 1
    }

    fun observeChanges() = changes

    fun labels(entries: List<ClipboardEntry>): List<String> {
        val labels = entries.map(::label).toSet()
        val defaults = listOf("电话", "邮箱", "链接", "其他").filter(labels::contains)
        return defaults + (labels - defaults.toSet()).sorted()
    }
}
