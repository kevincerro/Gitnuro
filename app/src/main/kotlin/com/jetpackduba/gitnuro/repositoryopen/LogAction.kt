package com.jetpackduba.gitnuro.repositoryopen

import com.jetpackduba.gitnuro.domain.models.Branch
import com.jetpackduba.gitnuro.domain.models.Commit
import com.jetpackduba.gitnuro.domain.models.Tag
import com.jetpackduba.gitnuro.domain.usecases.ResetType

sealed interface LogAction {
    data class Merge(val branch: Branch) : LogAction
    data class Rebase(val branch: Branch) : LogAction
    data class DeleteBranch(val branch: Branch) : LogAction
    data class CheckoutCommit(val commit: Commit) : LogAction
    data class RevertCommit(val commit: Commit) : LogAction
    data class CherryPickCommit(val commit: Commit) : LogAction
    data class CheckoutRemoteBranch(val branch: Branch) : LogAction
    data class CheckoutBranch(val branch: Branch) : LogAction
    data class RebaseInteractive(val commit: Commit) : LogAction
    data class CommitSelected(val commit: Commit, val isStash: Boolean) : LogAction
    data object ShowStatusAmending : LogAction
    data object UncommittedChangesSelected : LogAction
    data class DeleteStash(val commit: Commit) : LogAction
    data class ApplyStash(val commit: Commit) : LogAction
    data class PopStash(val commit: Commit) : LogAction
    data class CheckoutTag(val tag: Tag) : LogAction
    data class DeleteRemoteBranch(val branch: Branch) : LogAction
    data class DeleteTag(val tag: Tag) : LogAction
    data class PushToRemoteBranch(val branch: Branch) : LogAction
    data class PullFromRemoteBranch(val branch: Branch) : LogAction
    data class SearchValueChange(val filter: String) : LogAction
    data class ResetToCommit(val commit: Commit, val resetType: ResetType) : LogAction
    data class CreateWorktree(val commit: Commit, val path: String) : LogAction
    data class EditCommitMessage(val commit: Commit, val message: String) : LogAction
    data class DropCommit(val commit: Commit) : LogAction
    data class MoveCommit(val commit: Commit, val up: Boolean) : LogAction
    data class CreateAnnotatedTag(val commit: Commit, val name: String, val message: String) : LogAction

    /** The current branch is pulled as usual, any other local branch is fast-forwarded to its upstream */
    data class PullBranch(val branch: Branch) : LogAction
    data class PushBranch(val branch: Branch) : LogAction
    data class FastForwardToCurrent(val branch: Branch) : LogAction
    data class CreateWorktreeFromBranch(val branch: Branch, val path: String) : LogAction
    data class DeleteLocalAndRemoteBranch(val local: Branch, val remote: Branch) : LogAction
    data class CreatePatch(val commit: Commit) : LogAction
    data class PinBranch(val branch: Branch?) : LogAction
    data class SoloBranch(val branch: Branch?) : LogAction
    data object ApplyPatch : LogAction
    data class CompareWithWorkingDirectory(val commit: Commit) : LogAction
}