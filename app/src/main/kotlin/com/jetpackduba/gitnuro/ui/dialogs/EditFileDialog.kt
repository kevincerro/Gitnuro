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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import com.jetpackduba.gitnuro.ui.components.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_EDITABLE_FILE_SIZE = 5L * 1024 * 1024

private sealed interface EditFileState {
    data object Loading : EditFileState
    data class Loaded(val originalContent: String) : EditFileState
    data class Error(val message: String) : EditFileState
}

/**
 * Small built-in text editor used by the "Edit file" option of the status context menu.
 */
@Composable
fun EditFileDialog(
    file: File,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember(file) { mutableStateOf<EditFileState>(EditFileState.Loading) }
    var content by remember(file) { mutableStateOf("") }
    var saveError by remember(file) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(file) {
        state = withContext(Dispatchers.IO) {
            try {
                when {
                    !file.isFile -> EditFileState.Error("The file does not exist.")
                    file.length() > MAX_EDITABLE_FILE_SIZE -> EditFileState.Error("The file is too big to be edited here.")
                    else -> {
                        val bytes = file.readBytes()
                        if (bytes.contains(0)) {
                            EditFileState.Error("Binary files can't be edited.")
                        } else {
                            EditFileState.Loaded(bytes.decodeToString())
                        }
                    }
                }
            } catch (ex: Exception) {
                EditFileState.Error(ex.message ?: "The file could not be read.")
            }
        }

        (state as? EditFileState.Loaded)?.let {
            content = it.originalContent
        }
    }

    val save: () -> Unit = {
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    file.writeText(content)
                    null
                } catch (ex: Exception) {
                    ex.message ?: "The file could not be saved."
                }
            }

            if (error == null) {
                onDismiss()
            } else {
                saveError = error
            }
        }
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "Edit file — ${file.name}",
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(800.dp, 600.dp),
        ),
        onPreviewKeyEvent = { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) {
                false
            } else if (keyEvent.key == Key.Escape) {
                onDismiss()
                true
            } else if (keyEvent.key == Key.S && (keyEvent.isMetaPressed || keyEvent.isCtrlPressed)) {
                if (state is EditFileState.Loaded) save()
                true
            } else {
                false
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.surface)
                .padding(12.dp),
        ) {
            Text(
                text = file.path,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackgroundSecondary,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colors.background, RoundedCornerShape(4.dp)),
            ) {
                when (val currentState = state) {
                    EditFileState.Loading -> Unit
                    is EditFileState.Error -> Text(
                        text = currentState.message,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is EditFileState.Loaded -> {
                        BasicTextField(
                            value = content,
                            onValueChange = {
                                content = it
                                saveError = null
                            },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onBackground,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colors.primary),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                                .focusRequester(focusRequester),
                        )

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = saveError.orEmpty(),
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )

                PrimaryButton(
                    text = "Cancel",
                    backgroundColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
                    textColor = MaterialTheme.colors.onBackground,
                    onClick = onDismiss,
                )

                val loadedState = state as? EditFileState.Loaded
                PrimaryButton(
                    text = "Save",
                    enabled = loadedState != null && loadedState.originalContent != content,
                    onClick = save,
                )
            }
        }
    }
}
