/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input.candidates.expanded.window

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.CandidateAction
import org.fcitx.fcitx5.android.core.CandidateWord
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.candidates.HanziIndex
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.BooleanKey.ExpandedCandidatesEmpty
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.TransitionEvent.ExpandedCandidatesAttached
import org.fcitx.fcitx5.android.input.bar.ExpandButtonStateMachine.TransitionEvent.ExpandedCandidatesDetached
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.candidates.CandidateViewHolder
import org.fcitx.fcitx5.android.input.candidates.expanded.IndexedCandidate
import org.fcitx.fcitx5.android.input.candidates.expanded.CandidateFilterMode
import org.fcitx.fcitx5.android.input.candidates.expanded.CandidateTabActionsAdapter
import org.fcitx.fcitx5.android.input.candidates.expanded.CandidatesPagingSource
import org.fcitx.fcitx5.android.input.candidates.expanded.ExpandedCandidateLayout
import org.fcitx.fcitx5.android.input.candidates.expanded.PagingCandidateViewAdapter
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.inputView
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.views.recyclerview.verticalLayoutManager
import kotlin.math.max

abstract class BaseExpandedCandidateWindow<T : BaseExpandedCandidateWindow<T>> :
    InputWindow.SimpleInputWindow<T>(), InputBroadcastReceiver {

    companion object {
        /**
         * Bound the snapshot to keep filtering responsive. The UI states this limit;
         * normal paging still exposes the complete candidate list.
         */
        const val MaxFilterCandidates = 500
    }

    protected val service by manager.inputMethodService()
    protected val theme by manager.theme()
    protected val fcitx by manager.fcitx()
    protected val inputView by manager.inputView()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val horizontalCandidate: HorizontalCandidateComponent by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    protected val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation

    private lateinit var candidateLayout: ExpandedCandidateLayout

    protected val dividerDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val intrinsicSize = max(1, context.dp(1))
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = theme.dividerColor
        }
    }

    abstract fun onCreateCandidateLayout(): ExpandedCandidateLayout

    private var filterMode = CandidateFilterMode.None

    private var allCandidates: List<IndexedCandidate> = emptyList()

    private var filterJob: Job? = null

    private fun cycleFilterMode() {
        filterMode = filterMode.next()
        val mode = filterMode
        val generation = adapter.generation
        candidateLayout.filterUi.setMode(mode)
        candidateLayout.filterUi.setChips(emptyList())
        filterJob?.cancel()
        allCandidates = emptyList()
        clearFilter()
        if (mode == CandidateFilterMode.None) return
        filterJob = service.lifecycleScope.launch {
            try {
                val total = horizontalCandidate.adapter.total
                val limit = if (total in 1..MaxFilterCandidates) total else MaxFilterCandidates
                val snapshot = fcitx.runOnReady { getCandidates(0, limit) }
                    .mapIndexed { index, word -> IndexedCandidate(index, word, generation) }
                val chips = withContext(Dispatchers.Default) { chipsOf(snapshot, mode) }
                if (filterMode != mode || adapter.generation != generation) return@launch
                allCandidates = snapshot
                candidateLayout.filterUi.setChips(chips)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Candidate filtering failed")
                resetFilterMode()
            }
        }
    }

    private fun chipsOf(candidates: List<IndexedCandidate>, mode: CandidateFilterMode): List<String> =
        when (mode) {
            CandidateFilterMode.Radical -> candidates
                .mapNotNull { HanziIndex.of(it.candidate.text)?.radical }
                .distinct()
                .sortedWith(compareBy({ HanziIndex.radicalStrokes(it) }, { it }))
            CandidateFilterMode.Strokes -> candidates
                .mapNotNull { HanziIndex.of(it.candidate.text)?.strokes }
                .distinct().sorted().map { it.toString() }
            CandidateFilterMode.None -> emptyList()
        }

    private fun keyOf(candidate: CandidateWord): String? {
        val entry = HanziIndex.of(candidate.text) ?: return null
        return when (filterMode) {
            CandidateFilterMode.Radical -> entry.radical
            CandidateFilterMode.Strokes -> entry.strokes.toString()
            CandidateFilterMode.None -> null
        }
    }

    private fun applyFilter(chip: String?) {
        if (chip == null) {
            clearFilter()
            return
        }
        val matched = allCandidates.filter {
            it.generation == adapter.generation && keyOf(it.candidate) == chip
        }
        candidatesSubmitJob?.cancel()
        candidateLayout.resetPosition()
        // Each item carries its original index and input generation. There is no
        // independently mutable side table racing PagingDataAdapter's async diff.
        candidatesSubmitJob = service.lifecycleScope.launch {
            adapter.submitData(PagingData.from(matched))
        }
    }

    private fun clearFilter() {
        candidateLayout.resetPosition()
        startCandidatesSubmitJob()
    }

    private fun resetFilterMode() {
        val wasFiltering = filterMode != CandidateFilterMode.None
        filterMode = CandidateFilterMode.None
        filterJob?.cancel()
        filterJob = null
        allCandidates = emptyList()
        candidateLayout.filterUi.setMode(filterMode)
        candidateLayout.filterUi.setChips(emptyList())
        if (wasFiltering) clearFilter()
    }

    private fun startCandidatesSubmitJob() {
        candidatesSubmitJob?.cancel()
        candidatesSubmitJob = service.lifecycleScope.launch {
            candidatesPager.flow.collectLatest {
                adapter.submitData(it)
            }
        }
    }

    final override fun onCreateView(): View {
        candidateLayout = onCreateCandidateLayout().apply {
            filterUi.apply {
                setMode(filterMode)
                onModeClick = { cycleFilterMode() }
                onChipClick = { applyFilter(it) }
            }
            scrollableTabs.apply {
                adapter = tabsAdapter
                layoutManager = verticalLayoutManager()
            }
            pinnedTabs.apply {
                adapter = pinnedTabsAdapter
                layoutManager = verticalLayoutManager()
            }
        }
        return candidateLayout
    }

    private val keyActionListener = KeyActionListener { it, source ->
        if (it is KeyAction.LayoutSwitchAction) {
            when (it.act) {
                ExpandedCandidateLayout.Keyboard.UpBtnLabel -> prevPage()
                ExpandedCandidateLayout.Keyboard.DownBtnLabel -> nextPage()
            }
        } else {
            commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    abstract val adapter: PagingCandidateViewAdapter
    abstract val layoutManager: RecyclerView.LayoutManager

    val tabsAdapter by lazy {
        object : CandidateTabActionsAdapter(theme, false) {
            override fun onTriggerTabAction(id: Int) {
                fcitx.launchOnReady { it.triggerCandidateListTabAction(id) }
            }
        }
    }

    val pinnedTabsAdapter by lazy {
        object : CandidateTabActionsAdapter(theme, true) {
            override fun onTriggerTabAction(id: Int) {
                fcitx.launchOnReady { it.triggerCandidateListTabAction(id) }
            }
        }
    }

    private fun updateTabs(newTabs: Array<CandidateAction>) {
        val tabs = newTabs.takeWhile { !it.isSeparator }
        val pinnedTabs = newTabs.drop(tabs.size + 1).filter { !it.isSeparator }
        if (tabs.isEmpty() && pinnedTabs.isEmpty()) {
            candidateLayout.tabsContainer.visibility = View.GONE
        } else {
            candidateLayout.tabsContainer.visibility = View.VISIBLE
        }
        tabsAdapter.updateTabs(tabs)
        pinnedTabsAdapter.updateTabs(pinnedTabs)
    }

    private var offsetJob: Job? = null

    private val candidatesPager by lazy {
        Pager(
            config = PagingConfig(
                pageSize = 48,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                CandidatesPagingSource(
                    fcitx,
                    total = horizontalCandidate.adapter.total,
                    offset = adapter.offset,
                    generation = adapter.generation
                )
            }
        )
    }
    private var candidatesSubmitJob: Job? = null

    abstract fun prevPage()

    abstract fun nextPage()

    override fun onAttached() {
        bar.expandButtonStateMachine.push(ExpandedCandidatesAttached)
        candidateLayout.embeddedKeyboard.also {
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            it.keyActionListener = keyActionListener
        }
        updateTabs(fcitx.runImmediately { inputPanelCached.tabs })
        offsetJob = service.lifecycleScope.launch {
            horizontalCandidate.expandedCandidateOffset.collect {
                if (it <= 0) {
                    windowManager.attachWindow(KeyboardWindow)
                } else {
                    candidateLayout.resetPosition()
                    if (it != adapter.offset) {
                        adapter.refreshWithOffset(it)
                        resetFilterMode()
                    }
                }
            }
        }
        startCandidatesSubmitJob()
    }

    fun bindCandidateUiViewHolder(holder: CandidateViewHolder) {
        holder.itemView.setOnClickListener {
            if (adapter.canSelect(holder)) {
                val index = holder.idx
                val generation = adapter.generation
                fcitx.launchOnReady {
                    if (generation == adapter.generation) it.select(index)
                }
            }
        }
        holder.itemView.setOnLongClickListener {
            if (adapter.canSelect(holder)) {
                inputView.showCandidateActionMenu(holder.idx, holder.candidate.text, holder.ui.root)
                true
            } else false
        }
    }

    fun recycleCandidateViewHolder(holder: CandidateViewHolder) {
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
    }

    override fun onDetached() {
        bar.expandButtonStateMachine.push(
            ExpandedCandidatesDetached,
            ExpandedCandidatesEmpty to (horizontalCandidate.adapter.total == adapter.offset)
        )
        candidatesSubmitJob?.cancel()
        filterJob?.cancel()
        adapter.invalidateCandidates()
        filterMode = CandidateFilterMode.None
        allCandidates = emptyList()
        candidateLayout.filterUi.setMode(filterMode)
        candidateLayout.filterUi.setChips(emptyList())
        offsetJob?.cancel()
        candidateLayout.embeddedKeyboard.keyActionListener = null
    }

    override fun onPreeditEmptyStateUpdate(empty: Boolean) {
        if (empty) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun onInputPanelUpdate(data: FcitxEvent.InputPanelEvent.Data) {
        updateTabs(data.tabs)
        adapter.refreshWithOffset(adapter.offset)
        resetFilterMode()
    }

}