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
        content.addView(label("上划动作"))
        val current = KeyGestureActions.parse(KeyGestureActions.upSwipeSpec(requireContext(), key))
        val currentLua = current?.action as? KeyAction.LuaAction
        val functions = runCatching { LuaScriptManager.functions() }.getOrDefault(emptyList())
        val missingCurrentFunction = currentLua?.takeIf { current ->
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
            hint = "输入字符或文本；留空关闭"
            if (currentLua == null) setText((current?.action as? KeyAction.CommitAction)?.text.orEmpty())
        }
        val function = Spinner(requireContext()).apply {
            val labels = buildList {
                if (missingCurrentFunction != null) add("${missingCurrentFunction.function}  ·  未检测到")
                addAll(functions.map { "${it.name}  ·  ${it.file.name}" })
            }
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                labels.ifEmpty { listOf("未识别到可调用函数") }
            )
            isEnabled = labels.isNotEmpty()
            val selected = functions.indexOfFirst { it.name == currentLua?.function }
            if (selected >= 0) setSelection(selected + if (missingCurrentFunction == null) 0 else 1)
        }
        val value = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(type, LinearLayout.LayoutParams(0, -2, 0.8f))
            addView(value, LinearLayout.LayoutParams(0, -2, 1.6f))
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
        val argument = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            hint = "函数参数（可选）"
            setText(currentLua?.argument.orEmpty())
        }
        content.addView(argument, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(requireContext()).apply {
            val path = runCatching { LuaScriptManager.directory.absolutePath }
                .getOrDefault("data/lua/imeapi/extensions")
            text = "函数来自 $path 中的全局 function。"
            textSize = 12f
            setPadding(0, dp(4), 0, dp(8))
        })
        fun renderType(position: Int) {
            value.removeAllViews()
            if (position == 0) {
                value.addView(character, LinearLayout.LayoutParams(-1, -2))
                argument.visibility = View.GONE
            } else {
                value.addView(function, LinearLayout.LayoutParams(-1, -2))
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
        content.addView(label("长按候选（每行一项）"))
        val longPress = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(false)
            minLines = 10
            gravity = Gravity.TOP or Gravity.START
            setText(KeyGestureActions.longPressSpec(requireContext(), key))
        }
        content.addView(longPress, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(requireContext()).apply {
            text = "保存"
            setOnClickListener {
                runCatching {
                    val upSwipe = if (type.selectedItemPosition == 0) {
                        character.text.toString()
                    } else {
                        val selected = function.selectedItemPosition - if (missingCurrentFunction == null) 0 else 1
                        check(selected >= 0 && selected < functions.size) { "所选 Lua 函数未在脚本中检测到" }
                        val name = functions[selected].name
                        val arg = argument.text.toString()
                        "$name=lua:$name${if (arg.isEmpty()) "" else ":$arg"}"
                    }
                    KeyGestureActions.save(
                        requireContext(), key, upSwipe, longPress.text.toString()
                    )
                }.onSuccess {
                    Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                    showKeyList()
                }.onFailure(::showError)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
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
