package com.jetpackduba.gitnuro.domain.usecases

import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.interfaces.IFetchAllRemotesGitAction
import com.jetpackduba.gitnuro.domain.models.Remote
import com.jetpackduba.gitnuro.domain.models.TaskType
import javax.inject.Inject

class FetchAllBranchUseCase @Inject constructor(
    private val fetchAllGitAction: IFetchAllRemotesGitAction,
    private val useCaseExecutor: UseCaseExecutor,
) {
    operator fun invoke(specificRemote: Remote? = null) {
        useCaseExecutor.executeLaunch(
            taskType = TaskType.Fetch,
            refreshEvenIfFailed = true,
            // The remote branches (and the tags) are what a fetch moves: without refreshing them the graph gets the
            // new commits but their refs stay in the old ones, as if nothing had been fetched
            dataToRefresh = arrayOf(
                DataToRefresh.BRANCHES,
                DataToRefresh.REMOTES,
                DataToRefresh.TAGS,
                DataToRefresh.LOG,
            ),
        ) { repositoryPath ->
            fetchAllGitAction(repositoryPath, specificRemote)
        }
    }
}