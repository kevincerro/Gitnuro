package com.jetpackduba.gitnuro.repositoryopen

import com.jetpackduba.gitnuro.common.ProcessEnvironment
import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GenericError
import com.jetpackduba.gitnuro.domain.errors.okOrNull
import com.jetpackduba.gitnuro.domain.interfaces.IGetWorktreePathGitAction
import com.jetpackduba.gitnuro.domain.models.Commit
import com.jetpackduba.gitnuro.domain.models.TaskType
import com.jetpackduba.gitnuro.domain.usecases.DataToRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * Commit actions that use the git CLI ("View all files" and creating worktrees). Nothing here creates commits or
 * tags: those go through JGit, so they are signed with the app's signers.
 */
class CommitHistoryActions @Inject constructor(
    private val useCaseExecutor: UseCaseExecutor,
    private val getWorktreePathGitAction: IGetWorktreePathGitAction,
) {
    /**
     * Every file of the commit's tree ("View all files").
     */
    suspend fun listFiles(commitHash: String): List<String> {
        return useCaseExecutor.execute { gitDirectory ->
            val worktree = getWorktreePathGitAction(gitDirectory).okOrNull()
                ?: File(gitDirectory).let { if (it.name == ".git") it.parentFile.absolutePath else gitDirectory }

            val files = withContext(Dispatchers.IO) {
                git(worktree, "ls-tree", "-r", "--name-only", "-z", commitHash).okOrNull()
                    ?.split('\u0000')
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            }

            Either.Ok(files)
        }.okOrNull().orEmpty()
    }

    fun createWorktree(commit: Commit, path: String) = launch(TaskType.Generic("Creating worktree…", "Worktree created")) { worktree ->
        git(worktree, "worktree", "add", "--detach", path, commit.hash)
    }

    private fun launch(
        taskType: TaskType,
        block: suspend (worktree: String) -> Either<String, GenericError>,
    ) = useCaseExecutor.executeLaunch(
        taskType = taskType,
        dataToRefresh = arrayOf(DataToRefresh.ALL),
    ) { gitDirectory ->
        val worktree = getWorktreePathGitAction(gitDirectory).okOrNull()
            ?: File(gitDirectory).let { if (it.name == ".git") it.parentFile.absolutePath else gitDirectory }

        withContext(Dispatchers.IO) { block(worktree) }
    }

    private fun git(worktree: String, vararg args: String): Either<String, GenericError> {
        return try {
            val processBuilder = ProcessEnvironment.processBuilder(listOf("git", "-C", worktree) + args)
                .directory(File(worktree))

            // Never open an editor, everything is given to git
            processBuilder.environment()["GIT_EDITOR"] = "true"

            val process = processBuilder.start()
            process.outputStream.close()
            var error = ""
            val errorReader = thread { error = process.errorStream.bufferedReader().readText() }
            val output = process.inputStream.bufferedReader().readText()

            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                return Either.Err(GenericError("git took too long and was stopped"))
            }

            errorReader.join()

            if (process.exitValue() == 0) Either.Ok(output)
            else Either.Err(GenericError(error.ifBlank { output }.trim().ifBlank { "git failed" }))
        } catch (ex: Exception) {
            Either.Err(GenericError(ex.message.orEmpty(), ex))
        }
    }
}
