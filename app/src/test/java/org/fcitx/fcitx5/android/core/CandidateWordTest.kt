package org.fcitx.fcitx5.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateWordTest {
    @Test fun extractsMultipleComponentsAndHidesMetadata() {
        val candidate = CandidateWord("", "呀", "ya\u2063fcitx-radical:口牙口\u2063")
        assertEquals(listOf("口", "牙"), candidate.radicals)
        assertEquals("ya", candidate.visibleComment())
        assertEquals("呀 ya", candidate.textWithComment())
        assertTrue("口" in candidate.radicals)
        assertTrue("牙" in candidate.radicals)
    }
    @Test fun supplementaryComponentIsOneChip() {
        val candidate = CandidateWord("", "字", "\u2063fcitx-radical:𠂉口\u2063")
        assertEquals(listOf("𠂉", "口"), candidate.radicals)
    }
    @Test fun missingMetadataDoesNotInventComponents() {
        assertTrue(CandidateWord("", "呀", "kou'ya").radicals.isEmpty())
    }
}

