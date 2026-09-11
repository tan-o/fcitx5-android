package org.fcitx.fcitx5.android.input.history

import android.view.inputmethod.ExtractedTextRequest
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import splitties.dimensions.dp

class EditingHistory(private val service: FcitxInputMethodService) {
    private val history = EditHistory()
    private var enabled = false
    private var restoring = false
    fun reset(allowed: Boolean) { history.clear(); enabled = allowed }
    private fun snapshot() = service.currentInputConnection?.getExtractedText(ExtractedTextRequest().apply {
        hintMaxChars = 8193
    }, 0)?.takeIf { it.startOffset == 0 && it.partialStartOffset < 0 && it.text.length <= 8192 }
    fun capture() {
        if (!enabled || restoring || service.keyboardTextTarget != null) return
        val text = snapshot() ?: return
        history.record(text.text.toString(), text.selectionStart, text.selectionEnd)
    }
    fun show() {
        capture()
        if (!enabled || service.keyboardTextTarget != null || history.nodes.isEmpty()) {
            Toast.makeText(service, "当前输入框未提供可用的编辑历史", Toast.LENGTH_SHORT).show()
            return
        }
        val rows = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        val dialog = MaterialAlertDialogBuilder(service).setTitle("编辑历史（当前输入框）")
            .setView(ScrollView(service).apply { addView(rows) })
            .setNegativeButton(android.R.string.cancel, null).create()
        val children = history.nodes.groupBy { it.parent }
        fun addNodes(parent: Int?, depth: Int) {
            children[parent].orEmpty().forEach { node ->
                rows.addView(TextView(service).apply {
                    text = "${if (node.id == history.current) "●" else "○"} #${node.id}  ${node.text.replace('\n', ' ').take(60).ifEmpty { "空文本" }}"
                    textSize = 15f
                    setPadding(dp(16 + minOf(depth, 8) * 12), dp(12), dp(16), dp(12))
                    setOnClickListener {
                        val live = snapshot()
                        val active = history.nodes.find { it.id == history.current }
                        if (live == null || live.text.toString() != active?.text) {
                            Toast.makeText(service, "输入框内容已变化，请重新打开历史", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            return@setOnClickListener
                        }
                        val ic = service.currentInputConnection ?: return@setOnClickListener
                        restoring = true
                        try {
                            service.finishComposing()
                            ic.beginBatchEdit()
                            try {
                                if (ic.setSelection(0, live.text.length) && ic.commitText(node.text, 1)) {
                                    ic.setSelection(node.start.coerceIn(0, node.text.length), node.end.coerceIn(0, node.text.length))
                                    history.select(node.id)
                                }
                            } finally { ic.endBatchEdit() }
                        } finally { restoring = false }
                        dialog.dismiss()
                    }
                })
                addNodes(node.id, depth + 1)
            }
        }
        addNodes(null, 0)
        service.showDialog(dialog)
    }
}
