/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

enum class SwipeDirection {
    Up,
    Down;

    fun matches(totalY: Int): Boolean = totalY != 0 && (totalY > 0) == (this == Down)
}
