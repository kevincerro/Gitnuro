package com.jetpackduba.gitnuro.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.warning
import com.jetpackduba.gitnuro.theme.dialogOverlay
import com.jetpackduba.gitnuro.ui.dialogs.base.IconBasedDialog
import org.jetbrains.compose.resources.painterResource

/**
 * Second step before a force push: it overwrites the remote branch, so the remote commits that aren't in the local
 * branch are lost.
 */
@Composable
fun ForcePushDialog(
    localRef: String,
    remoteRef: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            scrimColor = MaterialTheme.colors.dialogOverlay,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        IconBasedDialog(
            icon = painterResource(Res.drawable.warning),
            title = "Force push '${localRef.shortRefName()}'?",
            subtitle = "This is a destructive operation: '${remoteRef.shortRefName()}' will be overwritten and the " +
                    "commits that are only in the remote branch will be lost. It can't be undone.",
            primaryActionText = "Force push",
            onDismiss = onDismiss,
            onPrimaryActionClicked = onConfirm,
            beforeActionsFocusRequester = null,
            actionsFocusRequester = null,
            afterActionsFocusRequester = null,
        ) {
            // The dialog is as narrow as its widest child, this gives the texts room to wrap nicely
            Spacer(Modifier.width(420.dp))
        }
    }
}

private fun String.shortRefName() = removePrefix("refs/heads/").removePrefix("refs/remotes/")
