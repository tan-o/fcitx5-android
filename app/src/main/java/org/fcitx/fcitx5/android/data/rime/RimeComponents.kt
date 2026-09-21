package org.fcitx.fcitx5.android.data.rime

import java.io.File
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Rime's native OpenCC filter with the unmodified upstream Moqi decomposition data. */
internal object RimeComponents {
    private const val Filter = "simplifier@fcitx_components"
    private const val Option = "_fcitx_components"
    private const val ObsoleteFilter = "lua_filter@*fcitx_radical_filter"

    fun install(userDir: File, schemas: List<String>, asset: (String) -> String) {
        listOf("moqi_chaifen_all.json", "moqi_chaifen_all.txt").forEach { name ->
            val path = "opencc/fcitx-components/$name"
            writeIfChanged(File(userDir, path), asset(path))
        }
        schemas.filter { it != "fcitx_components" }.forEach { schema ->
            val file = File(userDir, "$schema.custom.yaml")
            val yaml = Yaml(SafeConstructor(LoaderOptions().apply { isAllowDuplicateKeys = false }))
            val data = if (file.exists()) yaml.load<Map<String, Any?>>(file.readText()).orEmpty()
                else emptyMap()
            writeIfChanged(file, Yaml().dump(patch(data)))
        }
        // Remove the retired implementation, including its compiled dependency.
        listOf("fcitx_components.dict.yaml", "fcitx_components.schema.yaml",
            "lua/fcitx_radical_filter.lua", "build/fcitx_components.schema.yaml",
            "build/fcitx_components.reverse.bin", "build/fcitx_components.table.bin",
            "build/fcitx_components.prism.bin").forEach { File(userDir, it).delete() }
    }

    internal fun patch(data: Map<String, Any?>): Map<String, Any?> {
        val result = data.toMutableMap()
        val original = data["patch"]
        require(original == null || original is Map<*, *>) { "patch 必须是映射" }
        @Suppress("UNCHECKED_CAST")
        val patch = (original as? Map<String, Any?>).orEmpty().toMutableMap()
        fun list(key: String): MutableList<Any?> = when (val value = patch[key]) {
            null -> mutableListOf()
            is Collection<*> -> value.toMutableList()
            else -> error("$key 必须是列表")
        }
        val filters = list("engine/filters/+")
        filters.removeAll { it == ObsoleteFilter || it == Filter }
        filters.add(Filter)
        patch["engine/filters/+"] = filters
        // A reset-only switch is internal; it has no toolbar states or menu entry.
        val switches = list("switches/+")
        switches.removeAll { (it as? Map<*, *>)?.get("name") == Option }
        switches.add(linkedMapOf("name" to Option, "reset" to 1))
        patch["switches/+"] = switches
        patch["fcitx_components"] = linkedMapOf(
            "option_name" to Option,
            "opencc_config" to "fcitx-components/moqi_chaifen_all.json",
            "show_in_comment" to true,
            "tips" to "char",
            "inherit_comment" to true,
            "comment_format" to listOf("xform/[{}]//", "xform/^/\u2063fcitx-radical:/", "xform/$/\u2063/")
        )
        val dependencies = list("schema/dependencies/+")
        dependencies.removeAll { it == "fcitx_components" }
        if (dependencies.isEmpty()) patch.remove("schema/dependencies/+")
        else patch["schema/dependencies/+"] = dependencies
        result["patch"] = patch
        return result
    }

    private fun writeIfChanged(file: File, content: String) {
        if (file.isFile && file.readText() == content) return
        file.parentFile!!.mkdirs()
        val temporary = File(file.parentFile, file.name + ".installing")
        temporary.writeText(content)
        check(temporary.renameTo(file)) { "无法安装 ${file.name}" }
    }
}
