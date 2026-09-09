/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.expanded

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.candidates.CandidateItemUi
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder

data class IndexedCandidate(val index: Int, val candidate: CandidateWord, val generation: Int)

open class PagingCandidateViewAdapter(val theme: Theme) :
    PagingDataAdapter<IndexedCandidate, CandidateViewHolder>(diffCallback) {

    companion object {
        /**
         * Always re-bind all [CandidateViewHolder]s every time to make sure `idx` is up-to-date.
         * [CandidateViewHolder.update] would skip unnecessary UI updates.
         */
        private val diffCallback = object : DiffUtil.ItemCallback<IndexedCandidate>() {
            override fun areItemsTheSame(oldItem: IndexedCandidate, newItem: IndexedCandidate) = false
            override fun areContentsTheSame(oldItem: IndexedCandidate, newItem: IndexedCandidate) = false
        }
    }

    var offset = 0
        private set

    @Volatile
    var generation = 0
        private set

    fun invalidateCandidates() {
        generation++
    }

    fun refreshWithOffset(offset: Int) {
        this.offset = offset
        invalidateCandidates()
        refresh()
    }

    fun canSelect(holder: CandidateViewHolder): Boolean {
        val position = holder.bindingAdapterPosition
        if (position !in 0 until itemCount) return false
        val entry = peek(position) ?: return false
        return entry.generation == generation && entry.index == holder.idx &&
            entry.candidate == holder.candidate
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CandidateViewHolder {
        return CandidateViewHolder(CandidateItemUi(parent.context, theme))
    }

    override fun onBindViewHolder(holder: CandidateViewHolder, position: Int) {
        val entry = getItem(position)
        holder.update(entry?.index ?: -1, entry?.candidate ?: CandidateWord.Empty)
    }
}
