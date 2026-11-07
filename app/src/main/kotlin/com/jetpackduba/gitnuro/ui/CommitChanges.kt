package com.jetpackduba.gitnuro.ui

import androidx.compose.ui.text.TextRange
import com.jetpackduba.gitnuro.repositoryopen.CommitTreeFile
import com.jetpackduba.gitnuro.ui.status.SmallCheckbox
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.draw.shadow
import com.jetpackduba.gitnuro.repositoryopen.CommitSignature
import com.jetpackduba.gitnuro.repositoryopen.SignatureState
import com.jetpackduba.gitnuro.repositoryopen.SignatureType
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpackduba.gitnuro.LocalDateTimeFormat
import com.jetpackduba.gitnuro.app.generated.resources.*
import com.jetpackduba.gitnuro.compose.rememberInTab
import com.jetpackduba.gitnuro.domain.extensions.fileName
import com.jetpackduba.gitnuro.domain.extensions.filePath
import com.jetpackduba.gitnuro.domain.extensions.parentDirectoryPath
import com.jetpackduba.gitnuro.domain.models.Commit
import com.jetpackduba.gitnuro.domain.models.DiffSelected
import com.jetpackduba.gitnuro.domain.models.Identity
import com.jetpackduba.gitnuro.extensions.*
import com.jetpackduba.gitnuro.repositoryopen.CommitChangesAction
import com.jetpackduba.gitnuro.repositoryopen.CommitChangesState
import com.jetpackduba.gitnuro.repositoryopen.LogAction
import com.jetpackduba.gitnuro.repositoryopen.RepositoryOpenViewModel
import com.jetpackduba.gitnuro.theme.*
import com.jetpackduba.gitnuro.ui.components.*
import com.jetpackduba.gitnuro.ui.context_menu.ContextMenu
import com.jetpackduba.gitnuro.ui.context_menu.ContextMenuElement
import com.jetpackduba.gitnuro.ui.context_menu.committedChangesEntriesContextMenuItems
import com.jetpackduba.gitnuro.ui.tree_files.TreeItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.eclipse.jgit.diff.DiffEntry
import org.jetbrains.compose.resources.painterResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CHANGE_ROW_HEIGHT = 32

@Composable
fun CommitChanges(
    viewModel: RepositoryOpenViewModel,
    commitChangesState: CommitChangesState,
    onBlame: (String) -> Unit,
    onHistory: (String) -> Unit,
) {
    val diffSelected by viewModel
        .diffSelected
        .filterIsInstance<DiffSelected.CommitedChanges>()
        .collectAsState(null)

    val logState by viewModel.logState.collectAsState()
    val uncommittedChangesCount = if (logState.hasUncommittedChanges) logState.statusSummary.total else 0

    val commitSignature by viewModel.commitSignature.collectAsState()

    CommitChangesView(
        commitSignature = commitSignature,
        uncommittedChangesCount = uncommittedChangesCount,
        onViewUncommittedChanges = { viewModel.onAction(LogAction.UncommittedChangesSelected) },
        diffSelected = diffSelected,
        commitChangesState = commitChangesState,
        onBlame = onBlame,
        onHistory = onHistory,
        onOpenFileInFolder = { viewModel.openFileInFolder(it) },
        onAction = { viewModel.onAction(it) },
    )
}

@Composable
private fun CommitChangesView(
    commitSignature: CommitSignature?,
    uncommittedChangesCount: Int,
    onViewUncommittedChanges: () -> Unit,
    commitChangesState: CommitChangesState,
    diffSelected: DiffSelected.CommitedChanges?,
    onBlame: (String) -> Unit,
    onHistory: (String) -> Unit,
    onOpenFileInFolder: (String) -> Unit,
    onAction: (CommitChangesAction) -> Unit,
) {
    val commit = commitChangesState.commit

    val showSearch = commitChangesState.showSearch
    val showAsTree = commitChangesState.showAsTree

    // TODO Is this needed?
    var searchFilter by remember(commitChangesState.searchFilter, showSearch, commitChangesState) {
        mutableStateOf(commitChangesState.searchFilter)
    }

    val changesListScroll = rememberInTab("commitChangesChangesScroll", commitChangesState.commit) {
        LazyListState()
    }

    val textScroll = rememberInTab("commitChangesTextScroll", commitChangesState.commit) {
        ScrollState(0)
    }

    // The filter is always visible, like GitKraken
    val isFiltering = searchFilter.text.isNotBlank()
    val changes = if (isFiltering) commitChangesState.changesFiltered else commitChangesState.changes

    val onGenerateContextMenu: (DiffEntry) -> List<ContextMenuElement> = { diffEntry ->
        committedChangesEntriesContextMenuItems(
            diffEntry,
            onBlame = { onBlame(diffEntry.filePath) },
            onHistory = { onHistory(diffEntry.filePath) },
            onOpenFileInFolder = { onOpenFileInFolder(diffEntry.parentDirectoryPath) },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface),
    ) {
        if (uncommittedChangesCount > 0) {
            WorkingDirectoryChangesBanner(
                changesCount = uncommittedChangesCount,
                onViewChanges = onViewUncommittedChanges,
            )
        }

        CommitHeader(commit, commitSignature?.takeIf { it.commitHash == commit.hash })

        CommitMessageBox(
            message = commit.message,
            textScroll = textScroll,
        )

        CommitAuthorInfo(commit)

        ChangesSummary(
            changes = commitChangesState.changes,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 22.dp),
        )

        ChangesToolbar(
            showAsTree = showAsTree || commitChangesState.viewAllFilesChecked,
            viewAllFiles = commitChangesState.viewAllFilesChecked,
            onViewAllFilesChange = { onAction(CommitChangesAction.ToggleViewAllFiles(it)) },
            sortDescending = commitChangesState.sortDescending,
            canExpandAll = commitChangesState.treeExpansionState.canExpandAll,
            canCollapseAll = commitChangesState.treeExpansionState.canCollapseAll,
            showSearch = showSearch,
            onShowAsTreeChange = { onAction(CommitChangesAction.ToggleShowAsTree) },
            onToggleSortOrder = { onAction(CommitChangesAction.ToggleSortOrder) },
            onExpandAll = { onAction(CommitChangesAction.ExpandAllDirectories) },
            onCollapseAll = { onAction(CommitChangesAction.CollapseAllDirectories) },
            onSearchFilterToggled = { onAction(CommitChangesAction.SearchFilterToggle(it)) },
        )

        FilterTextField(
            value = searchFilter.text,
            onValueChange = { text ->
                onAction(CommitChangesAction.SearchFilterChanged(TextFieldValue(text, TextRange(text.length))))
            },
            placeholder = "Filter files",
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
        )

        if (commitChangesState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (commitChangesState.viewAllFiles) {
            TreeCommitAllFiles(
                treeItems = commitChangesState.allFilesTree,
                allChanges = commitChangesState.changes,
                diffSelected = diffSelected,
                changesListScroll = changesListScroll,
                onDiffSelected = { onAction(CommitChangesAction.SelectEntry(it)) },
                onGenerateContextMenu = onGenerateContextMenu,
                onDirectoryClicked = { onAction(CommitChangesAction.TreeDirectoryToggle(it.fullPath)) },
            )
        } else if (showAsTree) {
            val treeItems = if (isFiltering) {
                commitChangesState.changesTreeFiltered
            } else {
                commitChangesState.changesTree
            }

            TreeCommitLogChanges(
                diffSelected = diffSelected,
                changesListScroll = changesListScroll,
                treeItems = treeItems,
                allChanges = changes,
                onDiffSelected = { onAction(CommitChangesAction.SelectEntry(it)) },
                onGenerateContextMenu = onGenerateContextMenu,
                onDirectoryClicked = { onAction(CommitChangesAction.TreeDirectoryToggle(it.fullPath)) },
            )
        } else {
            ListCommitLogChanges(
                diffSelected = diffSelected,
                changesListScroll = changesListScroll,
                diffEntries = changes,
                onDiffSelected = { onAction(CommitChangesAction.SelectEntry(it)) },
                onGenerateContextMenu = onGenerateContextMenu,
            )
        }
    }
}

@Composable
private fun WorkingDirectoryChangesBanner(
    changesCount: Int,
    onViewChanges: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colors.primary.copy(alpha = 0.55f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (changesCount == 1) "1 file change in working directory" else "$changesCount file changes in working directory",
            style = MaterialTheme.typography.body1,
            color = Color.White,
            maxLines = 1,
        )

        Text(
            text = "View Change",
            style = MaterialTheme.typography.body1,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                .handMouseClickable(onClick = onViewChanges)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun CommitHeader(commit: Commit, signature: CommitSignature?) {
    var copied by remember(commit.hash) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (signature != null) {
            SignatureIcon(signature)
        } else {
            Icon(
                painter = painterResource(Res.drawable.description),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackgroundSecondary,
                modifier = Modifier.size(16.dp),
            )
        }

        Text(
            text = "commit:",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        )

        InstantTooltipText(
            text = commit.hash.take(6),
            tooltip = "Click to copy ${commit.hash}",
            modifier = Modifier.handMouseClickable {
                scope.launch {
                    clipboard.setText(AnnotatedString(commit.hash))
                    copied = true
                    delay(2000) // 2s
                    copied = false
                }
            },
        )

        if (copied) {
            Text(
                text = "Copied!",
                color = MaterialTheme.colors.primaryVariant,
                maxLines = 1,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignatureIcon(signature: CommitSignature) {
    val color = when (signature.state) {
        SignatureState.GOOD -> MaterialTheme.colors.addFile
        SignatureState.UNKNOWN -> MaterialTheme.colors.onBackgroundSecondary
        SignatureState.BAD -> MaterialTheme.colors.deleteFile
    }

    TooltipArea(
        delayMillis = 300,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
        tooltip = { SignatureDetails(signature) },
    ) {
        Icon(
            painter = painterResource(Res.drawable.sign),
            contentDescription = "GPG signature details",
            tint = color,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SignatureDetails(signature: CommitSignature) {
    val shape = RoundedCornerShape(6.dp)

    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colors.background)
            .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.1f), shape)
            .padding(16.dp),
    ) {
        Text(
            text = if (signature.type == SignatureType.SSH) "SSH Signature Details" else "GPG Signature Details",
            style = MaterialTheme.typography.h4,
            color = MaterialTheme.colors.onBackground,
        )

        if (signature.keyword.isNotEmpty()) {
            Text(
                text = signature.keyword,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Text(
            text = signature.description,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(top = if (signature.keyword.isNotEmpty()) 4.dp else 14.dp),
        )

        Column(modifier = Modifier.padding(top = 14.dp)) {
            if (signature.keyType.isNotEmpty()) SignatureDetailLine("Key Type:", signature.keyType)
            if (signature.keyId.isNotEmpty()) SignatureDetailLine("Key ID:", signature.keyId)
            if (signature.trust.isNotEmpty()) SignatureDetailLine("Trust:", signature.trust)
            if (signature.signer.isNotEmpty()) SignatureDetailLine("Primary User ID:", signature.signer)
        }
    }
}

@Composable
private fun SignatureDetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(end = 8.dp),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
        )
    }
}

@Composable
private fun InstantTooltipText(
    text: String,
    tooltip: String,
    modifier: Modifier,
) {
    com.jetpackduba.gitnuro.ui.components.tooltip.InstantTooltip(text = tooltip) {
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            maxLines = 1,
            modifier = modifier,
        )
    }
}

@Composable
private fun CommitMessageBox(
    message: String,
    textScroll: ScrollState,
) {
    val normalizedMessage = remember(message) { message.trim().replace("\r\n", "\n") }
    val summary = normalizedMessage.substringBefore("\n")
    val body = normalizedMessage.substringAfter("\n", missingDelimiterValue = "").trim()

    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colors.background)
            .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(textScroll)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    text = summary,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colors.onBackground,
                )

                if (body.isNotEmpty()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackgroundSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun CommitAuthorInfo(commit: Commit) {
    val author = commit.author
    val committer = commit.committer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AvatarImage(
            modifier = Modifier.size(40.dp),
            personIdent = author,
            shape = RoundedCornerShape(4.dp),
        )

        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TooltipText(
                text = author.name.orEmpty(),
                maxLines = 1,
                style = MaterialTheme.typography.body1,
                tooltipTitle = author.email.orEmpty(),
            )

            Text(
                text = "authored ${commit.date.toDetailedDateString()}",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackgroundSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (committer.email != author.email || committer.name != author.name) {
                Text(
                    text = "committed by ${committer.name.orEmpty()}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onBackgroundSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (commit.parentsHashes.isNotEmpty()) {
            Text(
                text = buildString {
                    append(if (commit.parentsHashes.count() > 1) "parents: " else "parent: ")
                    append(commit.parentsHashes.joinToString(", ") { it.take(6) })
                },
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun Long.toDetailedDateString(): String {
    val dateTimeFormat = LocalDateTimeFormat.current
    val dateTime = Instant.ofEpochSecond(this).atZone(ZoneId.systemDefault())
    val date = this.toSmartSystemString(allowRelative = false)
    val time = DateTimeFormatter.ofPattern(if (dateTimeFormat.is24hours) "HH:mm" else "hh:mm a").format(dateTime)

    return "$date @ $time"
}

private data class ChangesCount(
    val modified: Int = 0,
    val added: Int = 0,
    val deleted: Int = 0,
    val renamed: Int = 0,
) {
    operator fun plus(changeType: DiffEntry.ChangeType) = when (changeType) {
        DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.COPY -> copy(added = added + 1)
        DiffEntry.ChangeType.MODIFY -> copy(modified = modified + 1)
        DiffEntry.ChangeType.DELETE -> copy(deleted = deleted + 1)
        DiffEntry.ChangeType.RENAME -> copy(renamed = renamed + 1)
    }
}

private fun List<DiffEntry>.countChanges(): ChangesCount = fold(ChangesCount()) { count, entry ->
    count + entry.changeType
}

/**
 * Number of changes of each type contained inside each directory (recursively), keyed by directory path.
 */
private fun List<DiffEntry>.countChangesByDirectory(): Map<String, ChangesCount> {
    val counts = mutableMapOf<String, ChangesCount>()

    for (entry in this) {
        val parts = entry.filePath.split("/").dropLast(1)

        for (index in parts.indices) {
            val dirPath = parts.slice(0..index).joinToString("/")
            counts[dirPath] = counts.getOrElse(dirPath) { ChangesCount() } + entry.changeType
        }
    }

    return counts
}

@Composable
private fun ChangesSummary(
    changes: List<DiffEntry>,
    modifier: Modifier = Modifier,
) {
    val count = remember(changes) { changes.countChanges() }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count.modified > 0) {
            ChangeCountLabel(Icons.Default.Edit, MaterialTheme.colors.modifyFile, "${count.modified} modified")
        }

        if (count.added > 0) {
            ChangeCountLabel(Icons.Default.Add, MaterialTheme.colors.addFile, "${count.added} added")
        }

        if (count.deleted > 0) {
            ChangeCountLabel(Icons.Default.Remove, MaterialTheme.colors.deleteFile, "${count.deleted} deleted")
        }

        if (count.renamed > 0) {
            ChangeCountLabel(Icons.AutoMirrored.Filled.ArrowForward, MaterialTheme.colors.primaryVariant, "${count.renamed} renamed")
        }
    }
}

@Composable
private fun ChangeCountLabel(icon: ImageVector, color: Color, text: String) {
    Row(
        modifier = Modifier.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ChangesToolbar(
    showAsTree: Boolean,
    viewAllFiles: Boolean,
    onViewAllFilesChange: (Boolean) -> Unit,
    sortDescending: Boolean,
    canExpandAll: Boolean,
    canCollapseAll: Boolean,
    showSearch: Boolean,
    onShowAsTreeChange: (Boolean) -> Unit,
    onToggleSortOrder: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onSearchFilterToggled: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The filter is a field below the toolbar (always visible, like GitKraken)
        Spacer(Modifier.weight(1f))

        ChangesViewOptions(
            showAsTree = showAsTree,
            sortDescending = sortDescending,
            canExpandAll = canExpandAll,
            canCollapseAll = canCollapseAll,
            onShowAsTreeChange = onShowAsTreeChange,
            onToggleSortOrder = onToggleSortOrder,
            onExpandAll = onExpandAll,
            onCollapseAll = onCollapseAll,
            allowPath = !viewAllFiles,
        )

        Spacer(Modifier.weight(1f))

        SmallCheckbox(
            checked = viewAllFiles,
            onCheckedChange = { onViewAllFilesChange(!viewAllFiles) },
            text = "View all files",
        )
    }
}

@Composable
fun ListCommitLogChanges(
    diffEntries: List<DiffEntry>,
    diffSelected: DiffSelected.CommitedChanges?,
    changesListScroll: LazyListState,
    onDiffSelected: (DiffEntry) -> Unit,
    onGenerateContextMenu: (DiffEntry) -> List<ContextMenuElement>,
) {
    ScrollableLazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        state = changesListScroll,
    ) {
        items(items = diffEntries) { diffEntry ->
            ChangeRow(
                isSelected = diffSelected?.items?.any { it.diffEntry == diffEntry } ?: false,
                depth = 0,
                onClick = { onDiffSelected(diffEntry) },
                onGenerateContextMenu = { onGenerateContextMenu(diffEntry) },
            ) {
                ChangeTypeIcon(diffEntry)

                val parentDirectoryPath = diffEntry.parentDirectoryPath.removeSuffix("/")

                if (parentDirectoryPath.isNotEmpty()) {
                    Text(
                        text = "$parentDirectoryPath/",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackgroundSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                Text(
                    text = diffEntry.fileName,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * "View all files": the whole tree of the commit. Changed files show their change type and can be opened,
 * directories show how many changes they contain.
 */
@Composable
private fun TreeCommitAllFiles(
    treeItems: List<TreeItem<CommitTreeFile>>,
    allChanges: List<DiffEntry>,
    diffSelected: DiffSelected.CommitedChanges?,
    changesListScroll: LazyListState,
    onDiffSelected: (DiffEntry) -> Unit,
    onDirectoryClicked: (TreeItem.Dir) -> Unit,
    onGenerateContextMenu: (DiffEntry) -> List<ContextMenuElement>,
) {
    val changesByDirectory = remember(allChanges) { allChanges.countChangesByDirectory() }

    ScrollableLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = changesListScroll,
    ) {
        items(items = treeItems, key = { it.fullPath }) { entry ->
            when (entry) {
                is TreeItem.File -> {
                    val diffEntry = entry.data.diffEntry

                    ChangeRow(
                        isSelected = diffEntry != null && diffSelected?.items?.any { it.diffEntry == diffEntry } == true,
                        depth = entry.depth,
                        height = FileTreeMetrics.fileRowHeight,
                        onClick = { if (diffEntry != null) onDiffSelected(diffEntry) },
                        onGenerateContextMenu = { diffEntry?.let(onGenerateContextMenu).orEmpty() },
                    ) {
                        Spacer(Modifier.width(FileTreeMetrics.fileIconOffset))

                        if (diffEntry != null) {
                            ChangeTypeIcon(diffEntry)
                        } else {
                            // Keep the names aligned with the changed files
                            Spacer(Modifier.width(FileTreeMetrics.fileIconSize + FileTreeMetrics.fileNamePadding))
                        }

                        Text(
                            text = entry.displayName,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onBackground,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                is TreeItem.Dir -> ChangeRow(
                    isSelected = false,
                    depth = entry.depth,
                    height = FileTreeMetrics.directoryRowHeight,
                    onClick = { onDirectoryClicked(entry) },
                    onGenerateContextMenu = { emptyList() },
                ) {
                    DirectoryArrow(entry.isExpanded)

                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackgroundSecondary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = FileTreeMetrics.directoryNamePadding),
                    )

                    changesByDirectory[entry.fullPath]?.let { DirectoryChangesCount(it) }
                }
            }
        }
    }
}

@Composable
fun TreeCommitLogChanges(
    treeItems: List<TreeItem<DiffEntry>>,
    allChanges: List<DiffEntry>,
    diffSelected: DiffSelected.CommitedChanges?,
    changesListScroll: LazyListState,
    onDiffSelected: (DiffEntry) -> Unit,
    onDirectoryClicked: (TreeItem.Dir) -> Unit,
    onGenerateContextMenu: (DiffEntry) -> List<ContextMenuElement>,
) {
    val changesByDirectory = remember(allChanges) { allChanges.countChangesByDirectory() }

    ScrollableLazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        state = changesListScroll,
    ) {
        items(items = treeItems) { entry ->
            when (entry) {
                is TreeItem.File -> ChangeRow(
                    isSelected = diffSelected?.items?.any { it.diffEntry == entry.data } ?: false,
                    depth = entry.depth,
                    height = FileTreeMetrics.fileRowHeight,
                    onClick = { onDiffSelected(entry.data) },
                    onGenerateContextMenu = { onGenerateContextMenu(entry.data) },
                ) {
                    Spacer(Modifier.width(FileTreeMetrics.fileIconOffset))
                    ChangeTypeIcon(entry.data)

                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                is TreeItem.Dir -> ChangeRow(
                    isSelected = false,
                    depth = entry.depth,
                    height = FileTreeMetrics.directoryRowHeight,
                    onClick = { onDirectoryClicked(entry) },
                    onGenerateContextMenu = { emptyList() },
                ) {
                    DirectoryArrow(entry.isExpanded)

                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackgroundSecondary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = FileTreeMetrics.directoryNamePadding),
                    )

                    val count = changesByDirectory[entry.fullPath]

                    if (count != null) {
                        DirectoryChangesCount(count)
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryChangesCount(count: ChangesCount) {
    @Composable
    fun Entry(icon: ImageVector, color: Color, value: Int) {
        if (value <= 0) return

        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(start = 10.dp).size(11.dp))

        Text(
            text = value.toString(),
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(start = 3.dp),
        )
    }

    Entry(Icons.Default.Edit, MaterialTheme.colors.modifyFile, count.modified)
    Entry(Icons.Default.Add, MaterialTheme.colors.addFile, count.added)
    Entry(Icons.Default.Remove, MaterialTheme.colors.deleteFile, count.deleted)
    Entry(Icons.AutoMirrored.Filled.ArrowForward, MaterialTheme.colors.primaryVariant, count.renamed)
}

@Composable
private fun ChangeTypeIcon(diffEntry: DiffEntry) {
    Icon(
        painter = rememberVectorPainter(diffEntry.icon),
        contentDescription = null,
        tint = diffEntry.iconColor,
        modifier = Modifier
            .padding(end = FileTreeMetrics.fileNamePadding)
            .size(FileTreeMetrics.fileIconSize),
    )
}

@Composable
private fun ChangeRow(
    isSelected: Boolean,
    depth: Int,
    height: Dp = CHANGE_ROW_HEIGHT.dp,
    onClick: () -> Unit,
    onGenerateContextMenu: () -> List<ContextMenuElement>,
    content: @Composable RowScope.() -> Unit,
) {
    val hover = rememberListItemHover()
    val isHovered = hover.isHovered

    ContextMenu(items = onGenerateContextMenu) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(hover.modifier)
                .backgroundIf(
                    condition = isSelected,
                    color = MaterialTheme.colors.backgroundSelected,
                    elseColor = if (isHovered) MaterialTheme.colors.onBackground.copy(alpha = 0.05f) else null,
                )
                .listRowClickable(onClick = onClick)
                .padding(start = FileTreeMetrics.startPadding(depth), end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
