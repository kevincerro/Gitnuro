@file:OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.jetpackduba.gitnuro.ui.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpackduba.gitnuro.app.generated.resources.*
import com.jetpackduba.gitnuro.common.systemSeparator
import com.jetpackduba.gitnuro.domain.extensions.fileName
import com.jetpackduba.gitnuro.domain.extensions.parentDirectoryPath
import com.jetpackduba.gitnuro.domain.models.*
import com.jetpackduba.gitnuro.extensions.backgroundIf
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.extensions.icon
import com.jetpackduba.gitnuro.extensions.iconColor
import com.jetpackduba.gitnuro.extensions.setClipboardText
import com.jetpackduba.gitnuro.keybindings.KeybindingOption
import com.jetpackduba.gitnuro.keybindings.matchesBinding
import com.jetpackduba.gitnuro.theme.abortButton
import com.jetpackduba.gitnuro.theme.addFile
import com.jetpackduba.gitnuro.theme.deleteFile
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import com.jetpackduba.gitnuro.ui.components.*
import com.jetpackduba.gitnuro.ui.components.tooltip.DelayedTooltip
import com.jetpackduba.gitnuro.ui.context_menu.ContextMenuElement
import com.jetpackduba.gitnuro.ui.dialogs.EditFileDialog
import com.jetpackduba.gitnuro.ui.context_menu.statusDirEntriesContextMenuItems
import com.jetpackduba.gitnuro.ui.context_menu.statusEntriesContextMenuItems
import com.jetpackduba.gitnuro.ui.context_menu.statusEntryContextMenuItems
import com.jetpackduba.gitnuro.ui.log.laneColor
import com.jetpackduba.gitnuro.ui.tree_files.TreeItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.io.File
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Recommended maximum length of the first line of a commit message.
 */
private const val COMMIT_SUMMARY_RECOMMENDED_LENGTH = 72

private enum class StatusBottomTab { COMMIT, STASH }

@Composable
fun StatusPane(
    statusState: StatusState,
    currentBranchName: String?,
    onAction: (StatusAction) -> Unit,
    onStashWithMessage: (String) -> Unit,
    onBlameFile: (String) -> Unit,
    onHistoryFile: (String) -> Unit,
    /** Requests to move the selected file: 1 to the next one, -1 to the previous one */
    selectionMoves: Flow<Int> = emptyFlow(),
) {
    val swapUncommittedChanges = statusState.swapUncommittedChanges
    val stagedListState = statusState.stagedLazyListState
    val unstagedListState = statusState.unstagedLazyListState

    val commitMessage = statusState.commitMessage

    val isAmend = statusState.isAmend
    val rebaseInteractiveState = statusState.rebaseInteractiveState
    val selectedUnstagedDiffEntries = statusState.selectedUnstagedDiffEntries
    val selectedStagedDiffEntries = statusState.selectedStagedDiffEntries

    val showAsTree = statusState.showAsTree
    val showSearchStaged = statusState.showSearchStaged
    val searchFilterStaged = statusState.searchFilterStaged
    val showSearchUnstaged = statusState.showSearchUnstaged
    val searchFilterUnstaged = statusState.searchFilterUnstaged

    val isAmenableRebaseInteractive =
        statusState.repositoryState.isRebasing && rebaseInteractiveState is RebaseInteractiveState.ProcessingCommits && rebaseInteractiveState.isCurrentStepAmenable

    var pushAfterCommit by remember { mutableStateOf(false) }
    var showDiscardAllConfirmation by remember { mutableStateOf(false) }

    val doCommit = {
        onAction(StatusAction.Commit(commitMessage.text, pushAfterCommit))
    }

    val canCommit = commitMessage.text.isNotEmpty() && statusState.hasStagedFiles
    val canAmend = commitMessage.text.isNotEmpty() && statusState.hasPreviousCommits

    val stagedFiles = statusState.getEntriesByEntryType(EntryType.STAGED)
    val unstagedFiles = statusState.getEntriesByEntryType(EntryType.UNSTAGED)
    val changedFilesCount = remember(stagedFiles, unstagedFiles) {
        (stagedFiles + unstagedFiles).map { it.filePath }.distinct().count()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface),
    ) {
        AnimatedVisibility(
            visible = statusState.isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colors.primaryVariant)
        }

        StatusHeader(
            changedFilesCount = changedFilesCount,
            currentBranchName = currentBranchName,
            onDiscardAll = { showDiscardAllConfirmation = true },
        )

        if (showDiscardAllConfirmation && changedFilesCount > 0) {
            DiscardAllConfirmation(
                onConfirm = {
                    showDiscardAllConfirmation = false
                    onAction(StatusAction.DiscardAll)
                },
                onCancel = { showDiscardAllConfirmation = false },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            ChangesViewOptions(
                showAsTree = showAsTree,
                sortDescending = statusState.sortDescending,
                canExpandAll = statusState.treeExpansionState.canExpandAll,
                canCollapseAll = statusState.treeExpansionState.canCollapseAll,
                onShowAsTreeChange = { onAction(StatusAction.ToggleShowAsTree) },
                onToggleSortOrder = { onAction(StatusAction.ToggleSortOrder) },
                onExpandAll = { onAction(StatusAction.ExpandAllDirectories) },
                onCollapseAll = { onAction(StatusAction.CollapseAllDirectories) },
            )
        }

        // The lists get the focus when one of their files is clicked/staged: the focused row may disappear (e.g. it
        // has been staged) and, with the focus lost, the tab wouldn't receive the arrow keys that move the selection
        val listsFocusRequester = remember { FocusRequester() }
        val listsScope = rememberCoroutineScope()

        val onListAction: (StatusAction) -> Unit = { action ->
            if (action is StatusAction.SelectEntry || action is StatusAction.EntryAction) {
                listsFocusRequester.requestFocus()
            }

            onAction(action)
        }

        // Moves the selection to the next/previous file of the list (staged or unstaged) that has it
        val moveSelection: (Int) -> Unit = moveSelection@{ direction ->
            val entryType = when {
                selectedUnstagedDiffEntries.isNotEmpty() -> EntryType.UNSTAGED
                selectedStagedDiffEntries.isNotEmpty() -> EntryType.STAGED
                else -> return@moveSelection
            }

            val selectedEntries = when (entryType) {
                EntryType.STAGED -> selectedStagedDiffEntries
                EntryType.UNSTAGED -> selectedUnstagedDiffEntries
            }

            val listState = when (entryType) {
                EntryType.STAGED -> stagedListState
                EntryType.UNSTAGED -> unstagedListState
            }

            val entries = statusState.getVisibleEntries(entryType)
            val currentEntry = selectedEntries.last().statusEntry
            val currentIndex = entries.indexOfFirst { it is TreeItem.File<StatusEntry> && it.data == currentEntry }

            if (currentIndex < 0) {
                return@moveSelection
            }

            // Next file in that direction (directories of the tree are skipped)
            var nextIndex = currentIndex + direction

            while (nextIndex in entries.indices && entries[nextIndex] !is TreeItem.File<StatusEntry>) {
                nextIndex += direction
            }

            val nextEntryIndex = nextIndex
            val nextEntry = entries.getOrNull(nextEntryIndex) as? TreeItem.File<StatusEntry>

            if (nextEntry != null) {
                onAction(
                    StatusAction.SelectEntry(
                        statusEntry = nextEntry.data,
                        isCtrlPressed = false,
                        isMetaPressed = false,
                        isShiftPressed = false,
                        diffEntries = entries,
                        selectedEntries = selectedEntries,
                    )
                )

                listsScope.launch { listState.scrollToShowItem(nextEntryIndex) }
            }
        }

        // The arrow keys are received by the tab (wherever the focus is: the lists, the diff, a button...)
        val currentMoveSelection by rememberUpdatedState(moveSelection)

        LaunchedEffect(selectionMoves) {
            selectionMoves.collect { direction -> currentMoveSelection(direction) }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .focusRequester(listsFocusRequester)
                .focusable(),
        ) {
            @Composable
            fun staged() {
                StatusChangesList(
                    entryType = EntryType.STAGED,
                    statusState,
                    showSearchStaged,
                    showAsTree = showAsTree,
                    searchFilter = searchFilterStaged,
                    listState = stagedListState,
                    selectedEntries = selectedStagedDiffEntries,
                    filesCount = stagedFiles.count(),
                    onSearchFilterToggled = { onAction(StatusAction.SearchFilterToggledStaged(it)) },
                    onSearchFocused = { onAction(StatusAction.AddStagedSearchToCloseableView) },
                    onBlameFile = onBlameFile,
                    onHistoryFile = onHistoryFile,
                    onAction = onListAction,
                )
            }

            @Composable
            fun unstaged() {
                StatusChangesList(
                    entryType = EntryType.UNSTAGED,
                    statusState = statusState,
                    showSearch = showSearchUnstaged,
                    showAsTree = showAsTree,
                    searchFilter = searchFilterUnstaged,
                    listState = unstagedListState,
                    selectedEntries = selectedUnstagedDiffEntries,
                    filesCount = unstagedFiles.count(),
                    onSearchFilterToggled = { onAction(StatusAction.SearchFilterToggledUnstaged(it)) },
                    onSearchFocused = { onAction(StatusAction.AddUnstagedSearchToCloseableView) },
                    onBlameFile = onBlameFile,
                    onHistoryFile = onHistoryFile,
                    onAction = onListAction,
                )
            }

            // Unstaged files go first by default, the "swap" setting puts the staged ones on top
            if (swapUncommittedChanges) {
                staged()
                unstaged()
            } else {
                unstaged()
                staged()
            }
        }

        CommitPanel(
            statusState = statusState,
            canCommit = canCommit,
            canAmend = canAmend,
            isAmenableRebaseInteractive = isAmenableRebaseInteractive,
            stagedFilesCount = stagedFiles.count(),
            pushAfterCommit = pushAfterCommit,
            onPushAfterCommitChange = { pushAfterCommit = it },
            doCommit = doCommit,
            onStashWithMessage = onStashWithMessage,
            onAction = onAction,
        )
    }
}

/**
 * Scrolls the minimum needed to show the item at [index]: nothing if it's already visible, otherwise it ends up at the
 * top or at the bottom of the list depending on where it was.
 */
private suspend fun LazyListState.scrollToShowItem(index: Int) {
    val visibleItems = layoutInfo.visibleItemsInfo
    val firstVisibleItem = visibleItems.firstOrNull() ?: return
    val lastVisibleItem = visibleItems.last()

    when {
        index <= firstVisibleItem.index -> scrollToItem(index)
        index >= lastVisibleItem.index -> {
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

            scrollToItem(index, -(viewportHeight - lastVisibleItem.size).coerceAtLeast(0))
        }
    }
}

@Composable
private fun StatusHeader(
    changedFilesCount: Int,
    currentBranchName: String?,
    onDiscardAll: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp),
    ) {
        if (changedFilesCount > 0) {
            DelayedTooltip(
                text = "Discard all changes",
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                val shape = RoundedCornerShape(3.dp)
                val color = MaterialTheme.colors.deleteFile

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(shape)
                        .border(1.dp, color.copy(alpha = 0.8f), shape)
                        .background(color.copy(alpha = 0.15f))
                        .handMouseClickable(onClick = onDiscardAll),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.delete),
                        contentDescription = "Discard all changes",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (changedFilesCount == 1) "1 file change" else "$changedFilesCount file changes",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
            )

            if (currentBranchName != null) {
                Text(
                    text = "on",
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Text(
                    text = currentBranchName,
                    style = MaterialTheme.typography.body1,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(laneColor(0).copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscardAllConfirmation(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.deleteFile.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Discard all changes? Untracked files will be deleted. This can't be undone.",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )

        OutlinedActionButton(
            text = "Cancel",
            color = MaterialTheme.colors.onBackgroundSecondary,
            onClick = onCancel,
        )

        OutlinedActionButton(
            text = "Discard all",
            color = MaterialTheme.colors.deleteFile,
            modifier = Modifier.padding(start = 6.dp),
            onClick = onConfirm,
        )
    }
}

@Composable
fun ColumnScope.StatusChangesList(
    entryType: EntryType,
    statusState: StatusState,
    showSearch: Boolean,
    showAsTree: Boolean,
    searchFilter: TextFieldValue,
    listState: LazyListState,
    selectedEntries: List<DiffType.UncommittedDiff>,
    filesCount: Int,
    onSearchFilterToggled: (Boolean) -> Unit,
    onSearchFocused: () -> Unit,
    onBlameFile: (String) -> Unit,
    onHistoryFile: (String) -> Unit,
    onAction: (StatusAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var editingFile by remember { mutableStateOf<File?>(null) }

    val title = when (entryType) {
        EntryType.STAGED -> "Staged Files"
        EntryType.UNSTAGED -> "Unstaged Files"
    }

    val actionInfo = getActionInfo(entryType)
    val entries = statusState.getVisibleEntries(entryType)

    this.ChangesList(
        title = title,
        filesCount = filesCount,
        actionInfo = actionInfo,
        entryType = entryType,
        entries = entries,
        showSearch = showSearch,
        showAsTree = showAsTree,
        searchFilter = searchFilter,
        listState = listState,
        selectedEntries = selectedEntries,
        onSearchFilterToggled = onSearchFilterToggled,
        onSearchFocused = onSearchFocused,
        onBlameFile = onBlameFile,
        onHistoryFile = onHistoryFile,
        onAction = onAction,
        onCopy = { relative, entries ->
            scope.launch {
                copyEntriesPath(clipboard, entries, relative, statusState.repositoryPath)
            }
        },
        onEditFile = { entry ->
            statusState.repositoryPath?.let { repositoryPath ->
                editingFile = File(repositoryPath, entry.filePath)
            }
        },
    )

    editingFile?.let { file ->
        EditFileDialog(
            file = file,
            onDismiss = { editingFile = null },
        )
    }
}

@Composable
fun ColumnScope.ChangesList(
    title: String,
    filesCount: Int,
    actionInfo: ActionInfo,
    entryType: EntryType,
    showSearch: Boolean,
    searchFilter: TextFieldValue,
    listState: LazyListState,
    selectedEntries: List<DiffType.UncommittedDiff>,
    showAsTree: Boolean,
    entries: List<TreeItem<StatusEntry>>,
    onSearchFilterToggled: (Boolean) -> Unit,
    onSearchFocused: () -> Unit,
    onBlameFile: (String) -> Unit,
    onHistoryFile: (String) -> Unit,
    onAction: (StatusAction) -> Unit,
    onCopy: (relative: Boolean, entries: List<StatusEntry>) -> Unit,
    onEditFile: (StatusEntry) -> Unit,
) {
    fun entriesContextMenu(): (StatusEntry) -> List<ContextMenuElement> = { statusEntry ->
        statusEntryContextMenuItems(
            statusEntry = statusEntry,
            entryType = entryType,
            onStageToggle = { onAction(StatusAction.EntryAction(statusEntry)) },
            onReset = { onAction(StatusAction.Reset(statusEntry)) },
            onIgnoreFile = { onAction(StatusAction.IgnoreFile(statusEntry)) },
            onIgnoreExtension = { extension -> onAction(StatusAction.IgnoreExtension(extension)) },
            onStash = { onAction(StatusAction.StashFile(statusEntry)) },
            onHistory = { onHistoryFile(statusEntry.filePath) },
            onBlame = { onBlameFile(statusEntry.filePath) },
            onOpenInDiffTool = { onAction(StatusAction.OpenInDiffTool(statusEntry)) },
            onOpenInExternalEditor = { onAction(StatusAction.OpenInExternalEditor(statusEntry)) },
            onOpenInDefaultProgram = { onAction(StatusAction.OpenInDefaultProgram(statusEntry)) },
            onShowInFileManager = { onAction(StatusAction.ShowInFileManager(statusEntry)) },
            onCopyFilePath = { onCopy(false, listOf(statusEntry)) },
            onCreatePatch = { onAction(StatusAction.CreatePatch(statusEntry)) },
            onEditFile = { onEditFile(statusEntry) },
            onDelete = { onAction(StatusAction.Delete(statusEntry)) },
        )
    }

    fun selectedEntriesContextMenu(): (StatusEntry) -> List<ContextMenuElement> = {
        statusEntriesContextMenuItems(
            selectedEntriesCount = selectedEntries.count(),
            entryType = entryType,
            onDiscard = { onAction(StatusAction.DiscardSelected(entryType)) },
            onStageSelected = { onAction(StatusAction.SelectedEntriesAction(EntryType.UNSTAGED)) },
            onUnstageSelected = { onAction(StatusAction.SelectedEntriesAction(EntryType.STAGED)) },
            onCopyFilesPath = { relative ->
                onCopy(
                    relative,
                    selectedEntries.map { it.statusEntry },
                )
            },
        )
    }

    val showActionForSelected = remember(selectedEntries) { selectedEntries.count() > 1 }
    val keyboardModifiers = LocalWindowInfo.current.keyboardModifiers

    val allActionText = when {
        showActionForSelected -> actionInfo.applyToSelectedTitle
        entryType == EntryType.STAGED -> "Unstage All Changes"
        else -> "Stage All Changes"
    }

    ChangesSection(
        title = "$title ($filesCount)",
        allActionText = if (filesCount > 0) allActionText else null,
        allActionColor = if (entryType == EntryType.STAGED) MaterialTheme.colors.deleteFile else MaterialTheme.colors.addFile,
        showSearch = showSearch,
        searchFilter = searchFilter,
        onSearchFilterToggled = onSearchFilterToggled,
        onSearchFocused = onSearchFocused,
        onSearchFilterChanged = { onAction(StatusAction.SearchFilterChanged(it, entryType)) },
        listState = listState,
        onAllAction = {
            if (showActionForSelected) {
                onAction(StatusAction.SelectedEntriesAction(entryType))
            } else {
                onAction(StatusAction.AllEntriesAction(entryType))
            }
        },
    ) {
        items(entries, key = { it.fullPath }) { treeEntry ->
            val isEntrySelected = treeEntry is TreeItem.File<StatusEntry> &&
                    selectedEntries.any { entry ->
                        entry.statusEntry == treeEntry.data && ((entry.isUnstagedDiff && entryType == EntryType.UNSTAGED) ||
                                (entry.isStagedDiff && entryType == EntryType.STAGED))
                    }

            UncommittedTreeItemEntry(
                treeEntry,
                isSelected = isEntrySelected,
                actionTitle = actionInfo.applyToOneTitle,
                actionColor = actionInfo.color,
                showAsTree = showAsTree,
                onClick = {
                    if (treeEntry is TreeItem.File<StatusEntry>) {
                        onAction(
                            StatusAction.SelectEntry(
                                statusEntry = treeEntry.data,
                                isCtrlPressed = keyboardModifiers.isCtrlPressed,
                                isMetaPressed = keyboardModifiers.isMetaPressed,
                                isShiftPressed = keyboardModifiers.isShiftPressed,
                                diffEntries = entries,
                                selectedEntries = selectedEntries,
                            )
                        )
                    } else if (treeEntry is TreeItem.Dir) {
                        onAction(
                            StatusAction.TreeDirectoryToggle(
                                treeEntry.fullPath
                            )
                        )
                    }
                },
                onButtonClick = {
                    if (treeEntry is TreeItem.File<StatusEntry>) {
                        onAction(StatusAction.EntryAction(treeEntry.data))
                    }
                },
                onGenerateContextMenu = if (isEntrySelected && selectedEntries.count() > 1)
                    selectedEntriesContextMenu()
                else
                    entriesContextMenu(),
                onGenerateDirectoryContextMenu = { dir ->
                    statusDirEntriesContextMenuItems(
                        entryType = entryType,
                        onStageChanges = { onAction(StatusAction.DirectoryAction(dir.fullPath, entryType)) },
                        onDiscardDirectoryChanges = {},
                    )
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.ChangesSection(
    title: String,
    allActionText: String?,
    allActionColor: Color,
    showSearch: Boolean,
    searchFilter: TextFieldValue,
    listState: LazyListState,
    onSearchFilterToggled: (Boolean) -> Unit,
    onSearchFocused: () -> Unit,
    onSearchFilterChanged: (TextFieldValue) -> Unit,
    onAllAction: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = if (isExpanded) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .handMouseClickable { isExpanded = !isExpanded }
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (isExpanded) Res.drawable.expand_more else Res.drawable.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground,
                modifier = Modifier.size(16.dp),
            )

            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .weight(1f),
            )

            Icon(
                painter = painterResource(Res.drawable.search),
                contentDescription = "Filter files",
                tint = if (showSearch) MaterialTheme.colors.primaryVariant else MaterialTheme.colors.onBackgroundSecondary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(14.dp)
                    .handMouseClickable { onSearchFilterToggled(!showSearch) },
            )

            if (allActionText != null) {
                OutlinedActionButton(
                    text = allActionText,
                    color = allActionColor,
                    onClick = onAllAction,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.06f))
        )

        if (showSearch) {
            val searchFocusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                searchFocusRequester.requestFocus()
            }

            SearchTextField(
                searchFilter = searchFilter,
                onSearchFilterChanged = onSearchFilterChanged,
                searchFocusRequester = searchFocusRequester,
                onSearchFocused = onSearchFocused,
                onClose = { onSearchFilterToggled(false) },
            )
        }

        if (isExpanded) {
            ScrollableLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                this.content()
            }
        }
    }
}

@Composable
private fun OutlinedActionButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    val contentAlpha = if (enabled) 1f else 0.5f

    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colors.onBackground.copy(alpha = contentAlpha),
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            // Opaque so it hides whatever is below (e.g. the file name when shown over a row)
            .background(MaterialTheme.colors.surface)
            .background(color.copy(alpha = 0.15f * contentAlpha))
            .border(1.dp, color.copy(alpha = 0.8f * contentAlpha), shape)
            .handMouseClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun CommitPanel(
    statusState: StatusState,
    canCommit: Boolean,
    canAmend: Boolean,
    isAmenableRebaseInteractive: Boolean,
    stagedFilesCount: Int,
    pushAfterCommit: Boolean,
    onPushAfterCommitChange: (Boolean) -> Unit,
    doCommit: () -> Unit,
    onStashWithMessage: (String) -> Unit,
    onAction: (StatusAction) -> Unit,
) {
    val repositoryState = statusState.repositoryState
    val isSafeState = !repositoryState.isRebasing && !repositoryState.isMerging &&
            !repositoryState.isCherryPicking && !repositoryState.isReverting

    var selectedTab by remember { mutableStateOf(StatusBottomTab.COMMIT) }
    val tabsDividerColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f)
    val tab = if (isSafeState) selectedTab else StatusBottomTab.COMMIT

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .align(Alignment.CenterHorizontally)
                .width(80.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.12f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawLine(
                        color = tabsDividerColor,
                        start = Offset(0f, size.height - stroke / 2),
                        end = Offset(size.width, size.height - stroke / 2),
                        strokeWidth = stroke,
                    )
                },
            verticalAlignment = Alignment.Bottom,
        ) {
            BottomTab(
                title = "Commit",
                isSelected = tab == StatusBottomTab.COMMIT,
                onClick = { selectedTab = StatusBottomTab.COMMIT },
            ) { color -> CommitIcon(color) }

            if (isSafeState) {
                BottomTab(
                    title = "Stash",
                    isSelected = tab == StatusBottomTab.STASH,
                    onClick = { selectedTab = StatusBottomTab.STASH },
                ) { color ->
                    Icon(
                        painter = painterResource(Res.drawable.stash),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            when (tab) {
                StatusBottomTab.COMMIT -> CommitTabContent(
                    statusState = statusState,
                    canCommit = canCommit,
                    canAmend = canAmend,
                    isAmenableRebaseInteractive = isAmenableRebaseInteractive,
                    stagedFilesCount = stagedFilesCount,
                    pushAfterCommit = pushAfterCommit,
                    onPushAfterCommitChange = onPushAfterCommitChange,
                    doCommit = doCommit,
                    onAction = onAction,
                )

                StatusBottomTab.STASH -> StashTabContent(
                    hasChanges = statusState.hasStagedFiles || statusState.hasUnstagedFiles,
                    onStashWithMessage = onStashWithMessage,
                )
            }
        }
    }
}

@Composable
private fun BottomTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val color = if (isSelected) MaterialTheme.colors.onBackground else MaterialTheme.colors.onBackgroundSecondary

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .backgroundIf(isSelected, MaterialTheme.colors.onBackground.copy(alpha = 0.08f))
            .handMouseClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(color)

        if (isSelected) {
            Text(
                text = title,
                style = MaterialTheme.typography.body2,
                color = color,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/**
 * Commit symbol: a horizontal line crossing a circle.
 */
@Composable
private fun CommitIcon(color: Color) {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 14.dp)) {
        val strokeWidth = 1.5.dp.toPx()
        val radius = 3.5.dp.toPx()

        drawLine(color, Offset(0f, center.y), Offset(center.x - radius, center.y), strokeWidth)
        drawLine(color, Offset(center.x + radius, center.y), Offset(size.width, center.y), strokeWidth)
        drawCircle(color, radius, center, style = Stroke(strokeWidth))
    }
}

@Composable
private fun CommitTabContent(
    statusState: StatusState,
    canCommit: Boolean,
    canAmend: Boolean,
    isAmenableRebaseInteractive: Boolean,
    stagedFilesCount: Int,
    pushAfterCommit: Boolean,
    onPushAfterCommitChange: (Boolean) -> Unit,
    doCommit: () -> Unit,
    onAction: (StatusAction) -> Unit,
) {
    val repositoryState = statusState.repositoryState
    val commitMessage = statusState.commitMessage
    val isAmend = statusState.isAmend
    val isReadOnlyRebase = repositoryState.isRebasing && !isAmenableRebaseInteractive
    val isSafeState = !repositoryState.isRebasing && !repositoryState.isMerging &&
            !repositoryState.isCherryPicking && !repositoryState.isReverting

    val message = if (isReadOnlyRebase) statusState.previousCommitMessage.orEmpty() else commitMessage.text
    val (summary, description) = splitCommitMessage(message)

    fun updateMessage(newSummary: String, newDescription: String) {
        onAction(StatusAction.UpdateCommitMessage(TextFieldValue(joinCommitMessage(newSummary, newDescription))))
    }

    if (isSafeState) {
        SmallCheckbox(
            checked = isAmend,
            onCheckedChange = {
                if (statusState.hasPreviousCommits) {
                    onAction(StatusAction.ToggleAmend(!isAmend))
                }
            },
            text = stringResource(Res.string.uncommited_changes_amend_check),
        )
    }

    SummaryDescriptionField(
        summary = summary,
        description = description,
        summaryPlaceholder = if (isReadOnlyRebase) "Commit summary (read-only)" else "Commit summary",
        descriptionPlaceholder = "Description",
        enabled = !repositoryState.isRebasing || isAmenableRebaseInteractive,
        showCounter = true,
        onSummaryChange = { updateMessage(it, description) },
        onDescriptionChange = { updateMessage(summary, it) },
        onAccept = {
            if (isSafeState && (canCommit || isAmend && canAmend)) {
                doCommit()
            }
        },
    )

    when {
        repositoryState.isMerging -> MergeButtons(
            haveConflictsBeenSolved = !statusState.hasUnstagedFiles,
            onAbort = {
                onAction(StatusAction.ResetRepositoryState)
                onAction(StatusAction.UpdateCommitMessage(TextFieldValue("")))
            },
            onMerge = { doCommit() }
        )

        repositoryState.isRebasing -> {
            val rebaseInteractiveState = statusState.rebaseInteractiveState
            val isCurrentStepAmenable =
                (rebaseInteractiveState as? RebaseInteractiveState.ProcessingCommits)?.isCurrentStepAmenable == true
            val isAmendRebaseInteractive = statusState.isAmendRebaseInteractive

            RebasingButtons(
                canContinue = statusState.hasStagedFiles || statusState.hasUnstagedFiles ||
                        (isAmenableRebaseInteractive && isAmendRebaseInteractive && commitMessage.text.isNotEmpty()),
                haveConflictsBeenSolved = !statusState.hasUnstagedFiles,
                onAbort = {
                    onAction(StatusAction.AbortRebase)
                    onAction(StatusAction.UpdateCommitMessage(TextFieldValue("")))
                },
                onContinue = { onAction(StatusAction.ContinueRebase(commitMessage.text)) },
                onSkip = { onAction(StatusAction.SkipRebase) },
                isAmendable = isCurrentStepAmenable,
                isAmend = isAmendRebaseInteractive,
                onAmendChecked = { amend ->
                    if (amend && commitMessage.text.isEmpty()) {
                        onAction(StatusAction.UpdateCommitMessage(TextFieldValue(statusState.previousCommitMessage.orEmpty())))
                    }

                    onAction(StatusAction.ToggleAmendRebaseInteractive(amend))
                },
            )
        }

        repositoryState.isCherryPicking -> CherryPickingButtons(
            haveConflictsBeenSolved = !statusState.hasUnstagedFiles,
            onAbort = {
                onAction(StatusAction.ResetRepositoryState)
                onAction(StatusAction.UpdateCommitMessage(TextFieldValue("")))
            },
            onCommit = { doCommit() }
        )

        repositoryState.isReverting -> RevertingButtons(
            haveConflictsBeenSolved = !statusState.isLoading && statusState.haveConflictsBeenSolved,
            canCommit = commitMessage.text.isNotBlank(),
            onAbort = {
                onAction(StatusAction.ResetRepositoryState)
                onAction(StatusAction.UpdateCommitMessage(TextFieldValue("")))
            },
            onCommit = { doCommit() }
        )

        else -> {
            CommitOptions(
                pushAfterCommit = pushAfterCommit,
                onPushAfterCommitChange = onPushAfterCommitChange,
            )

            val buttonText = when {
                isAmend -> "Amend Previous Commit"
                stagedFilesCount == 0 -> "Stage Changes to Commit"
                commitMessage.text.isBlank() -> "Type a Message to Commit"
                stagedFilesCount == 1 -> "Commit Changes to 1 File"
                else -> "Commit Changes to $stagedFilesCount Files"
            }

            PanelPrimaryButton(
                text = buttonText,
                enabled = canCommit || (isAmend && canAmend),
                onClick = doCommit,
            ) { color -> CommitIcon(color) }
        }
    }
}

@Composable
private fun CommitOptions(
    pushAfterCommit: Boolean,
    onPushAfterCommitChange: (Boolean) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .handMouseClickable { isExpanded = !isExpanded },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (isExpanded) Res.drawable.expand_more else Res.drawable.chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colors.onBackgroundSecondary,
            modifier = Modifier.size(16.dp),
        )

        Text(
            text = "Commit options",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackgroundSecondary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }

    if (isExpanded) {
        SmallCheckbox(
            checked = pushAfterCommit,
            onCheckedChange = { onPushAfterCommitChange(!pushAfterCommit) },
            text = "Push after committing",
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun SmallCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(2.dp)

    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .handMouseClickable(onClick = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(shape)
                .border(1.dp, MaterialTheme.colors.onBackgroundSecondary, shape)
                .backgroundIf(checked, MaterialTheme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(Res.drawable.done),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun StashTabContent(
    hasChanges: Boolean,
    onStashWithMessage: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    fun stash() {
        if (!hasChanges) return

        val message = listOf(title.trim(), description.trim().replace("\n", " "))
            .filter { it.isNotEmpty() }
            .joinToString(" - ")

        onStashWithMessage(message)
        title = ""
        description = ""
    }

    SummaryDescriptionField(
        summary = title,
        description = description,
        summaryPlaceholder = "Stash title",
        descriptionPlaceholder = "Description",
        enabled = true,
        showCounter = false,
        onSummaryChange = { title = it },
        onDescriptionChange = { description = it },
        onAccept = { stash() },
    )

    PanelPrimaryButton(
        text = if (hasChanges) "Stash Changes" else "No Changes to Stash",
        enabled = hasChanges,
        onClick = { stash() },
        modifier = Modifier.padding(top = 8.dp),
    ) { color ->
        Icon(
            painter = painterResource(Res.drawable.stash),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SummaryDescriptionField(
    summary: String,
    description: String,
    summaryPlaceholder: String,
    descriptionPlaceholder: String,
    enabled: Boolean,
    showCounter: Boolean,
    onSummaryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAccept: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    val textColor = MaterialTheme.colors.onBackground.copy(alpha = if (enabled) 1f else 0.6f)

    // The fields keep their own state (text + cursor). The message stored in the view model reaches us
    // asynchronously, so feeding it straight back to the fields while typing makes the cursor jump when a key
    // is pressed before the previous change has come back. External values are only applied when they don't
    // come from our own edits (e.g. the message is cleared after committing or loaded when amending).
    var summaryValue by remember { mutableStateOf(TextFieldValue(summary, TextRange(summary.length))) }
    var descriptionValue by remember { mutableStateOf(TextFieldValue(description, TextRange(description.length))) }
    val pendingEdits = remember { mutableListOf<Pair<String, String>>() }

    LaunchedEffect(summary, description) {
        val external = summary to description
        val echoIndex = pendingEdits.indexOf(external)

        if (echoIndex >= 0) {
            // Our own edit coming back: drop it and any older edit
            repeat(echoIndex + 1) { pendingEdits.removeAt(0) }
        }

        // With no edits on their way, the fields must show the external value. This also covers an external change
        // that happens to match an old edit that never came back (e.g. the message cleared after committing).
        if (pendingEdits.isEmpty() && external != (summaryValue.text to descriptionValue.text)) {
            pendingEdits.clear()
            summaryValue = TextFieldValue(summary, TextRange(summary.length))
            descriptionValue = TextFieldValue(description, TextRange(description.length))
        }
    }

    fun onLocalSummaryChange(value: TextFieldValue) {
        val sanitized = value.copy(text = value.text.replace("\n", " "))
        val textChanged = sanitized.text != summaryValue.text
        summaryValue = sanitized

        if (textChanged) {
            pendingEdits.add(sanitized.text to descriptionValue.text)
            onSummaryChange(sanitized.text)
        }
    }

    fun onLocalDescriptionChange(value: TextFieldValue) {
        val textChanged = value.text != descriptionValue.text
        descriptionValue = value

        if (textChanged) {
            pendingEdits.add(summaryValue.text to value.text)
            onDescriptionChange(value.text)
        }
    }
    val placeholderColor = MaterialTheme.colors.onBackgroundSecondary
    val summaryStyle = TextStyle(fontSize = 15.sp, color = textColor)
    val descriptionStyle = MaterialTheme.typography.body2.copy(color = textColor)

    val acceptModifier = Modifier.onPreviewKeyEvent { keyEvent ->
        if (keyEvent.matchesBinding(KeybindingOption.TEXT_ACCEPT)) {
            onAccept()
            true
        } else {
            false
        }
    }

    Column(
        modifier = Modifier
            .padding(top = 2.dp)
            .fillMaxWidth()
            .height(130.dp)
            .clip(shape)
            .background(MaterialTheme.colors.background)
            .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.1f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (summaryValue.text.isEmpty()) {
                    Text(text = summaryPlaceholder, style = summaryStyle.copy(color = placeholderColor), maxLines = 1)
                }

                BasicTextField(
                    value = summaryValue,
                    onValueChange = ::onLocalSummaryChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = summaryStyle,
                    cursorBrush = SolidColor(MaterialTheme.colors.onBackground),
                    modifier = Modifier.fillMaxWidth().then(acceptModifier),
                )
            }

            if (showCounter) {
                val remaining = COMMIT_SUMMARY_RECOMMENDED_LENGTH - summaryValue.text.length

                Text(
                    text = remaining.toString(),
                    style = MaterialTheme.typography.body2,
                    color = if (remaining < 0) MaterialTheme.colors.error else MaterialTheme.colors.onBackgroundSecondary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (descriptionValue.text.isEmpty()) {
                Text(text = descriptionPlaceholder, style = descriptionStyle.copy(color = placeholderColor))
            }

            BasicTextField(
                value = descriptionValue,
                onValueChange = ::onLocalDescriptionChange,
                enabled = enabled,
                textStyle = descriptionStyle,
                cursorBrush = SolidColor(MaterialTheme.colors.onBackground),
                modifier = Modifier.fillMaxSize().then(acceptModifier),
            )
        }
    }
}

@Composable
private fun PanelPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.padding(top = 6.dp),
    icon: @Composable (Color) -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    val accent = MaterialTheme.colors.addFile
    val contentColor = MaterialTheme.colors.onBackground.copy(alpha = if (enabled) 1f else 0.5f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .border(1.dp, accent.copy(alpha = if (enabled) 0.9f else 0.4f), shape)
            .background(accent.copy(alpha = if (enabled) 0.25f else 0.08f))
            .handMouseClickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(contentColor)

        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Splits a commit message into its summary (first line) and description, removing the blank line that separates them.
 */
private fun splitCommitMessage(message: String): Pair<String, String> {
    val normalized = message.replace("\r\n", "\n")
    val summary = normalized.substringBefore("\n")
    val description = normalized.substringAfter("\n", missingDelimiterValue = "").removePrefix("\n")

    return summary to description
}

private fun joinCommitMessage(summary: String, description: String): String {
    return if (description.isEmpty()) summary else "$summary\n\n$description"
}

@Composable
fun MergeButtons(
    haveConflictsBeenSolved: Boolean,
    onAbort: () -> Unit,
    onMerge: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(36.dp)
    ) {
        AbortButton(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
                .fillMaxHeight(),
            onClick = onAbort
        )

        ConfirmationButton(
            text = stringResource(Res.string.uncommited_changes_primary_button_merge),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .fillMaxHeight(),
            enabled = haveConflictsBeenSolved,
            onClick = onMerge,
        )
    }
}

@Composable
fun CherryPickingButtons(
    haveConflictsBeenSolved: Boolean,
    onAbort: () -> Unit,
    onCommit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(36.dp)
    ) {
        AbortButton(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
                .fillMaxHeight(),
            onClick = onAbort
        )

        ConfirmationButton(
            text = stringResource(Res.string.uncommited_changes_primary_button_commit),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .fillMaxHeight(),
            enabled = haveConflictsBeenSolved,
            onClick = onCommit,
        )
    }
}

@Composable
fun RebasingButtons(
    canContinue: Boolean,
    isAmendable: Boolean,
    isAmend: Boolean,
    onAmendChecked: (Boolean) -> Unit,
    haveConflictsBeenSolved: Boolean,
    onAbort: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column {
        if (isAmendable) {
            CheckboxText(
                value = isAmend,
                onCheckedChange = { onAmendChecked(!isAmend) },
                text = stringResource(Res.string.uncommited_changes_amend_check)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(36.dp)
        ) {
            AbortButton(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
                    .fillMaxHeight(),
                onClick = onAbort
            )

            if (canContinue) {
                ConfirmationButton(
                    text = stringResource(Res.string.uncommited_changes_primary_button_continue),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .fillMaxHeight(),
                    enabled = haveConflictsBeenSolved,
                    onClick = onContinue,
                )
            } else {
                ConfirmationButton(
                    text = stringResource(Res.string.uncommited_changes_primary_button_skip),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                        .fillMaxHeight(),
                    onClick = onSkip,
                )
            }
        }
    }
}

@Composable
fun RevertingButtons(
    canCommit: Boolean,
    haveConflictsBeenSolved: Boolean,
    onAbort: () -> Unit,
    onCommit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(36.dp)
    ) {
        AbortButton(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            onClick = onAbort
        )

        ConfirmationButton(
            text = stringResource(Res.string.uncommited_changes_primary_button_continue),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .fillMaxHeight(),
            enabled = canCommit && haveConflictsBeenSolved,
            onClick = onCommit,
        )
    }
}

@Composable
fun AbortButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .handMouseClickable { onClick() }
            .focusable(false)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colors.abortButton),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.uncommited_changes_secondary_button_abort),
            style = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onError),
        )
    }
}

@Composable
fun ConfirmationButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    onClick: () -> Unit,
) {
    val (backgroundColor, contentColor) = if (enabled) {
        (MaterialTheme.colors.primary to MaterialTheme.colors.onPrimary)
    } else {
        (MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
            .compositeOver(MaterialTheme.colors.surface) to MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled))
    }

    Box(
        modifier = modifier
            .handMouseClickable { if (enabled) onClick() }
            .focusable(false) // TODO this and the abort button should be focusable (show some kind of border when focused?)
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body1.copy(color = contentColor),
        )
    }
}

@Composable
private fun UncommittedFileEntry(
    statusEntry: StatusEntry,
    isSelected: Boolean,
    showDirectory: Boolean,
    actionTitle: String,
    actionColor: Color,
    onClick: () -> Unit,
    onButtonClick: () -> Unit,
    depth: Int = 0,
    isTreeItem: Boolean = false,
    onGenerateContextMenu: (StatusEntry) -> List<ContextMenuElement>,
) {
    FileEntry(
        icon = statusEntry.icon,
        depth = depth,
        isTreeItem = isTreeItem,
        iconColor = statusEntry.iconColor,
        parentDirectoryPath = if (showDirectory) statusEntry.parentDirectoryPath else "",
        fileName = statusEntry.fileName,
        isSelected = isSelected,
        onClick = onClick,
        onDoubleClick = onButtonClick,
        onGenerateContextMenu = { onGenerateContextMenu(statusEntry) },
        trailingAction = { isHovered ->
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.CenterEnd),
                visible = isHovered,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val isStaged = statusEntry.entryType == EntryType.STAGED

                OutlinedActionButton(
                    text = if (isStaged) "Unstage File" else "Stage File",
                    color = if (isStaged) MaterialTheme.colors.deleteFile else MaterialTheme.colors.addFile,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onClick = onButtonClick,
                )
            }
        }
    )
}

@Composable
private fun TreeFileEntry(
    fileEntry: TreeItem.File<StatusEntry>,
    isSelected: Boolean,
    actionTitle: String,
    actionColor: Color,
    showAsTree: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onGenerateContextMenu: (StatusEntry) -> List<ContextMenuElement>,
) {
    UncommittedFileEntry(
        statusEntry = fileEntry.data,
        isSelected = isSelected,
        showDirectory = !showAsTree,
        actionTitle = actionTitle,
        actionColor = actionColor,
        onClick = onClick,
        onButtonClick = onDoubleClick,
        depth = fileEntry.depth,
        isTreeItem = showAsTree,
        onGenerateContextMenu = onGenerateContextMenu,
    )
}

@Composable
private fun UncommittedTreeItemEntry(
    entry: TreeItem<StatusEntry>,
    isSelected: Boolean,
    actionTitle: String,
    actionColor: Color,
    showAsTree: Boolean,
    onClick: () -> Unit,
    onButtonClick: () -> Unit,
    onGenerateContextMenu: (StatusEntry) -> List<ContextMenuElement>,
    onGenerateDirectoryContextMenu: (TreeItem.Dir) -> List<ContextMenuElement>,
) {
    when (entry) {
        is TreeItem.File -> TreeFileEntry(
            entry,
            isSelected,
            actionTitle,
            actionColor,
            showAsTree,
            onClick,
            onButtonClick,
            onGenerateContextMenu,
        )

        is TreeItem.Dir -> DirectoryEntry(
            dirName = entry.displayName,
            isExpanded = entry.isExpanded,
            onClick = onClick,
            depth = entry.depth,
            onGenerateContextMenu = { onGenerateDirectoryContextMenu(entry) },
        )
    }
}

@Composable
fun getActionInfo(entryType: EntryType): ActionInfo {
    val applyToOneTitle: String
    val applyToAllTitle: String
    val applyToSelectedTitle: String
    val icon: DrawableResource
    val color: Color
    val textColor: Color

    if (entryType == EntryType.STAGED) {
        applyToOneTitle = stringResource(Res.string.uncommited_changes_staged_item_action)
        applyToAllTitle = stringResource(Res.string.uncommited_changes_staged_all_items_action)
        applyToSelectedTitle = stringResource(Res.string.uncommited_changes_staged_selected_items_action)
        icon = Res.drawable.remove_done
        color = MaterialTheme.colors.error
        textColor = MaterialTheme.colors.onError
    } else {
        applyToOneTitle = stringResource(Res.string.uncommited_changes_unstaged_item_action)
        applyToAllTitle = stringResource(Res.string.uncommited_changes_unstaged_all_items_action)
        applyToSelectedTitle = stringResource(Res.string.uncommited_changes_unstaged_selected_items_action)
        icon = Res.drawable.done
        color = MaterialTheme.colors.primary
        textColor = MaterialTheme.colors.onPrimary
    }

    return ActionInfo(
        applyToOneTitle = applyToOneTitle,
        applyToAllTitle = applyToAllTitle,
        applyToSelectedTitle = applyToSelectedTitle,
        icon = icon,
        color = color,
        textColor = textColor,
    )
}


private suspend fun copyEntriesPath(
    clipboard: Clipboard,
    entries: List<StatusEntry>,
    relative: Boolean,
    repositoryPath: String?,
) {
    val pathsToCopy = entries.joinToString("\n") { entry ->
        if (relative) {
            entry.filePath
        } else {
            repositoryPath.orEmpty() + systemSeparator + entry.filePath
        }
    }

    clipboard.setClipboardText(pathsToCopy)
}