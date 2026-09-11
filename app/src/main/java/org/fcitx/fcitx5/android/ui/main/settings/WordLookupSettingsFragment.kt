package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import org.fcitx.fcitx5.android.data.translation.Maimemo
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment

class WordLookupSettingsFragment : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(EditTextPreference(ctx).apply {
                key = "maimemo_token_editor"
                title = "墨墨开放 API Token"
                summary = "在墨墨：我的 → 更多设置 → 实验功能 → 开放 API 获取。仅点击查词时发送查询。"
                isPersistent = false
                setOnBindEditTextListener { field ->
                    field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    field.setText("")
                }
                setOnPreferenceChangeListener { _, value ->
                    try { Maimemo.saveToken(value.toString()); true }
                    catch (e: Exception) { Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show(); false }
                }
            })
            addPreference(Preference(ctx).apply {
                title = "开放接口范围"
                summary = "可查单词和自己创建的释义；墨墨官方 API 未开放完整词典释义。留空保存 Token 可移除授权。"
                isSelectable = false
            })
        }
    }
}
