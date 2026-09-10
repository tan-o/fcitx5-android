package org.fcitx.fcitx5.android.input.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.fcitx.fcitx5.android.utils.appContext

object VoiceEngine {
    private val prefs get() = appContext.getSharedPreferences("voice", 0)
    var component: String
        get() = prefs.getString("component", "")!!
        set(value) { prefs.edit().putString("component", value).apply() }
    var language: String
        get() = prefs.getString("language", "zh-CN")!!
        set(value) { prefs.edit().putString("language", value).apply() }
    fun services(context: Context): List<Pair<String, String>> = context.packageManager
        .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        .mapNotNull { result ->
            val service = result.serviceInfo
            if (!service.exported || !service.enabled || service.permission != "android.permission.BIND_SPEECH_RECOGNITION_SERVICE") null
            else ComponentName(service.packageName, service.name).flattenToString() to
                "${result.loadLabel(context.packageManager)} (${service.packageName})"
        }
    fun create(context: Context): SpeechRecognizer {
        if (component.isNotEmpty()) {
            check(services(context).any { it.first == component }) { "选中的语音服务未对其它应用开放" }
            return SpeechRecognizer.createSpeechRecognizer(context, ComponentName.unflattenFromString(component)!!)
        }
        check(Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { "系统未提供可调用的离线语音服务，请在语音设置中检查" }
        return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    }
    fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
}
