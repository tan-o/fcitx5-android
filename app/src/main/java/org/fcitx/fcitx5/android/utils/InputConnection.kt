/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

import android.os.Build
import android.view.inputmethod.InputConnection

fun InputConnection.withBatchEdit(block: InputConnection.() -> Unit) {
    beginBatchEdit()
    block.invoke(this)
    endBatchEdit()
}

/** Delete one side without reading a potentially truncated snapshot of the editor. */
fun InputConnection.deleteToCursorBoundary(start: Int, end: Int, beforeCursor: Boolean): Boolean {
    if (start < 0 || end < start) return false
    finishComposingText()
    // Leave selected text intact. Bound the forward request so end + afterLength cannot overflow.
    return deleteSurroundingText(
        if (beforeCursor) start else 0,
        if (beforeCursor) 0 else Int.MAX_VALUE - end
    )
}

fun InputConnection.monitorCursorAnchor(enable: Boolean = true): Boolean {
    if (!enable) {
        requestCursorUpdates(0)
        return false
    }
    var scheduled = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        scheduled = requestCursorUpdates(
            InputConnection.CURSOR_UPDATE_MONITOR,
            InputConnection.CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS or InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER
        )
    }
    if (!scheduled) {
        scheduled = requestCursorUpdates(InputConnection.CURSOR_UPDATE_MONITOR)
    }
    return scheduled
}
