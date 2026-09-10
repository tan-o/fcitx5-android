package org.fcitx.fcitx5.android.input.history

import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    @Test fun editingAnOldVersionKeepsBothBranches() {
        val history = EditHistory()
        history.record("", 0, 0)
        history.record("你好", 2, 2)
        val fork = history.current!!
        history.record("你好世界", 4, 4)
        val original = history.current!!
        history.select(fork)
        history.record("你好朋友", 4, 4)
        assertEquals(2, history.nodes.count { it.parent == fork })
        assertEquals("你好世界", history.nodes.first { it.id == original }.text)
    }
    @Test fun applicationUndoReturnsToParentWithoutAddingANode() {
        val history = EditHistory()
        history.record("a", 1, 1)
        val parent = history.current
        history.record("ab", 2, 2)
        history.record("a", 1, 1)
        assertEquals(parent, history.current)
        assertEquals(2, history.nodes.size)
    }
    @Test fun boundedHistoryRetainsAReachableRoot() {
        val history = EditHistory()
        repeat(150) { history.record("$it", 0, 0) }
        assertEquals(100, history.nodes.size)
        assertTrue(history.nodes.any { it.parent == null })
        assertTrue(history.nodes.all { it.parent == null || history.nodes.any { parent -> parent.id == it.parent } })
    }
}
