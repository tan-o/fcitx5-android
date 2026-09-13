package org.fcitx.fcitx5.android.input.translation

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.translation.DeepSeek
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import splitties.dimensions.dp

class TranslationBar(private val service: FcitxInputMethodService, private val theme: Theme) {
    private var task: Job? = null
    private var balanceJob: Job? = null
    private var target: KeyboardTextTarget? = null
    private fun button(label: String) = TextView(service).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 16f
        setTextColor(theme.keyTextColor)
        setPadding(dp(8), 0, dp(8), 0)
    }
    private val language = button(DeepSeek.language).apply {
        setOnClickListener {
            PopupMenu(service, this).apply {
                listOf("English", "简体中文", "繁體中文", "日本語", "한국어", "Français", "Deutsch", "Español").forEach { lang ->
                    menu.add(lang).setOnMenuItemClickListener {
                        DeepSeek.language = lang
                        text = lang
                        true
                    }
                }
                show()
            }
        }
    }
    private val source = EditText(service).apply {
        hint = "输入要翻译的文本"
        setTextColor(theme.keyTextColor)
        setHintTextColor(theme.altKeyTextColor)
        textSize = 16f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        showSoftInputOnFocus = false
        maxLines = 2
    }
    private val balance = button("…").apply { textSize = 11f; setOnClickListener { refreshBalance() } }
    private val translate = button("译").apply { setOnClickListener { translate() } }
    private val row = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        minimumHeight = service.dp(48)
        gravity = Gravity.CENTER_VERTICAL
        addView(language, LinearLayout.LayoutParams(-2, -1))
        addView(source, LinearLayout.LayoutParams(0, -1, 1f))
        addView(balance, LinearLayout.LayoutParams(-2, -1))
        addView(translate, LinearLayout.LayoutParams(service.dp(40), -1))
        addView(button("×").apply { setOnClickListener { close() } }, LinearLayout.LayoutParams(service.dp(40), -1))
    }
    val root = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setBackgroundColor(theme.keyboardColor)
        addView(row, LinearLayout.LayoutParams(-1, service.dp(48)))
    }
    fun toggle() {
        if (root.visibility == View.VISIBLE) {
            close()
            return
        }
        translate.isEnabled = true
        root.visibility = View.VISIBLE
        service.requestInputLayout()
        service.closeTranslation = { close() }
        source.setText("")
        source.requestFocus()
        service.finishComposing()
        task = service.lifecycleScope.launch {
            try {
                service.postFcitxJob { focusOutIn() }.join()
                if (root.visibility != View.VISIBLE) return@launch
                target = KeyboardTextTarget(source).also { service.keyboardTextTarget = it }
                if (!DeepSeek.configured) {
                    Toast.makeText(service, "请先在应用设置的“DeepSeek 翻译”中填写 API Key 并选择模型。", Toast.LENGTH_LONG).show()
                } else {
                    refreshBalance()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(service, e.message ?: "输入面板初始化失败", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun refreshBalance() {
        balanceJob?.cancel()
        balanceJob = service.lifecycleScope.launch {
            try { balance.text = DeepSeek.balance() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { balance.text = "余额重试" }
        }
    }
    private fun translate() {
        val text = source.text.toString().trim()
        val language = DeepSeek.language
        if (text.isEmpty() || task?.isActive == true) return
        translate.isEnabled = false
        task = service.lifecycleScope.launch {
            try {
                val output = DeepSeek.translate(text, language)
                if (source.text.toString().trim() != text || DeepSeek.language != language) {
                    Toast.makeText(service, "原文或目标语言已变化，请重新翻译", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                service.postFcitxJob { focusOutIn() }.join()
                val previousTarget = target ?: return@launch
                if (service.keyboardTextTarget !== previousTarget) return@launch
                service.keyboardTextTarget = null
                service.commitText(output)
                source.text.clear()
                target = KeyboardTextTarget(source).also { service.keyboardTextTarget = it }
                refreshBalance()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Toast.makeText(service, e.message ?: "翻译失败", Toast.LENGTH_LONG).show()
            }
            finally { translate.isEnabled = true }
        }
    }
    fun close() {
        if (root.visibility != View.VISIBLE && target == null) return
        task?.cancel()
        balanceJob?.cancel()
        service.closeTranslation = null
        root.visibility = View.GONE
        service.requestInputLayout()
        val previous = target
        target = null
        service.lifecycleScope.launch {
            service.postFcitxJob { focusOutIn() }.join()
            if (service.keyboardTextTarget === previous) service.keyboardTextTarget = null
        }
    }
}
