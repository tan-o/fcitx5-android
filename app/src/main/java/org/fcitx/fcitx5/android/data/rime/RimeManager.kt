package org.fcitx.fcitx5.android.data.rime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand.FastForwardMode
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.util.FS
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.core.data.DataManager
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.utils.appContext
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.net.URI
import java.security.MessageDigest

private fun yaml(allowDuplicateKeys: Boolean = false) = Yaml(
    SafeConstructor(LoaderOptions().apply {
        codePointLimit = 2_000_000
        isAllowDuplicateKeys = allowDuplicateKeys
    })
)

internal fun repositorySchemaId(text: String): String? {
    val data = yaml(allowDuplicateKeys = true).load<Map<String, Any?>>(text)
    val schema = data["schema"] as? Map<*, *> ?: return null
    return schema["schema_id"]?.toString()
}

internal fun repositorySchemaList(text: String): List<String> {
    val data = yaml(allowDuplicateKeys = true).load<Map<String, Any?>>(text)
    return (data["schema_list"] as? Collection<*>)?.mapNotNull { entry ->
        (entry as? Map<*, *>)?.get("schema")?.toString()
    }.orEmpty()
}

internal fun normalizedSchemaAppend(
    current: Collection<*>,
    baseSchemas: Collection<String>,
    requestedSchemas: Collection<String>
): List<Any?> {
    val seen = baseSchemas.toMutableSet()
    val result = mutableListOf<Any?>()
    current.forEach { entry ->
        val schema = (entry as? Map<*, *>)?.get("schema")?.toString()
        if (schema == null || seen.add(schema)) result += entry
    }
    requestedSchemas.forEach { schema ->
        if (seen.add(schema)) result += linkedMapOf("schema" to schema)
    }
    return result
}

object RimeManager {
    private const val RadicalFilterAsset = "rime/lua/fcitx_radical_filter.lua"
    private const val RadicalFilterComponent = "lua_filter@*fcitx_radical_filter"

    data class GitProgress(val task: String, val completed: Int, val total: Int)

    private val lock = Mutex()
    val repositories = File(appContext.filesDir, "rime-repositories").apply { mkdirs() }
    val userDir: File get() = File(requireNotNull(FcitxApplication.getInstance().directBootAwareContext.getExternalFilesDir(null)), "data/rime").apply { mkdirs() }
    private val sharedDir get() = File(DataManager.dataDir, "usr/share/rime-data")
    fun repositoryFor(url: String): File {
        val uri = URI(url.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "请输入公开仓库的 HTTPS Git 地址"
        }
        val name = uri.path.substringAfterLast('/').removeSuffix(".git")
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        require(name.isNotBlank()) { "仓库地址缺少名称" }
        val hash = MessageDigest.getInstance("SHA-256").digest(url.trim().toByteArray())
            .take(4).joinToString("") { "%02x".format(it) }
        return File(repositories, "$name-$hash")
    }

    fun repository(name: String): File {
        require(name.matches(Regex("[a-zA-Z0-9_-]+"))) { "仓库名称无效" }
        return File(repositories, name).also {
            check(it.canonicalFile.parentFile == repositories.canonicalFile) { "仓库路径无效" }
        }
    }

    private fun progressMonitor(
        job: Job?,
        listener: (GitProgress) -> Unit
    ) = object : ProgressMonitor {
        private var task = "连接远端"
        private var completed = 0
        private var total = ProgressMonitor.UNKNOWN

        override fun start(totalTasks: Int) = Unit
        override fun beginTask(title: String, totalWork: Int) {
            task = title
            completed = 0
            total = totalWork
            listener(GitProgress(task, completed, total))
        }
        override fun update(delta: Int) {
            completed += delta
            listener(GitProgress(task, completed, total))
        }
        override fun endTask() {
            if (total > 0) completed = total
            listener(GitProgress(task, completed, total))
        }
        override fun isCancelled() = job?.isActive == false
        override fun showDuration(enabled: Boolean) = Unit
    }

    suspend fun clone(
        url: String,
        progress: (GitProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        lock.withLock {
            val dir = repositoryFor(url)
            FS.DETECTED.setUserHome(appContext.filesDir)
            check(!dir.exists()) { "仓库已存在" }
            val job = currentCoroutineContext()[Job]
            try {
                Git.cloneRepository().setURI(url.trim()).setDirectory(dir).setTimeout(120)
                    .setProgressMonitor(progressMonitor(job, progress)).call().close()
                currentCoroutineContext().ensureActive()
            }
            catch (e: Exception) { dir.deleteRecursively(); throw e }
            dir
        }
    }
    suspend fun update(
        repo: File,
        progress: (GitProgress) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        lock.withLock {
            val job = currentCoroutineContext()[Job]
            Git.open(repo).use { git ->
                check(git.status().call().isClean) { "仓库有未提交的修改，请先处理；个人设置应写入 custom 文件" }
                check(git.pull().setFastForward(FastForwardMode.FF_ONLY).setTimeout(120)
                    .setProgressMonitor(progressMonitor(job, progress)).call().isSuccessful) {
                    "更新失败：远端分支已分叉"
                }
                currentCoroutineContext().ensureActive()
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
            val schemaFiles = files.filter { it.parentFile == root && it.name.endsWith(".schema.yaml") }
            check(schemaFiles.isNotEmpty()) { "仓库根目录中没有 Rime schema 文件" }
            val schemaIdsByFile = schemaFiles.associate { file ->
                // Match librime's permissive handling for third-party schemas. Files created or
                // edited by this app still use strict parsing through yaml().
                val schemaId = repositorySchemaId(file.readText())
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
                    ?: error("${file.name} 缺少有效的 schema_id")
                file.name to schemaId
            }
            val configuredSchemas = File(root, "default.yaml").takeIf(File::isFile)?.let { defaultFile ->
                repositorySchemaList(defaultFile.readText())
            }.orEmpty()
            val availableSchemas = schemaIdsByFile.values.distinct()
            val schemaIds = configuredSchemas.filter(availableSchemas::contains).distinct()
                .ifEmpty { availableSchemas }
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
            enableSchemas(schemaIds, configuredSchemas)
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
            if (file.exists()) check(file.delete()) { "无法替换 ${file.name}" }
            check(temporary.renameTo(file)) { "保存失败" }
        }
    }

    private fun enableSchemas(schemaIds: List<String>, configuredSchemas: List<String>) {
        val file = customFile("default.custom.yaml")
        val data = if (file.exists()) {
            yaml().load<Map<String, Any?>>(file.readText()).toMutableMap()
        } else linkedMapOf<String, Any?>("patch" to linkedMapOf<String, Any?>())
        @Suppress("UNCHECKED_CAST")
        val patch = (data["patch"] as? Map<String, Any?>)?.toMutableMap()
            ?: error("default.custom.yaml 的 patch 必须是映射")
        val key = "schema_list/+"
        val entries = when (val current = patch[key]) {
            null -> mutableListOf<Any?>()
            is Collection<*> -> current.toMutableList()
            else -> error("default.custom.yaml 的 $key 必须是列表")
        }
        val replacementSchemas = (patch["schema_list"] as? Collection<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.get("schema")?.toString() }
            .orEmpty()
        val normalized = normalizedSchemaAppend(
            entries,
            configuredSchemas + replacementSchemas,
            schemaIds
        )
        if (normalized == entries) return
        if (normalized.isEmpty()) patch.remove(key) else patch[key] = normalized
        data["patch"] = patch
        writeYaml(file, data, "无法保存 ${file.name}")
    }

    private fun installRadicalMetadataFilter(schemaIds: List<String>) {
        listOf("fcitx_components.dict.yaml", "fcitx_components.schema.yaml").forEach { name ->
            val text = appContext.assets.open("rime/$name").bufferedReader().use { it.readText() }
            val target = File(userDir, name)
            if (!target.exists() || target.readText() != text) {
                val temporary = File(userDir, "$name.installing")
                temporary.writeText(text)
                if (target.exists()) check(target.delete()) { "无法替换 $name" }
                check(temporary.renameTo(target)) { "无法安装 $name" }
            }
        }
        val script = File(userDir, "lua/fcitx_radical_filter.lua")
        script.parentFile!!.mkdirs()
        val scriptText = appContext.assets.open(RadicalFilterAsset).bufferedReader().use { it.readText() }
        if (!script.exists() || script.readText() != scriptText) {
            val temporary = File(script.parentFile, script.name + ".installing")
            temporary.writeText(scriptText)
            if (script.exists()) check(script.delete()) { "无法替换 ${script.name}" }
            check(temporary.renameTo(script)) { "无法安装 ${script.name}" }
        }
        schemaIds.filter { it != "fcitx_components" }.forEach(::enableRadicalMetadataFilter)
    }

    private fun enableRadicalMetadataFilter(schemaId: String) {
        val file = customFile("$schemaId.custom.yaml")
        val data = if (file.exists()) {
            yaml().load<Map<String, Any?>>(file.readText()).toMutableMap()
        } else linkedMapOf<String, Any?>("patch" to linkedMapOf<String, Any?>())
        @Suppress("UNCHECKED_CAST")
        val patch = (data["patch"] as? Map<String, Any?>)?.toMutableMap()
            ?: error("${file.name} 的 patch 必须是映射")
        val key = "engine/filters/+"
        val filters = when (val current = patch[key]) {
            null -> mutableListOf<Any?>()
            is Collection<*> -> current.toMutableList()
            else -> error("${file.name} 的 $key 必须是列表")
        }
        if (RadicalFilterComponent !in filters) filters += RadicalFilterComponent
        patch[key] = filters
        val dependenciesKey = "schema/dependencies/+"
        val dependencies = when (val current = patch[dependenciesKey]) {
            null -> mutableListOf<Any?>()
            is Collection<*> -> current.toMutableList()
            else -> error("${file.name} 的 $dependenciesKey 必须是列表")
        }
        if ("fcitx_components" !in dependencies) dependencies += "fcitx_components"
        patch[dependenciesKey] = dependencies
        data["patch"] = patch
        writeYaml(file, data, "无法保存 ${file.name}")
    }

    private fun writeYaml(file: File, data: Map<String, Any?>, failure: String) {
        val temporary = File(file.parentFile, file.name + ".saving")
        temporary.writeText(Yaml().dump(data))
        if (file.exists()) check(file.delete()) { "无法替换 ${file.name}" }
        check(temporary.renameTo(file)) { failure }
    }
    fun schemas(): List<String> = (userDir.listFiles().orEmpty().toList() + sharedDir.listFiles().orEmpty().toList())
        .filter { it.name.endsWith(".schema.yaml") && it.name != "fcitx_components.schema.yaml" }
        .map { it.name.removeSuffix(".schema.yaml") }.distinct().sorted()

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
        // Keep the bundled candidate metadata filter in sync for both freshly
        // cloned repositories and existing installations upgraded in place.
        installRadicalMetadataFilter(schemas())
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
