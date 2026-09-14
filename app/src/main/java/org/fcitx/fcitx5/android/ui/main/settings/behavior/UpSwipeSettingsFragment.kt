/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.behavior

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.lua.LuaScriptManager
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyGestureActions

class UpSwipeSettingsFragment : Fragment() {
    private data class ActionEditor(
        val root: LinearLayout,
        val type: Spinner,
        val character: EditText,
        val function: Spinner,
        val displayName: EditText,
        val argument: EditText,
        val remove: Button,
        val functionOffset: Int
    )

    private lateinit var content: LinearLayout
    private lateinit var backCallback: OnBackPressedCallback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scroll = ScrollView(requireContext()).apply {
            isFillViewport = true
        }
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(20)
            setPadding(padding, dp(12), padding, dp(24))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = showKeyList()
        }
        showKeyList()
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
    }

    private fun showKeyList() {
        backCallback.isEnabled = false
        content.removeAllViews()
        content.addView(TextView(requireContext()).apply {
            text = getString(R.string.gesture_action_syntax)
            setPadding(0, 0, 0, dp(12))
        })
        KeyGestureActions.keys.forEach { key ->
            content.addView(Button(requireContext()).apply {
                isAllCaps = false
                text = summaryFor(key)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { showEditor(key) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun summaryFor(key: String): String = runCatching {
        val up = KeyGestureActions.upSwipe(requireContext(), key)?.label ?: "关闭"
        "$key  ·  上划：$up  ·  长按：${KeyGestureActions.longPress(requireContext(), key).size} 项"
    }.getOrElse { "$key  ·  配置读取失败" }

    private fun showEditor(key: String) {
        backCallback.isEnabled = true
        content.removeAllViews()
        content.addView(TextView(requireContext()).apply {
            text = "${key.uppercase()} 键"
            textSize = 22f
            setPadding(0, 0, 0, dp(12))
        })
        val functions = runCatching { LuaScriptManager.functions() }.getOrDefault(emptyList())
        content.addView(label("上划动作"))
        val upSwipe = createActionEditor(
            KeyGestureActions.parse(KeyGestureActions.upSwipeSpec(requireContext(), key)),
            functions,
            removable = false
        )
        content.addView(upSwipe.root, LinearLayout.LayoutParams(-1, -2))

        content.addView(label("长按候选"))
        val longPressContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val longPressEditors = mutableListOf<ActionEditor>()
        fun addLongPress(entry: KeyGestureActions.Entry?) {
            val editor = createActionEditor(entry, functions, removable = true)
            editor.remove.setOnClickListener {
                longPressEditors.remove(editor)
                longPressContainer.removeView(editor.root)
            }
            longPressEditors += editor
            longPressContainer.addView(editor.root, LinearLayout.LayoutParams(-1, -2))
        }
        KeyGestureActions.longPress(requireContext(), key).forEach(::addLongPress)
        content.addView(longPressContainer, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(requireContext()).apply {
            text = "＋ 添加一项"
            setOnClickListener { addLongPress(null) }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(requireContext()).apply {
            val path = runCatching { LuaScriptManager.directory.absolutePath }
                .getOrDefault("data/lua/imeapi/extensions")
            text = "函数来自 $path 中的全局 function。"
            textSize = 12f
            setPadding(0, dp(4), 0, dp(8))
        })
        content.addView(Button(requireContext()).apply {
            text = "保存"
            setOnClickListener {
                runCatching {
                    KeyGestureActions.save(
                        requireContext(),
                        key,
                        encode(upSwipe, functions),
                        longPressEditors.joinToString("\n") { encode(it, functions) }
                    )
                }.onSuccess {
                    Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                    showKeyList()
                }.onFailure(::showError)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    private fun createActionEditor(
        entry: KeyGestureActions.Entry?,
        functions: List<LuaScriptManager.FunctionInfo>,
        removable: Boolean
    ): ActionEditor {
        val currentLua = entry?.action as? KeyAction.LuaAction
        val missingCurrent = currentLua?.takeIf { current ->
            functions.none { it.name == current.function }
        }
        val type = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("字符", "Lua 函数")
            )
        }
        val character = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            hint = "输入字符或文本"
            setText((entry?.action as? KeyAction.CommitAction)?.text.orEmpty())
        }
        val functionOffset = if (missingCurrent == null) 0 else 1
        val function = Spinner(requireContext()).apply {
            val labels = buildList {
                if (missingCurrent != null) add("${missingCurrent.function}  ·  未检测到")
                addAll(functions.map { "${it.name}  ·  ${it.file.name}" })
            }
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                labels.ifEmpty { listOf("未识别到可调用函数") }
            )
            isEnabled = labels.isNotEmpty()
            val selected = functions.indexOfFirst { it.name == currentLua?.function }
            if (selected >= 0) setSelection(selected + functionOffset)
        }
        val value = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val remove = Button(requireContext()).apply {
            text = "删除"
            visibility = if (removable) View.VISIBLE else View.GONE
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(type, LinearLayout.LayoutParams(0, -2, 0.8f))
            addView(value, LinearLayout.LayoutParams(0, -2, 1.5f))
            addView(remove, LinearLayout.LayoutParams(-2, -2))
        }
        val argument = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            hint = "函数参数（可选）"
            setText(currentLua?.argument.orEmpty())
        }
        val displayName = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            hint = "显示名称（留空使用函数名）"
            if (currentLua != null) setText(entry?.label.orEmpty())
        }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(row, LinearLayout.LayoutParams(-1, -2))
            addView(displayName, LinearLayout.LayoutParams(-1, -2))
            addView(argument, LinearLayout.LayoutParams(-1, -2))
            setPadding(0, 0, 0, dp(6))
        }
        fun renderType(position: Int) {
            value.removeAllViews()
            if (position == 0) {
                value.addView(character, LinearLayout.LayoutParams(-1, -2))
                displayName.visibility = View.GONE
                argument.visibility = View.GONE
            } else {
                value.addView(function, LinearLayout.LayoutParams(-1, -2))
                displayName.visibility = View.VISIBLE
                argument.visibility = View.VISIBLE
            }
        }
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderType(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        type.setSelection(if (currentLua == null) 0 else 1)
        renderType(type.selectedItemPosition)
        return ActionEditor(
            root, type, character, function, displayName, argument, remove, functionOffset
        )
    }

    private fun encode(
        editor: ActionEditor,
        functions: List<LuaScriptManager.FunctionInfo>
    ): String {
        if (editor.type.selectedItemPosition == 0) return editor.character.text.toString()
        val selected = editor.function.selectedItemPosition - editor.functionOffset
        check(selected in functions.indices) { "所选 Lua 函数未在脚本中检测到" }
        val name = functions[selected].name
        val displayName = editor.displayName.text.toString().trim().ifEmpty { name }
        require('=' !in displayName && '\n' !in displayName) { "显示名称不能包含等号或换行" }
        val argument = editor.argument.text.toString()
        return "$displayName=lua:$name${if (argument.isEmpty()) "" else ":$argument"}"
    }

    private fun label(value: String) = TextView(requireContext()).apply {
        text = value
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun showError(error: Throwable) {
        Toast.makeText(requireContext(), error.message ?: "操作失败", Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
