package org.fcitx.fcitx5.android.input.translation

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import android.widget.PopupWindow
import android.graphics.drawable.ColorDrawable
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.translation.DeepSeek
import org.fcitx.fcitx5.android.data.translation.Maimemo
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.AppUtil
import splitties.dimensions.dp

class TranslationBar(private val service: FcitxInputMethodService, private val theme: Theme) {
    private var task: Job? = null
    private var balanceJob: Job? = null
    private var target: KeyboardTextTarget? = null
    private var lookupMode = false
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
        addView(button("⚙").apply { setOnClickListener {
            try { if (lookupMode) AppUtil.launchMainToLookup(service) else AppUtil.launchMainToTranslation(service) }
            catch (e: Exception) { Toast.makeText(service, e.message, Toast.LENGTH_LONG).show() }
        } }, LinearLayout.LayoutParams(service.dp(36), -1))
        addView(button("×").apply { setOnClickListener { close() } }, LinearLayout.LayoutParams(service.dp(40), -1))
    }
    private val result = TextView(service).apply {
        setTextColor(theme.keyTextColor)
        textSize = 16f
        setTextIsSelectable(true)
        setPadding(service.dp(16), service.dp(8), service.dp(16), service.dp(8))
    }
    private val results = ScrollView(service).apply { addView(result); visibility = View.GONE }
    private val lookupPopup = PopupWindow(results, -1, service.dp(180), false).apply {
        setBackgroundDrawable(ColorDrawable(theme.keyboardColor))
        elevation = service.dp(8).toFloat()
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
    }
    private fun showLookupResult(text: String) {
        result.text = text
        results.visibility = View.VISIBLE
        row.post {
            if (root.visibility != View.VISIBLE || row.windowToken == null || !lookupMode) return@post
            val position = IntArray(2)
            row.getLocationInWindow(position)
            lookupPopup.width = row.width
            val y = maxOf(0, position[1] - service.dp(180))
            try {
                if (lookupPopup.isShowing) lookupPopup.update(position[0], y, row.width, service.dp(180))
                else lookupPopup.showAtLocation(row, Gravity.TOP or Gravity.LEFT, position[0], y)
            } catch (e: android.view.WindowManager.BadTokenException) {
                Toast.makeText(service, text, Toast.LENGTH_LONG).show()
            }
        }
    }
    val root = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setBackgroundColor(theme.keyboardColor)
        addView(row, LinearLayout.LayoutParams(-1, service.dp(48)))
    }
    init { source.doAfterTextChanged { lookupPopup.dismiss() } }
    fun toggle(lookup: Boolean = false) {
        if (root.visibility == View.VISIBLE) {
            val sameMode = lookupMode == lookup
            close()
            if (sameMode) return
        }
        lookupMode = lookup
        if (!lookup && !DeepSeek.configured) {
            AppUtil.launchMainToTranslation(service)
            return
        }
        language.visibility = if (lookup) View.GONE else View.VISIBLE
        balance.visibility = if (lookup) View.GONE else View.VISIBLE
        source.hint = if (lookup) "输入要查询的单词" else "输入要翻译的文本"
        translate.text = if (lookup) "查" else "译"
        translate.isEnabled = true
        results.visibility = View.GONE
        root.visibility = View.VISIBLE
        service.closeTranslation = { close() }
        source.setText("")
        source.requestFocus()
        service.finishComposing()
        task = service.lifecycleScope.launch {
            service.postFcitxJob { focusOutIn() }.join()
            if (root.visibility != View.VISIBLE) return@launch
            target = KeyboardTextTarget(source).also { service.keyboardTextTarget = it }
            if (!lookupMode) refreshBalance()
            else if (!Maimemo.configured) showLookupResult("点击 ⚙ 设置墨墨 API Token 后即可查词。")
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
                if (lookupMode) {
                    val output = Maimemo.lookup(text)
                    if (source.text.toString().trim() == text && service.keyboardTextTarget === target) {
                        showLookupResult(output)
                    }
                    return@launch
                }
                val output = DeepSeek.translate(text, language)
                if (source.text.toString().trim() != text || DeepSeek.language != language) {
                    Toast.makeText(service, "原文或目标语言已变化，请重新翻译", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                service.postFcitxJob { focusOutIn() }.join()
                if (service.keyboardTextTarget !== target) return@launch
                service.keyboardTextTarget = null
                target = null
                root.visibility = View.GONE
                service.closeTranslation = null
                service.commitText(output)
                refreshBalance()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (lookupMode) showLookupResult(e.message ?: "查询失败")
                else Toast.makeText(service, e.message, Toast.LENGTH_LONG).show()
            }
            finally { translate.isEnabled = true }
        }
    }
    fun close() {
        lookupPopup.dismiss()
        if (root.visibility != View.VISIBLE && target == null) return
        task?.cancel()
        balanceJob?.cancel()
        service.closeTranslation = null
        root.visibility = View.GONE
        val previous = target
        target = null
        service.lifecycleScope.launch {
            service.postFcitxJob { focusOutIn() }.join()
            if (service.keyboardTextTarget === previous) service.keyboardTextTarget = null
        }
    }
}
