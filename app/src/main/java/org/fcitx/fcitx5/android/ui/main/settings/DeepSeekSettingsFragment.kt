package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import android.app.AlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.data.translation.DeepSeek
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment

class DeepSeekSettingsFragment : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(EditTextPreference(ctx).apply {
                key = "deepseek_key_editor"
                title = "API Key"
                summary = "保存在设备密钥库；留空可删除。文本只在点击翻译时发送。"
                isPersistent = false
                setOnBindEditTextListener { field ->
                    field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    field.setText("")
                }
                setOnPreferenceChangeListener { _, value ->
                    try { DeepSeek.saveKey(value.toString()); true }
                    catch (e: Exception) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show(); false }
                }
            })
            addPreference(Preference(ctx).apply {
                title = "模型"
                summary = DeepSeek.model.ifBlank { "点击读取可用模型" }
                setOnPreferenceClickListener {
                    lifecycleScope.launch {
                        isEnabled = false
                        try {
                            val models = DeepSeek.models()
                            check(models.isNotEmpty()) { "此账号没有可用模型" }
                            AlertDialog.Builder(ctx).setTitle("选择模型")
                                .setSingleChoiceItems(models.toTypedArray(), models.indexOf(DeepSeek.model)) { dialog, index ->
                                    DeepSeek.model = models[index]
                                    summary = models[index]
                                    dialog.dismiss()
                                }.setNegativeButton(android.R.string.cancel, null).show()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show() }
                        finally { isEnabled = true }
                    }
                    true
                }
            })
            addPreference(Preference(ctx).apply {
                title = "余额"
                summary = "点击查询"
                setOnPreferenceClickListener {
                    lifecycleScope.launch {
                        try { summary = DeepSeek.balance() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { summary = e.message }
                    }
                    true
                }
            })
        }
    }
}
