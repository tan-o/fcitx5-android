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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import org.fcitx.fcitx5.android.R
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
        val up = KeyGestureActions.upSwipeSpec(requireContext(), key).ifBlank { "关闭" }
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
        val upSwipe = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            setText(KeyGestureActions.upSwipeSpec(requireContext(), key))
        }
        content.addView(upSwipe, LinearLayout.LayoutParams(-1, -2))
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
                    KeyGestureActions.save(
                        requireContext(), key, upSwipe.text.toString(), longPress.text.toString()
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
