package org.fcitx.fcitx5.android.ui.main.settings

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import android.app.AlertDialog
import org.fcitx.fcitx5.android.input.voice.VoiceEngine
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.addPreference

class VoiceSettingsFragment : PaddingPreferenceFragment() {
    private lateinit var status: Preference
    private var recognizer: SpeechRecognizer? = null
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        status.summary = if (it) "麦克风权限已开启" else "未获得麦克风权限"
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            status = Preference(ctx).apply {
                title = "Samsung 与系统语音增强"
                summary = "通过系统公开语音服务在键盘内识别。Samsung 键盘私有模型只有在其提供公开服务时才能使用。"
            }
            addPreference(status)
            addPreference("麦克风权限") { microphone.launch(Manifest.permission.RECORD_AUDIO) }
            addPreference("识别服务") {
                val services = listOf("" to "系统离线语音") + VoiceEngine.services(ctx)
                AlertDialog.Builder(ctx).setTitle("选择公开语音服务")
                    .setSingleChoiceItems(services.map { it.second }.toTypedArray(), services.indexOfFirst { it.first == VoiceEngine.component }) { dialog, index ->
                        VoiceEngine.component = services[index].first
                        status.summary = "已选择 ${services[index].second}；扩展服务是否离线由服务实现决定"
                        dialog.dismiss()
                    }.show()
            }
            addPreference(EditTextPreference(ctx).apply {
                key = "voice_language_editor"
                isPersistent = false
                title = "识别语言"
                text = VoiceEngine.language
                summary = text
                dialogMessage = "例如 zh-CN、en-US、zh-HK"
                setOnPreferenceChangeListener { _, value ->
                    val language = value.toString().trim()
                    if (!Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*").matches(language)) false
                    else { VoiceEngine.language = language; summary = language; true }
                }
            })
            addPreference("检查已安装的离线语言") {
                if (Build.VERSION.SDK_INT < 33) status.summary = "此系统版本不提供模型查询接口"
                else try {
                    recognizer?.destroy()
                    recognizer = VoiceEngine.create(ctx).also {
                        it.checkRecognitionSupport(VoiceEngine.intent(), ContextCompat.getMainExecutor(ctx), object : RecognitionSupportCallback {
                            override fun onSupportResult(support: RecognitionSupport) {
                                status.summary = "已安装：${support.installedOnDeviceLanguages.joinToString()}\n可下载：${support.supportedOnDeviceLanguages.joinToString()}"
                            }
                            override fun onError(error: Int) { status.summary = "服务未返回模型信息（$error）" }
                        })
                    }
                } catch (e: Exception) { status.summary = e.message }
            }
            addPreference("请求下载所选语言的离线模型") {
                if (Build.VERSION.SDK_INT < 33) status.summary = "此系统版本不提供模型下载接口"
                else try {
                    recognizer?.destroy()
                    recognizer = VoiceEngine.create(ctx).also { it.triggerModelDownload(VoiceEngine.intent()) }
                    status.summary = "已向系统提交下载请求；完成后可再次检查已安装语言"
                } catch (e: Exception) { status.summary = e.message }
            }
        }
    }
    override fun onDestroyView() { recognizer?.destroy(); recognizer = null; super.onDestroyView() }
}
