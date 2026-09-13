package org.fcitx.fcitx5.android.input.translation

import android.text.Selection
import android.widget.EditText
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping

/** An editor inside the keyboard, separate from the application's input connection. */
class KeyboardTextTarget(private val editor: EditText) {
    private var composingStart = -1
    private var composingEnd = -1

    private fun selection(): IntRange {
        val length = editor.text.length
        val start = editor.selectionStart.coerceIn(0, length)
        val end = editor.selectionEnd.coerceIn(0, length)
        return minOf(start, end)..maxOf(start, end)
    }

    private fun composingRange(): IntRange? {
        val length = editor.text.length
        return if (composingStart in 0..length && composingEnd in composingStart..length) {
            composingStart..composingEnd
        } else null
    }

    private fun replace(range: IntRange, text: String) {
        val start = range.first
        val end = range.last
        editor.text.replace(start, end, text)
        Selection.setSelection(editor.text, start + text.length)
    }

    fun commit(text: String) {
        val range = composingRange() ?: selection()
        replace(range, text)
        composingStart = -1
        composingEnd = -1
    }

    private fun setPreedit(text: String) {
        val range = composingRange() ?: selection()
        val start = range.first
        replace(range, text)
        if (text.isEmpty()) {
            composingStart = -1
            composingEnd = -1
        } else {
            composingStart = start
            composingEnd = start + text.length
        }
    }

    private fun deleteSurrounding(before: Int, after: Int) {
        val selected = selection()
        val start = (selected.first - before).coerceAtLeast(0)
        val end = (selected.last + after).coerceAtMost(editor.text.length)
        replace(start..end, "")
        composingStart = -1
        composingEnd = -1
    }

    private fun moveCursor(offset: Int) {
        val selected = selection()
        val cursor = if (offset < 0) selected.first else selected.last
        Selection.setSelection(editor.text, (cursor + offset).coerceIn(0, editor.text.length))
        composingStart = -1
        composingEnd = -1
    }

    fun consume(event: FcitxEvent<*>): Boolean {
        when (event) {
            is FcitxEvent.CommitStringEvent -> commit(event.data.text)
            is FcitxEvent.ClientPreeditEvent -> setPreedit(event.data.toString())
            is FcitxEvent.DeleteSurroundingEvent -> deleteSurrounding(event.data.before, event.data.after)
            is FcitxEvent.KeyEvent -> {
                val key = event.data
                if (key.up) return true
                when (key.sym.sym) {
                    FcitxKeyMapping.FcitxKey_BackSpace -> deleteSurrounding(1, 0)
                    FcitxKeyMapping.FcitxKey_Left -> moveCursor(-1)
                    FcitxKeyMapping.FcitxKey_Right -> moveCursor(1)
                    FcitxKeyMapping.FcitxKey_Return -> commit("\n")
                    else -> if (key.unicode > 0) commit(String(Character.toChars(key.unicode)))
                }
            }
            else -> return false
        }
        return true
    }
}
