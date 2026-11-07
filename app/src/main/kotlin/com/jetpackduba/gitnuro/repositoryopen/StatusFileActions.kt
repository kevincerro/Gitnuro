package com.jetpackduba.gitnuro.repositoryopen

import com.jetpackduba.gitnuro.common.OS
import com.jetpackduba.gitnuro.common.currentOs
import com.jetpackduba.gitnuro.domain.IShellManager
import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GenericError
import com.jetpackduba.gitnuro.domain.interfaces.IGetWorktreePathGitAction
import com.jetpackduba.gitnuro.domain.models.EntryType
import com.jetpackduba.gitnuro.domain.models.StatusEntry
import com.jetpackduba.gitnuro.domain.models.StatusType
import com.jetpackduba.gitnuro.domain.models.TaskType
import com.jetpackduba.gitnuro.domain.usecases.DataToRefresh
import com.jetpackduba.gitnuro.domain.usecases.OpenFileInExternalAppUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.inject.Inject

/**
 * Actions available from the context menu of a single file in the status pane that don't have
 * a dedicated git action (most of them rely on the git CLI or on the OS).
 */
class StatusFileActions @Inject constructor(
    private val useCaseExecutor: UseCaseExecutor,
    private val shellManager: IShellManager,
    private val openFileInExternalAppUseCase: OpenFileInExternalAppUseCase,
    private val getWorktreePathGitAction: IGetWorktreePathGitAction,
) {
    private suspend fun worktreeOf(gitDirectory: String): String {
        return when (val result = getWorktreePathGitAction(gitDirectory)) {
            is Either.Ok -> result.value
            is Either.Err -> File(gitDirectory).let { if (it.name == ".git") it.parentFile.absolutePath else gitDirectory }
        }
    }

    fun ignoreFile(statusEntry: StatusEntry) = appendToGitignore("/" + statusEntry.filePath)

    fun ignoreExtension(extension: String) = appendToGitignore("*.$extension")

    private fun appendToGitignore(pattern: String) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Unspecified,
        dataToRefresh = arrayOf(DataToRefresh.STATUS),
        isForegroundTask = false,
    ) { gitDirectory ->
        // The tab's repository path is the .git directory, files and git commands need the working tree
        val repositoryPath = worktreeOf(gitDirectory)
        withContext(Dispatchers.IO) {
            val gitignore = File(repositoryPath, ".gitignore")
            val currentContent = if (gitignore.exists()) gitignore.readText() else ""

            if (currentContent.lines().none { it.trim() == pattern }) {
                val separator = if (currentContent.isEmpty() || currentContent.endsWith("\n")) "" else "\n"
                gitignore.appendText("$separator$pattern\n")
            }
        }

        Either.Ok(Unit)
    }

    /**
     * Like GitKraken's "Discard all changes": resets tracked files to HEAD and deletes untracked files
     * (ignored files are kept).
     */
    fun discardAll() = useCaseExecutor.executeLaunch(
        taskType = TaskType.DiscardFile(emptyList()),
        dataToRefresh = arrayOf(DataToRefresh.STATUS, DataToRefresh.LOG),
    ) { gitDirectory ->
        // The tab's repository path is the .git directory, files and git commands need the working tree
        val repositoryPath = worktreeOf(gitDirectory)
        when (val reset = runGit(repositoryPath, listOf("reset", "--hard", "HEAD"))) {
            is Either.Err -> reset
            is Either.Ok -> runGit(repositoryPath, listOf("clean", "-fd"))
        }
    }

    fun stashFile(statusEntry: StatusEntry) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Stash,
        dataToRefresh = arrayOf(DataToRefresh.STATUS, DataToRefresh.STASHES, DataToRefresh.LOG),
    ) { gitDirectory ->
        // The tab's repository path is the .git directory, files and git commands need the working tree
        val repositoryPath = worktreeOf(gitDirectory)
        val fileName = statusEntry.filePath.substringAfterLast('/')
        val paths = listOfNotNull(statusEntry.filePath, statusEntry.oldPath).distinct()

        runGit(
            repositoryPath,
            listOf("stash", "push", "--include-untracked", "-m", "Stash of $fileName", "--") + paths,
        )
    }

    fun openInDiffTool(statusEntry: StatusEntry) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Unspecified,
        dataToRefresh = emptyArray(),
        isForegroundTask = false,
    ) { gitDirectory ->
        // The tab's repository path is the .git directory, files and git commands need the working tree
        val repositoryPath = worktreeOf(gitDirectory)
        val configuredTool = runGit(repositoryPath, listOf("config", "--get", "diff.tool"))
            .let { if (it is Either.Ok) it.value.trim() else "" }

        val toolArgs = when {
            configuredTool.isNotEmpty() -> emptyList()
            currentOs == OS.MAC -> listOf("--tool=opendiff")
            else -> return@executeLaunch Either.Err(
                GenericError("No external diff tool configured. Set one with \"git config --global diff.tool <tool>\".")
            )
        }

        val cached = if (statusEntry.entryType == EntryType.STAGED) listOf("--cached") else emptyList()

        if (statusEntry.statusType == StatusType.ADDED && statusEntry.entryType == EntryType.UNSTAGED) {
            // Untracked files have no diff against the index, compare them against an empty file
            startGit(
                repositoryPath,
                listOf("difftool", "--no-prompt", "--no-index") + toolArgs +
                        listOf("--", nullDevice, statusEntry.filePath),
            )
        } else {
            startGit(
                repositoryPath,
                listOf("difftool", "--no-prompt") + toolArgs + cached + listOf("--", statusEntry.filePath),
            )
        }

        Either.Ok(Unit)
    }

    fun openInExternalEditor(statusEntry: StatusEntry) = withAbsoluteFile(statusEntry) { file ->
        when (currentOs) {
            OS.MAC -> shellManager.runCommandWithoutResult(listOf("open", "-t", file.absolutePath))
            else -> try {
                Desktop.getDesktop().edit(file)
            } catch (ex: Exception) {
                openFileInExternalAppUseCase(file.absolutePath)
            }
        }
    }

    fun openInDefaultProgram(statusEntry: StatusEntry) = withAbsoluteFile(statusEntry) { file ->
        openFileInExternalAppUseCase(file.absolutePath)
    }

    fun showInFileManager(statusEntry: StatusEntry) = withAbsoluteFile(statusEntry) { file ->
        when (currentOs) {
            OS.MAC -> shellManager.runCommandWithoutResult(listOf("open", "-R", file.absolutePath))
            OS.WINDOWS -> shellManager.runCommandWithoutResult(listOf("explorer", "/select,", file.absolutePath))
            else -> openFileInExternalAppUseCase(file.parentFile.absolutePath)
        }
    }

    fun createPatch(statusEntry: StatusEntry) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Unspecified,
        dataToRefresh = emptyArray(),
        isForegroundTask = false,
    ) { gitDirectory ->
        // The tab's repository path is the .git directory, files and git commands need the working tree
        val repositoryPath = worktreeOf(gitDirectory)
        val isUntracked = statusEntry.statusType == StatusType.ADDED && statusEntry.entryType == EntryType.UNSTAGED

        val diffArgs = when {
            isUntracked -> listOf("diff", "--binary", "--no-index", "--", nullDevice, statusEntry.filePath)
            statusEntry.entryType == EntryType.STAGED -> listOf("diff", "--binary", "--cached", "--", statusEntry.filePath)
            else -> listOf("diff", "--binary", "--", statusEntry.filePath)
        }

        // "git diff --no-index" returns 1 when there are differences
        val patch = when (val result = runGit(repositoryPath, diffArgs, allowedExitCodes = setOf(0, 1))) {
            is Either.Ok -> result.value
            is Either.Err -> return@executeLaunch result
        }

        if (patch.isBlank()) {
            return@executeLaunch Either.Err(GenericError("There are no changes to create a patch from."))
        }

        val fileName = statusEntry.filePath.substringAfterLast('/')
        val target = withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, "Save patch", FileDialog.SAVE).apply {
                directory = repositoryPath
                file = "$fileName.patch"
                isVisible = true
            }

            val directory = dialog.directory
            val name = dialog.file

            if (directory != null && name != null) File(directory, name) else null
        }

        if (target != null) {
            withContext(Dispatchers.IO) { target.writeText(patch) }
        }

        Either.Ok(Unit)
    }

    private fun withAbsoluteFile(statusEntry: StatusEntry, block: (File) -> Unit) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Unspecified,
        dataToRefresh = emptyArray(),
        isForegroundTask = false,
    ) { gitDirectory ->
        // The tab's repository path is the .git directory, files and git commands need the working tree
        val repositoryPath = worktreeOf(gitDirectory)
        val file = File(repositoryPath, statusEntry.filePath)

        if (!file.exists()) {
            return@executeLaunch Either.Err(GenericError("File \"${statusEntry.filePath}\" does not exist."))
        }

        block(file)
        Either.Ok(Unit)
    }

    private suspend fun runGit(
        repositoryPath: String,
        args: List<String>,
        allowedExitCodes: Set<Int> = setOf(0),
    ): Either<String, GenericError> = withContext(Dispatchers.IO) {
        try {
            val process = shellManager.runCommandProcess(listOf("git", "-C", repositoryPath) + args)
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode in allowedExitCodes) {
                Either.Ok(output)
            } else {
                Either.Err(GenericError(error.ifBlank { output }.trim().ifBlank { "git exited with code $exitCode" }))
            }
        } catch (ex: Exception) {
            Either.Err(GenericError(ex.message.orEmpty(), ex))
        }
    }

    /** Starts a git process without waiting for it (used for GUI tools) */
    private fun startGit(repositoryPath: String, args: List<String>) {
        val process = shellManager.runCommandProcess(listOf("git", "-C", repositoryPath) + args)
        // Drain the streams so the external tool never blocks on a full pipe
        Thread { process.inputStream.transferTo(java.io.OutputStream.nullOutputStream()) }.start()
        Thread { process.errorStream.transferTo(java.io.OutputStream.nullOutputStream()) }.start()
    }

    private val nullDevice: String
        get() = if (currentOs == OS.WINDOWS) "NUL" else "/dev/null"
}
