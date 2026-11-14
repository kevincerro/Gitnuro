package com.jetpackduba.gitnuro.repositoryopen

import androidx.compose.ui.input.key.*
import com.jetpackduba.gitnuro.ui.PushBehindBanner
import com.jetpackduba.gitnuro.domain.models.ToolbarPullMode
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jetpackduba.gitnuro.LocalTabFocusRequester
import com.jetpackduba.gitnuro.Screen
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.bottom_info_bar_email_not_set
import com.jetpackduba.gitnuro.app.generated.resources.bottom_info_bar_name_and_email
import com.jetpackduba.gitnuro.app.generated.resources.bottom_info_bar_name_not_set
import com.jetpackduba.gitnuro.domain.models.Identity
import com.jetpackduba.gitnuro.domain.models.PullType
import com.jetpackduba.gitnuro.domain.models.RebaseInteractiveState
import com.jetpackduba.gitnuro.domain.models.RepositoryState
import com.jetpackduba.gitnuro.domain.models.ui.SelectedItem
import com.jetpackduba.gitnuro.domain.repositories.CloseableView
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.keybindings.KeybindingOption
import com.jetpackduba.gitnuro.keybindings.matchesBinding
import com.jetpackduba.gitnuro.ui.*
import com.jetpackduba.gitnuro.ui.components.BottomInfoBar
import com.jetpackduba.gitnuro.ui.components.TripleVerticalSplitPanel
import com.jetpackduba.gitnuro.ui.diff.DiffPane
import com.jetpackduba.gitnuro.ui.dialogs.ForcePushDialog
import com.jetpackduba.gitnuro.ui.log.Log
import com.jetpackduba.gitnuro.ui.status.StatusPane
import com.jetpackduba.gitnuro.updates.Update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun RepositoryOpenPage(
    repositoryOpenViewModel: RepositoryOpenViewModel,
    onNavigate: (Screen) -> Unit, // TODO Perhaps have specific callbacks instead of directly navigating
    onOpenAnotherRepository: (String) -> Unit,
) {
    val repositoryState by repositoryOpenViewModel.repositoryState.collectAsState()
    val selectedItem by repositoryOpenViewModel.selectedItem.collectAsState()
    val blameState by repositoryOpenViewModel.blameState.collectAsState()
    val showHistory by repositoryOpenViewModel.showHistory.collectAsState()

    val focusRequester = remember { FocusRequester() }
    var showOpenPopup by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable(true)
            .onKeyEvent {
                // Arrow keys that the focused component didn't use (text fields do) move the selected changed file,
                // wherever the focus is
                when {
                    it.matchesBinding(KeybindingOption.DOWN) -> repositoryOpenViewModel.moveStatusSelection(1)
                    it.matchesBinding(KeybindingOption.UP) -> repositoryOpenViewModel.moveStatusSelection(-1)
                    else -> false
                }
            }
            .onPreviewKeyEvent {
                when {
                    // Cmd/Ctrl + Option/Alt + F focuses the side panel filter, like GitKraken
                    it.type == KeyEventType.KeyDown && it.key == Key.F && it.isAltPressed &&
                            (it.isMetaPressed || it.isCtrlPressed) -> {
                        repositoryOpenViewModel.requestSidePanelFilterFocus()
                        true
                    }

                    it.matchesBinding(KeybindingOption.PULL) -> {
                        // Same as clicking the toolbar's Pull button
                        repositoryOpenViewModel.runToolbarPull()
                        true
                    }

                    it.matchesBinding(KeybindingOption.PUSH) -> {
                        repositoryOpenViewModel.push(force = false, pushTags = false)
                        true
                    }

                    it.matchesBinding(KeybindingOption.BRANCH_CREATE) -> {
                        onNavigate(Screen.BranchCreate(null))
                        true
                    }

                    it.matchesBinding(KeybindingOption.STASH) -> {
                        repositoryOpenViewModel.stash()
                        true
                    }

                    it.matchesBinding(KeybindingOption.STASH_POP) -> {
                        repositoryOpenViewModel.popStash()
                        true
                    }

                    it.matchesBinding(KeybindingOption.EXIT) -> {
                        repositoryOpenViewModel.closeLastView()
                        true
                    }

                    it.matchesBinding(KeybindingOption.REFRESH) -> {
                        repositoryOpenViewModel.refreshAll()
                        true
                    }

                    it.matchesBinding(KeybindingOption.OPEN_REPOSITORY) -> {
                        showOpenPopup = true
                        true
                    }

                    it.matchesBinding(KeybindingOption.SETTINGS) -> {
                        onNavigate(Screen.GlobalSettings)
                        true
                    }

                    else -> false
                }

            }
    ) {
        CompositionLocalProvider(
            LocalTabFocusRequester provides focusRequester
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val pushBehindState by repositoryOpenViewModel.pushBehindState.collectAsState()
                val toolbarPullMode by repositoryOpenViewModel.toolbarPullMode
                    .collectAsState(ToolbarPullMode.PULL_FAST_FORWARD_IF_POSSIBLE)
                val currentPushBehind = pushBehindState
                var showForcePushConfirmation by remember(currentPushBehind) { mutableStateOf(false) }

                if (currentPushBehind != null && showForcePushConfirmation) {
                    ForcePushDialog(
                        localRef = currentPushBehind.localRef,
                        remoteRef = currentPushBehind.remoteRef,
                        onDismiss = { showForcePushConfirmation = false },
                        onConfirm = {
                            showForcePushConfirmation = false
                            repositoryOpenViewModel.push(force = true, pushTags = false)
                        },
                    )
                }

                if (currentPushBehind != null) {
                    PushBehindBanner(
                        state = currentPushBehind,
                        pullTitle = when (toolbarPullMode) {
                            ToolbarPullMode.PULL_FAST_FORWARD_ONLY -> "Pull (fast-forward only)"
                            ToolbarPullMode.PULL_REBASE -> "Pull (rebase)"
                            else -> "Pull (fast-forward if possible)"
                        },
                        onPull = { repositoryOpenViewModel.pullFromPushBehind() },
                        onForcePush = { showForcePushConfirmation = true },
                        onCancel = { repositoryOpenViewModel.dismissPushBehind() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else Menu(
                    viewModel = repositoryOpenViewModel,
                    modifier = Modifier.fillMaxWidth(),
                    onCreateBranch = { onNavigate(Screen.BranchCreate(null)) },
                    onStashWithMessage = { onNavigate(Screen.StashWithMessage) },
                    onOpenAnotherRepository = { onOpenAnotherRepository(it) },
                    onOpenAnotherRepositoryFromPicker = {
                        val repoToOpen = repositoryOpenViewModel.openDirectoryPicker()

                        if (repoToOpen != null) {
                            onOpenAnotherRepository(repoToOpen)
                        }
                    },
                    onQuickActions = { onNavigate(Screen.QuickActions) },
                    onShowSettingsDialog = { onNavigate(Screen.GlobalSettings) },
                    showOpenPopup = showOpenPopup,
                    onShowOpenPopupChange = { showOpenPopup = it }
                )

                RepoContent(
                    repositoryOpenViewModel = repositoryOpenViewModel,
                    selectedItem = selectedItem,
                    repositoryState = repositoryState,
                    blameState = blameState,
                    showHistory = showHistory,
                    onNavigate = onNavigate,
                )
            }
        }

        Spacer(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colors.primaryVariant.copy(alpha = 0.2f))
        )


        val userInfo by repositoryOpenViewModel.authorInfoSimple.collectAsState()
        val newUpdate = repositoryOpenViewModel.update.collectAsState().value

        RepositoryOpenBottomInfoBar(
            userInfo,
            newUpdate,
            onOpenUrlInBrowser = { repositoryOpenViewModel.openUrlInBrowser(it) },
            onShowAuthorInfoDialog = { onNavigate(Screen.Author) },
        )
    }

    LaunchedEffect(repositoryOpenViewModel) {
        focusRequester.requestFocus()

        repositoryOpenViewModel.requestTabFocusFlow.collectLatest {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun RepositoryOpenBottomInfoBar(
    userInfo: UiDataState<Identity>,
    newUpdate: Update?,
    onOpenUrlInBrowser: (String) -> Unit,
    onShowAuthorInfoDialog: () -> Unit,
) {
    BottomInfoBar(
        newUpdate,
        onOpenUrlInBrowser,
        leadingContent = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .handMouseClickable { onShowAuthorInfoDialog() },
                contentAlignment = Alignment.Center,
            ) {
                val identity = userInfo.data

                if (identity != null) {
                    val name = identity.name ?: stringResource(Res.string.bottom_info_bar_name_not_set)
                    val email = identity.email ?: stringResource(Res.string.bottom_info_bar_email_not_set)

                    Text(
                        text = stringResource(Res.string.bottom_info_bar_name_and_email, name, email),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground,
                    )
                }
            }
        }
    )
}

@Composable
fun RepoContent(
    repositoryOpenViewModel: RepositoryOpenViewModel,
    selectedItem: SelectedItem,
    repositoryState: RepositoryState,
    blameState: BlameState,
    showHistory: Boolean,
    onNavigate: (Screen) -> Unit,
) {
    if (showHistory) {
        val historyViewModel = repositoryOpenViewModel.historyViewModel

        if (historyViewModel != null) {
            FileHistory(
                historyViewModel = historyViewModel,
                onClose = {
                    repositoryOpenViewModel.closeHistory()
                }
            )
        }
    } else {
        MainContentView(
            viewModel = repositoryOpenViewModel,
            selectedItem = selectedItem,
            repositoryState = repositoryState,
            blameState = blameState,
            onNavigate = onNavigate,
        )
    }
}

/**
 * Minimum width of the right panel (commit details / uncommitted changes) so its content is never cramped.
 */
private const val MIN_THIRD_PANE_WIDTH = 380f

@Composable
fun MainContentView(
    viewModel: RepositoryOpenViewModel,
    selectedItem: SelectedItem,
    repositoryState: RepositoryState,
    blameState: BlameState,
    onNavigate: (Screen) -> Unit,
) {
    val diffSelected by viewModel.diffSelected.collectAsState()
    val rebaseInteractiveState by viewModel.rebaseInteractiveState.collectAsState()
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    val statusState by viewModel.statusState.collectAsState()

    // We create 2 mutableStates here because using directly the flow makes compose lose some drag events for some reason
    var firstWidth by remember(viewModel) { mutableStateOf(viewModel.firstPaneWidth.value) }
    var thirdWidth by remember(viewModel) { mutableStateOf(viewModel.thirdPaneWidth.value) }

    LaunchedEffect(Unit) {
        // Update the pane widths if they have been changed in a different tab
        viewModel.onPanelsWidthPersisted.collectLatest {
            firstWidth = viewModel.firstPaneWidth.value
            thirdWidth = viewModel.thirdPaneWidth.value
        }
    }

    val isRebaseAwaitingInteraction = rebaseInteractiveState is RebaseInteractiveState.AwaitingInteraction
    val isBlameExpanded = blameState is BlameState.Loaded && !blameState.isMinimized

    // When a diff replaces the graph, the side panel collapses into a rail with a button to go back to the graph
    val isDiffShown = diffSelected?.entries?.count() == 1 && !isBlameExpanded

    // The user can also collapse the side panel manually into the same rail
    var isSidePanelCollapsed by remember(viewModel) { mutableStateOf(false) }
    val showRail = isDiffShown || isSidePanelCollapsed

    TripleVerticalSplitPanel(
        modifier = Modifier.fillMaxSize(),
        firstWidth = when {
            isRebaseAwaitingInteraction -> 0f
            showRail -> SIDE_PANEL_RAIL_WIDTH
            else -> firstWidth
        },
        thirdWidth = thirdWidth.coerceAtLeast(MIN_THIRD_PANE_WIDTH),
        first = {
            if (showRail) {
                SidePanelRail(
                    viewModel = viewModel,
                    // With a diff open, the button goes back to the graph. Otherwise it expands the panel again
                    buttonTooltip = if (isDiffShown) "Back to the graph" else "Expand sidebar",
                    onButtonClick = {
                        if (isDiffShown) viewModel.clearDiff() else isSidePanelCollapsed = false
                    },
                    onEntryClick = {
                        isSidePanelCollapsed = false
                        if (isDiffShown) viewModel.clearDiff()
                    },
                )
            } else {
                SidePanel(
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    onCollapse = { isSidePanelCollapsed = true },
                )
            }
        },
        second = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (
                    rebaseInteractiveState is RebaseInteractiveState.AwaitingInteraction
                    && diffSelected == null
                ) {
                    RebaseInteractive(viewModel, rebaseInteractiveState)
                } else if (blameState is BlameState.Loaded && !blameState.isMinimized) {
                    Blame(
                        filePath = blameState.filePath,
                        blameResult = blameState.blameResult,
                        closeViewFlow = viewModel.closeViewFlow,
                        onClose = { viewModel.resetBlameState() },
                        onSelectCommit = { viewModel.selectCommit(it, isStash = false) },
                        addToCloseables = { viewModel.addCloseableView(CloseableView.BLAME) },
                    )
                } else {
                    Column {
                        Box(modifier = Modifier.weight(1f, true)) {
                            val showDiff = diffSelected?.entries?.count() == 1

                            Log(
                                modifier = Modifier.focusable(!showDiff),
                                viewModel = viewModel,
                                selectedItem = selectedItem,
                                repositoryState = repositoryState,
                                // TODO Move nav outside of this? Applies to next lines
                                onCreateBranch = { onNavigate(Screen.BranchCreate(it)) },
                                onResetBranch = { onNavigate(Screen.BranchReset(it)) },
                                onCreateTag = { onNavigate(Screen.TagCreate(it)) },
                                onChangeUpstreamBranch = { onNavigate(Screen.BranchChangeUpstream(it)) },
                                onRenameBranch = { onNavigate(Screen.BranchRename(it)) },
                            )

                            if (showDiff) {
                                val tabFocusRequester = LocalTabFocusRequester.current

                                DiffPane(
                                    viewModel = viewModel,
                                    onCloseDiffView = {
                                        tabFocusRequester.requestFocus()
                                    }
                                )
                            }
                        }

                        if (blameState is BlameState.Loaded) { // BlameState.isMinimized is true here
                            MinimizedBlame(
                                filePath = blameState.filePath,
                                onExpand = { viewModel.expandBlame() },
                                onClose = { viewModel.resetBlameState() }
                            )
                        }
                    }
                }
            }
        },
        third = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
            ) {
                val commitChangesState = viewModel.commitChangesState.collectAsState().value

                if (commitChangesState != null) {
                    CommitChanges(
                        viewModel = viewModel,
                        onBlame = { viewModel.blameFile(it) },
                        onHistory = { viewModel.fileHistory(it) },
                        commitChangesState = commitChangesState,
                    )
                } else if (rebaseInteractiveState !is RebaseInteractiveState.AwaitingInteraction) {
                    val branchesState by viewModel.branchesState.collectAsState()

                    StatusPane(
                        statusState = statusState,
                        currentBranchName = branchesState.currentBranch?.simpleName,
                        onAction = { viewModel.onAction(it) },
                        onStashWithMessage = { viewModel.stashWithMessage(it) },
                        onBlameFile = { viewModel.blameFile(it) },
                        onHistoryFile = { viewModel.fileHistory(it) },
                        selectionMoves = viewModel.statusSelectionMoves,
                    )
                }
            }
        },
        onFirstSizeDragStarted = { currentWidth ->
            firstWidth = currentWidth
            viewModel.setFirstPaneWidth(currentWidth)
        },
        onFirstSizeChange = {
            val newWidth = firstWidth + it / density

            if (newWidth > 150 && !isRebaseAwaitingInteraction && !isDiffShown) {
                firstWidth = newWidth
                viewModel.setFirstPaneWidth(newWidth)
            }
        },
        onFirstSizeDragStopped = {
            scope.launch {
                viewModel.persistFirstPaneWidth()
            }
        },
        onThirdSizeChange = {
            val newWidth = thirdWidth.coerceAtLeast(MIN_THIRD_PANE_WIDTH) - it / density

            if (newWidth >= MIN_THIRD_PANE_WIDTH) {
                thirdWidth = newWidth
                viewModel.setThirdPaneWidth(newWidth)
            }
        },
        onThirdSizeDragStarted = { currentWidth ->
            thirdWidth = currentWidth
            viewModel.setThirdPaneWidth(currentWidth)
        },
        onThirdSizeDragStopped = {
            scope.launch {
                viewModel.persistThirdPaneWidth()
            }
        },
    )
}

