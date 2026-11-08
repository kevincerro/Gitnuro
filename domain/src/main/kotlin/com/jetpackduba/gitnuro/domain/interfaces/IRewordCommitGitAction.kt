package com.jetpackduba.gitnuro.domain.interfaces

import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GitError
import com.jetpackduba.gitnuro.domain.models.Commit

interface IRewordCommitGitAction {
    /**
     * Changes the message of [commit] (which has to be part of the current branch) and rewrites the commits that
     * come after it. Trees don't change, so the working directory and the index are left untouched.
     */
    suspend operator fun invoke(
        repositoryPath: String,
        commit: Commit,
        message: String,
    ): Either<Unit, GitError>
}
