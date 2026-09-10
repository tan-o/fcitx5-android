package org.fcitx.fcitx5.android.input.translation

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping

/** An editor inside the keyboard, separate from the application's input connection. */
class KeyboardTextTarget(private val editor: EditText) {
    private val connection = editor.onCreateInputConnection(EditorInfo())!!
    fun commit(text: String) { connection.commitText(text, 1) }
    fun consume(event: FcitxEvent<*>): Boolean {
        when (event) {
            is FcitxEvent.CommitStringEvent -> commit(event.data.text)
            is FcitxEvent.ClientPreeditEvent -> connection.setComposingText(event.data.toString(), 1)
            is FcitxEvent.DeleteSurroundingEvent -> connection.deleteSurroundingText(event.data.before, event.data.after)
            is FcitxEvent.KeyEvent -> {
                val key = event.data
                if (key.up) return true
                val code = when (key.sym.sym) {
                    FcitxKeyMapping.FcitxKey_BackSpace -> KeyEvent.KEYCODE_DEL
                    FcitxKeyMapping.FcitxKey_Left -> KeyEvent.KEYCODE_DPAD_LEFT
                    FcitxKeyMapping.FcitxKey_Right -> KeyEvent.KEYCODE_DPAD_RIGHT
                    FcitxKeyMapping.FcitxKey_Return -> KeyEvent.KEYCODE_ENTER
                    else -> null
                }
                if (code != null) {
                    connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                } else if (key.unicode > 0) commit(String(Character.toChars(key.unicode)))
            }
            else -> return false
        }
        return true
    }
}
