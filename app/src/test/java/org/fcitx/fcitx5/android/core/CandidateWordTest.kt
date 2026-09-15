package org.fcitx.fcitx5.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateWordTest {
    @Test
    fun extractsRadicalAndHidesMetadata() {
        val candidate = CandidateWord(
            label = "",
            text = "呀",
            comment = "ya\u2063fcitx-radical:口\u2063"
        )

        assertEquals("口", candidate.radical)
        assertEquals("ya", candidate.visibleComment())
        assertEquals("呀 ya", candidate.textWithComment())
    }
}
