package com.jetpackduba.gitnuro.domain.interfaces

import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GitError

interface IRewriteHistoryGitAction {
    /**
     * Rebases [firstCommitHash] and the commits after it up to HEAD, picking the commits (full hashes, oldest first)
     * returned by [editTodo]. It can throw [IllegalStateException] to cancel the rewrite with a message.
     */
    suspend operator fun invoke(
        repositoryPath: String,
        firstCommitHash: String,
        editTodo: (List<String>) -> List<String>,
    ): Either<Unit, GitError>
}
