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
import java.io.File
import java.net.URI
import java.security.MessageDigest

object RimeManager {
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
            val files = root.walkTopDown().onEnter { it == root || !it.name.startsWith('.') }.filter { it.isFile && !it.name.endsWith(".custom.yaml") && !it.name.startsWith('.') }.toList()
            check(files.any { it.name.endsWith(".schema.yaml") }) { "仓库中没有 Rime schema 文件" }
            files.forEach { file ->
                check(file.canonicalPath.startsWith(root.path + File.separator)) { "方案包含目录外的链接" }
            }
            // The engine is restarted only after complete files have been installed.
            files.forEach { source ->
                val destination = File(userDir, source.relativeTo(root).path)
                destination.parentFile!!.mkdirs()
                val temporary = File(destination.parentFile, destination.name + ".installing")
                source.copyTo(temporary, overwrite = true)
                check(temporary.renameTo(destination)) { "无法安装 ${source.name}" }
            }
            FcitxDaemon.restartFcitx()
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
    fun schemas(): List<String> = (userDir.listFiles().orEmpty() + sharedDir.listFiles().orEmpty())
        .filter { it.name.endsWith(".schema.yaml") }.map { it.name.removeSuffix(".schema.yaml") }.distinct().sorted()

    suspend fun setModel(schema: String, enabled: Boolean) = withContext(Dispatchers.IO) {
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
    suspend fun redeploy() = withContext(Dispatchers.IO) { FcitxDaemon.restartFcitx() }
}
