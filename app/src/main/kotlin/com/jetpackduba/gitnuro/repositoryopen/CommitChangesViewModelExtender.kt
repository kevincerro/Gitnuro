package com.jetpackduba.gitnuro.repositoryopen

import androidx.compose.ui.text.input.TextFieldValue
import com.jetpackduba.gitnuro.collectLatestInCoroutineScope
import com.jetpackduba.gitnuro.common.flows.combine
import com.jetpackduba.gitnuro.domain.errors.AppError
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.errOrNull
import com.jetpackduba.gitnuro.domain.errors.okOrNull
import com.jetpackduba.gitnuro.domain.extensions.filePath
import com.jetpackduba.gitnuro.domain.extensions.lowercaseContains
import com.jetpackduba.gitnuro.domain.models.DiffSelected
import com.jetpackduba.gitnuro.domain.models.DiffType
import com.jetpackduba.gitnuro.domain.models.ui.SelectedItem
import com.jetpackduba.gitnuro.domain.repositories.CloseableView
import com.jetpackduba.gitnuro.domain.usecases.GetCommitDiffEntriesUseCase
import com.jetpackduba.gitnuro.extensions.stateIn
import com.jetpackduba.gitnuro.ui.tree_files.TreeItem
import com.jetpackduba.gitnuro.ui.tree_files.allDirectoriesOf
import com.jetpackduba.gitnuro.ui.tree_files.entriesToTreeEntry
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.eclipse.jgit.diff.DiffEntry
import kotlin.time.Duration.Companion.milliseconds

class CommitChangesViewModelExtender @AssistedInject constructor(
    private val getCommitDiffEntriesUseCase: GetCommitDiffEntriesUseCase,
    private val commitSignatureVerifier: CommitSignatureVerifier,
    private val commitHistoryActions: CommitHistoryActions,
    @Assisted private val viewModelScope: CoroutineScope,
    @Assisted private val showAsTree: StateFlow<Boolean>,
    @Assisted private val selectedItem: StateFlow<SelectedItem>,
    @Assisted private val diffSelected: StateFlow<DiffSelected?>,
    @Assisted private val onDiffSelected: (DiffSelected) -> Unit,
    @Assisted private val onAlternateShowAsTree: () -> Unit,
    @Assisted("addCloseableView") private val addCloseableView: (CloseableView) -> Unit,
    @Assisted("removeCloseableView") private val removeCloseableView: (CloseableView) -> Unit,
) : CoroutineScope by viewModelScope {

    @AssistedFactory
    interface Factory {
        fun create(
            viewModelScope: CoroutineScope,
            showAsTree: StateFlow<Boolean>,
            selectedItem: StateFlow<SelectedItem>,
            diffSelected: StateFlow<DiffSelected?>,
            onDiffSelected: (DiffSelected) -> Unit,
            onAlternateShowAsTree: () -> Unit,
            @Assisted("addCloseableView") addCloseableView: (CloseableView) -> Unit,
            @Assisted("removeCloseableView") removeCloseableView: (CloseableView) -> Unit,
        ): CommitChangesViewModelExtender
    }

    val showSearch: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val searchFilter: StateFlow<TextFieldValue>
        field = MutableStateFlow(TextFieldValue(""))

    private val commitChangesTreeContractedDirectories = MutableStateFlow(emptyList<String>())
    private val sortDescending = MutableStateFlow(false)
    private val viewAllFiles = MutableStateFlow(false)

    /**
     * Files of the selected commit's tree, only loaded while "View all files" is enabled.
     * Like GitKraken, every directory starts collapsed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val commitFiles: Flow<CommitFiles?> = combine(
        selectedItem.map { (it as? SelectedItem.CommitBasedItem)?.commit?.hash }.distinctUntilChanged(),
        viewAllFiles,
    ) { hash, viewAll -> hash.takeIf { viewAll } }
        .distinctUntilChanged()
        .mapLatest { hash -> hash?.let { CommitFiles(it, commitHistoryActions.listFiles(it)) } }
        .onEach { commitFiles ->
            if (commitFiles != null) {
                commitChangesTreeContractedDirectories.value = allDirectoriesOf(commitFiles.files)
            }
        }
        .onStart { emit(null) }

    private data class CommitFiles(val commitHash: String, val files: List<String>)

    /**
     * Signature of the selected commit. It includes the commit hash so the UI never shows the signature of a
     * previously selected commit while the new one is being verified.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val commitSignature: StateFlow<CommitSignature?> = selectedItem
        .map { (it as? SelectedItem.CommitBasedItem)?.commit?.hash }
        .distinctUntilChanged()
        .mapLatest { hash -> hash?.let { commitSignatureVerifier.verify(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Preserve the last loaded changes (from the previously selected commit) to prevent the UI from flickering while loading the data
    private val lastLoadedCommitsChanges = MutableStateFlow<List<DiffEntry>>(emptyList())

    /**
     * Changes of the selected commit. Only depends on the commit (and the order): expanding folders, switching
     * Path/Tree or "View all files" don't reload them, so the list doesn't flicker.
     */
    private val loadedChanges = combine(selectedItem, sortDescending) { item, sortDescending ->
        loadCommitChangesFlow(item, sortDescending)
    }.flattenConcat()

    val commitChangesState = combine(
        loadedChanges,
        showAsTree,
        commitChangesTreeContractedDirectories,
        viewAllFiles.combine(commitFiles) { viewAll, files -> viewAll to files },
        showSearch,
        searchFilter,
    ) { state, showAsTree, contractedDirectories, (viewAllChecked, commitFiles), showSearch, searchFilter ->
        if (state == null) return@combine null

        val sortDescending = state.sortDescending
        // The filter is always visible (like GitKraken), it filters as soon as it has text
        val filterText = searchFilter.text.trim()
        val isFiltering = filterText.isNotEmpty()

        // The full tree is only shown once its files are loaded, until then the changes keep being shown
        val loadedFiles = commitFiles?.takeIf { viewAllChecked && it.commitHash == state.commit.hash }?.files
        val allFiles = if (loadedFiles != null) {
            // Deleted files aren't in the tree anymore, but they are part of the commit's changes
            val changesByPath = state.changes.associateBy { it.filePath }
            (loadedFiles + changesByPath.keys).distinct().map { path -> CommitTreeFile(path, changesByPath[path]) }
        } else {
            emptyList()
        }

        val changesFiltered = if (isFiltering) state.changes.filter { it.filePath.lowercaseContains(filterText) } else emptyList()
        val allFilesFiltered = if (isFiltering) allFiles.filter { it.path.lowercaseContains(filterText) } else allFiles

        state.copy(
            showAsTree = showAsTree,
            treeContractedDirectories = contractedDirectories,
            changesTree = entriesToTreeEntry(showAsTree, state.changes, contractedDirectories, sortDescending) { it.filePath },
            viewAllFilesChecked = viewAllChecked,
            viewAllFiles = loadedFiles != null,
            allFiles = allFiles,
            allFilesTree = if (loadedFiles != null) {
                entriesToTreeEntry(true, allFilesFiltered, contractedDirectories, sortDescending) { it.path }
            } else {
                emptyList()
            },
            showSearch = showSearch,
            searchFilter = searchFilter,
            changesFiltered = changesFiltered,
            changesTreeFiltered = entriesToTreeEntry(showAsTree, changesFiltered, contractedDirectories, sortDescending) { it.filePath },
        )
    }
        .onEach {
            if (it != null && !it.isLoading && it.changes.isNotEmpty()) {
                lastLoadedCommitsChanges.value = it.changes
            }
        }
        .stateIn(null as CommitChangesState?)

    private fun loadCommitChangesFlow(
        item: SelectedItem,
        sortDescending: Boolean,
    ) = channelFlow {
        if (item is SelectedItem.CommitBasedItem) {
            send(
                CommitChangesState(
                    isLoading = false,
                    commit = item.commit,
                    showAsTree = false,
                    sortDescending = sortDescending,
                    showSearch = false,
                    searchFilter = TextFieldValue(""),
                    changes = lastLoadedCommitsChanges.value.sortedByPath(sortDescending)
                )
            )

            val changesResultDeferred = async { getCommitDiffEntriesUseCase(item.commit) }

            val loadingJob = launch {
                delay(150.milliseconds)

                if (!changesResultDeferred.isCompleted) {
                    send(
                        CommitChangesState(
                            isLoading = true,
                            commit = item.commit,
                            showAsTree = false,
                            sortDescending = sortDescending,
                            showSearch = false,
                            searchFilter = TextFieldValue(""),
                        )
                    )
                }
            }

            val changesResult: Either<List<DiffEntry>, AppError> = changesResultDeferred.await()
            loadingJob.cancel()

            val error = changesResult.errOrNull()
            val changes = changesResult.okOrNull()?.sortedByPath(sortDescending)

            val state = CommitChangesState(
                commit = item.commit,
                changes = changes.orEmpty(),
                // The tree, filters and "View all files" are added by commitChangesState
                showAsTree = false,
                sortDescending = sortDescending,
                showSearch = false,
                searchFilter = TextFieldValue(""),
                error = error,
                isLoading = false,
            )

            send(state)
        } else {
            send(null)
        }
    }


    init {
        showSearch.collectLatestInCoroutineScope {
            if (it) {
                addSearchToCloseableView()
            } else {
                removeCommitChangesSearchFromCloseableView()
            }
        }
    }

    fun onAction(action: CommitChangesAction) {
        when (action) {
            is CommitChangesAction.SearchFilterChanged -> searchFilter.value = action.filter
            is CommitChangesAction.SearchFilterToggle -> showSearch.value = action.show
            is CommitChangesAction.SelectEntry -> {
                onDiffSelected(DiffSelected.CommitedChanges(setOf(DiffType.CommitDiff(action.entry))))
            }

            CommitChangesAction.ToggleShowAsTree -> onAlternateShowAsTree()
            CommitChangesAction.ToggleSortOrder -> sortDescending.value = !sortDescending.value
            is CommitChangesAction.ToggleViewAllFiles -> {
                viewAllFiles.value = action.viewAll
                // Back to the changed files: show them expanded
                if (!action.viewAll) commitChangesTreeContractedDirectories.value = emptyList()
            }

            is CommitChangesAction.TreeDirectoryToggle -> onDirectoryVisibilityToggle(action.path)
            CommitChangesAction.ExpandAllDirectories -> commitChangesTreeContractedDirectories.value = emptyList()
            CommitChangesAction.CollapseAllDirectories -> collapseAllDirectories()
            CommitChangesAction.AddSearchToCloseables -> addSearchToCloseableView()
        }
    }

    private fun addSearchToCloseableView() = viewModelScope.launch {
        addCloseableView(CloseableView.COMMIT_CHANGES_SEARCH)
    }


    private fun removeCommitChangesSearchFromCloseableView() = viewModelScope.launch {
        removeCloseableView(CloseableView.COMMIT_CHANGES_SEARCH)
    }

    fun onDirectoryVisibilityToggle(directoryPath: String) {
        val contractedDirectories = commitChangesTreeContractedDirectories.value

        if (contractedDirectories.contains(directoryPath)) {
            commitChangesTreeContractedDirectories.value -= directoryPath
        } else {
            commitChangesTreeContractedDirectories.value += directoryPath
        }
    }

    private fun collapseAllDirectories() {
        val changes = commitChangesState.value?.changes.orEmpty()

        val state = commitChangesState.value
        val paths = if (state?.viewAllFiles == true) state.allFiles.map { it.path } else changes.map { it.filePath }
        commitChangesTreeContractedDirectories.value = allDirectoriesOf(paths)
    }

    private fun List<DiffEntry>.sortedByPath(descending: Boolean): List<DiffEntry> {
        // entriesToTreeEntry sorts using the same criteria in both modes
        return entriesToTreeEntry(false, this, emptyList(), descending) { it.filePath }
            .mapNotNull { (it as? TreeItem.File<DiffEntry>)?.data }
    }

    fun searchFilterToggled(show: Boolean) {
        showSearch.value = show
        searchFilter.value = TextFieldValue("")
    }

}