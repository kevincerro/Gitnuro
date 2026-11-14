package com.jetpackduba.gitnuro.repositoryopen

import com.jetpackduba.gitnuro.common.ProcessEnvironment
import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GenericError
import com.jetpackduba.gitnuro.domain.errors.okOrNull
import com.jetpackduba.gitnuro.domain.interfaces.IGetWorktreePathGitAction
import com.jetpackduba.gitnuro.domain.models.Branch
import com.jetpackduba.gitnuro.domain.models.Commit
import com.jetpackduba.gitnuro.domain.models.TaskType
import com.jetpackduba.gitnuro.domain.usecases.DataToRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
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

    /**
     * Worktree with [branch] checked out. A remote branch gets a local branch that tracks it (unless it already has
     * one, which is used instead).
     */
    fun createWorktree(branch: Branch, path: String) =
        launch(TaskType.Generic("Creating worktree…", "Worktree created")) { worktree ->
            if (branch.isLocal) {
                git(worktree, "worktree", "add", path, branch.simpleName)
            } else {
                val hasLocalBranch = git(worktree, "show-ref", "--verify", "--quiet", "refs/heads/${branch.simpleName}") is Either.Ok

                if (hasLocalBranch) {
                    git(worktree, "worktree", "add", path, branch.simpleName)
                } else {
                    git(worktree, "worktree", "add", "--track", "-b", branch.simpleName, path, branch.name)
                }
            }
        }

    /**
     * Moves [branch] (not checked out) forward to [target] without touching the working tree. It fails if it's not a
     * fast-forward (the branch has commits that [target] doesn't have).
     */
    fun fastForward(branch: Branch, target: String, successMessage: String) =
        launch(TaskType.Generic("Fast-forwarding ${branch.simpleName}…", successMessage)) { worktree ->
            // Fetching from the repository itself only updates the branch if it's a fast-forward
            git(worktree, "fetch", ".", "$target:${branch.name}")
                .mapError { "${branch.simpleName} can't be fast-forwarded: it has commits that $target doesn't have" }
        }

    /**
     * Upstream of a local branch as a remote branch ref (e.g. "refs/remotes/origin/main"), null if it has none.
     */
    suspend fun upstreamOf(branch: Branch): String? {
        return useCaseExecutor.execute { gitDirectory ->
            val worktree = worktreeOf(gitDirectory)
            val upstream = withContext(Dispatchers.IO) {
                git(worktree, "rev-parse", "--symbolic-full-name", "${branch.simpleName}@{upstream}").okOrNull()?.trim()
            }

            Either.Ok(upstream?.takeIf { it.startsWith("refs/remotes/") })
        }.okOrNull()
    }

    /**
     * Saves the commit as a patch file (git format-patch), in a file chosen with the system dialog.
     */
    fun createPatch(commit: Commit) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Unspecified,
        dataToRefresh = emptyArray(),
        isForegroundTask = false,
    ) { gitDirectory ->
        val worktree = worktreeOf(gitDirectory)
        val patch = withContext(Dispatchers.IO) {
            git(worktree, "format-patch", "-1", "--stdout", commit.hash)
        }

        val content = when (patch) {
            is Either.Ok -> patch.value
            is Either.Err -> return@executeLaunch patch
        }

        val target = withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, "Save patch", FileDialog.SAVE).apply {
                directory = worktree
                file = "${commit.hash.take(7)}.patch"
                isVisible = true
            }

            val directory = dialog.directory
            val name = dialog.file

            if (directory != null && name != null) File(directory, name) else null
        }

        if (target != null) {
            withContext(Dispatchers.IO) { target.writeText(content) }
        }

        Either.Ok(Unit)
    }

    /**
     * Applies a patch file, chosen with the system dialog, to the working directory (git apply).
     */
    fun applyPatch() = useCaseExecutor.executeLaunch(
        taskType = TaskType.Generic("Applying patch…", "Patch applied"),
        dataToRefresh = arrayOf(DataToRefresh.STATUS),
    ) { gitDirectory ->
        val worktree = worktreeOf(gitDirectory)

        val patchFile = withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, "Apply patch", FileDialog.LOAD).apply {
                directory = worktree
                isVisible = true
            }

            val directory = dialog.directory
            val name = dialog.file

            if (directory != null && name != null) File(directory, name) else null
        } ?: return@executeLaunch Either.Ok(Unit) // Cancelled

        val result = withContext(Dispatchers.IO) {
            git(worktree, "apply", "--whitespace=nowarn", patchFile.absolutePath)
        }

        when (result) {
            is Either.Ok -> Either.Ok(Unit)
            is Either.Err -> result
        }
    }

    /**
     * Opens the external diff tool comparing the commit with the working directory (all the files at once). Uses the
     * diff tool configured in git ("diff.tool") or FileMerge on macOS.
     */
    fun compareWithWorkingDirectory(commit: Commit) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Unspecified,
        dataToRefresh = emptyArray(),
        isForegroundTask = false,
    ) { gitDirectory ->
        val worktree = worktreeOf(gitDirectory)
        val configuredTool = git(worktree, "config", "--get", "diff.tool").okOrNull()?.trim().orEmpty()
        val isMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

        val toolArgs = when {
            configuredTool.isNotEmpty() -> emptyList()
            isMac -> listOf("--tool=opendiff")
            else -> return@executeLaunch Either.Err(
                GenericError("No external diff tool configured. Set one with \"git config --global diff.tool <tool>\".")
            )
        }

        // The tool stays open, git isn't waited for: its output is discarded so it never blocks
        withContext(Dispatchers.IO) {
            val process = ProcessEnvironment
                .processBuilder(listOf("git", "-C", worktree, "difftool", "--dir-diff", "--no-prompt") + toolArgs + commit.hash)
                .directory(File(worktree))
                .start()

            thread { process.inputStream.transferTo(java.io.OutputStream.nullOutputStream()) }
            thread { process.errorStream.transferTo(java.io.OutputStream.nullOutputStream()) }
        }

        Either.Ok(Unit)
    }

    private suspend fun worktreeOf(gitDirectory: String): String {
        return getWorktreePathGitAction(gitDirectory).okOrNull()
            ?: File(gitDirectory).let { if (it.name == ".git") it.parentFile.absolutePath else gitDirectory }
    }

    private fun Either<String, GenericError>.mapError(message: (GenericError) -> String): Either<String, GenericError> {
        return when (this) {
            is Either.Ok -> this
            is Either.Err -> Either.Err(GenericError(message(error)))
        }
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
