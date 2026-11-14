package com.jetpackduba.gitnuro.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.chevron_right
import com.jetpackduba.gitnuro.app.generated.resources.expand_more
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import org.jetbrains.compose.resources.painterResource

/** Height of [SideMenuHeader]: its row plus the divider line above it */
val SIDE_MENU_HEADER_HEIGHT = 31.dp

@Composable
fun SideMenuHeader(
    text: String,
    icon: Painter? = null,
    itemsCount: Int,
    isExpanded: Boolean,
    onExpand: () -> Unit = {},
    hoverIcon: @Composable (() -> Unit)? = null,
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    // Opaque: the header stays fixed at the top of its list and the rows scroll behind it
    Column(modifier = Modifier.background(MaterialTheme.colors.surface)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.06f))
        )

        Row(
            modifier = Modifier
                .height(30.dp)
                .fillMaxWidth()
                .hoverable(hoverInteraction)
                .handMouseClickable { onExpand() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (isExpanded) Res.drawable.expand_more else Res.drawable.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colors.onBackgroundSecondary,
                modifier = Modifier
                    .padding(start = 6.dp, end = 4.dp)
                    .size(16.dp),
            )

            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onBackground,
                    modifier = Modifier
                        .size(14.dp),
                )
            }

            Text(
                text = text.uppercase(),
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .weight(1f),
                maxLines = 1,
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground,
                overflow = TextOverflow.Ellipsis,
            )

            if (hoverIcon != null && isHovered) {
                hoverIcon()
            } else
                Text(
                    text = itemsCount.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.primaryVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
        }
    }
}
