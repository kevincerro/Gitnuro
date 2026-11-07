package com.jetpackduba.gitnuro.repositoryopen

import com.jetpackduba.gitnuro.data.git.signers.GpgBinary
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
 * Commit context menu actions that Gitnuro didn't have (GitKraken's menu). History rewrites (edit message, drop,
 * move) are done with "git rebase -i", giving it a prepared todo list instead of opening an editor.
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

    fun createAnnotatedTag(commit: Commit, name: String, message: String) =
        launch(TaskType.CreateTag) { worktree ->
            git(worktree, "tag", "-a", name, "-m", message, commit.hash)
        }

    fun editMessage(commit: Commit, message: String) =
        launch(TaskType.Generic("Editing commit message…", "Commit message edited")) { worktree ->
            val messageFile = File.createTempFile("gitnuro-message", ".txt").apply { writeText(message) }

            try {
                val head = git(worktree, "rev-parse", "HEAD").okOrNull()?.trim()

                if (head == commit.hash) {
                    // Only the message changes: --only without paths ignores whatever is staged
                    git(worktree, "commit", "--amend", "--only", "--allow-empty", "--no-verify", "-F", messageFile.absolutePath)
                } else {
                    rewriteHistory(worktree, commit) { todo ->
                        val index = todo.indexOfFirst { it.hash == commit.hash }
                        todo.toMutableList().apply {
                            add(
                                index + 1,
                                TodoLine.Exec(
                                    "git commit --amend --only --allow-empty --no-verify -F ${shellQuote(messageFile.absolutePath)}"
                                )
                            )
                        }
                    }
                }
            } finally {
                messageFile.delete()
            }
        }

    fun dropCommit(commit: Commit) = launch(TaskType.Generic("Dropping commit…", "Commit dropped")) { worktree ->
        rewriteHistory(worktree, commit) { todo -> todo.filterNot { it.hash == commit.hash } }
    }

    /**
     * [up] moves the commit after its child (newer), otherwise before its parent (older).
     */
    fun moveCommit(commit: Commit, up: Boolean) =
        launch(TaskType.Generic("Moving commit…", "Commit moved")) { worktree ->
            if (!up && commit.parentsHashes.isEmpty()) {
                return@launch Either.Err(GenericError("The first commit of the repository can't be moved down."))
            }

            // Moving down swaps it with its parent, so the rebase has to start one commit earlier
            val firstRewrittenCommit = if (up) commit.hash else commit.parentsHashes.first()

            rewriteHistory(worktree, firstRewrittenCommit) { todo ->
                val index = todo.indexOfFirst { it.hash == commit.hash }
                val otherIndex = if (up) index + 1 else index - 1

                if (index < 0 || otherIndex !in todo.indices) {
                    throw IllegalStateException("There is no commit to swap it with.")
                }

                todo.toMutableList().apply {
                    val other = this[otherIndex]
                    this[otherIndex] = this[index]
                    this[index] = other
                }
            }
        }

    private sealed interface TodoLine {
        val hash: String?
        val text: String

        data class Pick(override val hash: String) : TodoLine {
            override val text get() = "pick $hash"
        }

        data class Exec(val command: String) : TodoLine {
            override val hash: String? = null
            override val text get() = "exec $command"
        }
    }

    private fun rewriteHistory(
        worktree: String,
        commit: Commit,
        editTodo: (List<TodoLine>) -> List<TodoLine>,
    ) = rewriteHistory(worktree, commit.hash, editTodo)

    /**
     * Rebases [firstCommitHash] and its descendants up to HEAD with the todo list edited by [editTodo].
     */
    private fun rewriteHistory(
        worktree: String,
        firstCommitHash: String,
        editTodo: (List<TodoLine>) -> List<TodoLine>,
    ): Either<String, GenericError> {
        if (git(worktree, "merge-base", "--is-ancestor", firstCommitHash, "HEAD") !is Either.Ok) {
            return Either.Err(GenericError("The commit is not part of the current branch's history."))
        }

        val parents = git(worktree, "rev-list", "--parents", "-n", "1", firstCommitHash).okOrNull()
            ?.trim()?.split(" ")?.drop(1).orEmpty()

        if (parents.size > 1) {
            return Either.Err(GenericError("Merge commits can't be rewritten."))
        }

        val base = parents.firstOrNull()
        val range = if (base != null) "$base..HEAD" else "HEAD"

        val commits = git(worktree, "rev-list", "--reverse", "--parents", range).okOrNull()
            ?.lines()?.filter { it.isNotBlank() }?.map { it.split(" ") }
            ?: return Either.Err(GenericError("The commits to rewrite couldn't be read."))

        if (commits.any { it.size > 2 }) {
            return Either.Err(GenericError("There are merge commits after this commit, the history can't be rewritten."))
        }

        val todo = try {
            editTodo(commits.map { TodoLine.Pick(it.first()) })
        } catch (ex: IllegalStateException) {
            return Either.Err(GenericError(ex.message.orEmpty()))
        }

        val todoFile = File.createTempFile("gitnuro-rebase-todo", ".txt")

        return try {
            todoFile.writeText(todo.joinToString("\n", postfix = "\n") { it.text })

            git(
                worktree,
                "-c", "sequence.editor=cp ${shellQuote(todoFile.absolutePath)}",
                "rebase", "-i", "--autostash",
                *(if (base != null) arrayOf(base) else arrayOf("--root")),
            ).let { result ->
                if (result is Either.Err) {
                    Either.Err(
                        GenericError(
                            "The rebase stopped: ${result.error.message}\n" +
                                    "Fix the conflicts and continue or abort the rebase."
                        )
                    )
                } else {
                    result
                }
            }
        } finally {
            todoFile.delete()
        }
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

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
            val processBuilder = ProcessBuilder(listOf(GpgBinary.resolve("git") ?: "git", "-C", worktree) + args)
                .directory(File(worktree))

            processBuilder.environment().apply {
                // Never open an editor, everything is given to git
                put("GIT_EDITOR", "true")
                put("PATH", listOf(get("PATH").orEmpty(), "/opt/homebrew/bin", "/usr/local/bin").joinToString(File.pathSeparator))
            }

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
