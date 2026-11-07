@file:OptIn(ExperimentalComposeUiApi::class)

package com.jetpackduba.gitnuro.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetpackduba.gitnuro.extensions.backgroundIf
import com.jetpackduba.gitnuro.extensions.onDoubleClick
import com.jetpackduba.gitnuro.theme.backgroundSelected
import com.jetpackduba.gitnuro.theme.linesHeight
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val SUBENTRY_START_PADDING = 24

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SideMenuSubentry(
    text: String,
    fontWeight: FontWeight = FontWeight.Normal,
    iconResourcePath: DrawableResource,
    isSelected: Boolean,
    extraPadding: Dp = 0.dp,
    iconTint: Color = MaterialTheme.colors.onBackgroundSecondary,
    secondaryText: String? = null,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    /** Background of the whole row (e.g. the current branch, green in GitKraken) */
    backgroundColor: Color? = null,
    /** Shown at the very start of the row, before the indentation (e.g. the current branch checkbox) */
    leading: (@Composable () -> Unit)? = null,
    /** Replaces the icon (e.g. a remote's avatar) */
    iconContent: (@Composable () -> Unit)? = null,
    additionalInfo: @Composable () -> Unit = {},
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .height(MaterialTheme.linesHeight.sidePanelItemHeight)
            .fillMaxWidth()
            .hoverable(hoverInteraction)
            .clickable { onClick() }
            .run {
                if (onDoubleClick != null)
                    this.onDoubleClick(onDoubleClick)
                else
                    this
            }
            .backgroundIf(
                condition = isSelected,
                color = MaterialTheme.colors.backgroundSelected,
                elseColor = backgroundColor
                    ?: if (isHovered) MaterialTheme.colors.onBackground.copy(alpha = 0.05f) else null,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(SUBENTRY_START_PADDING.dp),
            contentAlignment = Alignment.Center,
        ) {
            leading?.invoke()
        }

        Spacer(Modifier.width(extraPadding))

        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(14.dp),
        ) {
            if (iconContent != null) {
                iconContent()
            } else {
                Icon(
                    painter = painterResource(iconResourcePath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = iconTint,
                )
            }
        }

        Text(
            text = text,
            fontWeight = fontWeight,
            modifier = Modifier.weight(1f, fill = secondaryText == null),
            maxLines = 1,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onBackground,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )

        if (secondaryText != null) {
            Text(
                text = secondaryText,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .weight(1f, fill = true),
                maxLines = 1,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onBackgroundSecondary,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        additionalInfo()
    }
}
