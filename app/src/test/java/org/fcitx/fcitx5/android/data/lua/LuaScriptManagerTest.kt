package org.fcitx.fcitx5.android.data.lua

import org.junit.Assert.assertEquals
import org.junit.Test

class LuaScriptManagerTest {
    @Test
    fun detectsOnlyCallableGlobalFunctions() {
        val source = """
            function insert_time(format)
                return os.date(format)
            end

            lookup_word = function(word)
                return word
            end

            local function helper()
                return "hidden"
            end

            local assigned_helper = function()
                return "hidden"
            end
        """.trimIndent()

        assertEquals(
            listOf("insert_time", "lookup_word"),
            LuaScriptManager.functionNames(source)
        )
    }
}
