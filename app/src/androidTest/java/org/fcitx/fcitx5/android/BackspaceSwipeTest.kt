package org.fcitx.fcitx5.android

import android.text.Editable
import android.text.Selection
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.Event
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.GestureType
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.translation.KeyboardTextTarget
import org.fcitx.fcitx5.android.utils.deleteToCursorBoundary
import org.junit.Assert.*
import org.junit.Test

class BackspaceSwipeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun bothDirectionsShowFullWidthHintsAndOnlyDeleteOnRelease() {
        instrumentation.runOnMainSync {
            for ((steps, before) in listOf(-3 to true, 3 to false)) {
                val keyboard = TextKeyboard(context, ThemePreset.MaterialLight)
                val actions = mutableListOf<KeyAction>()
                val popups = mutableListOf<PopupAction>()
                keyboard.keyActionListener = KeyActionListener { action, _ -> actions.add(action) }
                keyboard.popupActionListener = PopupActionListener { popups.add(it) }
                val key = keyboard.backspace
                fun send(type: GestureType, y: Int, cancelled: Boolean = false) {
                    key.onGestureListener!!.onGesture(key, Event(type, false, 0f, 0f, 0, 0, 0, y, cancelled))
                }
                send(GestureType.Down, 0)
                send(GestureType.Move, steps)
                assertTrue(actions.isEmpty())
                val hint = popups.filterIsInstance<PopupAction.PreviewAction>().last()
                assertTrue(hint.fitContent)
                assertEquals(context.getString(if (before) R.string.delete_before_cursor else R.string.delete_after_cursor), hint.content)
                send(GestureType.Up, steps)
                assertEquals(listOf(KeyAction.DeleteToBoundaryAction(before)), actions)
            }
        }
    }

    @Test fun returningToOriginOrCancellingDoesNotDeleteEitherSide() {
        instrumentation.runOnMainSync {
            for (steps in listOf(-3, 3)) for (cancel in listOf(false, true)) {
                val keyboard = TextKeyboard(context, ThemePreset.MaterialLight)
                val actions = mutableListOf<KeyAction>()
                keyboard.keyActionListener = KeyActionListener { action, _ -> actions.add(action) }
                val key = keyboard.backspace
                fun send(type: GestureType, y: Int, cancelled: Boolean = false) {
                    key.onGestureListener!!.onGesture(key, Event(type, false, 0f, 0f, 0, 0, 0, y, cancelled))
                }
                send(GestureType.Down, 0)
                send(GestureType.Move, steps)
                if (!cancel) send(GestureType.Move, 0)
                send(GestureType.Up, if (cancel) steps else 0, cancel)
                assertTrue(actions.isEmpty())
            }
        }
    }

    @Test fun deletesWholeSideOfLongMultilineTextAndPreservesOppositeSide() {
        instrumentation.runOnMainSync {
            val left = "左🙂\n".repeat(5000)
            val right = "右🙂\n".repeat(5000)
            for (before in listOf(true, false)) {
                val text = Editable.Factory.getInstance().newEditable(left + right)
                Selection.setSelection(text, left.length)
                val connection = object : BaseInputConnection(EditText(context), true) {
                    override fun getEditable(): Editable = text
                }
                assertTrue(connection.deleteToCursorBoundary(left.length, left.length, before))
                assertEquals(if (before) right else left, text.toString())
                assertEquals(if (before) 0 else left.length, Selection.getSelectionStart(text))
            }
        }
    }

    @Test fun preservesSelectionAndHandlesEmptySidesInBothEditors() {
        instrumentation.runOnMainSync {
            for (before in listOf(true, false)) for ((start, end) in listOf(0 to 0, 2 to 4, 6 to 6)) {
                val original = "ab中文cd"
                val expected = if (before) original.substring(start) else original.substring(0, end)
                val text = Editable.Factory.getInstance().newEditable(original)
                Selection.setSelection(text, start, end)
                val connection = object : BaseInputConnection(EditText(context), true) {
                    override fun getEditable(): Editable = text
                }
                assertTrue(connection.deleteToCursorBoundary(start, end, before))
                assertEquals(expected, text.toString())

                val editor = EditText(context).apply { setText(original); setSelection(start, end) }
                KeyboardTextTarget(editor).deleteToCursorBoundary(before)
                assertEquals(expected, editor.text.toString())
                assertEquals(Selection.getSelectionStart(text), editor.selectionStart)
                assertEquals(Selection.getSelectionEnd(text), editor.selectionEnd)
            }
        }
    }
}
