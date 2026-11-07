package com.jetpackduba.gitnuro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.branch
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private const val VISIBLE_DURATION_MS = 2200L
private const val ANIMATION_DURATION_MS = 180

/**
 * Prominent, non-blocking card shown after switching branches. It animates in, stays for a moment and animates out,
 * calling [onFinished] once it's fully hidden. Clicking it dismisses it early.
 */
@Composable
fun BranchSwitchedNotification(
    branchName: String,
    onFinished: () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        delay(VISIBLE_DURATION_MS)
        visibleState.targetState = false
    }

    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState) {
            currentOnFinished()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(ANIMATION_DURATION_MS)) + scaleIn(tween(ANIMATION_DURATION_MS), initialScale = 0.85f),
        exit = fadeOut(tween(ANIMATION_DURATION_MS)) + scaleOut(tween(ANIMATION_DURATION_MS), targetScale = 0.95f),
    ) {
        val shape = RoundedCornerShape(12.dp)

        Row(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 520.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colors.surface)
                .border(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.6f), shape)
                .clickable { visibleState.targetState = false }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.branch),
                    contentDescription = null,
                    tint = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Switched to branch",
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.body2,
                )
                Text(
                    text = branchName,
                    color = MaterialTheme.colors.onBackground,
                    style = MaterialTheme.typography.body1,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
