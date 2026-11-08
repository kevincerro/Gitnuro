package com.jetpackduba.gitnuro.domain.usecases

import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.interfaces.IRewordCommitGitAction
import com.jetpackduba.gitnuro.domain.models.Commit
import com.jetpackduba.gitnuro.domain.models.TaskType
import javax.inject.Inject

class RewordCommitUseCase @Inject constructor(
    private val useCaseExecutor: UseCaseExecutor,
    private val rewordCommitGitAction: IRewordCommitGitAction,
) {
    operator fun invoke(commit: Commit, message: String) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Generic("Editing commit message…", "Commit message edited"),
        dataToRefresh = arrayOf(DataToRefresh.ALL),
    ) { repositoryPath ->
        rewordCommitGitAction(repositoryPath, commit, message)
    }
}
