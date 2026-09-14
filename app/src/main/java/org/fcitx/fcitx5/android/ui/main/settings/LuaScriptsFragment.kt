/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.lua.LuaScriptManager
import java.io.File

class LuaScriptsFragment : Fragment() {
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
            override fun handleOnBackPressed() = showList()
        }
        showList()
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
    }

    private fun showList() {
        backCallback.isEnabled = false
        content.removeAllViews()
        val scriptPath = runCatching { LuaScriptManager.directory.absolutePath }
            .getOrDefault("Android/data/应用包名/files/data/lua/imeapi/extensions")
        content.addView(TextView(requireContext()).apply {
            text = "脚本由 fcitx5-lua 的 imeapi 加载。\n脚本目录：$scriptPath"
            setPadding(0, 0, 0, dp(12))
        })
        content.addView(Button(requireContext()).apply {
            text = "新建 Lua 脚本"
            setOnClickListener { showEditor(null) }
        }, LinearLayout.LayoutParams(-1, -2))

        runCatching { LuaScriptManager.list() }
            .onSuccess { files ->
                if (files.isEmpty()) {
                    content.addView(TextView(requireContext()).apply {
                        text = "暂无脚本"
                        setPadding(0, dp(18), 0, 0)
                    })
                } else files.forEach { file ->
                    content.addView(Button(requireContext()).apply {
                        isAllCaps = false
                        text = file.name
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setOnClickListener { showEditor(file) }
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            }
            .onFailure(::showFailurePage)
    }

    private fun showEditor(file: File?) {
        backCallback.isEnabled = true
        content.removeAllViews()
        val name = EditText(requireContext()).apply {
            hint = "脚本名，例如 custom_tools"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            setText(file?.name.orEmpty())
            isEnabled = file == null
        }
        val editor = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(false)
            minLines = 18
            gravity = Gravity.TOP or Gravity.START
            setText(runCatching { file?.readText() ?: LuaScriptManager.TEMPLATE }
                .getOrElse { "-- 读取失败：${it.message}" })
        }
        content.addView(name, LinearLayout.LayoutParams(-1, -2))
        content.addView(editor, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        content.addView(Button(requireContext()).apply {
            text = "保存并重新加载"
            setOnClickListener {
                val fileName = if (file == null) name.text.toString() else file.name
                saveAndReload(fileName, editor.text.toString())
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        if (file != null) {
            content.addView(Button(requireContext()).apply {
                text = "删除"
                setOnClickListener { deleteAndReload(file) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun saveAndReload(name: String, source: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { LuaScriptManager.save(name, source) }
                FcitxDaemon.restartFcitx()
            }.onSuccess {
                Toast.makeText(requireContext(), "已保存并重新加载 Lua", Toast.LENGTH_SHORT).show()
                showList()
            }.onFailure(::showError)
        }
    }

    private fun deleteAndReload(file: File) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { LuaScriptManager.delete(file) }
                FcitxDaemon.restartFcitx()
            }.onSuccess { showList() }.onFailure(::showError)
        }
    }

    private fun showFailurePage(error: Throwable) {
        content.addView(TextView(requireContext()).apply {
            text = "Lua 脚本目录不可用：${error.message ?: error.javaClass.simpleName}"
            setPadding(0, dp(18), 0, 0)
        })
    }

    private fun showError(error: Throwable) {
        Toast.makeText(requireContext(), error.message ?: "Lua 操作失败", Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
