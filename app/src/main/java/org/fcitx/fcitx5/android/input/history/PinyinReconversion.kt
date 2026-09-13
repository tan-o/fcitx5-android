package org.fcitx.fcitx5.android.input.history

import android.widget.Toast
import org.fcitx.fcitx5.android.input.FcitxInputMethodService

/** Keeps only the most recent committed phrase and its actual displayed spelling. */
class PinyinReconversion(private val service: FcitxInputMethodService) {
    private var enabled = false
    private var pending = ""
    private var committed = ""
    private var spelling = ""
    fun reset(allowed: Boolean) { enabled = allowed; pending = ""; committed = ""; spelling = "" }
    fun clear() = reset(enabled)
    fun preedit(text: String) {
        if (!enabled || text.isEmpty()) return
        pending = if (text.matches(Regex("[a-zA-ZüÜvV'’ ]+"))) text.replace(" ", "").replace('’', '\'') else ""
    }
    fun committed(text: String) {
        if (!enabled) return
        committed = if (pending.isNotEmpty() && text.any { it in '\u3400'..'\u9fff' }) text else ""
        spelling = if (committed.isNotEmpty()) pending else ""
        pending = ""
    }
    fun restore() {
        val ic = service.currentInputConnection ?: return
        val text = committed
        val raw = spelling
        if (!enabled || text.isEmpty() || raw.isEmpty() || service.keyboardTextTarget != null ||
            !ic.getSelectedText(0).isNullOrEmpty() || ic.getTextBeforeCursor(text.length, 0)?.toString() != text) {
            Toast.makeText(service, "光标需位于刚选的词后；只有保留了原始拼音的词才能重选", Toast.LENGTH_SHORT).show()
            return
        }
        service.postFcitxJob {
            if (!isEmpty()) return@postFcitxJob
            if (service.currentInputConnection !== ic || ic.getTextBeforeCursor(text.length, 0)?.toString() != text) return@postFcitxJob
            if (!ic.deleteSurroundingText(text.length, 0)) return@postFcitxJob
            committed = ""
            spelling = ""
            raw.forEach { sendKey(it) }
        }
    }
}
