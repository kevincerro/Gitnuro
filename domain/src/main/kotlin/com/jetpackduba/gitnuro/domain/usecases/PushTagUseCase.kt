package com.jetpackduba.gitnuro.domain.usecases

import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.interfaces.IPushTagGitAction
import com.jetpackduba.gitnuro.domain.models.Tag
import com.jetpackduba.gitnuro.domain.models.TaskType
import javax.inject.Inject

class PushTagUseCase @Inject constructor(
    private val useCaseExecutor: UseCaseExecutor,
    private val pushTagGitAction: IPushTagGitAction,
) {
    operator fun invoke(tag: Tag) = useCaseExecutor.executeLaunch(
        taskType = TaskType.PushTag,
        dataToRefresh = arrayOf(DataToRefresh.TAGS, DataToRefresh.LOG),
    ) { repositoryPath ->
        pushTagGitAction(repositoryPath, tag)
    }
}
