package com.jetpackduba.gitnuro.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Pointer position (in root coordinates) over the closest [ScrollableLazyColumn], or null when it's outside.
 *
 * Hover state of list rows is derived from it instead of `Modifier.hoverable`: hoverable only reacts to the pointer
 * entering/leaving, so when the list scrolls (or reuses a row for another item) under a still pointer, rows were left
 * "hovered" after the pointer moved away.
 */
val LocalListPointerPosition = compositionLocalOf<State<Offset?>?> { null }

/**
 * Tracks the pointer over the list, without consuming any event.
 */
@Composable
fun Modifier.trackListPointer(position: MutableState<Offset?>): Modifier {
    // Kept across recompositions: pointerInput(Unit) keeps its first lambda, which reads this holder
    val coordinatesHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }

    return this
        .onGloballyPositioned { coordinatesHolder[0] = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)

                    position.value = when (event.type) {
                        PointerEventType.Exit -> null
                        else -> {
                            val local = event.changes.firstOrNull()?.position
                            val layout = coordinatesHolder[0]
                            if (local != null && layout != null && layout.isAttached) layout.localToRoot(local) else null
                        }
                    }
                }
            }
        }
}

class ListItemHover(val modifier: Modifier, isHovered: State<Boolean>) {
    val isHovered by isHovered
}

/**
 * Hover state of a list row: apply [ListItemHover.modifier] to the row and read [ListItemHover.isHovered].
 * Outside of a [ScrollableLazyColumn] it's never hovered.
 */
@Composable
fun rememberListItemHover(): ListItemHover {
    val pointerPosition = LocalListPointerPosition.current
    var bounds by remember { mutableStateOf<Rect?>(null) }

    val isHovered = remember(pointerPosition) {
        derivedStateOf {
            val position = pointerPosition?.value
            val rowBounds = bounds
            position != null && rowBounds != null && rowBounds.contains(position)
        }
    }

    // Bounds in root are clipped to the visible area and are updated when the list scrolls
    val modifier = remember { Modifier.onGloballyPositioned { bounds = it.boundsInRoot() } }

    return remember(modifier, isHovered) { ListItemHover(modifier, isHovered) }
}
