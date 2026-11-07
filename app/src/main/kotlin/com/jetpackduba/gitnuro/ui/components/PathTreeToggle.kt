package com.jetpackduba.gitnuro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.list
import com.jetpackduba.gitnuro.app.generated.resources.tree
import com.jetpackduba.gitnuro.extensions.backgroundIf
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.theme.backgroundSelected
import com.jetpackduba.gitnuro.ui.components.tooltip.DelayedTooltip
import com.jetpackduba.gitnuro.app.generated.resources.unfold_less
import com.jetpackduba.gitnuro.app.generated.resources.unfold_more
import org.jetbrains.compose.resources.painterResource

/**
 * Segmented control to switch between showing the changed files as a flat list of paths or as a directory tree.
 */
@Composable
fun PathTreeToggle(
    showAsTree: Boolean,
    onShowAsTreeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** False when only the tree makes sense (e.g. viewing all the files of a commit) */
    allowPath: Boolean = true,
) {
    val shape = RoundedCornerShape(3.dp)

    Row(
        modifier = modifier
            .height(24.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.12f), shape),
    ) {
        if (allowPath) {
            SegmentedOption(
                text = "Path",
                icon = painterResource(Res.drawable.list),
                isSelected = !showAsTree,
                onClick = { if (showAsTree) onShowAsTreeChange(false) },
            )
        }

        SegmentedOption(
            text = "Tree",
            icon = painterResource(Res.drawable.tree),
            isSelected = showAsTree,
            onClick = { if (!showAsTree) onShowAsTreeChange(true) },
        )
    }
}

@Composable
private fun SegmentedOption(
    text: String,
    icon: Painter,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .backgroundIf(isSelected, MaterialTheme.colors.backgroundSelected, MaterialTheme.colors.background)
            .handMouseClickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colors.onBackground,
            modifier = Modifier.size(14.dp),
        )

        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Toolbar shown above a list of changed files: Path/Tree toggle, A→Z/Z→A sort toggle and, in tree mode,
 * expand all / collapse all buttons.
 */
@Composable
fun ChangesViewOptions(
    showAsTree: Boolean,
    sortDescending: Boolean,
    canExpandAll: Boolean,
    canCollapseAll: Boolean,
    onShowAsTreeChange: (Boolean) -> Unit,
    onToggleSortOrder: () -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    modifier: Modifier = Modifier,
    allowPath: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PathTreeToggle(
            showAsTree = showAsTree,
            onShowAsTreeChange = onShowAsTreeChange,
            allowPath = allowPath,
        )

        DelayedTooltip(if (sortDescending) "Sorted Z to A" else "Sorted A to Z") {
            ToolbarGroup {
                ToolbarTextButton(
                    text = if (sortDescending) "Z→A" else "A→Z",
                    onClick = onToggleSortOrder,
                )
            }
        }

        if (showAsTree && (canExpandAll || canCollapseAll)) {
            ToolbarGroup {
                if (canExpandAll) {
                    DelayedTooltip("Expand all") {
                        ToolbarIconButton(
                            icon = painterResource(Res.drawable.unfold_more),
                            enabled = true,
                            onClick = onExpandAll,
                        )
                    }
                }

                if (canExpandAll && canCollapseAll) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colors.onBackground.copy(alpha = 0.12f))
                    )
                }

                if (canCollapseAll) {
                    DelayedTooltip("Collapse all") {
                        ToolbarIconButton(
                            icon = painterResource(Res.drawable.unfold_less),
                            enabled = true,
                            onClick = onCollapseAll,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarGroup(content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(3.dp)

    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.12f), shape)
            .background(MaterialTheme.colors.background),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ToolbarTextButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .handMouseClickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onBackground,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: Painter,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 26.dp, height = 24.dp)
            .handMouseClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colors.onBackground.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(16.dp),
        )
    }
}
