package com.jetpackduba.gitnuro.ui.context_menu

import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.check
import com.jetpackduba.gitnuro.domain.models.Branch
import com.jetpackduba.gitnuro.domain.usecases.ResetType
import org.jetbrains.compose.resources.painterResource

/**
 * What can be done from the context menu of a branch badge of the graph. The badge can be a local branch, a remote
 * branch or both (a local branch with its upstream in the same commit).
 */
sealed interface BranchRefMenuAction {
    /** Pull into [branch]: the current one is pulled as usual, any other one is fast-forwarded to its upstream */
    data class Pull(val branch: Branch) : BranchRefMenuAction
    data class Push(val branch: Branch) : BranchRefMenuAction
    data class SetUpstream(val branch: Branch) : BranchRefMenuAction

    /** Moves [branch] (not checked out) forward to the current branch, only if it's an ancestor of it */
    data class FastForwardToCurrent(val branch: Branch) : BranchRefMenuAction
    data class Merge(val branch: Branch) : BranchRefMenuAction
    data class Rebase(val branch: Branch) : BranchRefMenuAction
    data object InteractiveRebaseOnto : BranchRefMenuAction

    data class CheckoutBranch(val branch: Branch) : BranchRefMenuAction
    data object CheckoutCommit : BranchRefMenuAction
    data class CreateWorktreeFromBranch(val branch: Branch) : BranchRefMenuAction
    data object CreateWorktreeFromCommit : BranchRefMenuAction

    data object CreateBranch : BranchRefMenuAction
    data object CherryPick : BranchRefMenuAction
    data class Reset(val type: ResetType) : BranchRefMenuAction
    data object Revert : BranchRefMenuAction
    data object InteractiveRebaseChildren : BranchRefMenuAction
    data object EditMessage : BranchRefMenuAction
    data object Drop : BranchRefMenuAction
    data class Move(val up: Boolean) : BranchRefMenuAction

    data class Rename(val branch: Branch) : BranchRefMenuAction
    data class Delete(val branch: Branch) : BranchRefMenuAction
    data class DeleteRemote(val branch: Branch) : BranchRefMenuAction
    data class DeleteLocalAndRemote(val local: Branch, val remote: Branch) : BranchRefMenuAction

    data class CopyBranchName(val branch: Branch) : BranchRefMenuAction
    data object CopyCommitSha : BranchRefMenuAction
    data object CreatePatch : BranchRefMenuAction
    data object ApplyPatch : BranchRefMenuAction
    data object CompareWithWorkingDirectory : BranchRefMenuAction

    /** Pins the branch to the first lane of the graph, null unpins it */
    data class Pin(val branch: Branch?) : BranchRefMenuAction

    /** Shows only the history of the branch, null shows everything again */
    data class Solo(val branch: Branch?) : BranchRefMenuAction

    data object CreateTag : BranchRefMenuAction
    data object CreateAnnotatedTag : BranchRefMenuAction
}

/**
 * Context menu of a branch badge, with the options of GitKraken's (without its AI and hosting services options).
 *
 * @param local the local branch of the badge, if any
 * @param remote the remote branch of the badge: the local one's upstream or the remote branch alone
 * @param pinnedBranchName full name of the branch pinned to the left, if any
 * @param soloBranchName full name of the branch being soloed, if any
 */
fun branchRefContextMenu(
    local: Branch?,
    remote: Branch?,
    currentBranch: Branch?,
    shortHash: String,
    isLastCommitOfCurrentBranch: Boolean,
    isFirstCommit: Boolean,
    pinnedBranchName: String?,
    soloBranchName: String?,
    onAction: (BranchRefMenuAction) -> Unit,
): List<ContextMenuElement> = mutableListOf<ContextMenuElement>().apply {
    val target = local ?: remote ?: return@apply
    val targetName = target.simpleNameWithRemote
    val current = currentBranch?.takeIf { it.name != "HEAD" }
    val currentName = current?.simpleName
    val isCurrent = local != null && local.isSameBranch(current)

    fun entry(label: String, action: BranchRefMenuAction) =
        add(ContextMenuElement.ContextTextEntry(label, onClick = { onAction(action) }))

    fun item(label: String, action: BranchRefMenuAction, isChecked: Boolean = false) =
        ContextMenuElement.ContextTextEntry(
            label,
            icon = if (isChecked) ({ painterResource(Res.drawable.check) }) else null,
            onClick = { onAction(action) },
        )

    fun separator() {
        if (isNotEmpty() && last() != ContextMenuElement.ContextSeparator) add(ContextMenuElement.ContextSeparator)
    }

    // Remote operations of the local branch
    if (local != null) {
        entry("Pull (fast-forward if possible)", BranchRefMenuAction.Pull(local))
        entry("Push", BranchRefMenuAction.Push(local))
        entry("Set Upstream", BranchRefMenuAction.SetUpstream(local))
    }

    // Operations between the branch and the current one
    if (current != null && !isCurrent) {
        separator()

        if (local != null) {
            entry("Fast-forward ${local.simpleName} to $currentName", BranchRefMenuAction.FastForwardToCurrent(local))
        }

        entry("Merge $targetName into $currentName", BranchRefMenuAction.Merge(target))
        entry("Rebase $currentName onto $targetName", BranchRefMenuAction.Rebase(target))
        entry("Interactive Rebase $currentName onto $targetName", BranchRefMenuAction.InteractiveRebaseOnto)
    }

    separator()
    add(
        ContextMenuElement.ContextSubmenu(
            composableLabel = { "Checkout" },
            items = buildList {
                if (local != null && !isCurrent) add(item(local.simpleName, BranchRefMenuAction.CheckoutBranch(local)))
                if (remote != null) add(item(remote.simpleNameWithRemote, BranchRefMenuAction.CheckoutBranch(remote)))
                add(item("This commit (detached HEAD)", BranchRefMenuAction.CheckoutCommit))
            },
        )
    )

    separator()
    add(
        ContextMenuElement.ContextSubmenu(
            composableLabel = { "Create worktree from" },
            items = buildList {
                if (!isCurrent) add(item(targetName, BranchRefMenuAction.CreateWorktreeFromBranch(target)))
                add(item("This commit", BranchRefMenuAction.CreateWorktreeFromCommit))
            },
        )
    )

    // Commit operations, the same as the commit's context menu
    separator()
    entry("Create branch here", BranchRefMenuAction.CreateBranch)
    // The commits of the current branch are already in it
    if (!isCurrent) entry("Cherry pick commit", BranchRefMenuAction.CherryPick)
    add(
        ContextMenuElement.ContextSubmenu(
            composableLabel = { "Reset ${currentName ?: "HEAD"} to this commit" },
            items = listOf(
                item("Soft - keep all changes", BranchRefMenuAction.Reset(ResetType.SOFT)),
                item("Mixed - keep working copy but reset index", BranchRefMenuAction.Reset(ResetType.MIXED)),
                item("Hard - discard all changes", BranchRefMenuAction.Reset(ResetType.HARD)),
            ),
        )
    )
    entry("Revert commit", BranchRefMenuAction.Revert)

    separator()
    if (!isLastCommitOfCurrentBranch) {
        entry("Interactive Rebase children of $shortHash", BranchRefMenuAction.InteractiveRebaseChildren)
    }
    entry("Edit commit message", BranchRefMenuAction.EditMessage)
    entry("Drop commit", BranchRefMenuAction.Drop)
    if (!isLastCommitOfCurrentBranch) entry("Move commit up", BranchRefMenuAction.Move(up = true))
    if (!isFirstCommit) entry("Move commit down", BranchRefMenuAction.Move(up = false))

    separator()
    entry("Apply patch", BranchRefMenuAction.ApplyPatch)
    if (local != null) {
        entry("Rename ${local.simpleName}", BranchRefMenuAction.Rename(local))
        if (!isCurrent) entry("Delete ${local.simpleName}", BranchRefMenuAction.Delete(local))
    }
    if (remote != null) {
        entry("Delete ${remote.simpleNameWithRemote}", BranchRefMenuAction.DeleteRemote(remote))
    }
    if (local != null && remote != null && !isCurrent) {
        entry(
            "Delete ${local.simpleName} and ${remote.simpleNameWithRemote}",
            BranchRefMenuAction.DeleteLocalAndRemote(local, remote),
        )
    }

    separator()
    entry("Copy branch name", BranchRefMenuAction.CopyBranchName(target))
    entry("Copy commit sha", BranchRefMenuAction.CopyCommitSha)
    entry("Create patch from commit", BranchRefMenuAction.CreatePatch)

    // Graph options: clicking the active branch again turns them off
    separator()
    val graphBranches = listOfNotNull(local, remote)

    add(
        ContextMenuElement.ContextSubmenu(
            composableLabel = { "Pin to Left" },
            items = graphBranches.map { branch ->
                val isPinned = branch.name == pinnedBranchName
                item(branch.simpleNameWithRemote, BranchRefMenuAction.Pin(branch.takeUnless { isPinned }), isPinned)
            },
        )
    )
    add(
        ContextMenuElement.ContextSubmenu(
            composableLabel = { "Solo" },
            items = graphBranches.map { branch ->
                val isSolo = branch.name == soloBranchName
                item(branch.simpleNameWithRemote, BranchRefMenuAction.Solo(branch.takeUnless { isSolo }), isSolo)
            },
        )
    )

    separator()
    entry("Compare commit against working directory", BranchRefMenuAction.CompareWithWorkingDirectory)

    separator()
    entry("Create tag here", BranchRefMenuAction.CreateTag)
    entry("Create annotated tag here", BranchRefMenuAction.CreateAnnotatedTag)
}
