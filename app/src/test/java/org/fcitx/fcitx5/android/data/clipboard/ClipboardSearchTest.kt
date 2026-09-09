package org.fcitx.fcitx5.android.data.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSearchTest {
    private val readings = mapOf("中" to "zhōng", "文" to "wén", "你" to "nǐ", "好" to "hǎo")
    private fun matches(text: String, query: String) = ClipboardSearch.matches(text, query, readings::get)

    @Test fun searchesChineseWithPinyinAndInitials() {
        assertTrue(matches("中文你好", "zhongwen"))
        assertTrue(matches("中文你好", "zwnh"))
        assertTrue(matches("中文你好", "你好"))
        assertFalse(matches("中文你好", "zhonghao"))
    }

    @Test fun queryIsLiteralAndEmptyQueryIncludesEverything() {
        assertTrue(matches("100%_done", "%_"))
        assertFalse(matches("100 percent", "%"))
        assertTrue(matches("anything", ""))
        assertTrue(matches("Ticket 123", "123"))
        assertTrue(matches("Hello", "HELLO"))
    }

    @Test fun preservesSupplementaryCharactersAndWordBoundaries() {
        assertTrue(matches("😀你好", "nh"))
        assertTrue(matches("😀你好", "😀"))
        assertFalse(matches("中 文", "zhongwen"))
        assertFalse(matches("中 文", "zw"))
    }
}
