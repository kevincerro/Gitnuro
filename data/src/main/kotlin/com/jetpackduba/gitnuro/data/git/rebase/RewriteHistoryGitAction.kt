package com.jetpackduba.gitnuro.data.git.rebase

import com.jetpackduba.gitnuro.data.git.JGit
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GitError
import com.jetpackduba.gitnuro.domain.interfaces.IRewriteHistoryGitAction
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RebaseTodoLine
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import javax.inject.Inject

/**
 * Rewrites the current branch with JGit's interactive rebase, giving it the todo list instead of asking the user.
 * The rewritten commits are signed by JGit with the app's signers, so signing never needs a terminal for the pinentry.
 */
class RewriteHistoryGitAction @Inject constructor(
    private val jgit: JGit,
) : IRewriteHistoryGitAction {
    override suspend operator fun invoke(
        repositoryPath: String,
        firstCommitHash: String,
        editTodo: (List<String>) -> List<String>,
    ): Either<Unit, GitError> = jgit.provide(repositoryPath) { git ->
        val repository = git.repository
        val headId = repository.resolve("HEAD") ?: throw IllegalStateException("The repository has no commits.")

        val (base, commits) = RevWalk(repository).use { walk ->
            val first = walk.parseCommit(ObjectId.fromString(firstCommitHash))

            if (!walk.isMergedInto(first, walk.parseCommit(headId))) {
                throw IllegalStateException("The commit is not part of the current branch's history.")
            }

            val base = when (first.parentCount) {
                0 -> throw IllegalStateException("The first commit of the repository can't be rewritten.")
                1 -> walk.parseCommit(first.getParent(0))
                else -> throw IllegalStateException("Merge commits can't be rewritten.")
            }

            walk.reset()
            walk.sort(RevSort.TOPO)
            walk.sort(RevSort.REVERSE, true)
            walk.markStart(walk.parseCommit(headId))
            walk.markUninteresting(base)

            base to walk.map { it.name }
        }

        if (commits.isEmpty()) return@provide

        val todo = editTodo(commits)

        if (git.status().call().hasUncommittedChanges()) {
            throw IllegalStateException("Commit or stash your changes before rewriting the history.")
        }

        if (todo.isEmpty()) {
            // Every commit after the base was dropped: nothing to rebase, the branch goes back to the base
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(base.name).call()
            return@provide
        }

        val handler = object : RebaseCommand.InteractiveHandler {
            override fun prepareSteps(steps: MutableList<RebaseTodoLine>) {
                val picks = steps.filter { it.action == RebaseTodoLine.Action.PICK }
                val newSteps = todo.map { hash ->
                    val id = ObjectId.fromString(hash)
                    picks.first { it.commit.prefixCompare(id) == 0 }
                }

                steps.clear()
                steps.addAll(newSteps)
            }

            override fun modifyCommitMessage(message: String): String = message
        }

        val result = git.rebase()
            .setUpstream(base)
            .runInteractively(handler)
            .call()

        when (result.status) {
            RebaseResult.Status.OK, RebaseResult.Status.FAST_FORWARD, RebaseResult.Status.UP_TO_DATE -> {}
            RebaseResult.Status.STOPPED -> throw IllegalStateException(
                "The rebase stopped because of conflicts. Fix them and continue or abort the rebase."
            )

            RebaseResult.Status.UNCOMMITTED_CHANGES, RebaseResult.Status.CONFLICTS -> throw IllegalStateException(
                "Commit or stash your changes before rewriting the history."
            )

            else -> throw IllegalStateException("The rebase failed: ${result.status}")
        }
    }
}

