/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2024 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.candidates.expanded

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import timber.log.Timber

class CandidatesPagingSource(val fcitx: FcitxConnection, val total: Int, val offset: Int, val generation: Int) :
    PagingSource<Int, IndexedCandidate>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, IndexedCandidate> {
        // use candidate index for key, null means load from beginning (with offset)
        val startIndex = params.key ?: offset
        val pageSize = params.loadSize
        Timber.d("getCandidates(offset=$startIndex, limit=$pageSize)")
        val candidates = fcitx.runOnReady {
            getCandidates(startIndex, pageSize)
        }
        val prevKey = if (startIndex > offset) maxOf(offset, startIndex - pageSize) else null
        val nextKey = if (total > 0) {
            if (candidates.isEmpty() || startIndex + candidates.size >= total) null else startIndex + candidates.size
        } else {
            if (candidates.size < pageSize) null else startIndex + pageSize
        }
        return LoadResult.Page(candidates.mapIndexed { index, candidate ->
            IndexedCandidate(startIndex + index, candidate, generation)
        }, prevKey, nextKey)
    }

    // always reload from beginning
    override fun getRefreshKey(state: PagingState<Int, IndexedCandidate>) = null

}
