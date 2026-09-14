package org.fcitx.fcitx5.android.ui.main.settings

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.work.WorkInfo
import androidx.work.WorkManager
import android.app.AlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.rime.RimeGitWorker
import org.fcitx.fcitx5.android.data.rime.RimeManager
import org.fcitx.fcitx5.android.data.rime.WanxiangModel
import org.fcitx.fcitx5.android.ui.common.PaddingPreferenceFragment
import org.fcitx.fcitx5.android.utils.addCategory
import org.fcitx.fcitx5.android.utils.addPreference
import java.io.File

class RimeSettingsFragment : PaddingPreferenceFragment() {
    private data class ProgressUi(
        val dialog: AlertDialog,
        val bar: ProgressBar,
        val message: TextView
    )

    private lateinit var status: Preference
    private lateinit var gitStatus: Preference
    private lateinit var repos: PreferenceCategory
    private lateinit var custom: PreferenceCategory
    private lateinit var modelStatus: Preference
    private lateinit var modelDownload: Preference
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

    private fun progressUi(title: String, button: String): ProgressUi {
        val ctx = requireContext()
        val message = TextView(ctx).apply {
            text = "准备中…"
            setPadding(0, 0, 0, dp(10))
        }
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(24)
            setPadding(padding, dp(12), padding, 0)
            addView(message, LinearLayout.LayoutParams(-1, -2))
            addView(bar, LinearLayout.LayoutParams(-1, dp(14)))
        }
        val dialog = AlertDialog.Builder(ctx).setTitle(title).setView(panel)
            .setNegativeButton(button, null).create()
        return ProgressUi(dialog, bar, message)
    }

    private fun showGitProgress(workName: String, title: String) {
        val ui = progressUi(title, "后台运行")
        ui.dialog.show()
        val observer = lifecycleScope.launch {
            WorkManager.getInstance(requireContext()).getWorkInfosForUniqueWorkFlow(workName)
                .collect { rows ->
                    val info = rows.firstOrNull { !it.state.isFinished } ?: rows.lastOrNull()
                    if (info == null) {
                        ui.message.text = "后台任务等待启动"
                        ui.bar.isIndeterminate = true
                        return@collect
                    }
                    if (!info.state.isFinished) {
                        val total = info.progress.getInt("total", 0)
                        val completed = info.progress.getInt("completed", 0)
                        ui.message.text = info.progress.getString("text") ?: "等待联网后开始"
                        ui.bar.isIndeterminate = total <= 0
                        if (total > 0) {
                            ui.bar.max = total
                            ui.bar.progress = completed.coerceIn(0, total)
                        }
                    } else {
                        ui.message.text = info.outputData.getString("text") ?: when (info.state) {
                            WorkInfo.State.SUCCEEDED -> "方案已部署并启用"
                            WorkInfo.State.CANCELLED -> "任务已取消"
                            else -> "任务失败"
                        }
                        ui.bar.isIndeterminate = false
                        ui.bar.max = 100
                        ui.bar.progress = if (info.state == WorkInfo.State.SUCCEEDED) 100 else 0
                        ui.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text = "关闭"
                        refresh()
                    }
                }
        }
        ui.dialog.setOnDismissListener { observer.cancel() }
    }

    private fun showModelDownload() {
        val ctx = requireContext()
        org.fcitx.fcitx5.android.data.download.ModelDownloadWorker.enqueue("wanxiang")
        val ui = progressUi("万象模型", "后台运行")
        var observer: Job? = null
        ui.dialog.setOnShowListener {
            observer = lifecycleScope.launch {
                WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow("model-wanxiang").collect { rows ->
                    val active = rows.firstOrNull { !it.state.isFinished }
                    if (active != null) {
                        val received = active.progress.getLong("received", 0)
                        val total = active.progress.getLong("total", 0)
                        ui.message.text = active.progress.getString("text") ?: "等待联网后开始"
                        ui.bar.isIndeterminate = total <= 0
                        if (total > 0) {
                            ui.bar.max = 1000
                            ui.bar.progress = (received * 1000 / total).toInt().coerceIn(0, 1000)
                        }
                    } else if (rows.isNotEmpty()) {
                        val failed = rows.firstOrNull {
                            it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
                        }
                        ui.message.text = if (WanxiangModel.installed) "模型已安装" else
                            failed?.outputData?.getString("text") ?: "下载失败，重新点击可续传"
                        ui.bar.isIndeterminate = false
                        ui.bar.max = 1000
                        ui.bar.progress = if (WanxiangModel.installed) 1000 else 0
                        refresh()
                    }
                }
            }
        }
        ui.dialog.setOnDismissListener { observer?.cancel() }
        ui.dialog.show()
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            status = Preference(ctx).apply { title = "Rime"; summary = "已内置；自定义设置保存在独立的 .custom.yaml 文件中" }
            addPreference(status)
            addPreference("Clone 方案仓库") {
                val input = EditText(ctx).apply { hint = "https://github.com/用户/仓库.git"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
                AlertDialog.Builder(ctx).setTitle("Clone 公开仓库").setView(input)
                    .setPositiveButton("Clone") { _, _ ->
                        runCatching { RimeGitWorker.enqueueClone(input.text.toString()) }
                            .onSuccess { showGitProgress(it, "Clone 并启用方案") }
                            .onFailure { status.summary = it.message }
                    }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
            gitStatus = Preference(ctx).apply {
                title = "方案后台任务"
                summary = "等待运行"
                isVisible = false
            }
            addPreference(gitStatus)
            addPreference("新建 .custom.yaml") {
                val input = EditText(ctx).apply { hint = "luna_pinyin.custom.yaml"; isSingleLine = true }
                AlertDialog.Builder(ctx).setTitle("文件名").setView(input)
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
                addPreference("语言模型参数", "按方案修改搭配长度和惩罚分数，保存到 .custom.yaml") {
                    val schemas = RimeManager.schemas()
                    AlertDialog.Builder(ctx).setTitle("选择已有方案")
                        .setItems(schemas.toTypedArray()) { _, index -> editGrammar(schemas[index]) }.show()
                }
                modelStatus = Preference(ctx).apply { title = "简体语言模型" }
                addPreference(modelStatus)
                modelDownload = Preference(ctx).apply {
                    setOnPreferenceClickListener {
                        showModelDownload()
                        true
                    }
                }
                addPreference(modelDownload)
                addPreference("为拼音方案启用／停用") {
                    val schemas = RimeManager.schemas()
                    AlertDialog.Builder(ctx).setTitle("选择已有方案").setItems(schemas.toTypedArray()) { _, i ->
                        AlertDialog.Builder(ctx).setTitle(schemas[i])
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
        lifecycleScope.launch {
            WorkManager.getInstance(ctx).getWorkInfosByTagFlow(RimeGitWorker.TAG).collect { rows ->
                val info = rows.firstOrNull { !it.state.isFinished } ?: rows.lastOrNull()
                gitStatus.isVisible = info != null
                if (info != null) {
                    val workName = RimeGitWorker.workName(info.tags)
                    gitStatus.summary = if (info.state.isFinished) {
                        info.outputData.getString("text") ?: when (info.state) {
                            WorkInfo.State.SUCCEEDED -> "方案已部署并启用"
                            WorkInfo.State.CANCELLED -> "任务已取消"
                            else -> "任务失败"
                        }
                    } else {
                        info.progress.getString("text") ?: "等待联网后开始"
                    }
                    gitStatus.setOnPreferenceClickListener {
                        if (workName != null) showGitProgress(workName, "Rime 方案后台任务")
                        true
                    }
                }
            }
        }
        lifecycleScope.launch {
            WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow("model-wanxiang").collect { rows ->
                val active = rows.firstOrNull { !it.state.isFinished }
                if (active != null) status.summary = active.progress.getString("text") ?: "后台任务等待运行／联网后续传"
                else if (rows.isNotEmpty()) {
                    refresh()
                    status.summary = rows.first().outputData.getString("text") ?: "任务已暂停；点击下载可续传"
                }
            }
        }
    }
    private fun refresh() {
        modelStatus.summary = if (WanxiangModel.installed) "已下载 · ${WanxiangModel.installedSize / 1024 / 1024} MiB；可为已有拼音方案启用" else "未下载；可选下载，不影响 Rime 正常使用，不替换输入方案"
        modelDownload.title = if (WanxiangModel.installed) "更新万象模型" else "下载万象模型"
        modelDownload.summary = "约 401 MiB，点击后下载并校验 SHA256；完成后为需要的方案启用"
        repos.removeAll()
        RimeManager.repositories.listFiles().orEmpty().filter { File(it, ".git").isDirectory }.sortedBy { it.name }.forEach { repo ->
            repos.addPreference(repo.name) {
                AlertDialog.Builder(requireContext()).setTitle(repo.name)
                    .setItems(arrayOf("部署此方案（保留 custom 文件）", "从远端更新", "删除仓库")) { _, index ->
                        when (index) {
                            0 -> runOperation { RimeManager.deploy(repo) }
                            1 -> runCatching { RimeGitWorker.enqueueUpdate(repo.name) }
                                .onSuccess { showGitProgress(it, "更新并重新部署") }
                                .onFailure { status.summary = it.message }
                            2 -> AlertDialog.Builder(requireContext()).setMessage("删除克隆的仓库？已部署的方案和自定义文件保留。")
                                .setPositiveButton("删除") { _, _ -> runOperation { withContext(Dispatchers.IO) { check(repo.deleteRecursively()) } } }
                                .setNegativeButton(android.R.string.cancel, null).show()
                        }
                    }.show()
            }
        }
        custom.removeAll()
        RimeManager.customFiles().forEach { file -> custom.addPreference(file.name) { edit(file) } }
    }
    private fun editGrammar(schema: String) {
        lifecycleScope.launch {
            try {
                val overrides = RimeManager.grammarOverrides(schema)
                val ctx = requireContext()
                val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
                panel.addView(TextView(ctx).apply { text = "留空表示不覆盖方案原值。下方提示为引擎默认值，并非方案当前生效值。" })
                val labels = listOf("最长搭配", "最短搭配", "搭配惩罚", "非搭配惩罚", "弱搭配惩罚", "尾部惩罚")
                val fields = RimeManager.grammarDefaults.entries.mapIndexed { index, (key, default) ->
                    panel.addView(TextView(ctx).apply { text = "${labels[index]} · $key" })
                    key to EditText(ctx).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or
                            (if (key.endsWith("_length")) 0 else InputType.TYPE_NUMBER_FLAG_DECIMAL)
                        hint = "引擎默认：$default"
                        setText(overrides[key].orEmpty())
                        panel.addView(this)
                    }
                }.toMap()
                val dialog = AlertDialog.Builder(ctx).setTitle(schema)
                    .setView(ScrollView(ctx).apply { addView(panel) })
                    .setPositiveButton("保存并部署", null).setNegativeButton(android.R.string.cancel, null).create()
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        lifecycleScope.launch {
                            try {
                                RimeManager.setGrammarOverrides(schema, fields.mapValues { it.value.text.toString().trim() })
                                dialog.dismiss()
                                runOperation { RimeManager.redeploy() }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { fields.values.first().error = e.message }
                        }
                    }
                }
                dialog.show()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status.summary = e.message }
        }
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
            val dialog = AlertDialog.Builder(requireContext()).setTitle(file.name)
                .setView(ScrollView(requireContext()).apply { addView(editor) })
                .setPositiveButton("保存", null).setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("删除") { _, _ ->
                    AlertDialog.Builder(requireContext()).setMessage("删除 ${file.name}？")
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
