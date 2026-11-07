package com.jetpackduba.gitnuro.repositoryopen

import androidx.compose.ui.text.input.TextFieldValue
import com.jetpackduba.gitnuro.domain.errors.AppError
import com.jetpackduba.gitnuro.domain.models.Commit
import com.jetpackduba.gitnuro.domain.extensions.filePath
import com.jetpackduba.gitnuro.ui.tree_files.TreeExpansionState
import com.jetpackduba.gitnuro.ui.tree_files.TreeItem
import com.jetpackduba.gitnuro.ui.tree_files.treeExpansionState
import org.eclipse.jgit.diff.DiffEntry

/**
 * A file of the commit's tree. [diffEntry] is set when the commit changed it.
 */
data class CommitTreeFile(val path: String, val diffEntry: DiffEntry?)

data class CommitChangesState(
    val isLoading: Boolean,
    val error: AppError? = null,
    val commit: Commit,
    val showAsTree: Boolean,
    val sortDescending: Boolean = false,
    val showSearch: Boolean,
    val searchFilter: TextFieldValue,
    val treeContractedDirectories: List<String> = emptyList(),
    val changes: List<DiffEntry> = emptyList(),
    val changesFiltered: List<DiffEntry> = emptyList(),
    val changesTree: List<TreeItem<DiffEntry>> = emptyList(),
    val changesTreeFiltered: List<TreeItem<DiffEntry>> = emptyList(),
    /** State of the "View all files" checkbox */
    val viewAllFilesChecked: Boolean = false,
    /** "View all files": the whole tree of the commit (true once its files are loaded) */
    val viewAllFiles: Boolean = false,
    val allFiles: List<CommitTreeFile> = emptyList(),
    val allFilesTree: List<TreeItem<CommitTreeFile>> = emptyList(),
) {
    val treeExpansionState: TreeExpansionState = treeExpansionState(
        if (viewAllFiles) allFiles.map { it.path } else changes.map { it.filePath },
        treeContractedDirectories,
    )
}
