package org.fcitx.fcitx5.android.ui.main.settings

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.lua.LuaScriptManager
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import java.io.File

class LuaScriptsFragment : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) = rebuild()

    private fun rebuild() {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(Preference(ctx).apply {
                title = "使用说明"
                summary = "脚本保存到 data/lua/imeapi/extensions。定义返回文本的全局函数后，可在按键手势中写：时间=lua:insert_current_time:%Y-%m-%d %H:%M"
                isSelectable = false
            })
            addPreference(Preference(ctx).apply {
                title = "新建 Lua 脚本"
                summary = "从可运行的时间插入示例开始"
                setOnPreferenceClickListener { showNameDialog(); true }
            })
            addPreference(PreferenceCategory(ctx).apply {
                title = "脚本"
                val files = LuaScriptManager.list()
                if (files.isEmpty()) {
                    addPreference(Preference(ctx).apply {
                        title = "暂无脚本"
                        isSelectable = false
                    })
                } else {
                    files.forEach { file ->
                        addPreference(Preference(ctx).apply {
                            title = file.name
                            summary = "点击编辑"
                            setOnPreferenceClickListener { showEditor(file); true }
                        })
                    }
                }
            })
        }
    }

    private fun showNameDialog() {
        val ctx = requireContext()
        val name = EditText(ctx).apply {
            hint = "例如 custom_tools"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle("脚本文件名")
            .setView(name)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("下一步", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val normalized = LuaScriptManager.normalizeName(name.text.toString())
                    check(!LuaScriptManager.directory.resolve(normalized).exists()) { "文件已存在" }
                    dialog.dismiss()
                    showEditor(null, normalized)
                } catch (e: Exception) {
                    name.error = e.message
                }
            }
        }
        dialog.show()
    }

    private fun showEditor(file: File?, newName: String = file?.name.orEmpty()) {
        val ctx = requireContext()
        val editor = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(false)
            minLines = 14
            setText(file?.readText() ?: LuaScriptManager.TEMPLATE)
            setSelection(text.length)
        }
        val container = LinearLayout(ctx).apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(editor, LinearLayout.LayoutParams(-1, -2))
        }
        val builder = AlertDialog.Builder(ctx)
            .setTitle(newName)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("保存", null)
        if (file != null) builder.setNeutralButton("删除", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    LuaScriptManager.save(newName, editor.text.toString())
                    FcitxDaemon.restartFcitx()
                    dialog.dismiss()
                    rebuild()
                    Toast.makeText(ctx, "已保存并重新加载 Lua", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                }
            }
            if (file != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(ctx)
                        .setTitle("删除 ${file.name}？")
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            try {
                                LuaScriptManager.delete(file)
                                FcitxDaemon.restartFcitx()
                                dialog.dismiss()
                                rebuild()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                            }
                        }.show()
                }
            }
        }
        dialog.show()
    }
}
