package org.fcitx.fcitx5.android.data.lua

import org.fcitx.fcitx5.android.utils.appContext
import java.io.File

object LuaScriptManager {
    val directory: File
        get() {
            val external = appContext.getExternalFilesDir(null)
                ?: error("应用外部存储当前不可用")
            return File(external, "data/lua/imeapi/extensions").also {
                check(it.isDirectory || it.mkdirs()) { "无法创建 Lua 脚本目录" }
            }
        }

    fun list(): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.extension.equals("lua", ignoreCase = true) }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()

    fun normalizeName(raw: String): String {
        val base = raw.trim().removeSuffix(".lua")
        require(base.matches(Regex("[A-Za-z0-9._-]+"))) {
            "文件名只能包含字母、数字、点、下划线和连字符"
        }
        return "$base.lua"
    }

    fun save(name: String, content: String): File {
        require(content.isNotBlank()) { "脚本内容不能为空" }
        return directory.resolve(normalizeName(name)).also { it.writeText(content) }
    }

    fun delete(file: File) {
        require(file.parentFile?.canonicalFile == directory.canonicalFile) { "无效脚本路径" }
        check(file.delete()) { "删除失败" }
    }

    const val TEMPLATE = """function insert_current_time(format)
    local pattern = format ~= "" and format or "%Y-%m-%d %H:%M"
    return os.date(pattern)
end
"""
}
