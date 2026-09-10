package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.rime.RimeManager
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import java.io.File

class RimeSettingsFragment : PaddingPreferenceFragment() {
    private lateinit var status: Preference
    private lateinit var repos: PreferenceCategory
    private lateinit var custom: PreferenceCategory
    private fun runOperation(block: suspend () -> Unit) {
        lifecycleScope.launch {
            preferenceScreen.isEnabled = false
            status.summary = "处理中…"
            try { block(); status.summary = "完成"; refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status.summary = e.message ?: e.javaClass.simpleName }
            finally { preferenceScreen.isEnabled = true }
        }
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            status = Preference(ctx).apply { title = "Rime"; summary = "已内置；自定义设置保存在独立的 .custom.yaml 文件中" }
            addPreference(status)
            addPreference("Clone 方案仓库") {
                val input = EditText(ctx).apply { hint = "https://github.com/用户/仓库.git"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
                MaterialAlertDialogBuilder(ctx).setTitle("Clone 公开仓库").setView(input)
                    .setPositiveButton("Clone") { _, _ -> runOperation { RimeManager.clone(input.text.toString()) } }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
            addPreference("新建 .custom.yaml") {
                val input = EditText(ctx).apply { hint = "luna_pinyin.custom.yaml"; isSingleLine = true }
                MaterialAlertDialogBuilder(ctx).setTitle("文件名").setView(input)
                    .setPositiveButton("创建") { _, _ ->
                        runOperation {
                            val file = RimeManager.customFile(input.text.toString().trim())
                            check(!file.exists()) { "文件已存在" }
                            RimeManager.saveCustom(file.name, "patch: {}\n")
                            edit(file)
                        }
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
            addPreference("重新部署", "保存修改后重新加载 Rime") { runOperation { RimeManager.redeploy() } }
            addCategory("万象语言模型") {
                addPreference("内置简体模型", "wanxiang-lts-zh-hans · 只加载模型，不替换方案")
                addPreference("更新万象模型", "只更新简体语言模型，并校验上游 SHA256") {
                    runOperation { org.fcitx.fcitx5.android.data.rime.WanxiangModel.update { status.summary = it } }
                }
                addPreference("为拼音方案启用／停用") {
                    val schemas = RimeManager.schemas()
                    MaterialAlertDialogBuilder(ctx).setTitle("选择已有方案").setItems(schemas.toTypedArray()) { _, i ->
                        MaterialAlertDialogBuilder(ctx).setTitle(schemas[i])
                            .setItems(arrayOf("启用万象模型", "停用语言模型")) { _, action ->
                                runOperation { RimeManager.setModel(schemas[i], action == 0); RimeManager.redeploy() }
                            }.show()
                    }.show()
                }
            }
            repos = PreferenceCategory(ctx).apply { title = "方案仓库" }
            addPreference(repos)
            custom = PreferenceCategory(ctx).apply { title = "自定义补丁" }
            addPreference(custom)
        }
        refresh()
    }
    private fun refresh() {
        repos.removeAll()
        RimeManager.repositories.listFiles().orEmpty().filter { File(it, ".git").isDirectory }.sortedBy { it.name }.forEach { repo ->
            repos.addPreference(repo.name) {
                MaterialAlertDialogBuilder(requireContext()).setTitle(repo.name)
                    .setItems(arrayOf("部署此方案（保留 custom 文件）", "从远端更新", "删除仓库")) { _, index ->
                        when (index) {
                            0 -> runOperation { RimeManager.deploy(repo) }
                            1 -> runOperation { RimeManager.update(repo) }
                            2 -> MaterialAlertDialogBuilder(requireContext()).setMessage("删除克隆的仓库？已部署的方案和自定义文件保留。")
                                .setPositiveButton("删除") { _, _ -> runOperation { withContext(Dispatchers.IO) { check(repo.deleteRecursively()) } } }
                                .setNegativeButton(android.R.string.cancel, null).show()
                        }
                    }.show()
            }
        }
        custom.removeAll()
        RimeManager.customFiles().forEach { file -> custom.addPreference(file.name) { edit(file) } }
    }
    private fun edit(file: File) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { file.readText() }
            val editor = EditText(requireContext()).apply {
                setText(text)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 14f
                minLines = 10
                gravity = android.view.Gravity.TOP
            }
            val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(file.name)
                .setView(ScrollView(requireContext()).apply { addView(editor) })
                .setPositiveButton("保存", null).setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("删除") { _, _ ->
                    MaterialAlertDialogBuilder(requireContext()).setMessage("删除 ${file.name}？")
                        .setPositiveButton("删除") { _, _ -> runOperation { withContext(Dispatchers.IO) { check(file.delete()) } } }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }.create()
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    lifecycleScope.launch {
                        try { RimeManager.saveCustom(file.name, editor.text.toString()); dialog.dismiss(); refresh() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { editor.error = e.message }
                    }
                }
            }
            dialog.show()
        }
    }
}
