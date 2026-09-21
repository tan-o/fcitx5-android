package org.fcitx.fcitx5.android.data.rime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RimeComponentsTest {
    @Test fun replacesRetiredFilterAndPreservesUserSettings() {
        val original = mapOf("patch" to linkedMapOf<String, Any?>(
            "translator/dictionary" to "rime_mint",
            "engine/filters/+" to listOf("lua_filter@*user_filter", "lua_filter@*fcitx_radical_filter"),
            "schema/dependencies/+" to listOf("radical_pinyin", "fcitx_components"),
            "switches/+" to listOf(mapOf("name" to "my_option", "reset" to 0))
        ))
        val updated = RimeComponents.patch(original)
        val patch = updated["patch"] as Map<*, *>
        assertEquals("rime_mint", patch["translator/dictionary"])
        assertEquals(listOf("lua_filter@*user_filter", "simplifier@fcitx_components"), patch["engine/filters/+"])
        assertEquals(listOf("radical_pinyin"), patch["schema/dependencies/+"])
        assertEquals(updated, RimeComponents.patch(updated))
    }

    @Test fun installsBeforeEngineStartAndDoesNotRewriteUnchangedFiles() {
        val dir = Files.createTempDirectory("rime-components").toFile()
        try {
            File(dir, "fcitx_components.dict.yaml").writeText("obsolete")
            RimeComponents.install(dir, listOf("rime_mint")) { "asset:$it" }
            val custom = File(dir, "rime_mint.custom.yaml")
            val content = custom.readText()
            custom.setLastModified(1234000)
            RimeComponents.install(dir, listOf("rime_mint")) { "asset:$it" }
            assertEquals(content, custom.readText())
            assertEquals(1234000L, custom.lastModified())
            assertFalse(File(dir, "fcitx_components.dict.yaml").exists())
            assertTrue(File(dir, "opencc/fcitx-components/moqi_chaifen_all.txt").isFile)
        } finally { dir.deleteRecursively() }
    }

    @Test fun exportProductionConfigurationForNativeIntegrationTest() {
        val output = System.getenv("RIME_COMPONENT_FIXTURE") ?: return
        val assets = File(requireNotNull(System.getenv("RIME_COMPONENT_ASSETS")))
        // The native test consumes files produced by the real Android installer.
        RimeComponents.install(File(output), listOf("rime_mint")) { File(assets, it).readText() }
    }
}
