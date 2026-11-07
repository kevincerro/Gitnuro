package com.jetpackduba.gitnuro.repositoryopen

import androidx.compose.ui.text.input.TextFieldValue
import org.eclipse.jgit.diff.DiffEntry

sealed interface CommitChangesAction {
    data class TreeDirectoryToggle(val path: String) : CommitChangesAction
    data object ToggleShowAsTree : CommitChangesAction
    data object ToggleSortOrder : CommitChangesAction
    data class ToggleViewAllFiles(val viewAll: Boolean) : CommitChangesAction
    data object ExpandAllDirectories : CommitChangesAction
    data object CollapseAllDirectories : CommitChangesAction
    data class SelectEntry(val entry: DiffEntry) : CommitChangesAction
    data class SearchFilterChanged(val filter: TextFieldValue) : CommitChangesAction
    data class SearchFilterToggle(val show: Boolean) : CommitChangesAction
    data object AddSearchToCloseables : CommitChangesAction
}