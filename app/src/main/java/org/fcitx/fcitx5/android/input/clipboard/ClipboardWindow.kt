/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupMenu
import androidx.annotation.Keep
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingData
import androidx.paging.CombinedLoadStates
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.SnackbarContentLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.data.clipboard.ClipboardSearch
import org.fcitx.fcitx5.android.data.clipboard.ClipboardTags
import org.fcitx.fcitx5.android.data.handwriting.HandwritingPinyin
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.clipboard.ClipboardStateMachine.BooleanKey.ClipboardDbEmpty
import org.fcitx.fcitx5.android.input.clipboard.ClipboardStateMachine.BooleanKey.ClipboardListeningEnabled
import org.fcitx.fcitx5.android.input.clipboard.ClipboardStateMachine.State.AddMore
import org.fcitx.fcitx5.android.input.clipboard.ClipboardStateMachine.State.EnableListening
import org.fcitx.fcitx5.android.input.clipboard.ClipboardStateMachine.State.Normal
import org.fcitx.fcitx5.android.input.clipboard.ClipboardStateMachine.TransitionEvent.ClipboardDbUpdated
import org.fcitx.fcitx5.android.input.clipboard.ClipboardStateMachine.TransitionEvent.ClipboardListeningUpdated
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.AppUtil
import org.fcitx.fcitx5.android.utils.clipboardManager
import org.fcitx.fcitx5.android.utils.EventStateMachine
import org.fcitx.fcitx5.android.utils.item
import org.fcitx.fcitx5.android.utils.styledColorOrDefault
import org.mechdancer.dependency.manager.must
import splitties.dimensions.dp
import splitties.resources.styledColor
import splitties.views.dsl.core.withTheme

class ClipboardWindow : InputWindow.ExtendedInputWindow<ClipboardWindow>() {

    private val service: FcitxInputMethodService by manager.inputMethodService()
    private val windowManager: InputWindowManager by manager.must()
    private val theme by manager.theme()

    private val snackbarCtx by lazy {
        context.withTheme(R.style.InputViewSnackbarTheme)
    }
    private var snackbarInstance: Snackbar? = null

    private lateinit var stateMachine: EventStateMachine<ClipboardStateMachine.State, ClipboardStateMachine.TransitionEvent, ClipboardStateMachine.BooleanKey>

    @Keep
    private val clipboardEnabledListener = ManagedPreference.OnChangeListener<Boolean> { _, it ->
        stateMachine.push(
            ClipboardListeningUpdated, ClipboardListeningEnabled to it
        )
    }

    private val prefs = AppPrefs.getInstance().clipboard

    private val clipboardEnabledPref = prefs.clipboardListening
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste
    private val clipboardMaskSensitive by prefs.clipboardMaskSensitive

    private val clipboardEntryRadius by ThemeManager.prefs.clipboardEntryRadius

    private var adapterSubmitJob: Job? = null

    private var inSearchMode = false

    private var searchQuery = ""
    private var selectedTag: String? = null
    private var tagEntry: ClipboardEntry? = null

    private fun submitEntries(pagingSourceFactory: () -> PagingSource<Int, ClipboardEntry>) {
        adapterSubmitJob?.cancel()
        val pager = Pager(PagingConfig(pageSize = 16), pagingSourceFactory = pagingSourceFactory)
        adapterSubmitJob = service.lifecycleScope.launch {
            pager.flow.collectLatest { adapter.submitData(it) }
        }
    }

    private fun setSearching(on: Boolean) {
        tagEntry = null
        inSearchMode = on
        searchQuery = ""
        ui.setSearchMode(on)
        ui.updateSearchQuery("")
        if (on) {
            submitSearchEntries("")
        } else {
            submitTabEntries(ui.tabsUi.activeTab)
        }
    }

    private fun updateSearchQuery(query: String) {
        searchQuery = query
        if (tagEntry != null) {
            ui.updateTagQuery(query)
        } else {
            ui.updateSearchQuery(query)
            submitSearchEntries(query)
        }
    }

    private fun submitSearchEntries(query: String) {
        adapterSubmitJob?.cancel()
        adapterSubmitJob = service.lifecycleScope.launch {
            delay(120)
            ClipboardManager.observeEntries().collectLatest { entries ->
                val matches = withContext(Dispatchers.Default) {
                    entries.filter { ClipboardSearch.matches(it.text, query, HandwritingPinyin::of) }
                }
                adapter.submitData(PagingData.from(matches))
                stateMachine.push(ClipboardDbUpdated, ClipboardDbEmpty to matches.isEmpty())
            }
        }
    }

    private val searchKeyActionListener: KeyActionListener = KeyActionListener { action, _ ->
        when (action) {
            is KeyAction.FcitxKeyAction -> updateSearchQuery(searchQuery + action.act)
            is KeyAction.CommitAction -> updateSearchQuery(searchQuery + action.text)
            is KeyAction.LayoutSwitchAction -> ui.switchSearchKeyboard(action.act)
            is KeyAction.SymAction -> when (val sym = action.sym.sym) {
                FcitxKeyMapping.FcitxKey_BackSpace -> {
                    if (searchQuery.isNotEmpty()) {
                        updateSearchQuery(searchQuery.substring(0, searchQuery.offsetByCodePoints(searchQuery.length, -1)))
                    }
                }
                FcitxKeyMapping.FcitxKey_space -> updateSearchQuery("$searchQuery ")
                FcitxKeyMapping.FcitxKey_Return -> {
                    tagEntry?.let { ClipboardTags.set(it.id, searchQuery) }
                    setSearching(false)
                }
                in 0xffb0..0xffb9 -> updateSearchQuery(searchQuery + (sym - 0xffb0))
                0xffab -> updateSearchQuery("$searchQuery+")
                0xffad -> updateSearchQuery("$searchQuery-")
                0xffaa -> updateSearchQuery("$searchQuery*")
                0xffaf -> updateSearchQuery("$searchQuery/")
                0xffac -> updateSearchQuery("$searchQuery,")
                0xffae -> updateSearchQuery("$searchQuery.")
                0xffbd -> updateSearchQuery("$searchQuery=")
                else -> {}
            }
            else -> {}
        }
    }

    private val loadStateListener: (CombinedLoadStates) -> Unit = {
        if (!inSearchMode && ui.tabsUi.activeTab == ClipboardTab.Recent &&
            it.refresh is androidx.paging.LoadState.NotLoading) {
            val empty = adapter.itemCount == 0
            stateMachine.push(ClipboardDbUpdated, ClipboardDbEmpty to empty)
        }
    }

    private fun submitTabEntries(tab: ClipboardTab) {
        when (tab) {
            ClipboardTab.Recent -> {
                selectedTag = null
                ui.hideCategories()
                submitEntries { ClipboardManager.recentEntries() }
            }
            ClipboardTab.Pinned -> {
                adapterSubmitJob?.cancel()
                adapterSubmitJob = service.lifecycleScope.launch {
                    ClipboardManager.observeEntries().collectLatest { entries ->
                        val pinned = entries.filter(ClipboardEntry::pinned)
                        val labels = ClipboardTags.labels(pinned)
                        if (selectedTag !in labels) selectedTag = null
                        ui.showCategories(labels, selectedTag) { tag ->
                            if (tag != selectedTag) {
                                selectedTag = tag
                                submitTabEntries(ClipboardTab.Pinned)
                            }
                        }
                        val shown = selectedTag?.let { tag ->
                            pinned.filter { ClipboardTags.label(it) == tag }
                        } ?: pinned
                        adapter.submitData(PagingData.from(shown))
                        stateMachine.push(ClipboardDbUpdated, ClipboardDbEmpty to shown.isEmpty())
                    }
                }
            }
        }
    }

    private fun promptTag(entry: ClipboardEntry) {
        tagEntry = entry
        inSearchMode = true
        searchQuery = ClipboardTags.customLabel(entry)
        ui.setSearchMode(true)
        ui.updateTagQuery(searchQuery)
    }

    private val adapter: ClipboardAdapter by lazy {
        object : ClipboardAdapter(
            theme,
            context.dp(clipboardEntryRadius.toFloat()),
            clipboardMaskSensitive
        ) {
            override fun onPin(id: Int) {
                service.lifecycleScope.launch { ClipboardManager.pin(id) }
            }

            override fun onUnpin(id: Int) {
                service.lifecycleScope.launch { ClipboardManager.unpin(id) }
            }

            override fun onEdit(id: Int) {
                AppUtil.launchClipboardEdit(context, id)
            }

            override fun onTag(entry: ClipboardEntry) {
                promptTag(entry)
            }

            override fun onShare(entry: ClipboardEntry) {
                val target = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, entry.text)
                }
                val chooser = Intent.createChooser(target, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(chooser)
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch {
                    ClipboardManager.delete(id)
                    showUndoSnackbar(id)
                }
            }

            override fun onPaste(entry: ClipboardEntry) {
                service.commitText(entry.text)
                if (clipboardReturnAfterPaste) windowManager.attachWindow(KeyboardWindow)
            }
        }
    }

    private val ui: ClipboardUi by lazy {
        ClipboardUi(context, theme).apply {
            recyclerView.apply {
                itemAnimator = null
                layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                adapter = this@ClipboardWindow.adapter
            }
            ItemTouchHelper(object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val entry = adapter.getEntryAt(viewHolder.bindingAdapterPosition) ?: return
                    service.lifecycleScope.launch {
                        ClipboardManager.delete(entry.id)
                        showUndoSnackbar(entry.id)
                    }
                }
            }).attachToRecyclerView(recyclerView)
            enableUi.enableButton.setOnClickListener {
                clipboardEnabledPref.setValue(true)
            }
            tabsUi.onTabSelected = {
                selectedTag = null
                submitTabEntries(it)
            }
            searchKeyboards.forEach { it.keyActionListener = searchKeyActionListener }
            searchBar.setOnLongClickListener {
                val clip = context.clipboardManager.primaryClip
                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context) else null
                if (text != null) updateSearchQuery(text.toString())
                true
            }
            searchButton.setOnClickListener {
                setSearching(!inSearchMode)
            }
            deleteAllButton.setOnClickListener {
                service.lifecycleScope.launch {
                    promptDeleteAll(ClipboardManager.haveUnpinned())
                }
            }
        }
    }

    override fun onCreateView(): View = ui.root

    private var promptMenu: PopupMenu? = null

    private fun promptDeleteAll(skipPinned: Boolean) {
        promptMenu?.dismiss()
        promptMenu = PopupMenu(context, ui.deleteAllButton).apply {
            menu.add(buildSpannedString {
                bold {
                    color(context.styledColorOrDefault(android.R.attr.colorAccent, theme.genericActiveForegroundColor)) {
                        append(context.getString(if (skipPinned) R.string.delete_all_except_pinned else R.string.delete_all_pinned_items))
                    }
                }
            }).isEnabled = false
            menu.add(android.R.string.cancel)
            menu.item(android.R.string.ok) {
                service.lifecycleScope.launch {
                    val ids = ClipboardManager.deleteAll(skipPinned)
                    showUndoSnackbar(*ids)
                }
            }
            setOnDismissListener {
                if (it === promptMenu) promptMenu = null
            }
            show()
        }
    }

    private val pendingDeleteIds = arrayListOf<Int>()

    @SuppressLint("RestrictedApi")
    private fun showUndoSnackbar(vararg id: Int) {
        id.forEach { pendingDeleteIds.add(it) }
        val str = context.resources.getString(R.string.num_items_deleted, pendingDeleteIds.size)
        snackbarInstance = Snackbar.make(snackbarCtx, ui.root, str, Snackbar.LENGTH_LONG)
            .setBackgroundTint(theme.popupBackgroundColor)
            .setTextColor(theme.popupTextColor)
            .setActionTextColor(theme.genericActiveBackgroundColor)
            .setAction(R.string.undo) {
                service.lifecycleScope.launch {
                    ClipboardManager.undoDelete(*pendingDeleteIds.toIntArray())
                    pendingDeleteIds.clear()
                }
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                    if (snackbarInstance === transientBottomBar) {
                        snackbarInstance = null
                    }
                    when (event) {
                        BaseCallback.DISMISS_EVENT_SWIPE,
                        BaseCallback.DISMISS_EVENT_MANUAL,
                        BaseCallback.DISMISS_EVENT_TIMEOUT -> {
                            service.lifecycleScope.launch {
                                ClipboardManager.realDelete()
                                pendingDeleteIds.clear()
                            }
                        }
                        BaseCallback.DISMISS_EVENT_ACTION,
                        BaseCallback.DISMISS_EVENT_CONSECUTIVE -> {
                            // user clicked "undo" or deleted more items which makes a new snackbar
                        }
                    }
                }
            }).apply {
                val hMargin = snackbarCtx.dp(24)
                val vMargin = snackbarCtx.dp(16)
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = hMargin
                    rightMargin = hMargin
                    bottomMargin = vMargin
                }
                ((view as FrameLayout).getChildAt(0) as SnackbarContentLayout).apply {
                    messageView.letterSpacing = 0f
                    actionView.letterSpacing = 0f
                }
                show()
            }
    }

    override fun onAttached() {
        val isEmpty = ClipboardManager.itemCount == 0
        val isListening = clipboardEnabledPref.getValue()
        val initialState = when {
            !isListening -> EnableListening
            isEmpty -> AddMore
            else -> Normal
        }
        stateMachine = ClipboardStateMachine.new(initialState, isEmpty, isListening) {
            ui.switchUiByState(it)
        }
        // manually switch to initial ui
        ui.switchUiByState(initialState)
        adapter.addLoadStateListener(loadStateListener)
        ui.searchKeyboards.forEach { it.onAttach() }
        ui.updateSearchQuery("")
        submitTabEntries(ui.tabsUi.activeTab)
        clipboardEnabledPref.registerOnChangeListener(clipboardEnabledListener)
    }

    override fun onDetached() {
        inSearchMode = false
        searchQuery = ""
        ui.setSearchMode(false)
        clipboardEnabledPref.unregisterOnChangeListener(clipboardEnabledListener)
        adapter.removeLoadStateListener(loadStateListener)
        ui.searchKeyboards.forEach { it.onDetach() }
        adapter.onDetached()
        adapterSubmitJob?.cancel()
        promptMenu?.dismiss()
        snackbarInstance?.dismiss()
    }

    override val title: String by lazy {
        context.getString(R.string.clipboard)
    }

    override fun onCreateBarExtension(): View = ui.extension
}
