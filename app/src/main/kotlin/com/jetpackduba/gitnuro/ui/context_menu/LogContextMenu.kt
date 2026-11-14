package com.jetpackduba.gitnuro.ui.context_menu

import com.jetpackduba.gitnuro.domain.usecases.ResetType

/**
 * Commit context menu, with the same options and order as GitKraken's (without its AI options).
 */
fun logContextMenu(
    shortHash: String,
    currentBranchName: String?,
    isLastCommit: Boolean,
    isFirstCommit: Boolean,
    branchesAtCommit: List<Pair<String, List<ContextMenuElement>>>,
    onCheckoutCommit: () -> Unit,
    onCreateWorktree: () -> Unit,
    onCreateNewBranch: () -> Unit,
    onCherryPickCommit: () -> Unit,
    onResetBranch: (ResetType) -> Unit,
    onRevertCommit: () -> Unit,
    onRebaseInteractive: () -> Unit,
    onEditCommitMessage: () -> Unit,
    onDropCommit: () -> Unit,
    onMoveCommitUp: () -> Unit,
    onMoveCommitDown: () -> Unit,
    onCreateNewTag: () -> Unit,
    onCreateAnnotatedTag: () -> Unit,
    onApplyPatch: () -> Unit,
    onCompareWithWorkingDirectory: () -> Unit,
) = mutableListOf<ContextMenuElement>().apply {
    fun entry(label: String, onClick: () -> Unit) = add(ContextMenuElement.ContextTextEntry(label, onClick = onClick))

    entry("Checkout this commit", onCheckoutCommit)

    add(ContextMenuElement.ContextSeparator)
    entry("Create worktree from this commit", onCreateWorktree)

    add(ContextMenuElement.ContextSeparator)
    entry("Create branch here", onCreateNewBranch)
    entry("Cherry pick commit", onCherryPickCommit)

    add(
        ContextMenuElement.ContextSubmenu(
            composableLabel = { "Reset ${currentBranchName ?: "HEAD"} to this commit" },
            items = listOf(
                ContextMenuElement.ContextTextEntry("Soft - keep all changes", onClick = { onResetBranch(ResetType.SOFT) }),
                ContextMenuElement.ContextTextEntry(
                    "Mixed - keep working copy but reset index",
                    onClick = { onResetBranch(ResetType.MIXED) },
                ),
                ContextMenuElement.ContextTextEntry("Hard - discard all changes", onClick = { onResetBranch(ResetType.HARD) }),
            ),
        )
    )

    entry("Revert commit", onRevertCommit)

    add(ContextMenuElement.ContextSeparator)

    if (!isLastCommit) {
        entry("Interactive Rebase children of $shortHash", onRebaseInteractive)
    }

    entry("Edit commit message", onEditCommitMessage)
    entry("Drop commit", onDropCommit)

    if (!isLastCommit) {
        entry("Move commit up", onMoveCommitUp)
    }

    if (!isFirstCommit) {
        entry("Move commit down", onMoveCommitDown)
    }

    add(ContextMenuElement.ContextSeparator)
    entry("Apply patch", onApplyPatch)

    if (branchesAtCommit.isNotEmpty()) {
        add(ContextMenuElement.ContextSeparator)

        branchesAtCommit.forEach { (name, items) ->
            add(ContextMenuElement.ContextSubmenu(composableLabel = { name }, items = items))
        }
    }

    add(ContextMenuElement.ContextSeparator)
    entry("Compare commit against working directory", onCompareWithWorkingDirectory)

    add(ContextMenuElement.ContextSeparator)
    entry("Create tag here", onCreateNewTag)
    entry("Create annotated tag here", onCreateAnnotatedTag)
}
