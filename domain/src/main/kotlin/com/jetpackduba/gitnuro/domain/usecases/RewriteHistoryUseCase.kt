package com.jetpackduba.gitnuro.domain.usecases

import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GenericError
import com.jetpackduba.gitnuro.domain.interfaces.IRewriteHistoryGitAction
import com.jetpackduba.gitnuro.domain.models.Commit
import com.jetpackduba.gitnuro.domain.models.TaskType
import javax.inject.Inject

/**
 * Commit context menu history rewrites (GitKraken's drop and move), done with a rebase of the current branch.
 */
class RewriteHistoryUseCase @Inject constructor(
    private val useCaseExecutor: UseCaseExecutor,
    private val rewriteHistoryGitAction: IRewriteHistoryGitAction,
) {
    fun drop(commit: Commit) = rewrite(TaskType.Generic("Dropping commit…", "Commit dropped"), commit.hash) { todo ->
        todo - commit.hash
    }

    /**
     * [up] moves the commit after its child (newer), otherwise before its parent (older).
     */
    fun move(commit: Commit, up: Boolean) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Generic("Moving commit…", "Commit moved"),
        dataToRefresh = arrayOf(DataToRefresh.ALL),
    ) { repositoryPath ->
        val parent = commit.parentsHashes.firstOrNull()

        if (!up && parent == null) {
            return@executeLaunch Either.Err(GenericError("The first commit of the repository can't be moved down."))
        }

        // Moving down swaps it with its parent, so the rebase has to start one commit earlier
        val firstRewrittenCommit = if (up) commit.hash else checkNotNull(parent)

        rewriteHistoryGitAction(repositoryPath, firstRewrittenCommit) { todo ->
            val index = todo.indexOf(commit.hash)
            val otherIndex = if (up) index + 1 else index - 1

            if (index < 0 || otherIndex !in todo.indices) {
                throw IllegalStateException("There is no commit to swap it with.")
            }

            todo.toMutableList().apply {
                this[index] = todo[otherIndex]
                this[otherIndex] = todo[index]
            }
        }
    }

    private fun rewrite(taskType: TaskType, firstCommitHash: String, editTodo: (List<String>) -> List<String>) =
        useCaseExecutor.executeLaunch(
            taskType = taskType,
            dataToRefresh = arrayOf(DataToRefresh.ALL),
        ) { repositoryPath ->
            rewriteHistoryGitAction(repositoryPath, firstCommitHash, editTodo)
        }
}
