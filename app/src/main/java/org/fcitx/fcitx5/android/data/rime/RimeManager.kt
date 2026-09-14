package org.fcitx.fcitx5.android.data.rime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand.FastForwardMode
import org.eclipse.jgit.util.FS
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.core.data.DataManager
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.utils.appContext
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor
import timber.log.Timber
import java.io.File
import java.net.URI
import java.security.MessageDigest

object RimeManager {
    private const val CALCULATOR_TRANSLATOR = "lua_translator@*fcitx_calculator"
    private const val CALCULATOR_TRANSLATORS_PATCH = "engine/translators/+"
    private const val CALCULATOR_PATTERN_PATCH = "recognizer/patterns/fcitx_calculator"
    private val lock = Mutex()
    val repositories = File(appContext.filesDir, "rime-repositories").apply { mkdirs() }
    val userDir: File get() = File(requireNotNull(FcitxApplication.getInstance().directBootAwareContext.getExternalFilesDir(null)), "data/rime").apply { mkdirs() }
    private val sharedDir get() = File(DataManager.dataDir, "usr/share/rime-data")
    private fun yaml() = Yaml(SafeConstructor(LoaderOptions().apply { codePointLimit = 2_000_000; isAllowDuplicateKeys = false }))
    suspend fun clone(url: String): File = withContext(Dispatchers.IO) {
        lock.withLock {
            val uri = URI(url.trim())
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "请输入公开仓库的 HTTPS Git 地址" }
            FS.DETECTED.setUserHome(appContext.filesDir)
            val name = uri.path.substringAfterLast('/').removeSuffix(".git").replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
            val dir = File(repositories, "$name-$hash")
            check(!dir.exists()) { "仓库已存在" }
            try { Git.cloneRepository().setURI(url.trim()).setDirectory(dir).setTimeout(120).call().close() }
            catch (e: Exception) { dir.deleteRecursively(); throw e }
            dir
        }
    }
    suspend fun update(repo: File) = withContext(Dispatchers.IO) {
        lock.withLock {
            Git.open(repo).use { git ->
                check(git.status().call().isClean) { "仓库有未提交的修改，请先处理；个人设置应写入 custom 文件" }
                check(git.pull().setFastForward(FastForwardMode.FF_ONLY).setTimeout(120).call().isSuccessful) { "更新失败：远端分支已分叉" }
            }
        }
    }
    suspend fun deploy(repo: File) = withContext(Dispatchers.IO) {
        lock.withLock {
            val root = repo.canonicalFile
            check(root.parentFile == repositories.canonicalFile)
            val files = root.walkTopDown().onEnter {
                check(!java.nio.file.Files.isSymbolicLink(it.toPath())) { "方案不能包含目录链接" }
                it == root || !it.name.startsWith('.')
            }.filter { it.isFile && !it.name.endsWith(".custom.yaml") && !it.name.startsWith('.') }.toList()
            check(files.any { it.parentFile == root && it.name.endsWith(".schema.yaml") }) { "仓库根目录中没有 Rime schema 文件" }
            files.forEach { file ->
                check(file.canonicalPath.startsWith(root.path + File.separator)) { "方案包含目录外的链接" }
            }
            // The engine is restarted only after complete files have been installed.
            files.forEach { source ->
                val destination = File(userDir, source.relativeTo(root).path)
                check(destination.canonicalPath.startsWith(userDir.canonicalPath + File.separator)) { "目标路径不能位于 Rime 目录外" }
                destination.parentFile!!.mkdirs()
                val temporary = File(destination.parentFile, destination.name + ".installing")
                source.copyTo(temporary, overwrite = true)
                if (destination.exists()) check(destination.delete()) { "无法替换 ${source.name}" }
                check(temporary.renameTo(destination)) { "无法安装 ${source.name}" }
            }
            redeploy()
        }
    }
    fun customFiles(): List<File> = userDir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".custom.yaml") }.sortedBy { it.name }
    fun customFile(name: String): File {
        require(Regex("[a-zA-Z0-9_-]+\\.custom\\.yaml").matches(name)) { "文件名须为 方案名.custom.yaml" }
        return File(userDir, name)
    }
    suspend fun saveCustom(name: String, text: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val data = yaml().load<Any>(text)
            require(data is Map<*, *> && data["patch"] is Map<*, *>) { "需要 patch: 映射结构" }
            val file = customFile(name)
            val temporary = File(file.parentFile, file.name + ".saving")
            temporary.writeText(text)
            check(temporary.renameTo(file)) { "保存失败" }
        }
    }
    fun schemas(): List<String> = (userDir.listFiles().orEmpty().toList() + sharedDir.listFiles().orEmpty().toList())
        .filter { it.name.endsWith(".schema.yaml") }.map { it.name.removeSuffix(".schema.yaml") }.distinct().sorted()

    /** Install the calculator as a normal Rime translator for every available schema. */
    fun prepareBuiltInFeatures() {
        schemas().forEach { schema ->
            runCatching {
                val file = customFile("$schema.custom.yaml")
                val data = if (file.exists()) {
                    yaml().load<Map<String, Any?>>(file.readText()).toMutableMap()
                } else {
                    linkedMapOf<String, Any?>("patch" to linkedMapOf<String, Any?>())
                }
                @Suppress("UNCHECKED_CAST")
                val patch = (data["patch"] as? Map<String, Any?>)?.toMutableMap()
                    ?: error("${file.name} 的 patch 必须是映射")
                val translators = when (val current = patch[CALCULATOR_TRANSLATORS_PATCH]) {
                    null -> mutableListOf()
                    is Collection<*> -> current.toMutableList()
                    else -> error("${file.name} 的 $CALCULATOR_TRANSLATORS_PATCH 必须是列表")
                }
                if (CALCULATOR_TRANSLATOR !in translators) translators += CALCULATOR_TRANSLATOR
                patch[CALCULATOR_TRANSLATORS_PATCH] = translators
                patch[CALCULATOR_PATTERN_PATCH] = "^=.*$"
                data["patch"] = patch
                val output = Yaml().dump(data)
                if (!file.exists() || file.readText() != output) {
                    val temporary = File(file.parentFile, file.name + ".saving")
                    temporary.writeText(output)
                    if (file.exists()) check(file.delete()) { "无法更新 ${file.name}" }
                    check(temporary.renameTo(file)) { "无法保存 ${file.name}" }
                }
            }.onFailure { Timber.e(it, "Failed to enable Rime calculator for %s", schema) }
        }
    }

    suspend fun setModel(schema: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        check(!enabled || WanxiangModel.installed) { "请先下载万象模型" }
        val file = customFile("$schema.custom.yaml")
        val data: MutableMap<String, Any?> = if (file.exists()) {
            val loaded = yaml().load<Map<String, Any?>>(file.readText())
            loaded.toMutableMap()
        } else linkedMapOf("patch" to linkedMapOf<String, Any?>())
        @Suppress("UNCHECKED_CAST")
        val patch = (data["patch"] as? Map<String, Any?>)?.toMutableMap() ?: error("patch 必须是映射")
        patch["grammar/language"] = if (enabled) "wanxiang-lts-zh-hans" else ""
        if (enabled) patch["translator/contextual_suggestions"] = true
        data["patch"] = patch
        saveCustom(file.name, Yaml().dump(data))
    }
    suspend fun redeploy() = withContext(Dispatchers.IO) {
        val name = "rime-deploy-${java.util.UUID.randomUUID()}"
        val connection = FcitxDaemon.connect(name)
        try { connection.runOnReady { setAddonSubConfig("rime", "deploy") } }
        finally { FcitxDaemon.disconnect(name) }
    }

    val grammarDefaults: Map<String, Number> = linkedMapOf(
        "collocation_max_length" to 4, "collocation_min_length" to 3,
        "collocation_penalty" to -12.0, "non_collocation_penalty" to -12.0,
        "weak_collocation_penalty" to -24.0, "rear_penalty" to -18.0
    )

    suspend fun grammarOverrides(schema: String): Map<String, String> = withContext(Dispatchers.IO) {
        val file = customFile("$schema.custom.yaml")
        if (!file.exists()) return@withContext emptyMap()
        val data = yaml().load<Map<String, Any?>>(file.readText())
        val patch = data["patch"] as? Map<*, *> ?: error("patch 必须是映射")
        grammarDefaults.keys.mapNotNull { key ->
            (patch["grammar/$key"] ?: (patch["grammar"] as? Map<*, *>)?.get(key))?.let { key to it.toString() }
        }.toMap()
    }

    suspend fun setGrammarOverrides(schema: String, values: Map<String, String>) = withContext(Dispatchers.IO) {
        val parsed = values.filterValues { it.isNotBlank() }.mapValues { (key, value) ->
            require(key in grammarDefaults)
            if (key.endsWith("_length")) value.toInt().also { require(it > 0) { "搭配长度必须大于 0" } }
            else value.toDouble().also { require(it.isFinite()) { "参数必须是有限数字" } }
        }
        val min = parsed["collocation_min_length"]?.toInt()
        val max = parsed["collocation_max_length"]?.toInt()
        require(min == null || max == null || min <= max) { "最短搭配不能超过最长搭配" }
        val file = customFile("$schema.custom.yaml")
        val data = if (file.exists()) yaml().load<Map<String, Any?>>(file.readText()).toMutableMap()
            else linkedMapOf<String, Any?>("patch" to linkedMapOf<String, Any?>())
        @Suppress("UNCHECKED_CAST")
        val patch = (data["patch"] as? Map<String, Any?>)?.toMutableMap() ?: error("patch 必须是映射")
        @Suppress("UNCHECKED_CAST")
        val nested = (patch["grammar"] as? Map<String, Any?>)?.toMutableMap()
        grammarDefaults.keys.forEach { key ->
            nested?.remove(key)
            patch.remove("grammar/$key")
            parsed[key]?.let { patch["grammar/$key"] = it }
        }
        if (nested != null) patch["grammar"] = nested
        data["patch"] = patch
        saveCustom(file.name, Yaml().dump(data))
    }
}
