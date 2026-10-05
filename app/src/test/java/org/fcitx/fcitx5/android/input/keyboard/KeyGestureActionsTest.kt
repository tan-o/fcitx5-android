/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyGestureActionsTest {
    private fun roundTrip(text: String) = KeyGestureActions.parse(KeyGestureActions.encodeText(text))

    @Test fun plainTextIsStoredAsIs() {
        assertEquals("a=b", KeyGestureActions.encodeText("a=b"))
        assertEquals(KeyAction.CommitAction("a=b"), roundTrip("a=b")?.action)
    }

    @Test fun commandLikeTextStaysLiteral() {
        assertEquals(KeyAction.CommitAction("lua:insert_time"), roundTrip("lua:insert_time")?.action)
        assertEquals(KeyAction.CommitAction("x=lua:f"), roundTrip("x=lua:f")?.action)
        assertEquals("x=lua:f", roundTrip("x=lua:f")?.label)
    }

    @Test fun labeledCommandsStillParse() {
        val entry = KeyGestureActions.parse("Time=lua:insert_time:%H")
        assertEquals("Time", entry?.label)
        assertEquals(KeyAction.LuaAction("insert_time", "%H"), entry?.action)
    }
}
