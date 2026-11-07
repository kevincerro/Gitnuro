package com.jetpackduba.gitnuro.ui.context_menu

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLocalization
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition
import com.jetpackduba.gitnuro.app.generated.resources.*
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.extensions.handOnHover
import com.jetpackduba.gitnuro.keybindings.KeybindingOption
import com.jetpackduba.gitnuro.keybindings.matchesBinding
import com.jetpackduba.gitnuro.theme.isDark
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import org.jetbrains.compose.resources.painterResource
import java.awt.event.KeyEvent
import kotlin.math.abs

private var lastCheck: Long = 0
private const val MIN_TIME_BETWEEN_POPUPS_IN_MS = 20

@Composable
fun ContextMenu(enabled: Boolean = true, items: () -> List<ContextMenuElement>, function: @Composable () -> Unit) {
    Box(modifier = Modifier.contextMenu(enabled, items), propagateMinConstraints = true) {
        function()
    }
}

@Composable
fun DropDownMenu(
    enabled: Boolean = true,
    showIcons: Boolean = true,
    items: () -> List<ContextMenuElement>,
    function: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .dropdownMenu(enabled, showIcons, items),
        propagateMinConstraints = true,
    ) {
        function()
    }
}


@Composable
fun DropDownMenuText(
    enabled: Boolean = true,
    showIcons: Boolean = true,
    currentValue: String,
    width: Dp = 180.dp,
    items: () -> List<ContextMenuElement>,
) {
    DropDownMenu(enabled, showIcons, items) {
        Row(
            modifier = Modifier.width(width)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.1F),
                    shape = RoundedCornerShape(4.dp),
                )
                .clip(shape = RoundedCornerShape(4.dp))
                .background(MaterialTheme.colors.background)
                .padding(vertical = 8.dp, horizontal = 12.dp)
                .handOnHover(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentValue,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )

            Icon(
                painter = painterResource(Res.drawable.dropdown),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.contextMenu(enabled: Boolean, items: () -> List<ContextMenuElement>): Modifier {
    val (mouseOffset, setMouseOffset) = remember { mutableStateOf<Offset?>(null) }
    val (elementPosition, setElementPosition) = remember { mutableStateOf<Offset?>(null) }

    val modifier = this
        .onGloballyPositioned { layoutCoordinates ->
            val offsetToRoot = layoutCoordinates.localToRoot(Offset.Zero)
            setElementPosition(offsetToRoot)
        }
        .pointerInput(items, enabled) {
            awaitEachGesture {
                val event = awaitEventFirstDown()

                if (enabled && event.buttons.isSecondaryPressed) {
                    val currentCheck = System.currentTimeMillis()

                    if (lastCheck != 0L && currentCheck - lastCheck < MIN_TIME_BETWEEN_POPUPS_IN_MS) {
                        println("Popup ignored!")
                    } else {
                        lastCheck = currentCheck
                        event.changes.forEach { it.consume() }
                        setMouseOffset(event.changes[0].position)
                    }
                }
            }
        }

    if (mouseOffset != null && elementPosition != null) {
        val contextMenuElements = items()
        if (contextMenuElements.isNotEmpty()) {
            DisableSelection {
                showPopup(
                    showIcons = true,
                    x = (elementPosition.x + mouseOffset.x).toInt(),
                    y = (elementPosition.y + mouseOffset.y).toInt(),
                    contextMenuElements = contextMenuElements,
                    onDismissRequest = { setMouseOffset(null) },
                )
            }
        }
    }

    return modifier
}

private suspend fun AwaitPointerEventScope.awaitEventFirstDown(): PointerEvent {
    var event: PointerEvent
    do {
        event = awaitPointerEvent()
    } while (
        !event.changes.fastAll { it.changedToDown() }
    )
    return event
}


@Composable
private fun Modifier.dropdownMenu(
    enabled: Boolean,
    showIcons: Boolean,
    items: () -> List<ContextMenuElement>
): Modifier {
    val (isClicked, setIsClicked) = remember { mutableStateOf(false) }
    val (offset, setOffset) = remember { mutableStateOf<Offset?>(null) }
    val mod = this
        .onGloballyPositioned { layoutCoordinates ->
            val offsetToRoot = layoutCoordinates.localToRoot(Offset.Zero)
            val offsetToBottomOfComponent = offsetToRoot.copy(y = offsetToRoot.y + layoutCoordinates.size.height)
            setOffset(offsetToBottomOfComponent)
        }
        .handMouseClickable(enabled) {
            setIsClicked(true)
        }

    if (offset != null && isClicked) {
        showPopup(
            showIcons,
            offset.x.toInt(),
            offset.y.toInt(),
            items(),
            onDismissRequest = { setIsClicked(false) })
    }

    return mod
}

@Composable
fun showPopup(
    showIcons: Boolean,
    x: Int,
    y: Int,
    contextMenuElements: List<ContextMenuElement>,
    onDismissRequest: () -> Unit,
) {
    Popup(
        properties = PopupProperties(
            focusable = true,
        ),
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val resultY = if (popupContentSize.height > windowSize.height) {
                    0 // If the popup is taller than the window itself
                } else if (y + popupContentSize.height > windowSize.height) {
                    // If the end of the popup would go out of bounds.
                    // Move the Y a bit to the top to make it fit
                    y - abs(windowSize.height - (y + popupContentSize.height))
                } else {
                    y
                }

                val resultX = if (x + popupContentSize.width > windowSize.width && popupContentSize.width < x) {
                    // If the end of the popup would go out of bounds.
                    // Move the X a bit to the left to make it fit
                    x - abs(windowSize.width - (x + popupContentSize.width))
                } else {
                    x
                }

                return IntOffset(resultX, resultY)
            }
        },
        onDismissRequest = onDismissRequest
    ) {

        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .menuSurface()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.matchesBinding(KeybindingOption.EXIT)) {
                        onDismissRequest()
                        true
                    } else
                        false
                },
        ) {
            MenuItemsColumn(
                showIcons = showIcons,
                contextMenuElements = contextMenuElements,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
private fun Modifier.menuSurface(): Modifier {
    return this
        .dropShadow(
            shape = RoundedCornerShape(6.dp),
            shadow = Shadow(
                radius = 5.dp,
                spread = 6.dp,
                color = Color(0x20000000),
            )
        )
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colors.background)
        .width(IntrinsicSize.Max)
        .widthIn(min = 180.dp)
        .run {
            if (MaterialTheme.colors.isDark) {
                this.border(
                    1.dp,
                    MaterialTheme.colors.onBackground.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                )
            } else
                this
        }
}

@Composable
private fun MenuItemsColumn(
    showIcons: Boolean,
    contextMenuElements: List<ContextMenuElement>,
    onDismissRequest: () -> Unit,
) {
    // Only reserve space for icons when at least one entry has one
    val iconsVisible = showIcons && contextMenuElements.any {
        (it is ContextMenuElement.ContextTextEntry && it.icon != null) ||
                (it is ContextMenuElement.ContextSubmenu && it.icon != null)
    }
    var openSubmenuIndex by remember(contextMenuElements) { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        contextMenuElements.forEachIndexed { index, item ->
            when (item) {
                is ContextMenuElement.ContextTextEntry -> TextEntry(
                    iconsVisible,
                    item,
                    onDismissRequest = onDismissRequest,
                    onHovered = { openSubmenuIndex = null },
                )

                is ContextMenuElement.ContextSubmenu -> SubmenuEntry(
                    showIcons = iconsVisible,
                    submenu = item,
                    isOpen = openSubmenuIndex == index,
                    onOpen = { openSubmenuIndex = index },
                    onClose = { if (openSubmenuIndex == index) openSubmenuIndex = null },
                    onDismissRequest = onDismissRequest,
                )

                ContextMenuElement.ContextSeparator -> Separator()
                is ContextMenuElement.ContextHint -> Text(
                    text = item.text,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onBackgroundSecondary,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun Separator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colors.onBackground.copy(alpha = 0.15f))
    )
}

@Composable
private fun MenuEntryRow(
    showIcons: Boolean,
    icon: @Composable (() -> Painter)?,
    label: String,
    isHighlighted: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    trailing: @Composable (Color) -> Unit = {},
) {
    val contentColor = if (isHighlighted) Color.White else MaterialTheme.colors.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isHighlighted) MaterialTheme.colors.primary else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Default)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcons) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp)
            ) {
                if (icon != null) {
                    Icon(
                        painter = icon(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = if (isHighlighted) contentColor else MaterialTheme.colors.onBackgroundSecondary.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Text(
            label,
            style = MaterialTheme.typography.body2,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        trailing(contentColor)
    }
}

@Composable
fun TextEntry(
    showIcons: Boolean,
    contextTextEntry: ContextMenuElement.ContextTextEntry,
    onDismissRequest: () -> Unit,
    onHovered: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(isHovered) {
        if (isHovered) onHovered()
    }

    MenuEntryRow(
        showIcons = showIcons,
        icon = contextTextEntry.icon,
        label = contextTextEntry.composableLabel(),
        isHighlighted = isHovered,
        interactionSource = interactionSource,
        onClick = {
            onDismissRequest()
            contextTextEntry.onClick()
        },
    )
}

@Composable
private fun SubmenuEntry(
    showIcons: Boolean,
    submenu: ContextMenuElement.ContextSubmenu,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val density = LocalDensity.current

    LaunchedEffect(isHovered) {
        if (isHovered) onOpen()
    }

    Box {
        MenuEntryRow(
            showIcons = showIcons,
            icon = submenu.icon,
            label = submenu.composableLabel(),
            isHighlighted = isHovered || isOpen,
            interactionSource = interactionSource,
            onClick = onOpen,
            trailing = { color ->
                Icon(
                    painter = painterResource(Res.drawable.chevron_right),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 16.dp).size(14.dp),
                    tint = color,
                )
            },
        )

        if (isOpen) {
            val verticalPaddingPx = with(density) { 4.dp.roundToPx() }

            Popup(
                properties = PopupProperties(focusable = false),
                onDismissRequest = onClose,
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val x = if (anchorBounds.right + popupContentSize.width <= windowSize.width) {
                            anchorBounds.right
                        } else {
                            (anchorBounds.left - popupContentSize.width).coerceAtLeast(0)
                        }

                        val y = (anchorBounds.top - verticalPaddingPx)
                            .coerceAtMost(windowSize.height - popupContentSize.height)
                            .coerceAtLeast(0)

                        return IntOffset(x, y)
                    }
                },
            ) {
                Box(modifier = Modifier.menuSurface()) {
                    MenuItemsColumn(
                        showIcons = showIcons,
                        contextMenuElements = submenu.items,
                        onDismissRequest = onDismissRequest,
                    )
                }
            }
        }
    }
}

sealed class ContextMenuElement(
    label: String,
    onClick: () -> Unit = {},
) : ContextMenuItem(label, onClick) {
    class ContextTextEntry(
        label: String,
        val composableLabel: @Composable (() -> String) = { label },
        val icon: @Composable (() -> Painter)? = null,
        onClick: () -> Unit = {},
    ) : ContextMenuElement(label, onClick) {
        constructor(
            composableLabel: @Composable (() -> String),
            icon: @Composable (() -> Painter)? = null,
            onClick: () -> Unit = {},
        ) : this(
            label = "",
            composableLabel,
            icon,
            onClick,
        )
    }

    class ContextSubmenu(
        val composableLabel: @Composable (() -> String),
        val icon: @Composable (() -> Painter)? = null,
        val items: List<ContextMenuElement>,
    ) : ContextMenuElement("", {})

    /** Non clickable explanatory text */
    class ContextHint(val text: String) : ContextMenuElement(text, {})

    object ContextSeparator : ContextMenuElement("", {})
}


@ExperimentalFoundationApi
class AppPopupMenu : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable () -> Unit,
    ) {
        val localization = LocalLocalization.current
        val items = {
            listOfNotNull(
                textManager.copy?.let {
                    ContextMenuElement.ContextTextEntry(
                        label = localization.copy,
                        icon = { painterResource(Res.drawable.copy) },
                        onClick = it.execute
                    )
                },
                textManager.cut?.let {
                    ContextMenuElement.ContextTextEntry(
                        label = localization.cut,
                        icon = { painterResource(Res.drawable.cut) },
                        onClick = it.execute
                    )
                },
                textManager.paste?.let {
                    ContextMenuElement.ContextTextEntry(
                        label = localization.paste,
                        icon = { painterResource(Res.drawable.paste) },
                        onClick = it.execute
                    )
                },
                textManager.selectAll?.let {
                    ContextMenuElement.ContextTextEntry(
                        label = localization.selectAll,
                        icon = null,
                        onClick = it.execute
                    )
                },
            )
        }
        CompositionLocalProvider(
            LocalContextMenuRepresentation provides AppContextMenuRepresentation()
        ) {
            ContextMenuArea(items, state, content = content)
        }

    }
}

class AppContextMenuRepresentation : ContextMenuRepresentation {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        LightDefaultContextMenuRepresentation
        val status = state.status
        if (status is ContextMenuState.Status.Open) {
            var focusManager: FocusManager? by mutableStateOf(null)
            var inputModeManager: InputModeManager? by mutableStateOf(null)

            Popup(
                properties = PopupProperties(
                    focusable = true,
                ),
                onDismissRequest = { state.status = ContextMenuState.Status.Closed },
                popupPositionProvider = rememberPopupPositionProviderAtPosition(
                    positionPx = status.rect.center
                ),
                onKeyEvent = {
                    if (it.type == KeyEventType.KeyDown) {
                        when (it.key.nativeKeyCode) {
                            KeyEvent.VK_ESCAPE -> {
                                state.status = ContextMenuState.Status.Closed
                                true
                            }

                            KeyEvent.VK_DOWN -> {
                                inputModeManager?.requestInputMode(InputMode.Keyboard)
                                focusManager?.moveFocus(FocusDirection.Next)
                                true
                            }

                            KeyEvent.VK_UP -> {
                                inputModeManager?.requestInputMode(InputMode.Keyboard)
                                focusManager?.moveFocus(FocusDirection.Previous)
                                true
                            }

                            else -> false
                        }
                    } else {
                        false
                    }
                },
            ) {
                focusManager = LocalFocusManager.current
                inputModeManager = LocalInputModeManager.current

                val border = if (MaterialTheme.colors.isDark) {
                    BorderStroke(2.dp, MaterialTheme.colors.onBackgroundSecondary.copy(alpha = 0.2f))
                } else
                    null

                Column(
                    modifier = Modifier
                        .shadow(8.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colors.background)
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .run {
                            if (border != null)
                                border(border, MaterialTheme.shapes.small)
                            else
                                this
                        }

                ) {
                    items().forEach { item ->
                        when (item) {
                            is ContextMenuElement.ContextTextEntry -> TextEntry(
                                showIcons = true,
                                contextTextEntry = item,
                                onDismissRequest = { state.status = ContextMenuState.Status.Closed }
                            )

                            is ContextMenuElement.ContextSeparator -> Separator()
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

fun MutableList<ContextMenuElement>.addSubmenu(
    composableLabel: @Composable (() -> String),
    icon: @Composable (() -> Painter)? = null,
    items: List<ContextMenuElement>,
) {
    this.add(
        ContextMenuElement.ContextSubmenu(
            composableLabel = composableLabel,
            icon = icon,
            items = items,
        )
    )
}

fun MutableList<ContextMenuElement>.addContextMenu(
    composableLabel: @Composable (() -> String),
    icon: @Composable (() -> Painter)? = null,
    onClick: () -> Unit = {},
) {
    this.add(
        ContextMenuElement.ContextTextEntry(
            composableLabel = composableLabel,
            icon = icon,
            onClick = onClick,
        )
    )
}