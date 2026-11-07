package com.jetpackduba.gitnuro.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import com.jetpackduba.gitnuro.ui.components.PrimaryButton

/**
 * Edits the full message of a commit (summary + description).
 */
@Composable
fun EditCommitMessageDialog(
    shortHash: String,
    initialMessage: String,
    onDismiss: () -> Unit,
    onAccept: (String) -> Unit,
) {
    var message by remember {
        mutableStateOf(TextFieldValue(initialMessage, TextRange(initialMessage.length)))
    }
    val canAccept = message.text.isNotBlank() && message.text.trim() != initialMessage.trim()

    TextDialogWindow(
        title = "Edit commit message — $shortHash",
        size = DpSize(620.dp, 380.dp),
        acceptText = "Save",
        canAccept = canAccept,
        onDismiss = onDismiss,
        onAccept = { onAccept(message.text.trim()) },
    ) { focusRequester ->
        DialogTextArea(
            value = message,
            onValueChange = { message = it },
            placeholder = "Commit message",
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
    }
}

@Composable
fun AnnotatedTagDialog(
    shortHash: String,
    onDismiss: () -> Unit,
    onAccept: (name: String, message: String) -> Unit,
) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var message by remember { mutableStateOf(TextFieldValue("")) }
    val trimmedName = name.text.trim()
    val canAccept = trimmedName.isNotEmpty() && !trimmedName.contains(' ') && message.text.isNotBlank()

    TextDialogWindow(
        title = "Create annotated tag at $shortHash",
        size = DpSize(520.dp, 340.dp),
        acceptText = "Create tag",
        canAccept = canAccept,
        onDismiss = onDismiss,
        onAccept = { onAccept(trimmedName, message.text.trim()) },
    ) { focusRequester ->
        DialogTextArea(
            value = name,
            onValueChange = { name = it.copy(text = it.text.replace("\n", "")) },
            placeholder = "Tag name",
            modifier = Modifier.height(36.dp).focusRequester(focusRequester),
        )

        Spacer(Modifier.height(8.dp))

        DialogTextArea(
            value = message,
            onValueChange = { message = it },
            placeholder = "Tag message",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TextDialogWindow(
    title: String,
    size: DpSize,
    acceptText: String,
    canAccept: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    content: @Composable ColumnScope.(FocusRequester) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = title,
        state = rememberDialogState(position = WindowPosition(Alignment.Center), size = size),
        onPreviewKeyEvent = { keyEvent ->
            when {
                keyEvent.type != KeyEventType.KeyDown -> false
                keyEvent.key == Key.Escape -> {
                    onDismiss()
                    true
                }

                keyEvent.key == Key.Enter && (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) -> {
                    if (canAccept) onAccept()
                    true
                }

                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.surface)
                .padding(12.dp),
        ) {
            content(focusRequester)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    text = "Cancel",
                    backgroundColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
                    textColor = MaterialTheme.colors.onBackground,
                    onClick = onDismiss,
                )

                PrimaryButton(
                    text = acceptText,
                    enabled = canAccept,
                    onClick = onAccept,
                )
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
private fun DialogTextArea(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.15f), shape)
            .background(MaterialTheme.colors.background, shape)
            .padding(8.dp),
    ) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onBackgroundSecondary,
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = MaterialTheme.typography.body1.fontSize,
                color = MaterialTheme.colors.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colors.primary),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
    }
}
