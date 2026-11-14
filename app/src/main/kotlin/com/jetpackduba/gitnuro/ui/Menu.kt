package com.jetpackduba.gitnuro.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.jetpackduba.gitnuro.app.generated.resources.*
import com.jetpackduba.gitnuro.domain.models.PullType
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.extensions.handOnHover
import com.jetpackduba.gitnuro.extensions.ignoreKeyEvents
import com.jetpackduba.gitnuro.repositoryopen.LogAction
import com.jetpackduba.gitnuro.keybindings.Keybinding
import com.jetpackduba.gitnuro.keybindings.KeybindingOption
import com.jetpackduba.gitnuro.keybindings.keyBinding
import com.jetpackduba.gitnuro.repositoryopen.RepositoryOpenViewModel
import com.jetpackduba.gitnuro.theme.monoTypography
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import com.jetpackduba.gitnuro.ui.components.PrimaryButton
import com.jetpackduba.gitnuro.ui.components.tooltip.InstantTooltip
import com.jetpackduba.gitnuro.ui.context_menu.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import com.jetpackduba.gitnuro.theme.deleteFile
import com.jetpackduba.gitnuro.theme.addFile
import com.jetpackduba.gitnuro.repositoryopen.PushBehindState
import com.jetpackduba.gitnuro.domain.models.ToolbarPullMode
import com.jetpackduba.gitnuro.repositoryopen.LfsOperation
import com.jetpackduba.gitnuro.ui.context_menu.DropDownMenu
import com.jetpackduba.gitnuro.ui.context_menu.ContextMenuElement

private const val DISABLED_BUTTON_ALPHA = 0.5F

@Composable
fun Menu(
    modifier: Modifier,
    viewModel: RepositoryOpenViewModel,
    onCreateBranch: () -> Unit,
    onOpenAnotherRepository: (String) -> Unit,
    onOpenAnotherRepositoryFromPicker: () -> Unit,
    onStashWithMessage: () -> Unit,
    onQuickActions: () -> Unit,
    onShowSettingsDialog: () -> Unit,
    showOpenPopup: Boolean,
    onShowOpenPopupChange: (Boolean) -> Unit,
) {
    val isPullWithRebaseDefault by viewModel.isPullWithRebaseDefault.collectAsState(false)
    val lastLoadedTabs by viewModel.displayLastLoadedTabs.collectAsState()
    val hasUncommittedChanges by viewModel.hasUncommittedChanges.collectAsState()
    val stashesState by viewModel.stashesState.collectAsState()
    val remotes by viewModel.remoteState.collectAsState()
    val hasRemotes = remotes.remotes.isNotEmpty()
    val (position, setPosition) = remember { mutableStateOf<LayoutCoordinates?>(null) }

    val branchesState by viewModel.branchesState.collectAsState()
    val statusState by viewModel.statusState.collectAsState()
    val repositoryName = remember(statusState.repositoryPath) {
        statusState.repositoryPath?.let { File(it).name }.orEmpty()
    }
    val currentBranch = branchesState.currentBranch

    Row(
        modifier = modifier
            .background(MaterialTheme.colors.surface)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BreadcrumbItem(
                modifier = Modifier.onGloballyPositioned { setPosition(it) },
                label = "repository",
                value = repositoryName,
                onClick = { onShowOpenPopupChange(true) },
            )

            Icon(
                painter = painterResource(Res.drawable.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackgroundSecondary,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(18.dp),
            )

            DropDownMenu(
                items = {
                    branchesState.branches.map { branch ->
                        val isCurrent = branch.name == currentBranch?.name

                        ContextMenuElement.ContextTextEntry(
                            label = branch.simpleName,
                            icon = { painterResource(if (isCurrent) Res.drawable.done else Res.drawable.branch) },
                            onClick = { if (!isCurrent) viewModel.checkoutBranch(branch) },
                        )
                    }
                }
            ) {
                BreadcrumbItem(
                    label = "branch",
                    value = currentBranch?.simpleName ?: "detached HEAD",
                    onClick = null,
                )
            }
        }

        val undoRedoState by viewModel.undoRedoState.collectAsState()
        val undoAction = undoRedoState.undo
        val redoAction = undoRedoState.redo

        MenuButton(
            title = "Undo",
            icon = painterResource(Res.drawable.undo),
            enabled = undoAction != null,
            tooltip = undoAction?.let { "Undo ${it.description}" } ?: "Nothing to undo",
            keybinding = null,
            onClick = { viewModel.undo() },
        )

        MenuButton(
            title = "Redo",
            icon = painterResource(Res.drawable.redo),
            enabled = redoAction != null,
            tooltip = redoAction?.let { "Redo ${it.description}" } ?: "Nothing to redo",
            keybinding = null,
            onClick = { viewModel.redo() },
        )

        val toolbarPullMode by viewModel.toolbarPullMode.collectAsState(ToolbarPullMode.PULL_FAST_FORWARD_IF_POSSIBLE)

        val pullTooltip = if (!hasRemotes) {
            stringResource(Res.string.menu_pull_tooltip_disabled)
        } else {
            toolbarPullMode.title
        }

        // The button shows the operation it runs: "Fetch" or "Pull", each one with its icon
        val isFetchMode = toolbarPullMode == ToolbarPullMode.FETCH_ALL

        ExtendedMenuButton(
            title = if (isFetchMode) "Fetch" else stringResource(Res.string.menu_pull),
            tooltipText = pullTooltip,
            enabled = hasRemotes,
            icon = painterResource(if (isFetchMode) Res.drawable.fetch else Res.drawable.download),
            keybinding = KeybindingOption.PULL.keyBinding,
            onClick = { viewModel.runToolbarPull() },
            extendedListItems = buildList {
                add(
                    ContextMenuElement.ContextHint(
                        "Click an option to run it. Click its radio button to make it the default operation of this button"
                    )
                )

                ToolbarPullMode.entries.forEach { mode ->
                    add(
                        ContextMenuElement.ContextTextEntry(
                            label = mode.title,
                            icon = {
                                painterResource(
                                    if (mode == toolbarPullMode) Res.drawable.radio_checked else Res.drawable.radio_unchecked
                                )
                            },
                            // The option runs once, the default of the button doesn't change
                            onClick = { viewModel.runToolbarPull(mode) },
                            // The radio button only makes it the default, nothing is run
                            onIconClick = { viewModel.setToolbarPullMode(mode) },
                        )
                    )
                }
            },
        )

        val pushTooltip = if (!hasRemotes) {
            stringResource(Res.string.menu_push_tooltip_disabled)
        } else {
            stringResource(Res.string.menu_push_tooltip)
        }

        // Like GitKraken, Push is a plain button (no dropdown with extra push options)
        MenuButton(
            title = stringResource(Res.string.menu_push),
            tooltip = pushTooltip,
            enabled = hasRemotes,
            icon = painterResource(Res.drawable.upload),
            keybinding = KeybindingOption.PUSH.keyBinding,
            onClick = { viewModel.push(force = false, pushTags = false) },
        )

        MenuButton(
            title = stringResource(Res.string.menu_branch),
            icon = painterResource(Res.drawable.branch),
            onClick = {
                onCreateBranch()
            },
            tooltip = stringResource(Res.string.menu_branch_tooltip),
            keybinding = KeybindingOption.BRANCH_CREATE.keyBinding,
        )

        val enableStash = hasUncommittedChanges

        val stashTooltipText = if (enableStash) {
            stringResource(Res.string.menu_stash_tooltip)
        } else {
            stringResource(Res.string.menu_stash_tooltip_disabled)
        }

        ExtendedMenuButton(
            title = stringResource(Res.string.menu_stash),
            tooltipText = stashTooltipText,
            enabled = enableStash,
            icon = painterResource(Res.drawable.stash),
            keybinding = if (enableStash) KeybindingOption.STASH.keyBinding else null,
            onClick = { viewModel.stash() },
            extendedListItems = stashContextMenuItems(
                onStashWithMessage = onStashWithMessage
            )
        )

        val enablePopStash = stashesState.stashes.isNotEmpty()

        val popStashTooltipText = if (enablePopStash) {
            stringResource(Res.string.menu_pop_stash_tooltip)
        } else {
            stringResource(Res.string.menu_pop_stash_tooltip_disabled)
        }

        MenuButton(
            title = stringResource(Res.string.menu_pop_stash),
            icon = painterResource(Res.drawable.apply_stash),
            keybinding = KeybindingOption.STASH_POP.keyBinding,
            tooltip = popStashTooltipText,
            enabled = enablePopStash,
        ) { viewModel.popStash() }

        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .width(1.dp)
                .height(32.dp)
                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.12f))
        )

        MenuButton(
            title = stringResource(Res.string.menu_terminal),
            icon = painterResource(Res.drawable.terminal),
            onClick = { viewModel.openTerminal() },
            tooltip = stringResource(Res.string.menu_terminal_tooltip),
            keybinding = null,
        )

        val isLfsRepository by viewModel.isLfsRepository.collectAsState()

        if (isLfsRepository) {
            LfsMenuButton(onOperation = { viewModel.runLfs(it) })
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(
                title = stringResource(Res.string.menu_actions),
                icon = painterResource(Res.drawable.bolt),
                onClick = onQuickActions,
                tooltip = stringResource(Res.string.menu_actions_tooltip),
                keybinding = null,
            )

            MenuButton(
                title = stringResource(Res.string.menu_settings),
                icon = painterResource(Res.drawable.settings),
                onClick = onShowSettingsDialog,
                tooltip = stringResource(Res.string.menu_settings_tooltip),
                keybinding = KeybindingOption.SETTINGS.keyBinding,
            )

            MenuButton(
                title = "Search",
                icon = painterResource(Res.drawable.search),
                onClick = { viewModel.onAction(LogAction.SearchValueChange("")) },
                tooltip = "Search commits by message, author or hash",
                keybinding = null,
            )
        }
    }

    if (showOpenPopup && position != null) {
        val boundsInRoot = position.boundsInRoot()

        Popup(
            popupPositionProvider =
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        return IntOffset(boundsInRoot.left.toInt(), boundsInRoot.bottom.toInt())
                    }
                },
            onDismissRequest = { onShowOpenPopupChange(false) },
            properties = PopupProperties(focusable = true),
        ) {
            val searchFocusRequester = remember { FocusRequester() }

            Column(
                modifier = Modifier
                    .width(600.dp)
                    .heightIn(max = 600.dp)
                    .background(MaterialTheme.colors.surface)
                    .border(2.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                PrimaryButton(
                    text = stringResource(Res.string.menu_open_dialog_title),
                    onClick = {
                        onShowOpenPopupChange(false)
                        onOpenAnotherRepositoryFromPicker()
                    },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                )

                Box(modifier = Modifier.weight(1f)) {
                    RecentRepositoriesList(
                        recentlyOpenedRepositories = lastLoadedTabs,
                        canRepositoriesBeRemoved = false,
                        searchFieldFocusRequester = searchFocusRequester,
                        onRemoveRepositoryFromRecent = {},
                        onOpenKnownRepository = {
                            onShowOpenPopupChange(false)
                            onOpenAnotherRepository(it)
                        },
                        onExitClicked = {
                            onShowOpenPopupChange(false)
                        }
                    )
                }
            }

            LaunchedEffect(Unit) {
                searchFocusRequester.requestFocus()
            }
        }
    }
}

private val ToolbarPullMode.title: String
    get() = when (this) {
        ToolbarPullMode.FETCH_ALL -> "Fetch All"
        ToolbarPullMode.PULL_FAST_FORWARD_IF_POSSIBLE -> "Pull (fast-forward if possible)"
        ToolbarPullMode.PULL_FAST_FORWARD_ONLY -> "Pull (fast-forward only)"
        ToolbarPullMode.PULL_REBASE -> "Pull (rebase)"
    }

/**
 * Replaces the toolbar when a push would be rejected because the remote has commits the local branch doesn't have.
 */
@Composable
fun PushBehindBanner(
    state: PushBehindState,
    pullTitle: String,
    onPull: () -> Unit,
    onForcePush: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .background(MaterialTheme.colors.surface)
            .background(MaterialTheme.colors.primary.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "'${state.localRef}' is behind '${state.remoteRef}'. Update your branch by doing a Pull.",
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onBackground,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )

        BannerButton(text = pullTitle, color = MaterialTheme.colors.addFile, onClick = onPull)
        BannerButton(text = "Force Push", color = MaterialTheme.colors.deleteFile, onClick = onForcePush)
        BannerButton(text = "Cancel", color = MaterialTheme.colors.onBackground, onClick = onCancel)
    }
}

@Composable
private fun BannerButton(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)

    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colors.onBackground,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = 0.9f), shape)
            .background(color.copy(alpha = 0.15f))
            .handMouseClickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun LfsMenuButton(onOperation: (LfsOperation) -> Unit) {
    DropDownMenu(
        showIcons = false,
        items = {
            LfsOperation.entries.map { operation ->
                ContextMenuElement.ContextTextEntry(
                    label = operation.menuTitle,
                    onClick = { onOperation(operation) },
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LFS",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onBackground,
            )

            Icon(
                painter = painterResource(Res.drawable.dropdown),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun MenuButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String,
    icon: Painter,
    keybinding: Keybinding?,
    tooltip: String,
    tooltipEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val keybinding = if (enabled) keybinding else null

    InstantTooltip(
        text = tooltip,
        enabled = tooltipEnabled,
        trailingContent = if (keybinding != null) {
            { KeybindingHint(keybinding) }
        } else {
            null
        }
    ) {
        val color = MaterialTheme.colors.onBackground.copy(alpha = if (enabled) 1F else DISABLED_BUTTON_ALPHA)

        Column(
            modifier = modifier
                .ignoreKeyEvents()
                .clip(RoundedCornerShape(4.dp))
                .handMouseClickable(enabled) { onClick() }
                .size(width = 52.dp, height = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MenuButtonLabel(title, color)

            Icon(
                painter = icon,
                contentDescription = title,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(18.dp),
                tint = color,
            )
        }
    }
}

@Composable
fun KeybindingHint(keybinding: Keybinding) {
    val parts = remember(keybinding) { getParts(keybinding) }.joinToString("+")

    Text(
        parts,
        fontFamily = monoTypography(),
        fontSize = MaterialTheme.typography.caption.fontSize,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colors.onBackgroundSecondary,
    )
}

@Preview
@Composable
fun KeybindingHintPartPreview() {
    KeybindingHintPart("CTRL")
}

@Composable
fun KeybindingHintPart(part: String) {
    Text(
        text = part,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colors.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(2.dp, MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colors.primary.copy(alpha = 0.05f))
            .padding(horizontal = 4.dp, vertical = 4.dp)

    )
}

fun getParts(keybinding: Keybinding): List<String> {
    val parts = mutableListOf<String>()

    if (keybinding.control) {
        parts.add("Ctrl")
    }

    if (keybinding.meta) {
        parts.add("⌘")
    }

    if (keybinding.alt) {
        parts.add("Alt")
    }

    if (keybinding.shift) {
        parts.add("Shift")
    }

    val key = when (keybinding.key) {
        Key.A -> "A"
        Key.B -> "B"
        Key.C -> "C"
        Key.D -> "D"
        Key.E -> "E"
        Key.F -> "F"
        Key.G -> "G"
        Key.H -> "H"
        Key.I -> "I"
        Key.J -> "J"
        Key.K -> "K"
        Key.L -> "L"
        Key.M -> "M"
        Key.N -> "N"
        Key.O -> "O"
        Key.P -> "P"
        Key.Q -> "Q"
        Key.R -> "R"
        Key.S -> "S"
        Key.T -> "T"
        Key.U -> "U"
        Key.V -> "V"
        Key.W -> "W"
        Key.X -> "X"
        Key.Y -> "Y"
        Key.Z -> "Z"
        Key.Tab -> "Tab"
        else -> throw NotImplementedError("Key not implemented")
    }

    parts.add(key)

    return parts
}

@Composable
fun ExtendedMenuButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String,
    tooltipText: String,
    icon: Painter,
    keybinding: Keybinding?,
    onClick: () -> Unit,
    extendedListItems: List<ContextMenuElement>,
) {
    val color = MaterialTheme.colors.onBackground.copy(alpha = if (enabled) 1F else DISABLED_BUTTON_ALPHA)
    val keybinding = if (enabled) keybinding else null

    Row(
        modifier = modifier
            .size(width = 60.dp, height = 44.dp)
            .ignoreKeyEvents()
            .clip(RoundedCornerShape(4.dp))
            .handMouseClickable(enabled) { onClick() }
    ) {
        InstantTooltip(
            text = tooltipText,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            trailingContent = if (keybinding != null) {
                { KeybindingHint(keybinding) }
            } else {
                null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MenuButtonLabel(title, color)

                Icon(
                    painter = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(18.dp),
                    tint = color,
                )
            }
        }

        DropDownMenu(
            enabled = enabled,
            items = { extendedListItems }
        ) {
            // Like GitKraken: a small grey filled triangle, vertically centered in the button
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
                    .ignoreKeyEvents(),
                contentAlignment = Alignment.Center,
            ) {
                DropdownTriangle(color = MaterialTheme.colors.onBackgroundSecondary.copy(alpha = color.alpha))
            }
        }
    }
}

@Composable
private fun DropdownTriangle(color: Color) {
    Canvas(modifier = Modifier.size(width = 9.dp, height = 5.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }

        drawPath(path, color)
    }
}

@Composable
private fun MenuButtonLabel(title: String, color: Color) {
    Text(
        text = title,
        fontSize = 11.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        color = color.copy(alpha = color.alpha * 0.85f),
    )
}

/**
 * Element of the repository/branch breadcrumb: a small label on top of the value, followed by a dropdown arrow.
 */
@Composable
private fun BreadcrumbItem(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .run { if (onClick != null) handMouseClickable(onClick = onClick) else handOnHover() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colors.onBackgroundSecondary,
            maxLines = 1,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )

            Icon(
                painter = painterResource(Res.drawable.dropdown),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackgroundSecondary,
                modifier = Modifier.padding(start = 4.dp).size(14.dp),
            )
        }
    }
}
