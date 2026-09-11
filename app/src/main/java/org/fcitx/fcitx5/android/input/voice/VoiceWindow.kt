package org.fcitx.fcitx5.android.input.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.utils.AppUtil

class VoiceWindow : InputWindow.ExtendedInputWindow<VoiceWindow>(), RecognitionListener {
    private val service by manager.inputMethodService()
    private val theme by manager.theme()
    private var recognizer: SpeechRecognizer? = null
    private var localSession: OfflineVoiceSession? = null
    private var active = false
    private var result = ""
    private lateinit var preview: TextView
    override val title = "语音输入"
    override fun onCreateView(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        preview = TextView(context).apply { textSize = 20f; gravity = Gravity.CENTER; setTextColor(theme.keyTextColor) }
        addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(LinearLayout(context).apply {
            listOf("开始" to { start() }, "停止" to { localSession?.finishRecording(); recognizer?.stopListening(); Unit }, "插入" to {
                if (result.isNotEmpty()) { service.commitText(result); result = ""; preview.text = "" }
            }, "设置" to { AppUtil.launchMainToVoice(context) }).forEach { (label, action) ->
                addView(Button(context).apply { text = label; setOnClickListener { action() } }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        })
    }
    private fun start() {
        active = true
        service.stopVoiceInput = { stop() }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            preview.text = "请在语音设置中允许麦克风权限"
            return
        }
        result = ""
        recognizer?.destroy()
        recognizer = null
        localSession?.cancel()
        localSession = null
        if (VoiceEngine.local) {
            localSession = OfflineVoiceSession(service.lifecycleScope,
                { if (active) preview.text = it },
                { if (active) { result = it; preview.text = it } })
            return
        }
        try {
            recognizer = VoiceEngine.create(context).also {
                it.setRecognitionListener(this)
                it.startListening(VoiceEngine.intent())
            }
            preview.text = "正在连接语音服务…"
        } catch (e: Exception) { preview.text = e.message }
    }
    override fun onAttached() { active = true; start() }
    private fun stop() { active = false; localSession?.cancel(); localSession = null; recognizer?.cancel(); recognizer?.destroy(); recognizer = null; result = ""; service.stopVoiceInput = null }
    override fun onDetached() { stop() }
    override fun onReadyForSpeech(params: Bundle?) { if (active) preview.text = "正在聆听…" }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { if (active) preview.text = "正在识别…" }
    override fun onError(error: Int) {
        if (active) preview.text = when (error) {
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "此语言的离线模型尚未下载，请打开语音设置"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "此服务不支持所选语言"
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有识别到内容，点击开始重试"
            else -> "语音服务错误 $error，点击开始重试"
        }
    }
    override fun onResults(results: Bundle?) {
        if (!active) return
        result = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        preview.text = result
    }
    override fun onPartialResults(partialResults: Bundle?) {
        if (active) preview.text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
