package com.jetpackduba.gitnuro.domain.interfaces

import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GitError
import com.jetpackduba.gitnuro.domain.models.Commit

interface ICreateTagGitAction {
    suspend operator fun invoke(
        repositoryPath: String,
        tag: String,
        commit: Commit,
        message: String? = null,
    ): Either<Unit, GitError>
}
