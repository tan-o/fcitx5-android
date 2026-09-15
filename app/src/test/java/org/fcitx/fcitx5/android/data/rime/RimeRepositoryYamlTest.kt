package org.fcitx.fcitx5.android.data.rime

import org.junit.Assert.assertEquals
import org.junit.Test

class RimeRepositoryYamlTest {
    @Test
    fun acceptsDuplicateKeysUsedByThirdPartySchemas() {
        val schema = """
            schema:
              schema_id: wubi86_jidian
            translator:
              dictionary: wubi86_jidian
              enable_user_dict: true
              enable_user_dict: false
        """.trimIndent()

        assertEquals("wubi86_jidian", repositorySchemaId(schema))
    }

    @Test
    fun readsRepositoryDefaultSchemaList() {
        val defaults = """
            schema_list:
              - schema: wubi86_jidian
              - schema: luna_pinyin
        """.trimIndent()

        assertEquals(
            listOf("wubi86_jidian", "luna_pinyin"),
            repositorySchemaList(defaults)
        )
    }

    @Test
    fun removesSchemasAlreadyEnabledByRepositoryDefault() {
        val current = listOf(
            linkedMapOf("schema" to "rime_mint"),
            linkedMapOf("schema" to "custom_schema"),
            linkedMapOf("schema" to "rime_mint")
        )

        assertEquals(
            listOf(linkedMapOf("schema" to "custom_schema")),
            normalizedSchemaAppend(
                current,
                baseSchemas = listOf("rime_mint"),
                requestedSchemas = listOf("rime_mint")
            )
        )
    }

    @Test
    fun appendsMissingSchemasOnlyOnce() {
        assertEquals(
            listOf(linkedMapOf("schema" to "new_schema")),
            normalizedSchemaAppend(
                current = emptyList<Any?>(),
                baseSchemas = emptyList(),
                requestedSchemas = listOf("new_schema", "new_schema")
            )
        )
    }
}
