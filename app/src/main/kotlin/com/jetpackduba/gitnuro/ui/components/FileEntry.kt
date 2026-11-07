package com.jetpackduba.gitnuro.ui.components

import com.jetpackduba.gitnuro.extensions.listRowClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetpackduba.gitnuro.extensions.backgroundIf
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.extensions.onDoubleClick
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.arrow_drop_down
import com.jetpackduba.gitnuro.app.generated.resources.arrow_right
import com.jetpackduba.gitnuro.theme.backgroundSelected
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import com.jetpackduba.gitnuro.ui.context_menu.ContextMenu
import com.jetpackduba.gitnuro.ui.context_menu.ContextMenuElement
import org.jetbrains.compose.resources.painterResource

/**
 * Metrics of the changed files tree, measured from GitKraken so both trees look the same.
 */
object FileTreeMetrics {
    /** Space before the first column (the directory triangle) */
    val rowStartPadding = 9.dp

    /** Extra start space for every nesting level */
    val levelIndent = 15.dp

    val directoryRowHeight = 25.dp
    val fileRowHeight = 30.dp

    /** The triangle drawable has internal padding, the visible triangle is ~8dp wide */
    val directoryArrowSize = 20.dp
    val directoryNamePadding = 3.dp

    /** Space before the file status icon so it's slightly shifted to the right of the parent's triangle column */
    val fileIconOffset = 8.dp
    val fileIconSize = 14.dp
    val fileNamePadding = 6.dp

    fun startPadding(depth: Int) = rowStartPadding + levelIndent * depth
}

@Composable
fun FileEntry(
    icon: ImageVector,
    iconColor: Color,
    parentDirectoryPath: String,
    fileName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    depth: Int = 0,
    isTreeItem: Boolean = false,
    onGenerateContextMenu: () -> List<ContextMenuElement>,
    trailingAction: (@Composable BoxScope.(isHovered: Boolean) -> Unit)?,
) {
    val hover = rememberListItemHover()
    val isHovered = hover.isHovered

    TreeRow(
        height = FileTreeMetrics.fileRowHeight,
        depth = depth,
        isSelected = isSelected,
        hover = hover,
        onClick = onClick,
        onDoubleClick = onDoubleClick,
        onGenerateContextMenu = onGenerateContextMenu,
        trailingAction = trailingAction?.let { action -> { action(isHovered) } },
    ) {
        if (isTreeItem) {
            Spacer(Modifier.width(FileTreeMetrics.fileIconOffset))
        }

        Icon(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(end = FileTreeMetrics.fileNamePadding)
                .size(FileTreeMetrics.fileIconSize),
            tint = iconColor,
        )

        if (parentDirectoryPath.isNotEmpty()) {
            Text(
                text = parentDirectoryPath.removeSuffix("/"),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.body1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onBackgroundSecondary,
            )

            Text(
                text = "/",
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.body1,
                overflow = TextOverflow.Visible,
                color = MaterialTheme.colors.onBackgroundSecondary,
            )
        }

        Text(
            text = fileName,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(end = 16.dp),
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onBackground,
        )
    }
}

@Composable
fun DirectoryEntry(
    dirName: String,
    isExpanded: Boolean,
    depth: Int = 0,
    onClick: () -> Unit,
    onGenerateContextMenu: () -> List<ContextMenuElement>,
) {
    TreeRow(
        height = FileTreeMetrics.directoryRowHeight,
        depth = depth,
        isSelected = false,
        hover = rememberListItemHover(),
        onClick = onClick,
        onDoubleClick = {},
        onGenerateContextMenu = onGenerateContextMenu,
        trailingAction = null,
    ) {
        DirectoryArrow(isExpanded)

        Text(
            text = dirName,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = FileTreeMetrics.directoryNamePadding, end = 16.dp),
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onBackgroundSecondary,
        )
    }
}

@Composable
fun DirectoryArrow(isExpanded: Boolean) {
    Icon(
        painter = painterResource(if (isExpanded) Res.drawable.arrow_drop_down else Res.drawable.arrow_right),
        contentDescription = null,
        tint = MaterialTheme.colors.onBackgroundSecondary,
        modifier = Modifier.size(FileTreeMetrics.directoryArrowSize),
    )
}

@Composable
private fun TreeRow(
    height: Dp,
    depth: Int,
    isSelected: Boolean,
    hover: ListItemHover,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onGenerateContextMenu: () -> List<ContextMenuElement>,
    trailingAction: (@Composable BoxScope.() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    val isHovered = hover.isHovered

    Box(
        modifier = Modifier
            .listRowClickable { onClick() }
            .onDoubleClick(onDoubleClick)
            .fillMaxWidth()
            .then(hover.modifier)
    ) {
        ContextMenu(
            items = onGenerateContextMenu,
        ) {
            Row(
                modifier = Modifier
                    .height(height)
                    .fillMaxWidth()
                    .backgroundIf(
                        condition = isSelected,
                        color = MaterialTheme.colors.backgroundSelected,
                        elseColor = if (isHovered) MaterialTheme.colors.onBackground.copy(alpha = 0.05f) else null,
                    )
                    .padding(start = FileTreeMetrics.startPadding(depth)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        trailingAction?.invoke(this)
    }
}
