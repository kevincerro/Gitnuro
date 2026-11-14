@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.jetpackduba.gitnuro.ui.log

import androidx.compose.ui.draw.alpha
import com.jetpackduba.gitnuro.ui.components.rememberListItemHover
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import com.jetpackduba.gitnuro.ui.components.RemoteAvatar
import com.jetpackduba.gitnuro.ui.components.LocalRemoteAvatars
import com.jetpackduba.gitnuro.ui.components.remoteOwnerAvatarUrl
import com.jetpackduba.gitnuro.ui.dialogs.AnnotatedTagDialog
import com.jetpackduba.gitnuro.ui.dialogs.EditCommitMessageDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpackduba.gitnuro.app.generated.resources.*
import com.jetpackduba.gitnuro.common.printLog
import com.jetpackduba.gitnuro.domain.BranchesConstants.LOCAL_PREFIX_LENGTH
import com.jetpackduba.gitnuro.domain.models.*
import com.jetpackduba.gitnuro.domain.models.ui.SelectedItem
import com.jetpackduba.gitnuro.extensions.*
import com.jetpackduba.gitnuro.keybindings.KeybindingOption
import com.jetpackduba.gitnuro.keybindings.matchesBinding
import com.jetpackduba.gitnuro.repositoryopen.LogAction
import com.jetpackduba.gitnuro.repositoryopen.LogSearch
import com.jetpackduba.gitnuro.repositoryopen.LogState
import com.jetpackduba.gitnuro.repositoryopen.RepositoryOpenViewModel
import com.jetpackduba.gitnuro.theme.*
import com.jetpackduba.gitnuro.ui.components.AvatarImage
import com.jetpackduba.gitnuro.ui.components.ScrollableLazyColumn
import com.jetpackduba.gitnuro.ui.components.tooltip.InstantTooltip
import com.jetpackduba.gitnuro.ui.components.tooltip.InstantTooltipPosition
import com.jetpackduba.gitnuro.ui.context_menu.*
import com.jetpackduba.gitnuro.ui.resizePointerIconEast
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.eclipse.jgit.lib.Constants
import org.jetbrains.compose.resources.painterResource
import java.time.Instant
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Colors assigned to each lane of the graph, cycling when there are more lanes than colors.
 */
val graphLaneColors = listOf(
    Color(0xFF15A0BF),
    Color(0xFF0669F7),
    Color(0xFF8E00C2),
    Color(0xFFC517B6),
    Color(0xFFD90171),
    Color(0xFFCD0101),
    Color(0xFFF25D2E),
    Color(0xFFF2CA33),
    Color(0xFF7BD938),
    Color(0xFF2ECE9D),
)

fun laneColor(lane: Int) = graphLaneColors[lane % graphLaneColors.size]

private const val CANVAS_MIN_WIDTH = 60
private const val CANVAS_DEFAULT_WIDTH = 150
private const val REFS_MIN_WIDTH = 60
private const val REFS_DEFAULT_WIDTH = 130
private const val MIN_GRAPH_LANES = 2

private const val HORIZONTAL_SCROLL_PIXELS_MULTIPLIER = 10

/**
 * Additional number of lanes to simulate to create a margin at the end of the graph.
 */
private const val MARGIN_GRAPH_LANES = 1
private const val LANE_WIDTH = 22f
private const val GRAPH_START_PADDING = 8f
private const val COLUMN_RESIZE_HANDLE_WIDTH = 6

private const val AVATAR_NODE_SIZE = 22
private const val MERGE_NODE_SIZE = 10
private const val STASH_NODE_SIZE = 18
private const val LINE_STROKE_WIDTH = 2f

/**
 * Height of the colored band of each row: the same as the commit's node (its avatar), centered on it. The rest of
 * the row is left empty, so consecutive rows are separated (like GitKraken).
 */
private val ROW_BAND_HEIGHT = AVATAR_NODE_SIZE.dp

/** Space left above (and below) the band in a row of [rowHeight] px */
private fun Density.rowBandInset(rowHeight: Float): Float {
    return ((rowHeight - ROW_BAND_HEIGHT.toPx()) / 2f).coerceAtLeast(0f)
}

private const val SELECTED_TINT_ALPHA = 0.35f
private const val ROW_TINT_ALPHA = 0.13f

private const val LOG_BOTTOM_PADDING = 80

private const val MIN_COMMITS_BEFORE_REQUESTING_MORE = 200

private const val TAG = "LogView"

/**
 * Horizontal center of a lane, in dp, relative to the start of the graph column.
 */
private fun laneCenterX(lane: Int): Float = GRAPH_START_PADDING + lane * LANE_WIDTH + LANE_WIDTH / 2

@Composable
fun Log(
    modifier: Modifier,
    viewModel: RepositoryOpenViewModel,
    selectedItem: SelectedItem,
    repositoryState: RepositoryState,
    onCreateBranch: (Commit) -> Unit,
    onResetBranch: (Commit) -> Unit,
    onCreateTag: (Commit) -> Unit,
    onChangeUpstreamBranch: (Branch) -> Unit,
    onRenameBranch: (Branch) -> Unit,
) {
    val logStatusState = viewModel.logState.collectAsState()
    val logStatus = logStatusState.value
    var graphPadding by remember(viewModel) { mutableStateOf(viewModel.graphPadding) }
    var refsWidth by remember(viewModel) { mutableStateOf(REFS_DEFAULT_WIDTH.toFloat()) }

    LaunchedEffect(logStatus.verticalScrollState, logStatus.commitList) {
        launch {
            viewModel.focusCommit.collect { commit ->
                scrollToCommit(logStatus.verticalScrollState, logStatus.commitList, commit.commit)
            }
        }
        launch {
            viewModel.scrollToUncommittedChanges.collect {
                scrollToUncommittedChanges(logStatus.verticalScrollState, logStatus.commitList)
            }
        }
    }

    LaunchedEffect(selectedItem) {
        if (
            (selectedItem is SelectedItem.CommitBasedItem && selectedItem !is SelectedItem.CommitItem) ||
            (selectedItem is SelectedItem.CommitItem && selectedItem.scrollToItem)
        ) {
            scrollToCommit(logStatus.verticalScrollState, logStatus.commitList, selectedItem.commit)
        }
    }

    val remotesState by viewModel.remoteState.collectAsState()
    val remoteAvatars = remember(remotesState) {
        remotesState.remotes
            .mapNotNull { remoteView ->
                val remote = remoteView.remoteInfo.remote
                remoteOwnerAvatarUrl(remote.fetchUri)?.let { remote.name to it }
            }
            .toMap()
    }

    Box(modifier) {
        CompositionLocalProvider(LocalRemoteAvatars provides remoteAvatars) {
        LogView(
            logState = logStatus,
            selectedItem = selectedItem,
            repositoryState = repositoryState,
            graphPadding = graphPadding,
            refsWidth = refsWidth,
            onRequestMoreLogItems = { firstVisibleItemIndex -> viewModel.loadMoreLogItems(firstVisibleItemIndex) },
            onCreateBranch = onCreateBranch,
            onResetBranch = onResetBranch,
            onCreateTag = onCreateTag,
            onChangeUpstreamBranch = onChangeUpstreamBranch,
            onRenameBranch = onRenameBranch,
            onGraphPaddingChange = { newGraphPadding ->
                graphPadding = newGraphPadding
                viewModel.graphPadding = graphPadding
            },
            onRefsWidthChange = { delta ->
                refsWidth = (refsWidth + delta).coerceAtLeast(REFS_MIN_WIDTH.toFloat())
            },
            onAction = { viewModel.onAction(it) },
            searchView = {
                SearchFilter(
                    logViewModel = viewModel,
                    searchFilterResults = it,
                    searchFocused = { viewModel.addSearchToCloseableView() },
                )
            }
        )
        }

        AnimatedVisibility(visible = logStatus.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LogView(
    logState: LogState,
    selectedItem: SelectedItem,
    repositoryState: RepositoryState,
    graphPadding: Float,
    refsWidth: Float,
    onRequestMoreLogItems: (Int) -> Unit,
    onCreateBranch: (Commit) -> Unit,
    onResetBranch: (Commit) -> Unit,
    onCreateTag: (Commit) -> Unit,
    onChangeUpstreamBranch: (Branch) -> Unit,
    onRenameBranch: (Branch) -> Unit,
    onGraphPaddingChange: (Float) -> Unit,
    onRefsWidthChange: (Float) -> Unit,
    onAction: (LogAction) -> Unit,
    searchView: @Composable (LogSearch.SearchResults) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val hasUncommittedChanges = logState.hasUncommittedChanges
    val commitList = logState.commitList

    val verticalScrollState = logState.verticalScrollState
    val horizontalScrollState = logState.horizontalScrollState
    val searchFilterValue = logState.searchFilter

    LaunchedEffect(verticalScrollState, logState) {
        snapshotFlow { verticalScrollState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect {
                val commitsList = logState.commitList

                // TODO Check what would happen with a repo with multiple starting commits
                if (
                    commitsList.commits.isNotEmpty() &&
                    commitsList.commits.count() - it < MIN_COMMITS_BEFORE_REQUESTING_MORE &&
                    commitsList.commits.entries.last().value.commit.parentCount > 0
                ) {
                    printLog(TAG, "Requesting more items")
                    onRequestMoreLogItems(verticalScrollState.firstVisibleItemIndex)
                }
            }
    }

    val selectedCommit = if (selectedItem is SelectedItem.CommitBasedItem) {
        selectedItem.commit
    } else {
        null
    }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colors.background)
            .fillMaxSize()
    ) {
        val refsColumnWidth = refsWidth.dp

        val maxLinePosition = if (commitList.isNotEmpty())
            commitList.maxLane
        else
            MIN_GRAPH_LANES

        // Width that shows every lane of the graph
        val graphRealWidthValue = (laneCenterX(maxLinePosition + MARGIN_GRAPH_LANES) + LANE_WIDTH / 2)
            .coerceAtLeast(CANVAS_MIN_WIDTH.toFloat())

        // The column can't be wider than its graph: with all the lanes visible there is nothing else to show (and
        // nothing to scroll). When it's narrower, the graph is scrolled horizontally.
        val graphWidthValue = (CANVAS_DEFAULT_WIDTH + graphPadding)
            .coerceIn(CANVAS_MIN_WIDTH.toFloat(), graphRealWidthValue)

        val graphWidth = graphWidthValue.dp
        val graphRealWidth = graphRealWidthValue.dp

        // The new width is limited here, from the displayed one: the stored padding never goes beyond the limits, so
        // dragging back from a limit has effect right away
        val onGraphWidthDrag: (Float) -> Unit = { delta ->
            val newWidth = (graphWidthValue + delta).coerceIn(CANVAS_MIN_WIDTH.toFloat(), graphRealWidthValue)

            onGraphPaddingChange(newWidth - CANVAS_DEFAULT_WIDTH)
        }

        if (searchFilterValue is LogSearch.SearchResults) {
            searchView(searchFilterValue)
        }

        val density = LocalDensity.current.density

        GraphHeader(
            refsWidth = refsColumnWidth,
            graphWidth = graphWidth,
            onRefsWidthChange = { onRefsWidthChange(it / density) },
            onGraphWidthChange = { onGraphWidthDrag(it / density) },
            onShowSearch = {
                onAction(LogAction.SearchValueChange(""))
            }
        )

        Box {
            // This Box is only used to get a scroll state. With this scroll state we will manually add an offset in
            // the graph of each row
            Box(
                Modifier
                    .padding(start = refsColumnWidth)
                    .width(graphWidth)
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScrollState)
                    .padding(bottom = 8.dp)
            ) {
                // The content has to be bigger in order to show the scroll bar in the parent component
                Box(
                    modifier = Modifier.width(graphRealWidth)
                )
            }

            CommitsList(
                scrollState = verticalScrollState,
                horizontalScrollState = horizontalScrollState,
                hasUncommittedChanges = hasUncommittedChanges,
                searchFilter = if (searchFilterValue is LogSearch.SearchResults) searchFilterValue.commits else null,
                selectedCommit = selectedCommit,
                logState = logState,
                repositoryState = repositoryState,
                selectedItem = selectedItem,
                commitList = commitList,
                branches = logState.branches,
                tags = logState.tags,
                stashes = logState.stashes,
                refsWidth = refsColumnWidth,
                graphWidth = graphWidth,
                graphRealWidth = graphRealWidth,
                onCreateBranch = onCreateBranch,
                onResetBranch = onResetBranch,
                onCreateTag = onCreateTag,
                onChangeUpstreamBranch = onChangeUpstreamBranch,
                onRenameBranch = onRenameBranch,
                onAction = onAction,
            )

            ColumnResizeHandle(
                position = refsColumnWidth,
                showLine = false,
                onDrag = { onRefsWidthChange(it / density) },
            )

            ColumnResizeHandle(
                position = refsColumnWidth + graphWidth,
                showLine = false,
                onDrag = { onGraphWidthDrag(it / density) },
            )

            // Scrollbar used to scroll horizontally the graph nodes
            // Added after every component to have the highest priority when clicking
            HorizontalScrollbar(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = refsColumnWidth + 4.dp, bottom = 4.dp)
                    .width(graphWidth - 8.dp),
                style = LocalScrollbarStyle.current.copy(
                    unhoverColor = MaterialTheme.colors.scrollbarNormal,
                    hoverColor = MaterialTheme.colors.scrollbarHover,
                ),
                adapter = rememberScrollbarAdapter(horizontalScrollState)
            )

            val isFirstItemVisible by remember(verticalScrollState) {
                derivedStateOf { verticalScrollState.firstVisibleItemIndex > 0 }
            }

            if (isFirstItemVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(50))
                        .handMouseClickable {
                            scope.launch {
                                verticalScrollState.scrollToItem(0)
                            }
                        }
                        .background(MaterialTheme.colors.primary)
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(Res.drawable.align_top),
                            contentDescription = null,
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )

                        Text(
                            text = "Scroll to top",
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colors.onPrimary,
                            maxLines = 1,
                            style = MaterialTheme.typography.body2,
                        )
                    }
                }
            }
        }
    }
}

suspend fun scrollToCommit(
    verticalScrollState: LazyListState,
    commitList: GraphCommits,
    commit: Commit,
) {
    val index = commitList.commits.entries.indexOfFirst { it.value.hash == commit?.hash }
    // TODO Show a message informing the user why we aren't scrolling
    // Index can be -1 if the ref points to a commit that is not shown in the graph due to the limited
    // number of displayed commits.
    val isCommitAlreadyVisible = verticalScrollState.layoutInfo.visibleItemsInfo.any {
        (it.key as? String).orEmpty().contains(commit.message)
    }

    if (!isCommitAlreadyVisible) {
        if (index >= 0) verticalScrollState.scrollToItem(index)
    }
}

suspend fun scrollToUncommittedChanges(
    verticalScrollState: LazyListState,
    commitList: GraphCommits,
) {
    if (commitList.isNotEmpty())
        verticalScrollState.scrollToItem(0)
}

@Composable
fun SearchFilter(
    logViewModel: RepositoryOpenViewModel,
    searchFilterResults: LogSearch.SearchResults,
    searchFocused: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searchFilterText by remember { mutableStateOf(logViewModel.savedSearchFilter) }
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        textFieldFocusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = searchFilterText,
            onValueChange = {
                searchFilterText = it
                scope.launch {
                    logViewModel.onSearchValueChanged(it)
                }
            },
            maxLines = 1,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(textFieldFocusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        searchFocused()
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    when {
                        keyEvent.matchesBinding(KeybindingOption.SIMPLE_ACCEPT) -> {
                            scope.launch {
                                logViewModel.selectNextFilterCommit()
                            }
                            true
                        }

                        else -> false
                    }
                },
            label = {
                Text("Search by message, author name or commit ID")
            },
            colors = textFieldColors(),
            textStyle = MaterialTheme.typography.body1,
            trailingIcon = {
                Row(
                    modifier = Modifier
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searchFilterText.isNotEmpty()) {
                        Text(
                            "${searchFilterResults.index}/${searchFilterResults.totalCount}",
                            color = MaterialTheme.colors.onBackgroundSecondary,
                        )
                    }

                    IconButton(
                        modifier = Modifier
                            .handOnHover(),
                        onClick = {
                            scope.launch { logViewModel.selectPreviousFilterCommit() }
                        }
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                    }

                    IconButton(
                        modifier = Modifier
                            .handOnHover(),
                        onClick = {
                            scope.launch { logViewModel.selectNextFilterCommit() }
                        }
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }

                    IconButton(
                        modifier = Modifier
                            .handOnHover()
                            .padding(end = 4.dp),
                        onClick = { logViewModel.closeSearch() }
                    ) {
                        Icon(painterResource(Res.drawable.close), contentDescription = null)
                    }
                }
            }
        )
    }
}

@Composable
private fun scrollOnUncommittedChangesAppear(
    scrollState: LazyListState,
    hasUncommittedChanges: Boolean
) {
    // On scrollstate/tab change this is reset to prevent triggering a scroll in other tabs
    val isFirst = remember(scrollState) { booleanArrayOf(true) }

    LaunchedEffect(scrollState, hasUncommittedChanges) {
        if (isFirst[0]) {
            isFirst[0] = false
            return@LaunchedEffect
        }

        if (hasUncommittedChanges && scrollState.firstVisibleItemIndex == 1) {
            scrollState.scrollToItem(0)
        }
    }
}

@Composable
private fun scrollOnNewCommitAdded(
    scrollState: LazyListState,
    commits: LinkedHashMap<String, GraphCommit>,
) {
    // On scrollstate/tab change this is reset to prevent triggering a scroll in other tabs
    val isFirst = remember(scrollState) { booleanArrayOf(true) }

    LaunchedEffect(scrollState, commits.keys.firstOrNull()) {
        if (isFirst[0]) {
            isFirst[0] = false
            return@LaunchedEffect
        }

        scrollState.scrollToItem(0)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CommitsList(
    scrollState: LazyListState,
    hasUncommittedChanges: Boolean,
    searchFilter: List<GraphCommit>?,
    selectedCommit: Commit?,
    logState: LogState,
    repositoryState: RepositoryState,
    selectedItem: SelectedItem,
    commitList: GraphCommits,
    branches: Map<String, List<Branch>>,
    tags: Map<String, List<Tag>>,
    stashes: HashSet<String>,
    onAction: (LogAction) -> Unit,
    onCreateBranch: (Commit) -> Unit,
    onResetBranch: (Commit) -> Unit,
    onCreateTag: (Commit) -> Unit,
    onChangeUpstreamBranch: (Branch) -> Unit,
    onRenameBranch: (Branch) -> Unit,
    refsWidth: Dp,
    graphWidth: Dp,
    graphRealWidth: Dp,
    horizontalScrollState: ScrollState,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var commitDialog by remember { mutableStateOf<CommitDialogRequest?>(null) }

    when (val dialog = commitDialog) {
        is CommitDialogRequest.EditMessage -> EditCommitMessageDialog(
            shortHash = dialog.commit.hash.take(6),
            initialMessage = dialog.commit.message,
            onDismiss = { commitDialog = null },
            onAccept = { message ->
                commitDialog = null
                onAction(LogAction.EditCommitMessage(dialog.commit, message))
            },
        )

        is CommitDialogRequest.AnnotatedTag -> AnnotatedTagDialog(
            shortHash = dialog.commit.hash.take(6),
            onDismiss = { commitDialog = null },
            onAccept = { name, message ->
                commitDialog = null
                onAction(LogAction.CreateAnnotatedTag(dialog.commit, name, message))
            },
        )

        else -> {}
    }

    scrollOnUncommittedChangesAppear(scrollState, hasUncommittedChanges)
    scrollOnNewCommitAdded(scrollState, commitList.commits)

    // Commits that start a new relative date group ("a week ago", "2 months ago"...) mapped to the group label
    val dateSeparators = remember(commitList) {
        val now = Instant.now().epochSecond
        var previousLabel: String? = null

        buildMap<String, String> {
            for (graphCommit in commitList.values) {
                val label = graphCommit.date.toRelativeAgoString(now)

                if (label != previousLabel) {
                    put(graphCommit.hash, label)
                    previousLabel = label
                }
            }
        }
    }

    ScrollableLazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            // The underlying composable assigned to the horizontal scroll bar won't be receiving the scroll events
            // because the commits list will consume the events, so this code tries to scroll manually when it detects
            // horizontal scrolling
            .onPointerEvent(PointerEventType.Scroll) { pointerEvent ->
                scope.launch {
                    val xScroll = pointerEvent.changes.map { it.scrollDelta.x }.sum()
                    horizontalScrollState.scrollBy(xScroll * HORIZONTAL_SCROLL_PIXELS_MULTIPLIER)
                }
            },
    ) {
        if (
            hasUncommittedChanges ||
            repositoryState.isMerging ||
            repositoryState.isRebasing ||
            repositoryState.isCherryPicking
        ) {
            item {
                UncommittedChangesLine(
                    refsWidth = refsWidth,
                    graphWidth = graphWidth,
                    graphRealWidth = graphRealWidth,
                    horizontalScrollState = horizontalScrollState,
                    hasPreviousCommits = commitList.commits.isNotEmpty(),
                    isSelected = selectedItem == SelectedItem.UncommittedChanges,
                    statusSummary = logState.statusSummary,
                    repositoryState = repositoryState,
                    onClick = { onAction(LogAction.UncommittedChangesSelected) },
                )
            }
        }

        // Setting a key makes the graph preserve the scroll position when a new line has been added on top (uncommitted changes)
        // Therefore, after popping a stash, the uncommitted changes wouldn't be visible and requires the user scrolling.
        // TODO This should be improved in case it's a dangling branch, shouldn't happen often but could be a thing
        items(
            items = commitList.values.toList(),
            key = { commit ->
                commit.hash + commit.lane + branches[commit.hash].orEmpty().joinToString() + tags[commit.hash].orEmpty()
                    .joinToString()
            },
        )
        { graphNode ->
            val isStash = stashes.contains(graphNode.hash)
            CommitLine(
                refsWidth = refsWidth,
                graphWidth = graphWidth,
                graphRealWidth = graphRealWidth,
                graphNode = graphNode,
                dateSeparator = dateSeparators[graphNode.hash],
                isSelected = selectedCommit?.hash == graphNode.hash,
                showInAmend = logState.currentBranch?.hash == graphNode.hash && !hasUncommittedChanges,
                isStash = isStash,
                branches = branches[graphNode.hash].orEmpty(),
                tags = tags[graphNode.hash].orEmpty(),
                currentBranch = logState.currentBranch,
                matchesSearchFilter = searchFilter?.contains(graphNode),
                horizontalScrollState = horizontalScrollState,
                showCreateNewBranch = { onCreateBranch(graphNode.commit) },
                showCreateNewTag = { onCreateTag(graphNode.commit) },
                resetBranch = { onResetBranch(graphNode.commit) },
                onMergeBranch = { onAction(LogAction.Merge(it)) },
                onDeleteBranch = { onAction(LogAction.DeleteBranch(it)) },
                onDeleteRemoteBranch = { onAction(LogAction.DeleteRemoteBranch(it)) },
                onCheckoutTag = { onAction(LogAction.CheckoutTag(it)) },
                onDeleteTag = { onAction(LogAction.DeleteTag(it)) },
                onPushToRemoteBranch = { onAction(LogAction.PushToRemoteBranch(it)) },
                onPullFromRemoteBranch = { onAction(LogAction.PullFromRemoteBranch(it)) },
                onRebaseBranch = { onAction(LogAction.Rebase(it)) },
                onRebaseInteractive = { onAction(LogAction.RebaseInteractive(graphNode.commit)) },
                onRevCommitSelected = { onAction(LogAction.CommitSelected(graphNode.commit, isStash)) },
                onChangeDefaultUpstreamBranch = { onChangeUpstreamBranch(it) },
                onRenameBranch = { onRenameBranch(it) },
                onDeleteStash = { onAction(LogAction.DeleteStash(graphNode.commit)) },
                onApplyStash = { onAction(LogAction.ApplyStash(graphNode.commit)) },
                onPopStash = { onAction(LogAction.PopStash(graphNode.commit)) },
                onShowStatusAmending = { onAction(LogAction.ShowStatusAmending) },
                onCheckoutCommit = { onAction(LogAction.CheckoutCommit(graphNode.commit)) },
                onRevertCommit = { onAction(LogAction.RevertCommit(graphNode.commit)) },
                onCherryPickCommit = { onAction(LogAction.CherryPickCommit(graphNode.commit)) },
                onCheckoutRemoteBranch = { onAction(LogAction.CheckoutRemoteBranch(it)) },
                onCheckoutBranch = { onAction(LogAction.CheckoutBranch(it)) },
                onCopyBranchNameToClipboard = {
                    scope.launch {
                        clipboard.setClipboardText(it.simpleName)
                    }
                },
                onLogAction = onAction,
                onShowCommitDialog = { request ->
                    if (request is CommitDialogRequest.Worktree) {
                        // The worktree only needs a folder, picked with the system dialog
                        scope.launch {
                            chooseWorktreeDirectory()?.let { path ->
                                onAction(LogAction.CreateWorktree(request.commit, path))
                            }
                        }
                    } else {
                        commitDialog = request
                    }
                },
            )
        }

        item {
            Box(modifier = Modifier.height(LOG_BOTTOM_PADDING.dp))
        }
    }
}

@Composable
fun GraphHeader(
    refsWidth: Dp,
    graphWidth: Dp,
    onRefsWidthChange: (Float) -> Unit,
    onGraphWidthChange: (Float) -> Unit,
    onShowSearch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(MaterialTheme.colors.tertiarySurface)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GraphHeaderLabel("BRANCH / TAG", Modifier.width(refsWidth))
            GraphHeaderLabel("GRAPH", Modifier.width(graphWidth))
            GraphHeaderLabel("COMMIT MESSAGE", Modifier.weight(1f))

            IconButton(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(24.dp)
                    .handOnHover(),
                onClick = onShowSearch
            ) {
                Icon(
                    painterResource(Res.drawable.search),
                    modifier = Modifier.size(14.dp),
                    contentDescription = null,
                    tint = MaterialTheme.colors.onBackgroundSecondary,
                )
            }
        }

        ColumnResizeHandle(position = refsWidth, showLine = true, onDrag = onRefsWidthChange)
        ColumnResizeHandle(position = refsWidth + graphWidth, showLine = true, onDrag = onGraphWidthChange)

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.06f))
        )
    }
}

@Composable
private fun GraphHeaderLabel(text: String, modifier: Modifier) {
    Text(
        modifier = modifier.padding(start = 8.dp),
        text = text,
        color = MaterialTheme.colors.onBackgroundSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun ColumnResizeHandle(
    position: Dp,
    showLine: Boolean,
    onDrag: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = (position - (COLUMN_RESIZE_HANDLE_WIDTH / 2).dp).coerceAtLeast(0.dp))
            .width(COLUMN_RESIZE_HANDLE_WIDTH.dp)
            .fillMaxHeight()
            .draggable(rememberDraggableState { onDrag(it) }, Orientation.Horizontal)
            .pointerHoverIcon(resizePointerIconEast)
    ) {
        if (showLine) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(color = MaterialTheme.colors.onBackground.copy(alpha = 0.1F))
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun UncommittedChangesLine(
    refsWidth: Dp,
    graphWidth: Dp,
    graphRealWidth: Dp,
    horizontalScrollState: ScrollState,
    hasPreviousCommits: Boolean,
    isSelected: Boolean,
    repositoryState: RepositoryState,
    statusSummary: StatusSummary,
    onClick: () -> Unit,
) {
    val wipColor = laneColor(0)

    Row(
        modifier = Modifier
            .height(MaterialTheme.linesHeight.logCommitHeight)
            .fillMaxWidth()
            .listRowClickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(refsWidth))

        Box(
            modifier = Modifier
                .width(graphWidth)
                .fillMaxHeight()
                .clipToBounds()
                // No lane band on the WIP row (in GitKraken it starts at the HEAD commit), only the selection
                .rowHighlightFromNode(
                    laneCenterX(0),
                    MaterialTheme.colors.backgroundSelected.takeIf { isSelected },
                    horizontalScrollState,
                )
        ) {
            UncommittedChangesGraphNode(
                hasPreviousCommits = hasPreviousCommits,
                color = wipColor,
                modifier = Modifier
                    .offset { IntOffset(-horizontalScrollState.value, 0) }
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(graphRealWidth)
                    .fillMaxHeight(),
            )
        }

        val text = when {
            repositoryState.isRebasing -> "// Pending changes to rebase"
            repositoryState.isMerging -> "// Pending changes to merge"
            repositoryState.isCherryPicking -> "// Pending changes to cherry-pick"
            repositoryState.isReverting -> "// Pending changes to revert"
            else -> "// WIP"
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .rowBand(MaterialTheme.colors.backgroundSelected.takeIf { isSelected })
                .messageLeftBorder(wipColor)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Like GitKraken, the WIP text is shown in an input-like box
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                color = MaterialTheme.colors.onBackgroundSecondary,
                // Only a border (no background) and compact, like GitKraken
                modifier = Modifier
                    .widthIn(min = 100.dp)
                    .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            )

            LogStatusSummary(
                statusSummary = statusSummary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun LogStatusSummary(statusSummary: StatusSummary, modifier: Modifier) {
    Row(
        modifier = modifier,
    ) {
        if (statusSummary.modifiedCount > 0) {
            SummaryEntry(
                count = statusSummary.modifiedCount,
                icon = Icons.Default.Edit,
                color = MaterialTheme.colors.modifyFile,
            )
        }

        if (statusSummary.addedCount > 0) {
            SummaryEntry(
                count = statusSummary.addedCount,
                icon = Icons.Default.Add,
                color = MaterialTheme.colors.addFile,
            )
        }

        if (statusSummary.deletedCount > 0) {
            SummaryEntry(
                count = statusSummary.deletedCount,
                icon = Icons.Default.Remove,
                color = MaterialTheme.colors.deleteFile,
            )
        }

        if (statusSummary.conflictingCount > 0) {
            SummaryEntry(
                count = statusSummary.conflictingCount,
                icon = Icons.Default.Warning,
                color = MaterialTheme.colors.conflictFile,
            )
        }
    }
}

@Composable
fun SummaryEntry(
    count: Int, icon: ImageVector, color: Color,
) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon, tint = color, contentDescription = null, modifier = Modifier.size(12.dp)
        )

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

/**
 * Translucent band in the lane color that goes from the commit node to the end of the graph column.
 */
private fun Modifier.laneTint(
    laneCenter: Float,
    color: Color,
    isSelected: Boolean,
    horizontalScrollState: ScrollState,
) = drawBehind {
    val start = (laneCenter.dp.toPx() - horizontalScrollState.value).coerceIn(0f, size.width)

    drawRowBand(
        color = color.copy(alpha = if (isSelected) SELECTED_TINT_ALPHA else ROW_TINT_ALPHA),
        start = start,
    )
}

/**
 * Band of a row from [start] to the end of the cell, as tall as the commit's node and vertically centered.
 */
private fun DrawScope.drawRowBand(color: Color, start: Float = 0f) {
    val inset = rowBandInset(size.height)

    drawRect(
        color = color,
        topLeft = Offset(start, inset),
        size = Size(size.width - start, size.height - inset * 2),
    )
}

/**
 * Background of a whole cell with the same vertical inset as the bands of the graph.
 */
private fun Modifier.rowBand(color: Color?) = drawBehind {
    if (color != null) drawRowBand(color)
}

/**
 * Fills the graph cell from the node to its end (selection/hover start at the node, like GitKraken).
 */
private fun Modifier.rowHighlightFromNode(
    laneCenter: Float,
    color: Color?,
    horizontalScrollState: ScrollState,
) = drawBehind {
    if (color == null) return@drawBehind

    val start = (laneCenter.dp.toPx() - horizontalScrollState.value).coerceIn(0f, size.width)
    drawRowBand(color = color, start = start)
}

private fun Modifier.messageLeftBorder(color: Color, width: Dp = 2.dp) = drawBehind {
    val inset = rowBandInset(size.height)

    drawRect(color = color, topLeft = Offset(0f, inset), size = Size(width.toPx(), size.height - inset * 2))
}

@Composable
private fun CommitLine(
    refsWidth: Dp,
    graphWidth: Dp,
    graphRealWidth: Dp,
    graphNode: GraphCommit,
    dateSeparator: String?,
    isSelected: Boolean,
    currentBranch: Branch?,
    isStash: Boolean,
    showInAmend: Boolean,
    matchesSearchFilter: Boolean?,
    showCreateNewBranch: () -> Unit,
    showCreateNewTag: () -> Unit,
    resetBranch: () -> Unit,
    onApplyStash: () -> Unit,
    onPopStash: () -> Unit,
    onDeleteStash: () -> Unit,
    onMergeBranch: (Branch) -> Unit,
    onDeleteBranch: (Branch) -> Unit,
    onDeleteRemoteBranch: (Branch) -> Unit,
    onCheckoutTag: (Tag) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onPushToRemoteBranch: (Branch) -> Unit,
    onPullFromRemoteBranch: (Branch) -> Unit,
    onRebaseBranch: (Branch) -> Unit,
    onRevCommitSelected: () -> Unit,
    onRebaseInteractive: () -> Unit,
    onShowStatusAmending: () -> Unit,
    onCheckoutCommit: () -> Unit,
    onRevertCommit: () -> Unit,
    onCherryPickCommit: () -> Unit,
    onCheckoutRemoteBranch: (Branch) -> Unit,
    onCheckoutBranch: (Branch) -> Unit,
    onChangeDefaultUpstreamBranch: (Branch) -> Unit,
    onRenameBranch: (Branch) -> Unit,
    onCopyBranchNameToClipboard: (Branch) -> Unit,
    horizontalScrollState: ScrollState,
    branches: List<Branch>,
    tags: List<Tag>,
    onLogAction: (LogAction) -> Unit,
    onShowCommitDialog: (CommitDialogRequest) -> Unit,
) {
    val isLastCommitOfCurrentBranch = currentBranch?.hash == graphNode.hash
    val nodeColor = laneColor(graphNode.lane)
    val rowHover = rememberListItemHover()
    val isHovered = rowHover.isHovered
    val hoverColor = MaterialTheme.colors.onBackground.copy(alpha = 0.04f)

    val refs = remember(branches, tags, currentBranch, isStash) {
        if (isStash) emptyList() else buildLogRefs(branches, tags, currentBranch)
    }

    val branchMenuItems: (Branch) -> List<ContextMenuElement> = { branch ->
        branchContextMenuItems(
            branch = branch,
            currentBranch = currentBranch,
            isCurrentBranch = branch.isSameBranch(currentBranch),
            isLocal = branch.isLocal,
            onCheckoutBranch = { if (it.isRemote) onCheckoutRemoteBranch(it) else onCheckoutBranch(it) },
            onMergeBranch = onMergeBranch,
            onDeleteBranch = onDeleteBranch,
            onDeleteRemoteBranch = onDeleteRemoteBranch,
            onRebaseBranch = onRebaseBranch,
            onPushToRemoteBranch = onPushToRemoteBranch,
            onPullFromRemoteBranch = onPullFromRemoteBranch,
            onChangeDefaultUpstreamBranch = onChangeDefaultUpstreamBranch,
            onRenameBranch = onRenameBranch,
            onCopyBranchNameToClipboard = onCopyBranchNameToClipboard,
        )
    }

    ContextMenu(
        items = {
            if (isStash) {
                stashesContextMenuItems(
                    onApply = onApplyStash,
                    onPop = onPopStash,
                    onDelete = onDeleteStash,
                )
            } else {
                val commit = graphNode.commit

                logContextMenu(
                    shortHash = commit.hash.take(6),
                    currentBranchName = currentBranch?.simpleName,
                    isLastCommit = isLastCommitOfCurrentBranch,
                    isFirstCommit = commit.parentsHashes.isEmpty(),
                    branchesAtCommit = branches.map { branch -> branch.simpleName to branchMenuItems(branch) },
                    onCheckoutCommit = onCheckoutCommit,
                    onCreateWorktree = { onShowCommitDialog(CommitDialogRequest.Worktree(commit)) },
                    onCreateNewBranch = showCreateNewBranch,
                    onCherryPickCommit = onCherryPickCommit,
                    onResetBranch = { resetType -> onLogAction(LogAction.ResetToCommit(commit, resetType)) },
                    onRevertCommit = onRevertCommit,
                    onRebaseInteractive = onRebaseInteractive,
                    onEditCommitMessage = { onShowCommitDialog(CommitDialogRequest.EditMessage(commit)) },
                    onDropCommit = { onLogAction(LogAction.DropCommit(commit)) },
                    onMoveCommitUp = { onLogAction(LogAction.MoveCommit(commit, up = true)) },
                    onMoveCommitDown = { onLogAction(LogAction.MoveCommit(commit, up = false)) },
                    onCreateNewTag = showCreateNewTag,
                    onCreateAnnotatedTag = { onShowCommitDialog(CommitDialogRequest.AnnotatedTag(commit)) },
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .height(MaterialTheme.linesHeight.logCommitHeight)
                .fillMaxWidth()
                .then(rowHover.modifier)
                .listRowClickable { onRevCommitSelected() }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                RefsCell(
                    width = refsWidth,
                    refs = refs,
                    color = nodeColor,
                    isRowSelected = isSelected,
                    onSelectRow = onRevCommitSelected,
                    onCheckoutRef = { ref ->
                        when (ref) {
                            // Already checked out, like the context menu (that doesn't offer it)
                            is LogRef.BranchRef if ref.isCurrent -> {}
                            is LogRef.BranchRef -> if (ref.branch.isRemote) {
                                onCheckoutRemoteBranch(ref.branch)
                            } else {
                                onCheckoutBranch(ref.branch)
                            }

                            is LogRef.TagRef -> onCheckoutTag(ref.tag)
                        }
                    },
                    onGenerateContextMenu = { ref ->
                        when (ref) {
                            is LogRef.BranchRef -> branchMenuItems(ref.branch)
                            is LogRef.TagRef -> tagContextMenuItems(
                                onCheckoutTag = { onCheckoutTag(ref.tag) },
                                onDeleteTag = { onDeleteTag(ref.tag) },
                            )
                        }
                    },
                    onGenerateRemoteContextMenu = branchMenuItems,
                )

                Box(
                    modifier = Modifier
                        .width(graphWidth)
                        .fillMaxHeight()
                        .clipToBounds()
                        .laneTint(laneCenterX(graphNode.lane), nodeColor, isSelected, horizontalScrollState)
                        // Like GitKraken, the hover highlight starts at the commit node (not under refs)
                        .rowHighlightFromNode(laneCenterX(graphNode.lane), hoverColor.takeIf { isHovered && !isSelected }, horizontalScrollState)
                ) {
                    CommitsGraph(
                        modifier = Modifier
                            .offset { IntOffset(-horizontalScrollState.value, 0) }
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .width(graphRealWidth)
                            .fillMaxHeight(),
                        plotCommit = graphNode,
                        isStash = isStash,
                        hasRefs = refs.isNotEmpty(),
                        nodeColor = nodeColor,
                    )
                }

                CommitMessage(
                    modifier = Modifier
                        .weight(1f)
                        .rowBand(
                            if (isSelected) MaterialTheme.colors.backgroundSelected else hoverColor.takeIf { isHovered }
                        ),
                    graphCommit = graphNode,
                    nodeColor = nodeColor,
                    matchesSearchFilter = matchesSearchFilter,
                    reserveEndSpace = dateSeparator != null,
                )
            }

            if (dateSeparator != null) {
                DateSeparator(dateSeparator)
            }
        }
    }
}

@Composable
private fun BoxScope.DateSeparator(label: String) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colors.onBackground.copy(alpha = 0.08f))
    )

    Text(
        text = label,
        fontSize = 10.sp,
        color = MaterialTheme.colors.onBackgroundSecondary,
        maxLines = 1,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colors.secondarySurface)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun CommitMessage(
    modifier: Modifier,
    graphCommit: GraphCommit,
    nodeColor: Color,
    matchesSearchFilter: Boolean?,
    reserveEndSpace: Boolean,
) {
    val searchMatchColor = MaterialTheme.colors.secondary

    Row(
        modifier = modifier
            .fillMaxHeight()
            .hoverable(
                // This modifier is added just to prevent committer tooltip is shown then it is underneath this message
                remember { MutableInteractionSource() },
            )
            .run {
                if (matchesSearchFilter == true)
                    messageLeftBorder(searchMatchColor, 4.dp)
                else
                    messageLeftBorder(nodeColor)
            }
            .padding(start = 10.dp, end = if (reserveEndSpace) 96.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (summary, description) = remember(graphCommit.hash) {
            splitCommitMessage(graphCommit.commit.message)
        }

        val summaryColor = if (matchesSearchFilter == false)
            MaterialTheme.colors.onBackgroundSecondary
        else
            MaterialTheme.colors.onBackground
        val descriptionColor = MaterialTheme.colors.onBackgroundSecondary

        val text = remember(summary, description, summaryColor, descriptionColor) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = summaryColor)) {
                    append(summary)
                }

                if (description.isNotEmpty()) {
                    withStyle(SpanStyle(color = descriptionColor)) {
                        append("  ")
                        append(description)
                    }
                }
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Splits a commit message in its summary (first line) and a single line version of its description.
 */
private fun splitCommitMessage(message: String): Pair<String, String> {
    val lines = message
        .trim()
        .replace("\r\n", "\n")
        .lines()

    val summary = lines.firstOrNull().orEmpty()
    val description = lines
        .drop(1)
        .map { it.trim().removePrefix("* ").removePrefix("- ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" | ")

    return summary to description
}

private sealed interface LogRef {
    val name: String

    data class BranchRef(
        val branch: Branch,
        val trackingRemoteBranch: Branch?,
        val isCurrent: Boolean,
    ) : LogRef {
        // Like GitKraken, remote branches show only their name, the remote's avatar tells where they are
        override val name: String = branch.simpleName
        val hasLocal = branch.isLocal
        val hasRemote = branch.isRemote || trackingRemoteBranch != null
    }

    data class TagRef(val tag: Tag) : LogRef {
        override val name: String = tag.simpleName
    }
}

private fun buildLogRefs(branches: List<Branch>, tags: List<Tag>, currentBranch: Branch?): List<LogRef> {
    val branchRefs = getBranchesGrouped(groupBranches = true, branches)
        .map { (branch, trackingRemoteBranch) ->
            LogRef.BranchRef(
                branch = branch,
                trackingRemoteBranch = trackingRemoteBranch,
                isCurrent = branch.isLocal && branch.isSameBranch(currentBranch),
            )
        }
        // Like GitKraken: current branch, then branches that exist both locally and in the remote, then local ones
        .sortedWith(
            compareByDescending<LogRef.BranchRef> { it.isCurrent }
                .thenByDescending { it.hasLocal && it.hasRemote }
                .thenByDescending { it.hasLocal }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )

    return branchRefs + tags.map { LogRef.TagRef(it) }
}

@Composable
private fun RefsCell(
    width: Dp,
    refs: List<LogRef>,
    color: Color,
    isRowSelected: Boolean,
    onSelectRow: () -> Unit,
    onCheckoutRef: (LogRef) -> Unit,
    onGenerateContextMenu: (LogRef) -> List<ContextMenuElement>,
    onGenerateRemoteContextMenu: (Branch) -> List<ContextMenuElement>,
) {
    Row(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val primaryRef = refs.firstOrNull() ?: return@Row
        val otherRefs = refs.drop(1)

        val isHighlighted = isRowSelected || (primaryRef is LogRef.BranchRef && primaryRef.isCurrent)

        // Like GitKraken, when a commit has several refs, hovering them shows all of them stacked over the graph
        val cellHover = remember { MutableInteractionSource() }
        val isCellHovered by cellHover.collectIsHoveredAsState()
        val popupHover = remember { MutableInteractionSource() }
        val isPopupHovered by popupHover.collectIsHoveredAsState()
        var showAllRefs by remember { mutableStateOf(false) }
        // The primary label is cut (ellipsis): hovering it shows it complete, like GitKraken
        var isPrimaryTruncated by remember(primaryRef) { mutableStateOf(false) }
        // Set after right clicking a ref of the list, so its context menu isn't closed when the pointer leaves it
        var isPinned by remember { mutableStateOf(false) }

        // The expanded list opens over the label while hovering it, so the two clicks of a double click can land on
        // different components (label and list). Each combinedClickable only sees its own clicks and the double click
        // was lost: it's detected here, shared by both.
        val doubleClickTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
        val lastClick = remember { arrayOfNulls<Pair<LogRef, Long>>(1) }
        val onRefClick: (LogRef) -> Unit = { ref ->
            val now = System.currentTimeMillis()
            val previous = lastClick[0]

            if (previous != null && previous.first == ref && now - previous.second <= doubleClickTimeout) {
                lastClick[0] = null
                onCheckoutRef(ref)
            } else {
                lastClick[0] = ref to now
                onSelectRow()
            }
        }

        LaunchedEffect(isCellHovered, isPopupHovered, isPinned, isPrimaryTruncated) {
            if (isCellHovered || isPopupHovered || isPinned) {
                showAllRefs = otherRefs.isNotEmpty() || isPrimaryTruncated
                if (isPopupHovered) isPinned = false
            } else {
                delay(150) // Time to move the pointer from the label to the list
                showAllRefs = false
            }
        }

        Row(
            modifier = Modifier.hoverable(cellHover),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContextMenu(items = { onGenerateContextMenu(primaryRef) }) {
                RefLabel(
                    ref = primaryRef,
                    color = color,
                    isHighlighted = isHighlighted,
                    onGenerateRemoteContextMenu = onGenerateRemoteContextMenu,
                    onTruncatedChange = { isPrimaryTruncated = it },
                    modifier = Modifier
                        .widthIn(max = (width - if (otherRefs.isEmpty()) 16.dp else 44.dp).coerceAtLeast(24.dp))
                        .clickable { onRefClick(primaryRef) }
                        .handOnHover(),
                )
            }

            if (otherRefs.isNotEmpty()) {
                // Same height as the label. Hidden while the expanded list (which shows every ref) is open
                Box(
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .height(20.dp)
                        .alpha(if (showAllRefs) 0f else 1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.45f))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+${otherRefs.count()}",
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }

            if (showAllRefs) {
                val density = LocalDensity.current
                val framePaddingPx = with(density) { 3.dp.roundToPx() }

                Popup(
                    properties = PopupProperties(focusable = false),
                    onDismissRequest = {
                        isPinned = false
                        showAllRefs = false
                    },
                    popupPositionProvider = object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.top - framePaddingPx)
                    },
                ) {
                    // A single block with the branch color, the refs are rows inside it (no box per ref)
                    Column(
                        modifier = Modifier
                            .hoverable(popupHover)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colors.surface)
                            .background(color.copy(alpha = 0.85f))
                            .padding(vertical = 2.dp)
                            .width(IntrinsicSize.Max),
                    ) {
                        refs.forEach { ref ->
                            ContextMenu(
                                items = {
                                    isPinned = true
                                    onGenerateContextMenu(ref)
                                },
                            ) {
                                RefLabel(
                                    ref = ref,
                                    color = color,
                                    isHighlighted = ref == primaryRef && isHighlighted,
                                    isFlat = true,
                                    onGenerateRemoteContextMenu = onGenerateRemoteContextMenu,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 360.dp)
                                        .clickable { onRefClick(ref) }
                                        .handOnHover(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Connector from the label to the graph column. The graph draws the remaining part up to the node.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.7f))
        )
    }
}

@Composable
private fun RefLabel(
    ref: LogRef,
    color: Color,
    isHighlighted: Boolean,
    onGenerateRemoteContextMenu: (Branch) -> List<ContextMenuElement>,
    modifier: Modifier,
    /** Inside the expanded list of refs: no box of its own, the list draws the background */
    isFlat: Boolean = false,
    onTruncatedChange: (Boolean) -> Unit = {},
) {
    val iconTint = Color.White.copy(alpha = 0.9f)

    Row(
        modifier = modifier
            .height(20.dp)
            .run {
                if (isFlat) this else clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = if (isHighlighted) 0.85f else 0.4f))
            }
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ref is LogRef.BranchRef && ref.isCurrent) {
            Icon(
                painter = painterResource(Res.drawable.check),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.padding(end = 3.dp).size(12.dp),
            )
        }

        Text(
            text = ref.name,
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { onTruncatedChange(it.hasVisualOverflow) },
            modifier = Modifier.weight(1f, fill = false),
        )

        when (ref) {
            is LogRef.BranchRef -> {
                if (ref.hasLocal) {
                    Icon(
                        painter = painterResource(Res.drawable.computer),
                        contentDescription = "Local branch",
                        tint = iconTint,
                        modifier = Modifier.padding(start = 4.dp).size(12.dp),
                    )
                }

                if (ref.hasRemote) {
                    val remoteBranch = ref.trackingRemoteBranch ?: ref.branch
                    val tooltip = if (ref.trackingRemoteBranch != null)
                        "${ref.branch.simpleName} is up to date with ${ref.trackingRemoteBranch.simpleNameWithRemote}"
                    else
                        ref.branch.simpleNameWithRemote

                    InstantTooltip(text = tooltip, position = InstantTooltipPosition.RIGHT) {
                        ContextMenu(items = { onGenerateRemoteContextMenu(remoteBranch) }) {
                            RemoteAvatar(
                                remoteName = remoteBranch.remoteName,
                                tint = iconTint,
                                modifier = Modifier.padding(start = 4.dp).size(14.dp),
                            )
                        }
                    }
                }
            }

            is LogRef.TagRef -> Icon(
                painter = painterResource(Res.drawable.tag),
                contentDescription = "Tag",
                tint = iconTint,
                modifier = Modifier.padding(start = 4.dp).size(12.dp),
            )
        }
    }
}

@Composable
fun CommitsGraph(
    modifier: Modifier = Modifier,
    plotCommit: GraphCommit,
    isStash: Boolean,
    hasRefs: Boolean,
    nodeColor: Color,
) {
    val passingLanes = plotCommit.passingLanes
    val forkingOffLanes = plotCommit.forkingOffLanes
    val mergingLanes = plotCommit.mergingLanes
    val density = LocalDensity.current.density
    val itemPosition = plotCommit.lane
    val isMerge = plotCommit.commit.parentCount > 1

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            clipRect {
                val nodeX = laneCenterX(itemPosition) * density
                val centerY = this.center.y
                val strokeWidth = LINE_STROKE_WIDTH * density
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(3f * density, 3f * density))

                if (hasRefs) {
                    drawLine(
                        color = nodeColor.copy(alpha = 0.7f),
                        start = Offset(0f, centerY),
                        end = Offset(nodeX, centerY),
                        strokeWidth = 1f * density,
                    )
                }

                if (plotCommit.childCount > 0) {
                    drawLine(
                        color = nodeColor,
                        start = Offset(nodeX, centerY),
                        end = Offset(nodeX, 0f),
                        strokeWidth = strokeWidth,
                    )
                }

                forkingOffLanes.forEach { plotLane ->
                    drawLaneConnection(
                        startX = nodeX,
                        centerY = centerY,
                        targetX = laneCenterX(plotLane) * density,
                        endY = 0f,
                        color = laneColor(plotLane),
                        strokeWidth = strokeWidth,
                        maxRadius = LANE_WIDTH * density / 2,
                    )
                }

                mergingLanes.forEach { plotLane ->
                    drawLaneConnection(
                        startX = nodeX,
                        centerY = centerY,
                        targetX = laneCenterX(plotLane) * density,
                        endY = this.size.height,
                        color = laneColor(plotLane),
                        strokeWidth = strokeWidth,
                        maxRadius = LANE_WIDTH * density / 2,
                        pathEffect = if (isStash) dashEffect else null,
                    )
                }

                if (plotCommit.commit.parentCount > 0) {
                    drawLine(
                        color = nodeColor,
                        start = Offset(nodeX, centerY),
                        end = Offset(nodeX, this.size.height),
                        strokeWidth = strokeWidth,
                        pathEffect = if (isStash) dashEffect else null,
                    )
                }

                passingLanes.forEach { plotLane ->
                    val laneX = laneCenterX(plotLane) * density
                    drawLine(
                        color = laneColor(plotLane),
                        start = Offset(laneX, 0f),
                        end = Offset(laneX, this.size.height),
                        strokeWidth = strokeWidth,
                    )
                }
            }
        }

        val nodeSize = when {
            isStash -> STASH_NODE_SIZE
            isMerge -> MERGE_NODE_SIZE
            else -> AVATAR_NODE_SIZE
        }

        CommitNode(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = (laneCenterX(itemPosition) - nodeSize / 2f).dp),
            author = plotCommit.author,
            isMerge = isMerge,
            isStash = isStash,
            color = nodeColor,
        )
    }
}

/**
 * Draws a line that leaves the node horizontally and turns with a rounded corner into the target lane, continuing
 * vertically until [endY].
 */
private fun DrawScope.drawLaneConnection(
    startX: Float,
    centerY: Float,
    targetX: Float,
    endY: Float,
    color: Color,
    strokeWidth: Float,
    maxRadius: Float,
    pathEffect: PathEffect? = null,
) {
    val directionX = sign(targetX - startX)
    val directionY = sign(endY - centerY)
    val radius = min(maxRadius, min(abs(targetX - startX), abs(endY - centerY)))

    val path = Path().apply {
        moveTo(startX, centerY)
        lineTo(targetX - directionX * radius, centerY)
        cubicTo(
            targetX - directionX * radius * 0.45f, centerY,
            targetX, centerY + directionY * radius * 0.55f,
            targetX, centerY + directionY * radius,
        )
        lineTo(targetX, endY)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, pathEffect = pathEffect)
    )
}

@Composable
fun CommitNode(
    modifier: Modifier = Modifier,
    author: Identity,
    isMerge: Boolean,
    isStash: Boolean,
    color: Color,
) {
    when {
        isStash -> {
            val background = MaterialTheme.colors.background

            Box(
                modifier = modifier
                    .size(STASH_NODE_SIZE.dp)
                    .drawBehind {
                        val stroke = 1.5f * density
                        drawRoundRect(color = background, cornerRadius = CornerRadius(2f * density))
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(2f * density),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * density, 2f * density)),
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.stash),
                    modifier = Modifier.size(12.dp),
                    contentDescription = null,
                    tint = color,
                )
            }
        }

        isMerge -> {
            InstantTooltip(
                "${author.name} <${author.email}>",
                position = InstantTooltipPosition.RIGHT,
            ) {
                Box(
                    modifier = modifier
                        .size(MERGE_NODE_SIZE.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        else -> {
            InstantTooltip(
                "${author.name} <${author.email}>",
                position = InstantTooltipPosition.RIGHT,
            ) {
                Box(
                    modifier = modifier
                        .size(AVATAR_NODE_SIZE.dp)
                        .clip(CircleShape)
                        .background(color)
                        .padding(2.dp)
                ) {
                    AvatarImage(
                        modifier = Modifier.fillMaxSize(),
                        personIdent = author,
                        shape = CircleShape,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
fun UncommittedChangesGraphNode(
    modifier: Modifier = Modifier,
    hasPreviousCommits: Boolean,
    color: Color,
) {
    val density = LocalDensity.current.density

    Canvas(
        modifier = modifier
    ) {
        clipRect {
            val nodeX = laneCenterX(0) * density
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(3f * density, 3f * density))

            // Like GitKraken, the WIP node is not connected to HEAD

            val radius = (AVATAR_NODE_SIZE / 2f - 1) * density

            // Hollow dashed ring, like GitKraken (the row background shows through)
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(nodeX, this.center.y),
                style = Stroke(width = LINE_STROKE_WIDTH * density, pathEffect = dashEffect),
            )
        }
    }
}

fun getBranchesGrouped(groupBranches: Boolean, branches: List<Branch>): List<Pair<Branch, Branch?>> {
    return if (groupBranches) {
        val branchesToSkip = mutableListOf<Branch>()

        branches.mapNotNull { branch ->
            if (branchesToSkip.contains(branch)) {
                return@mapNotNull null
            }

            val trackingBranch = branch.trackingBranch
            val trackingRemoteBranch = if (branch.matchesTrackingBranchName && trackingBranch != null) {
                val remoteBranchInSameCommit = branches.firstOrNull {
                    it.isRemote && it.remoteName == trackingBranch.remote &&
                            it.simpleName == trackingBranch.branch
                }

                if (remoteBranchInSameCommit != null) {
                    branchesToSkip.add(remoteBranchInSameCommit)
                    remoteBranchInSameCommit
                } else {
                    null
                }
            } else {
                null
            }

            branch to trackingRemoteBranch
        }
    } else {
        branches.map { it to (null as Branch?) }
    }
}

val Branch.logName: String
    get() = when {
        this.name == Constants.HEAD -> {
            this.name
        }

        this.isRemote -> {
            name.replace("refs/remotes/", "")
        }

        else -> {
            val split = this.name.split("/")
            split.takeLast(split.size - LOCAL_PREFIX_LENGTH).joinToString("/")
        }
    }

/**
 * Commit context menu options that need extra input from the user.
 */
sealed interface CommitDialogRequest {
    val commit: Commit

    data class EditMessage(override val commit: Commit) : CommitDialogRequest
    data class AnnotatedTag(override val commit: Commit) : CommitDialogRequest
    data class Worktree(override val commit: Commit) : CommitDialogRequest
}

private suspend fun chooseWorktreeDirectory(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
    val isMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    if (isMac) {
        // The AWT dialog is the native one on macOS, it picks folders with this property
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Choose an empty folder for the new worktree", java.awt.FileDialog.LOAD)
            dialog.isVisible = true
            val directory = dialog.directory
            val file = dialog.file
            if (directory != null && file != null) java.io.File(directory, file).absolutePath else null
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
    } else {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Choose an empty folder for the new worktree"
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        }

        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }
}
