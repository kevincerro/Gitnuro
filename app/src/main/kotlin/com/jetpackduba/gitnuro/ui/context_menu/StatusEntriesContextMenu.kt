package com.jetpackduba.gitnuro.ui.context_menu

import com.jetpackduba.gitnuro.app.generated.resources.*
import com.jetpackduba.gitnuro.common.OS
import com.jetpackduba.gitnuro.common.currentOs
import com.jetpackduba.gitnuro.domain.models.EntryType
import com.jetpackduba.gitnuro.domain.models.StatusEntry
import com.jetpackduba.gitnuro.domain.models.StatusType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

fun statusEntryContextMenuItems(
    statusEntry: StatusEntry,
    entryType: EntryType,
    onStageToggle: () -> Unit,
    onReset: () -> Unit,
    onIgnoreFile: () -> Unit,
    onIgnoreExtension: (extension: String) -> Unit,
    onStash: () -> Unit,
    onHistory: () -> Unit,
    onBlame: () -> Unit,
    onOpenInDiffTool: () -> Unit,
    onOpenInExternalEditor: () -> Unit,
    onOpenInDefaultProgram: () -> Unit,
    onShowInFileManager: () -> Unit,
    onCopyFilePath: () -> Unit,
    onCreatePatch: () -> Unit,
    onEditFile: () -> Unit,
    onDelete: () -> Unit,
): List<ContextMenuElement> {
    val fileName = statusEntry.filePath.substringAfterLast('/')
    val extension = fileName
        .substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { fileName.lastIndexOf('.') > 0 }
        .orEmpty()
    val fileExists = statusEntry.statusType != StatusType.REMOVED
    val hasHistory = statusEntry.statusType != StatusType.ADDED

    return mutableListOf<ContextMenuElement>().apply {
        addContextMenu(
            composableLabel = {
                stringResource(
                    if (entryType == EntryType.UNSTAGED) Res.string.status_entry_menu_stage
                    else Res.string.status_entry_menu_unstage
                )
            },
            onClick = onStageToggle,
        )

        addContextMenu(
            composableLabel = { stringResource(Res.string.status_entry_menu_discard_changes) },
            onClick = onReset,
        )

        if (entryType == EntryType.UNSTAGED) {
            addSubmenu(
                composableLabel = { stringResource(Res.string.status_entry_menu_ignore) },
                items = mutableListOf<ContextMenuElement>().apply {
                    addContextMenu(
                        composableLabel = { stringResource(Res.string.status_entry_menu_ignore_file, fileName) },
                        onClick = onIgnoreFile,
                    )

                    if (extension.isNotEmpty()) {
                        addContextMenu(
                            composableLabel = {
                                stringResource(Res.string.status_entry_menu_ignore_extension, extension)
                            },
                            onClick = { onIgnoreExtension(extension) },
                        )
                    }
                },
            )
        }

        addContextMenu(
            composableLabel = { stringResource(Res.string.status_entry_menu_stash_file) },
            onClick = onStash,
        )

        if (hasHistory) {
            add(ContextMenuElement.ContextSeparator)

            addContextMenu(
                composableLabel = { stringResource(Res.string.status_entry_menu_file_history) },
                onClick = onHistory,
            )

            if (fileExists) {
                addContextMenu(
                    composableLabel = { stringResource(Res.string.status_entry_menu_file_blame) },
                    onClick = onBlame,
                )
            }
        }

        add(ContextMenuElement.ContextSeparator)

        addContextMenu(
            composableLabel = { stringResource(Res.string.status_entry_menu_open_diff_tool) },
            onClick = onOpenInDiffTool,
        )

        if (fileExists) {
            addContextMenu(
                composableLabel = { stringResource(Res.string.status_entry_menu_open_external_editor) },
                onClick = onOpenInExternalEditor,
            )

            addContextMenu(
                composableLabel = { stringResource(Res.string.status_entry_menu_open_default_program) },
                onClick = onOpenInDefaultProgram,
            )

            addContextMenu(
                composableLabel = {
                    stringResource(
                        when (currentOs) {
                            OS.MAC -> Res.string.status_entry_menu_show_in_finder
                            OS.WINDOWS -> Res.string.status_entry_menu_show_in_explorer
                            else -> Res.string.status_entry_menu_show_in_folder
                        }
                    )
                },
                onClick = onShowInFileManager,
            )
        }

        add(ContextMenuElement.ContextSeparator)

        addContextMenu(
            composableLabel = { stringResource(Res.string.status_entry_menu_copy_file_path) },
            onClick = onCopyFilePath,
        )

        addContextMenu(
            composableLabel = { stringResource(Res.string.status_entry_menu_create_patch) },
            onClick = onCreatePatch,
        )

        if (fileExists) {
            add(ContextMenuElement.ContextSeparator)

            addContextMenu(
                composableLabel = { stringResource(Res.string.status_entry_menu_edit_file) },
                onClick = onEditFile,
            )

            if (entryType == EntryType.UNSTAGED) {
                addContextMenu(
                    composableLabel = { stringResource(Res.string.status_entry_menu_delete_file) },
                    onClick = onDelete,
                )
            }
        }
    }
}

fun statusEntriesContextMenuItems(
    selectedEntriesCount: Int,
    entryType: EntryType,
    onDiscard: () -> Unit,
    onStageSelected: () -> Unit,
    onUnstageSelected: () -> Unit,
    onCopyFilesPath: (relative: Boolean) -> Unit,
): List<ContextMenuElement> {
    return mutableListOf<ContextMenuElement>().apply {

        when (entryType) {
            EntryType.STAGED -> addContextMenu(
                composableLabel = {
                    stringResource(
                        Res.string.status_entries_context_menu_unstage_multiple_files,
                        selectedEntriesCount,
                    )
                },
                icon = { painterResource(Res.drawable.remove_done) },
                onClick = onUnstageSelected,
            )

            EntryType.UNSTAGED -> addContextMenu(
                composableLabel = {
                    stringResource(
                        Res.string.status_entries_context_menu_stage_multiple_files,
                        selectedEntriesCount,
                    )
                },
                icon = { painterResource(Res.drawable.done) },
                onClick = onStageSelected,
            )
        }

        addContextMenu(
            composableLabel = {
                stringResource(
                    Res.string.status_entries_context_menu_discard_multiple_files,
                    selectedEntriesCount
                )
            },
            icon = { painterResource(Res.drawable.undo) },
            onClick = onDiscard,
        )

        add(ContextMenuElement.ContextSeparator)

        addContextMenu(
            composableLabel = {
                stringResource(Res.string.status_entries_context_menu_copy_files_absolute_paths)
            },
            icon = { painterResource(Res.drawable.copy) },
            onClick = { onCopyFilesPath(false) },
        )

        addContextMenu(
            composableLabel = {
                stringResource(Res.string.status_entries_context_menu_copy_files_relative_paths)
            },
            icon = { painterResource(Res.drawable.copy_all) },
            onClick = { onCopyFilesPath(true) },
        )
    }
}
