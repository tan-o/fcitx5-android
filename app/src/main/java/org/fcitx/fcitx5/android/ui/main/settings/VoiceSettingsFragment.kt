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
import androidx.preference.SwitchPreferenceCompat
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.fcitx.fcitx5.android.input.voice.OfflineSpeechModel
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
            addPreference(SwitchPreferenceCompat(ctx).apply {
                title = "使用 Qwen3-ASR 本地模型"
                summary = "支持粤语、普通话和英文；关闭后使用下方选择的系统公开服务"
                isPersistent = false
                isChecked = VoiceEngine.local
                setOnPreferenceChangeListener { _, value -> VoiceEngine.local = value as Boolean; true }
            })
            addPreference(EditTextPreference(ctx).apply {
                key = "voice_hotwords_editor"
                title = "语音模型提示词／热词"
                dialogMessage = "填写人名、地名或专业词语，帮助模型转写；此接口提供热词上下文，不是聊天系统提示词。"
                isPersistent = false
                text = VoiceEngine.hotwords
                summary = text?.ifBlank { "未设置" }
                setOnPreferenceChangeListener { _, value ->
                    val words = value.toString().trim()
                    if (words.length > 2000) false else { VoiceEngine.hotwords = words; summary = words.ifBlank { "未设置" }; true }
                }
            })
            addPreference("下载 Qwen3-ASR 0.6B INT8", "${if (OfflineSpeechModel.installed) "已安装" else "未安装"} · 下载约 838 MiB，解压约 940 MiB；需要 2 GiB 可用空间") {
                lifecycleScope.launch {
                    val message = TextView(ctx).apply { text = "准备下载…" }
                    val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
                    val panel = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(32, 24, 32, 24)
                        addView(message)
                        addView(progress, LinearLayout.LayoutParams(-1, -2))
                    }
                    val task = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                    val dialog = AlertDialog.Builder(ctx).setTitle("Qwen3-ASR").setView(panel)
                        .setNegativeButton("取消") { _, _ -> task?.cancel() }.create()
                    dialog.setCanceledOnTouchOutside(false)
                    dialog.setOnCancelListener { task?.cancel() }
                    dialog.show()
                    preferenceScreen.isEnabled = false
                    try {
                        OfflineSpeechModel.download { received, total, label ->
                            progress.isIndeterminate = total == 0L
                            if (total > 0) progress.progress = (received * 100 / total).toInt()
                            message.text = if (total > 0) "$label ${progress.progress}%\n${received / 1024 / 1024}/${total / 1024 / 1024} MiB" else label
                        }
                        status.summary = "Qwen3-ASR 已安装，可在键盘中直接使用"
                    } catch (e: CancellationException) { status.summary = "下载已取消"; throw e }
                    catch (e: Exception) { status.summary = e.message }
                    finally { dialog.dismiss(); preferenceScreen.isEnabled = true }
                }
            }
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
