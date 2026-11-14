@file:OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jetpackduba.gitnuro.ui

import com.jetpackduba.gitnuro.repositoryopen.AheadBehind
import com.jetpackduba.gitnuro.ui.components.LocalRemoteAvatars
import com.jetpackduba.gitnuro.ui.components.RemoteAvatar
import com.jetpackduba.gitnuro.ui.components.remoteOwnerAvatarUrl
import com.jetpackduba.gitnuro.theme.addFile
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetpackduba.gitnuro.LocalTabFocusRequester
import com.jetpackduba.gitnuro.Screen
import com.jetpackduba.gitnuro.app.generated.resources.*
import com.jetpackduba.gitnuro.domain.models.*
import com.jetpackduba.gitnuro.domain.models.ui.SelectedItem
import com.jetpackduba.gitnuro.extensions.handOnHover
import com.jetpackduba.gitnuro.extensions.setClipboardText
import com.jetpackduba.gitnuro.repositoryopen.RepositoryOpenViewModel
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import com.jetpackduba.gitnuro.ui.components.AdjustableOutlinedTextField
import com.jetpackduba.gitnuro.ui.components.ScrollableLazyColumn
import com.jetpackduba.gitnuro.ui.components.SIDE_MENU_HEADER_HEIGHT
import com.jetpackduba.gitnuro.ui.components.SideMenuHeader
import com.jetpackduba.gitnuro.ui.components.SideMenuSubentry
import com.jetpackduba.gitnuro.ui.components.tooltip.DelayedTooltip
import com.jetpackduba.gitnuro.ui.context_menu.*
import com.jetpackduba.gitnuro.viewmodels.sidepanel.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SidePanel(
    viewModel: RepositoryOpenViewModel,
    onNavigate: (Screen) -> Unit,
    onCollapse: () -> Unit,
) {
    val filter by viewModel.filter.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()

    val branchesState by viewModel.branchesState.collectAsState()
    val remotesState by viewModel.remoteState.collectAsState()
    val tagsState by viewModel.tagsState.collectAsState()
    val stashesState by viewModel.stashesState.collectAsState()
    val submodulesState by viewModel.submodulesState.collectAsState()

    val searchFocusRequester = remember { FocusRequester() }
    val tabFocusRequester = LocalTabFocusRequester.current
    val collapsedFolders = remember { mutableStateListOf<String>() }

    val onToggleFolder: (String) -> Unit = { key ->
        if (collapsedFolders.contains(key)) collapsedFolders.remove(key) else collapsedFolders.add(key)
    }

    val viewingCount = branchesState.branches.count() +
            remotesState.remotes.sumOf { it.remoteInfo.branchesList.withoutRemoteHead().count() } +
            stashesState.stashes.count() +
            tagsState.tags.count()

    Column(
        modifier = Modifier.background(MaterialTheme.colors.surface)
    ) {
        // Like GitKraken, the collapse button sits alone at the top left, above the "Viewing" counter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp),
        ) {
            RailCircleButton(
                icon = Res.drawable.chevron_left,
                tooltip = "Collapse sidebar",
                onClick = onCollapse,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Viewing",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground,
            )

            Text(
                text = viewingCount.toString(),
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.primaryVariant,
                modifier = Modifier.padding(start = 4.dp),
            )

        }

        FilterTextField(
            value = filter,
            onValueChange = { newValue ->
                viewModel.newFilter(newValue)
            },
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .padding(bottom = 6.dp),
            textFieldModifier = Modifier
                .focusRequester(searchFocusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        viewModel.addSidePanelSearchToCloseables()
                    } else {
                        viewModel.removeSidePanelSearchFromCloseables()
                    }
                }
        )

        val aheadBehind by viewModel.branchesAheadBehind.collectAsState()
        val remoteAvatars = remember(remotesState) {
            remotesState.remotes
                .mapNotNull { remoteView ->
                    val remote = remoteView.remoteInfo.remote
                    remoteOwnerAvatarUrl(remote.fetchUri)?.let { remote.name to it }
                }
                .toMap()
        }

        LaunchedEffect(viewModel) {
            viewModel.focusSidePanelFilter.collect { searchFocusRequester.requestFocus() }
        }

        CompositionLocalProvider(LocalRemoteAvatars provides remoteAvatars) {
        // Every section (Local, Remote, Stashes, Tags and Submodules) is an independent block: its header stays fixed
        // and only its content scrolls. The expanded ones share the height of the panel and the edge between two of
        // them can be dragged to make one bigger and the other smaller.
        val blockWeights = viewModel.sidePanelBlockWeights
        val blockHeights = remember { IntArray(SIDE_PANEL_BLOCKS_COUNT) }
        val expandedBlocks = listOf(
            branchesState.isExpanded,
            remotesState.isExpanded,
            stashesState.isExpanded,
            tagsState.isExpanded,
            submodulesState.isExpanded,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp)
        ) {
        SidePanelBlock(
            index = 0,
            expandedBlocks = expandedBlocks,
            weights = blockWeights,
            heights = blockHeights,
        ) {
            localBranches(
                branchesState = branchesState,
                selectedItem = selectedItem,
                viewModel = viewModel,
                onChangeDefaultUpstreamBranch = { onNavigate(Screen.BranchChangeUpstream(it)) },
                onRenameBranch = { onNavigate(Screen.BranchRename(it)) },
                collapsedFolders = collapsedFolders,
                onToggleFolder = onToggleFolder,
                aheadBehind = aheadBehind,
            )
        }

        SidePanelBlock(
            index = 1,
            expandedBlocks = expandedBlocks,
            weights = blockWeights,
            heights = blockHeights,
        ) {
            remotes(
                remotesState = remotesState,
                viewModel = viewModel,
                onShowAddEditRemoteDialog = { onNavigate(Screen.AddEditRemote(it)) },
                collapsedFolders = collapsedFolders,
                onToggleFolder = onToggleFolder,
            )
        }

        SidePanelBlock(
            index = 2,
            expandedBlocks = expandedBlocks,
            weights = blockWeights,
            heights = blockHeights,
        ) {
            stashes(
                stashesState = stashesState,
                selectedItem = selectedItem,
                onExpandStashes = { viewModel.onExpandStashes() },
                onSelectStash = { viewModel.selectStash(it) },
                onApplyStash = { viewModel.applyStash(it) },
                onPopStash = { viewModel.popStash(it) },
                onDeleteStash = { viewModel.deleteStash(it) },
            )
        }

        SidePanelBlock(
            index = 3,
            expandedBlocks = expandedBlocks,
            weights = blockWeights,
            heights = blockHeights,
        ) {
            tags(
                tagsState = tagsState,
                selectedItem = selectedItem,
                viewModel = viewModel,
            )
        }

        SidePanelBlock(
            index = 4,
            expandedBlocks = expandedBlocks,
            weights = blockWeights,
            heights = blockHeights,
        ) {
            submodules(
                submodulesState = submodulesState,
                viewModel = viewModel,
                onAddSubmodule = { onNavigate(Screen.SubmoduleAdd) },
            )
        }
        }
        }
    }
}

private const val SIDE_PANEL_BLOCKS_COUNT = 5

/** An expanded block can't be made smaller than this by dragging its edges */
private val SIDE_PANEL_BLOCK_MIN_HEIGHT = 64.dp

/**
 * Block of the side panel with its own scroll. While it's expanded it shares the available height with the other
 * expanded blocks according to its weight, otherwise it's only as tall as its header.
 *
 * The top edge of an expanded block can be dragged to resize it against the closest expanded block above it.
 */
@Composable
private fun ColumnScope.SidePanelBlock(
    index: Int,
    expandedBlocks: List<Boolean>,
    weights: MutableList<Float>,
    heights: IntArray,
    content: LazyListScope.() -> Unit,
) {
    val isExpanded = expandedBlocks[index]
    val previousExpandedIndex = (index - 1 downTo 0).firstOrNull { expandedBlocks[it] }
    val minHeight = with(LocalDensity.current) { SIDE_PANEL_BLOCK_MIN_HEIGHT.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.weight(weights[index]) else Modifier)
            .onSizeChanged { heights[index] = it.height },
    ) {
        if (isExpanded) {
            ScrollableLazyColumn(
                modifier = Modifier.fillMaxWidth(),
                // The scrollbar belongs to the content, it starts below the fixed header of the section
                scrollbarTopPadding = SIDE_MENU_HEADER_HEIGHT,
                content = content,
            )
        } else {
            // Only the headers. Not the scrollable column: its scrollbar fills the available height, so a collapsed
            // block would take the whole panel and leave no space for the expanded ones
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }

        if (isExpanded && previousExpandedIndex != null) {
            SidePanelBlockResizeHandle(
                modifier = Modifier.align(Alignment.TopCenter),
                onDrag = { delta ->
                    val totalHeight = (heights[previousExpandedIndex] + heights[index]).toFloat()
                    val totalWeight = weights[previousExpandedIndex] + weights[index]

                    if (totalHeight > 0f && totalWeight > 0f) {
                        // The height of the block above is taken from its weight: the measured ones aren't updated
                        // until the next layout and several drag events can arrive before it
                        val previousHeight = totalHeight * weights[previousExpandedIndex] / totalWeight
                        val blockMinHeight = minHeight.coerceAtMost(totalHeight / 2f)
                        val newPreviousHeight = (previousHeight + delta)
                            .coerceIn(blockMinHeight, totalHeight - blockMinHeight)
                        val newPreviousWeight = totalWeight * newPreviousHeight / totalHeight

                        weights[previousExpandedIndex] = newPreviousWeight
                        weights[index] = totalWeight - newPreviousWeight
                    }
                },
            )
        }
    }
}

@Composable
private fun SidePanelBlockResizeHandle(
    modifier: Modifier,
    onDrag: (Float) -> Unit,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .hoverable(interactionSource)
            .pointerHoverIcon(resizePointerIconNorth)
            .draggable(
                state = rememberDraggableState { delta -> currentOnDrag(delta) },
                orientation = Orientation.Vertical,
                interactionSource = interactionSource,
            ),
    ) {
        if (isHovered || isDragged) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colors.primaryVariant)
            )
        }
    }
}

/**
 * Compact filter like GitKraken's: placeholder with its shortcut and the search icon at the end.
 */
@Composable
fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    textFieldModifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    val isMac = remember { System.getProperty("os.name").orEmpty().lowercase().contains("mac") }
    val placeholder = placeholder ?: if (isMac) "Filter (⌘ + Option + f)" else "Filter (Ctrl + Alt + F)"
    val shape = RoundedCornerShape(3.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(shape)
            .background(MaterialTheme.colors.background)
            .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.12f), shape)
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackgroundSecondary,
                    maxLines = 1,
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colors.onBackground),
                modifier = Modifier.fillMaxWidth().then(textFieldModifier),
            )
        }

        if (value.isNotEmpty()) {
            Icon(
                painterResource(Res.drawable.close),
                contentDescription = "Clear filter",
                tint = MaterialTheme.colors.onBackground,
                modifier = Modifier
                    .size(14.dp)
                    .handMouseClickable { onValueChange("") },
            )
        } else {
            Icon(
                painterResource(Res.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackgroundSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun LazyListScope.localBranches(
    branchesState: BranchesState,
    selectedItem: SelectedItem,
    viewModel: RepositoryOpenViewModel,
    onChangeDefaultUpstreamBranch: (Branch) -> Unit,
    onRenameBranch: (Branch) -> Unit,
    collapsedFolders: List<String>,
    onToggleFolder: (String) -> Unit,
    aheadBehind: Map<String, AheadBehind>,
) {
    val isExpanded = branchesState.isExpanded
    val branches = branchesState.branches
    val currentBranch = branchesState.currentBranch

    stickyHeader {
        ContextMenu(
            items = { emptyList() }
        ) {
            SideMenuHeader(
                text = stringResource(Res.string.side_pane_local_branches_title),
                icon = painterResource(Res.drawable.computer),
                itemsCount = branches.count(),
                hoverIcon = null,
                isExpanded = isExpanded,
                onExpand = { viewModel.onExpandBranches() }
            )
        }
    }

    if (isExpanded) {
        val rows = buildBranchTree(
            branches = branches,
            keyPrefix = "local",
            collapsedFolders = collapsedFolders,
            displayName = { it.simpleName },
        )

        items(rows, key = { it.key }) { row ->
            if (row is BranchTreeRow.Folder) {
                BranchFolder(row, onClick = { onToggleFolder(row.key) })
                return@items
            }

            val branch = (row as BranchTreeRow.Leaf).branch
            val scope = rememberCoroutineScope()
            val clipboard = LocalClipboard.current

            Branch(
                branch = branch,
                displayName = row.name,
                depth = row.depth,
                aheadBehind = aheadBehind[branch.name],
                isSelectedItem = selectedItem is SelectedItem.BranchItem && selectedItem.branch == branch,
                currentBranch = currentBranch,
                onBranchClicked = { viewModel.selectBranch(branch) },
                onBranchDoubleClicked = { if (!branch.isSameBranch(currentBranch)) viewModel.checkoutBranch(branch) },
                onCheckoutBranch = { viewModel.checkoutBranch(it) },
                onMergeBranch = { viewModel.mergeBranch(it) },
                onRebaseBranch = { viewModel.rebaseBranch(it) },
                onDeleteBranch = { viewModel.deleteBranch(it) },
                onChangeDefaultUpstreamBranch = { onChangeDefaultUpstreamBranch(it) },
                onRenameBranch = { onRenameBranch(it) },
                onCopyBranchNameToClipboard = {
                    scope.launch {
                        clipboard.setClipboardText(it.simpleName)
                    }
                },
            )
        }
    }
}

fun LazyListScope.remotes(
    remotesState: RemotesState,
    viewModel: RepositoryOpenViewModel,
    onShowAddEditRemoteDialog: (Remote?) -> Unit,
    collapsedFolders: List<String>,
    onToggleFolder: (String) -> Unit,
) {
    val isExpanded = remotesState.isExpanded
    val remotes = remotesState.remotes

    stickyHeader {
        SideMenuHeader(
            text = stringResource(Res.string.side_pane_remotes_title),
            icon = painterResource(Res.drawable.cloud),
            // Like GitKraken, the number of remote branches (not of remotes)
            itemsCount = remotes.sumOf { it.remoteInfo.branchesList.withoutRemoteHead().count() },
            hoverIcon = {
                IconButton(
                    onClick = { onShowAddEditRemoteDialog(null) },
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(16.dp)
                        .handOnHover(),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.add),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(),
                        tint = MaterialTheme.colors.onBackground,
                    )
                }
            },
            isExpanded = isExpanded,
            onExpand = { viewModel.onExpandRemotes() }
        )
    }

    if (isExpanded) {
        for (remote in remotes) {
            item {
                Remote(
                    remote = remote,
                    onEditRemote = {
                        val wrapper = remote.remoteInfo.remote
                        onShowAddEditRemoteDialog(wrapper)
                    },
                    onDeleteRemote = { viewModel.deleteRemote(remote.remoteInfo) },
                    onRemoteClicked = { viewModel.onRemoteClicked(remote) },
                    onFetchBranches = { viewModel.onFetchRemoteBranches(remote) },
                )
            }

            if (remote.isExpanded) {
                val rows = buildBranchTree(
                    branches = remote.remoteInfo.branchesList.withoutRemoteHead(),
                    keyPrefix = "remote/${remote.remoteInfo.remote.name}",
                    collapsedFolders = collapsedFolders,
                    displayName = { it.simpleName },
                    baseDepth = 1,
                )

                items(rows, key = { it.key }) { row ->
                    if (row is BranchTreeRow.Folder) {
                        BranchFolder(row, onClick = { onToggleFolder(row.key) })
                        return@items
                    }

                    val remoteBranch = (row as BranchTreeRow.Leaf).branch
                    val scope = rememberCoroutineScope()
                    val clipboard = LocalClipboard.current
                    RemoteBranches(
                        remoteBranch = remoteBranch,
                        displayName = row.name,
                        depth = row.depth,
                        currentBranch = remotesState.currentBranch,
                        onBranchClicked = { viewModel.selectBranch(remoteBranch) },
                        onCheckoutBranch = { viewModel.checkoutRemoteBranch(it) },
                        onDeleteBranch = { viewModel.deleteRemoteBranch(it) },
                        onPushRemoteBranch = { viewModel.pushToRemoteBranch(it) },
                        onPullRemoteBranch = { viewModel.pullFromRemoteBranch(it) },
                        onRebaseRemoteBranch = { viewModel.rebaseBranch(it) },
                        onMergeRemoteBranch = { viewModel.mergeBranch(it) },
                        onCopyBranchNameToClipboard = {
                            scope.launch {
                                clipboard.setClipboardText(it.simpleName)
                            }
                        }
                    )
                }
            }
        }
    }
}


fun LazyListScope.tags(
    tagsState: TagsState,
    viewModel: RepositoryOpenViewModel,
    selectedItem: SelectedItem,
) {
    val isExpanded = tagsState.isExpanded
    val tags = tagsState.tags

    stickyHeader {
        ContextMenu(
            items = { emptyList() }
        ) {
            SideMenuHeader(
                text = stringResource(Res.string.side_pane_tags_title),
                icon = painterResource(Res.drawable.tag),
                itemsCount = tags.count(),
                hoverIcon = null,
                isExpanded = isExpanded,
                onExpand = { viewModel.onExpandTags() }
            )
        }
    }

    if (isExpanded) {
        items(tags, key = { it.name }) { tag ->
            Tag(
                tag,
                isSelected = selectedItem is SelectedItem.TagItem && selectedItem.tag == tag,
                onTagClicked = { viewModel.selectTag(tag) },
                onCheckoutTag = { viewModel.checkoutTagCommit(tag) },
                onDeleteTag = { viewModel.deleteTag(tag) }
            )
        }
    }
}

fun LazyListScope.stashes(
    stashesState: StashesState,
    selectedItem: SelectedItem,
    onExpandStashes: () -> Unit,
    onSelectStash: (Commit) -> Unit,
    onApplyStash: (Commit) -> Unit,
    onPopStash: (Commit) -> Unit,
    onDeleteStash: (Commit) -> Unit,
) {
    val isExpanded = stashesState.isExpanded
    val stashes = stashesState.stashes

    stickyHeader {
        ContextMenu(
            items = { emptyList() }
        ) {
            SideMenuHeader(
                text = stringResource(Res.string.side_pane_stashes_title),
                icon = painterResource(Res.drawable.stash),
                itemsCount = stashes.count(),
                hoverIcon = null,
                isExpanded = isExpanded,
                onExpand = onExpandStashes,
            )
        }
    }

    if (isExpanded) {
        items(stashes, key = { it.hash }) { stash ->
            Stash(
                stash,
                isSelected = selectedItem is SelectedItem.CommitItem && selectedItem.isStash && selectedItem.commit.hash == stash.hash,
                onClick = { onSelectStash(stash) },
                onApply = { onApplyStash(stash) },
                onPop = { onPopStash(stash) },
                onDelete = { onDeleteStash(stash) },
            )
        }
    }
}

fun LazyListScope.submodules(
    submodulesState: SubmodulesState,
    viewModel: RepositoryOpenViewModel,
    onAddSubmodule: () -> Unit,
) {
    val isExpanded = submodulesState.isExpanded
    val submodules = submodulesState.submodules

    stickyHeader {
        ContextMenu(
            items = { emptyList() }
        ) {
            SideMenuHeader(
                text = stringResource(Res.string.side_pane_submodules_title),
                icon = painterResource(Res.drawable.topic),
                itemsCount = submodules.count(),
                hoverIcon = {
                    IconButton(
                        onClick = onAddSubmodule,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(16.dp)
                            .handOnHover(),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.add),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize(),
                            tint = MaterialTheme.colors.onBackground,
                        )
                    }
                },
                isExpanded = isExpanded,
                onExpand = { viewModel.onExpandSubmodules() }
            )
        }
    }

    if (isExpanded) {
        items(submodules, key = { it.first }) { submodule ->
            Submodule(
                submodule = submodule,
                onInitializeSubmodule = { viewModel.initializeSubmodule(submodule.first) },
//                onDeinitializeSubmodule = { submodulesViewModel.onDeinitializeSubmodule(submodule.first) },
                onSyncSubmodule = { viewModel.syncSubmodule(submodule.first) },
                onUpdateSubmodule = { viewModel.updateSubmodule(submodule.first) },
                onOpenSubmoduleInTab = { viewModel.onOpenSubmoduleInTab(submodule.first) },
                onDeleteSubmodule = { viewModel.deleteSubmodule(submodule.first) },
            )
        }
    }
}

@Composable
private fun Branch(
    branch: Branch,
    displayName: String,
    depth: Int,
    aheadBehind: AheadBehind?,
    currentBranch: Branch?,
    isSelectedItem: Boolean,
    onBranchClicked: () -> Unit,
    onBranchDoubleClicked: () -> Unit,
    onCheckoutBranch: (Branch) -> Unit,
    onMergeBranch: (Branch) -> Unit,
    onRebaseBranch: (Branch) -> Unit,
    onDeleteBranch: (Branch) -> Unit,
    onChangeDefaultUpstreamBranch: (Branch) -> Unit,
    onRenameBranch: (Branch) -> Unit,
    onCopyBranchNameToClipboard: (Branch) -> Unit,
) {
    val isCurrentBranch = currentBranch?.name == branch.name

    ContextMenu(
        items = {
            branchContextMenuItems(
                branch = branch,
                currentBranch = currentBranch,
                isCurrentBranch = isCurrentBranch,
                isLocal = true,
                onCheckoutBranch = onCheckoutBranch,
                onMergeBranch = onMergeBranch,
                onDeleteBranch = onDeleteBranch,
                onRebaseBranch = onRebaseBranch,
                onPushToRemoteBranch = {},
                onPullFromRemoteBranch = {},
                onChangeDefaultUpstreamBranch = onChangeDefaultUpstreamBranch,
                onRenameBranch = onRenameBranch,
                onCopyBranchNameToClipboard = onCopyBranchNameToClipboard
            )
        }
    ) {
        // Like GitKraken, the current branch has a green row with a checked checkbox at the start
        SideMenuSubentry(
            text = displayName,
            iconResourcePath = Res.drawable.branch,
            extraPadding = (TREE_DEPTH_PADDING * depth).dp,
            isSelected = isSelectedItem,
            backgroundColor = if (isCurrentBranch) MaterialTheme.colors.addFile.copy(alpha = 0.3f) else null,
            leading = if (isCurrentBranch) {
                { CurrentBranchCheckbox() }
            } else {
                null
            },
            onClick = onBranchClicked,
            onDoubleClick = onBranchDoubleClicked,
        ) {
            if (aheadBehind != null) {
                AheadBehindLabel(aheadBehind)
            }
        }
    }
}


@Composable
private fun Remote(
    remote: RemoteView,
    onEditRemote: () -> Unit,
    onDeleteRemote: () -> Unit,
    onFetchBranches: () -> Unit,
    onRemoteClicked: () -> Unit,
) {
    ContextMenu(
        items = {
            remoteContextMenu(
                onEdit = onEditRemote,
                onDelete = onDeleteRemote,
                onFetch = onFetchBranches,
            )
        }
    ) {
        // Like GitKraken, the remote shows its owner's avatar (a cloud when it's unknown)
        SideMenuSubentry(
            text = remote.remoteInfo.remote.name,
            iconResourcePath = Res.drawable.cloud,
            onClick = onRemoteClicked,
            isSelected = false,
            iconContent = {
                RemoteAvatar(
                    remoteName = remote.remoteInfo.remote.name,
                    tint = MaterialTheme.colors.onBackgroundSecondary,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}


@Composable
private fun RemoteBranches(
    remoteBranch: Branch,
    displayName: String,
    depth: Int,
    currentBranch: Branch?,
    onBranchClicked: () -> Unit,
    onCheckoutBranch: (Branch) -> Unit,
    onDeleteBranch: (Branch) -> Unit,
    onPushRemoteBranch: (Branch) -> Unit,
    onPullRemoteBranch: (Branch) -> Unit,
    onRebaseRemoteBranch: (Branch) -> Unit,
    onMergeRemoteBranch: (Branch) -> Unit,
    onCopyBranchNameToClipboard: (Branch) -> Unit,
) {
    ContextMenu(
        items = {
            branchContextMenuItems(
                branch = remoteBranch,
                currentBranch = currentBranch,
                isCurrentBranch = false,
                isLocal = false,
                onCheckoutBranch = onCheckoutBranch,
                onMergeBranch = onMergeRemoteBranch,
                onDeleteBranch = {},
                onDeleteRemoteBranch = onDeleteBranch,
                onRebaseBranch = onRebaseRemoteBranch,
                onPushToRemoteBranch = onPushRemoteBranch,
                onPullFromRemoteBranch = onPullRemoteBranch,
                onChangeDefaultUpstreamBranch = {},
                onRenameBranch = {},
                onCopyBranchNameToClipboard = onCopyBranchNameToClipboard
            )
        }
    ) {
        SideMenuSubentry(
            text = displayName,
            extraPadding = (TREE_DEPTH_PADDING * depth).dp,
            isSelected = false,
            iconResourcePath = Res.drawable.branch,
            onClick = onBranchClicked,
            onDoubleClick = { onCheckoutBranch(remoteBranch) },
        )
    }
}

@Composable
private fun Tag(
    tag: Tag,
    isSelected: Boolean,
    onTagClicked: () -> Unit,
    onCheckoutTag: () -> Unit,
    onDeleteTag: () -> Unit,
) {
    ContextMenu(
        items = {
            tagContextMenuItems(
                onCheckoutTag = onCheckoutTag,
                onDeleteTag = onDeleteTag,
            )
        }
    ) {
        SideMenuSubentry(
            text = tag.simpleName,
            isSelected = isSelected,
            iconResourcePath = Res.drawable.tag,
            onClick = onTagClicked,
        )
    }
}


@Composable
private fun Stash(
    stash: Commit,
    isSelected: Boolean,
    onClick: () -> Unit,
    onApply: () -> Unit,
    onPop: () -> Unit,
    onDelete: () -> Unit,
) {
    ContextMenu(
        items = {
            stashesContextMenuItems(
                onApply = onApply,
                onPop = onPop,
                onDelete = onDelete,
            )
        }
    ) {
        val (message, branchName) = remember(stash.hash) { parseStashMessage(stash.shortMessage) }

        SideMenuSubentry(
            text = message,
            secondaryText = branchName?.let { "on: $it" },
            isSelected = isSelected,
            iconResourcePath = Res.drawable.stash,
            onClick = onClick,
        )
    }
}

@Composable
private fun Submodule(
    submodule: Pair<String, Submodule>,
    onInitializeSubmodule: () -> Unit,
//    onDeinitializeSubmodule: () -> Unit,
    onSyncSubmodule: () -> Unit,
    onUpdateSubmodule: () -> Unit,
    onOpenSubmoduleInTab: () -> Unit,
    onDeleteSubmodule: () -> Unit,
) {
    ContextMenu(
        items = {
            submoduleContextMenuItems(
                submodule.second,
                onInitializeSubmodule = onInitializeSubmodule,
//                onDeinitializeSubmodule = onDeinitializeSubmodule,
                onSyncSubmodule = onSyncSubmodule,
                onUpdateSubmodule = onUpdateSubmodule,
                onOpenSubmoduleInTab = onOpenSubmoduleInTab,
                onDeleteSubmodule = onDeleteSubmodule,
            )
        }
    ) {
        SideMenuSubentry(
            text = submodule.first,
            iconResourcePath = Res.drawable.topic,
            isSelected = false,
            onClick = {
                if (submodule.second.state.isValid) {
                    onOpenSubmoduleInTab()
                }
            },
        ) {
            val stateName = submodule.second.state.toString()
            DelayedTooltip(stateName) {
                Text(
                    text = stateName.first().toString(),
                    color = MaterialTheme.colors.onBackgroundSecondary,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
private const val TREE_DEPTH_PADDING = 14

private sealed interface BranchTreeRow {
    val key: String
    val name: String
    val depth: Int

    data class Folder(
        override val key: String,
        override val name: String,
        override val depth: Int,
        val isExpanded: Boolean,
    ) : BranchTreeRow

    data class Leaf(
        override val key: String,
        override val name: String,
        override val depth: Int,
        val branch: Branch,
    ) : BranchTreeRow
}

private class BranchTreeNode {
    val children = sortedMapOf<String, BranchTreeNode>(String.CASE_INSENSITIVE_ORDER)
    var branch: Branch? = null
}

/**
 * Groups branches in folders using their "/" separated prefixes (e.g. `feature/login` goes inside the `feature`
 * folder) and flattens the result into the rows that have to be displayed, skipping collapsed folders' content.
 */
private fun buildBranchTree(
    branches: List<Branch>,
    keyPrefix: String,
    collapsedFolders: List<String>,
    displayName: (Branch) -> String,
    baseDepth: Int = 0,
): List<BranchTreeRow> {
    val root = BranchTreeNode()

    for (branch in branches) {
        var node = root
        for (part in displayName(branch).split("/")) {
            node = node.children.getOrPut(part) { BranchTreeNode() }
        }
        node.branch = branch
    }

    val rows = mutableListOf<BranchTreeRow>()

    // Like GitKraken, folders and branches are sorted together alphabetically
    fun addRows(node: BranchTreeNode, path: String, depth: Int) {
        for ((name, child) in node.children.entries) {
            if (child.children.isNotEmpty()) {
                val folderPath = if (path.isEmpty()) name else "$path/$name"
                val key = "$keyPrefix:folder:$folderPath"
                val isExpanded = !collapsedFolders.contains(key)

                rows.add(BranchTreeRow.Folder(key, name, depth, isExpanded))

                if (isExpanded) {
                    addRows(child, folderPath, depth + 1)
                }
            }

            // A branch can share its name with a folder (e.g. "feature" and "feature/login")
            child.branch?.let { branch ->
                rows.add(BranchTreeRow.Leaf("$keyPrefix:branch:${branch.name}", name, depth, branch))
            }
        }
    }

    addRows(root, "", baseDepth)

    return rows
}

@Composable
private fun BranchFolder(
    folder: BranchTreeRow.Folder,
    onClick: () -> Unit,
) {
    SideMenuSubentry(
        text = folder.name,
        iconResourcePath = if (folder.isExpanded) Res.drawable.folder_open else Res.drawable.folder,
        extraPadding = (TREE_DEPTH_PADDING * folder.depth).dp,
        isSelected = false,
        onClick = onClick,
    )
}

private val stashMessageRegex = Regex("""^(WIP on|On) ([^:]+):\s*(.*)$""")

/**
 * Splits the default stash message format ("WIP on branch: hash message" or "On branch: message") into the message
 * itself and the branch where the stash was created.
 */
private fun parseStashMessage(message: String): Pair<String, String?> {
    val match = stashMessageRegex.matchEntire(message.trim()) ?: return message to null
    val (prefix, branch, content) = match.destructured

    val text = if (prefix == "WIP on") "WIP on $branch" else content.ifBlank { "Stash" }

    return text to branch
}

const val SIDE_PANEL_RAIL_WIDTH = 40f

/**
 * Collapsed version of the side panel shown while a diff replaces the graph. It only shows the counters of each
 * section and a button to close the diff and go back to the graph.
 */
@Composable
fun SidePanelRail(
    viewModel: RepositoryOpenViewModel,
    buttonTooltip: String,
    onButtonClick: () -> Unit,
    onEntryClick: () -> Unit,
) {
    val branchesState by viewModel.branchesState.collectAsState()
    val remotesState by viewModel.remoteState.collectAsState()
    val tagsState by viewModel.tagsState.collectAsState()
    val stashesState by viewModel.stashesState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailCircleButton(
            icon = Res.drawable.chevron_right,
            tooltip = buttonTooltip,
            onClick = onButtonClick,
        )

        SidePanelRailEntry(Res.drawable.computer, branchesState.branches.count(), onEntryClick)
        SidePanelRailEntry(
            Res.drawable.cloud,
            remotesState.remotes.sumOf { it.remoteInfo.branchesList.count() },
            onEntryClick,
        )
        SidePanelRailEntry(Res.drawable.stash, stashesState.stashes.count(), onEntryClick)
        SidePanelRailEntry(Res.drawable.tag, tagsState.tags.count(), onEntryClick)
    }
}

@Composable
private fun RailCircleButton(
    icon: DrawableResource,
    tooltip: String,
    onClick: () -> Unit,
) {
    DelayedTooltip(tooltip) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.85f))
                .handMouseClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = tooltip,
                tint = MaterialTheme.colors.surface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SidePanelRailEntry(
    icon: DrawableResource,
    count: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(top = 14.dp)
            .handMouseClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colors.onBackgroundSecondary,
            modifier = Modifier.size(16.dp),
        )

        Text(
            text = count.toString(),
            fontSize = 10.sp,
            color = MaterialTheme.colors.primaryVariant,
        )
    }
}

@Composable
private fun CurrentBranchCheckbox() {
    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colors.addFile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.done),
            contentDescription = "Current branch",
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
    }
}

/**
 * "1↑ 2↓": commits the branch is ahead/behind its upstream, like GitKraken.
 */
@Composable
private fun AheadBehindLabel(aheadBehind: AheadBehind) {
    val text = buildString {
        if (aheadBehind.ahead > 0) append("${aheadBehind.ahead}↑")
        if (aheadBehind.ahead > 0 && aheadBehind.behind > 0) append(" ")
        if (aheadBehind.behind > 0) append("${aheadBehind.behind}↓")
    }

    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onBackground,
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp, end = 12.dp),
    )
}

/**
 * "origin/HEAD" only points to the remote's default branch, GitKraken doesn't list it.
 */
private fun List<Branch>.withoutRemoteHead(): List<Branch> = filterNot { it.simpleName == "HEAD" }
