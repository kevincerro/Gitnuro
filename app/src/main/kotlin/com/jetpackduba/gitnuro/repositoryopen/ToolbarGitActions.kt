package com.jetpackduba.gitnuro.repositoryopen

import com.jetpackduba.gitnuro.common.ProcessEnvironment
import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GenericError
import com.jetpackduba.gitnuro.domain.errors.okOrNull
import com.jetpackduba.gitnuro.domain.interfaces.IGetWorktreePathGitAction
import com.jetpackduba.gitnuro.domain.models.TaskType
import com.jetpackduba.gitnuro.domain.usecases.DataToRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * Last HEAD movement that can be undone, read from HEAD's reflog.
 */
data class UndoableAction(
    val kind: Kind,
    /** e.g. "commit 'Fix tests'" */
    val description: String,
    /** HEAD when the action was done (to check nothing happened since) */
    val headAfter: String,
    /** HEAD before the action */
    val headBefore: String,
    /** Only for checkouts: where HEAD was and where it went */
    val checkoutFrom: String? = null,
    val checkoutTo: String? = null,
) {
    enum class Kind {
        /** commit / amend: moving back keeps the changes staged */
        COMMIT,

        /** merge, pull, reset, cherry-pick, revert: the branch is moved back keeping local changes */
        MOVE_BRANCH,
        CHECKOUT,
    }
}

/**
 * The current branch can't be pushed without forcing: its upstream has commits the local branch doesn't have.
 */
data class PushBehindState(val localRef: String, val remoteRef: String)

data class AheadBehind(val ahead: Int, val behind: Int)

data class UndoRedoState(
    val undo: UndoableAction? = null,
    val redo: UndoableAction? = null,
)

enum class LfsOperation(val menuTitle: String, val runningTitle: String, val successMessage: String) {
    FETCH("Fetch all LFS files", "Fetching LFS files…", "LFS files fetched"),
    CHECKOUT("Checkout all LFS files", "Checking out LFS files…", "LFS files checked out"),
    PULL("Pull all LFS files", "Pulling LFS files…", "LFS files pulled"),
    PUSH("Push all LFS files", "Pushing LFS files…", "LFS files pushed"),
    PRUNE("Prune local LFS repository", "Pruning local LFS repository…", "Local LFS repository pruned"),
}

private const val FIELD_SEPARATOR = "\u001f"

/**
 * Toolbar actions that GitKraken has and are implemented with the git / git-lfs CLI: Undo, Redo and the LFS menu.
 */
class ToolbarGitActions @Inject constructor(
    private val useCaseExecutor: UseCaseExecutor,
    private val getWorktreePathGitAction: IGetWorktreePathGitAction,
) {
    /**
     * Only the last undone action can be redone, and only while HEAD hasn't moved since the undo.
     */
    private var redoEntry: Pair<UndoableAction, String>? = null // action to head after undoing it

    suspend fun undoRedoState(): UndoRedoState {
        return useCaseExecutor.execute { gitDirectory ->
            val worktree = worktreeOf(gitDirectory)
            val head = git(worktree, "rev-parse", "HEAD")?.trim()

            val redo = redoEntry?.takeIf { (_, headAfterUndo) -> headAfterUndo == head }?.first
            if (redo == null) redoEntry = null

            // Our own undo is the last reflog entry right after undoing, offering to undo it would just be a redo
            val undo = if (redo != null) null else readLastAction(worktree)

            Either.Ok(UndoRedoState(undo = undo, redo = redo))
        }.okOrNull() ?: UndoRedoState()
    }

    fun undo(action: UndoableAction) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Undo(action.description),
        dataToRefresh = arrayOf(DataToRefresh.ALL),
    ) { gitDirectory ->
        val worktree = worktreeOf(gitDirectory)
        val head = git(worktree, "rev-parse", "HEAD")?.trim()

        if (head != action.headAfter) {
            return@executeLaunch Either.Err(GenericError("The repository has changed, nothing was undone."))
        }

        val result = when (action.kind) {
            UndoableAction.Kind.COMMIT -> gitOrError(worktree, "reset", "--soft", action.headBefore)
            UndoableAction.Kind.MOVE_BRANCH -> gitOrError(worktree, "reset", "--keep", action.headBefore)
            UndoableAction.Kind.CHECKOUT -> gitOrError(worktree, "checkout", action.checkoutFrom ?: action.headBefore)
        }

        if (result is Either.Ok) {
            val newHead = git(worktree, "rev-parse", "HEAD")?.trim()
            redoEntry = newHead?.let { action to it }
        }

        result
    }

    fun redo(action: UndoableAction) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Redo(action.description),
        dataToRefresh = arrayOf(DataToRefresh.ALL),
    ) { gitDirectory ->
        val worktree = worktreeOf(gitDirectory)

        val result = when (action.kind) {
            UndoableAction.Kind.COMMIT -> gitOrError(worktree, "reset", "--soft", action.headAfter)
            UndoableAction.Kind.MOVE_BRANCH -> gitOrError(worktree, "reset", "--keep", action.headAfter)
            UndoableAction.Kind.CHECKOUT -> gitOrError(worktree, "checkout", action.checkoutTo ?: action.headAfter)
        }

        if (result is Either.Ok) {
            redoEntry = null
        }

        result
    }

    /**
     * Reads HEAD's last reflog entry, like "commit: Fix tests" or "checkout: moving from main to feature".
     */
    private fun readLastAction(worktree: String): UndoableAction? {
        val output = git(worktree, "reflog", "-2", "--format=%H$FIELD_SEPARATOR%gs", "HEAD") ?: return null
        val entries = output.lines().filter { it.isNotBlank() }.map { it.split(FIELD_SEPARATOR, limit = 2) }

        if (entries.size < 2) return null

        val (headAfter, subject) = entries[0].let { it[0] to it.getOrElse(1) { "" } }
        val headBefore = entries[1][0]
        val operation = subject.substringBefore(":").trim()
        val message = subject.substringAfter(":", "").trim()

        return when {
            operation == "commit" || operation == "commit (amend)" || operation == "commit (merge)" -> UndoableAction(
                kind = UndoableAction.Kind.COMMIT,
                description = (if (operation == "commit (amend)") "amend" else "commit") +
                        (if (message.isNotEmpty()) " '${message.ellipsize()}'" else ""),
                headAfter = headAfter,
                headBefore = headBefore,
            )

            operation == "checkout" -> {
                // "moving from <from> to <to>"
                val match = Regex("""moving from (.+) to (.+)""").find(message) ?: return null
                val (from, to) = match.destructured

                UndoableAction(
                    kind = UndoableAction.Kind.CHECKOUT,
                    description = "checkout '$to'",
                    headAfter = headAfter,
                    headBefore = headBefore,
                    checkoutFrom = from,
                    checkoutTo = to,
                )
            }

            // Rebases are not included: their last reflog entry is the end of the rebase, not the state before it
            !operation.contains("rebase") && (
                    operation.startsWith("merge") || operation.startsWith("pull") ||
                            operation == "reset" || operation == "cherry-pick" || operation == "revert"
                    ) ->
                UndoableAction(
                    kind = UndoableAction.Kind.MOVE_BRANCH,
                    description = operation.substringBefore(" ").let { if (it == "reset") "reset" else it },
                    headAfter = headAfter,
                    headBefore = headBefore,
                )

            else -> null
        }?.takeIf { it.headBefore != it.headAfter || it.kind == UndoableAction.Kind.CHECKOUT }
    }

    /**
     * Checks with the last fetched state whether pushing the current branch would be rejected because its upstream
     * has commits that aren't in the local branch (GitKraken asks to pull or force push in that case).
     */
    suspend fun pushBehindState(): PushBehindState? {
        return useCaseExecutor.execute { gitDirectory ->
            val worktree = worktreeOf(gitDirectory)
            val localRef = git(worktree, "symbolic-ref", "-q", "HEAD")?.trim()
            val remoteRef = git(worktree, "rev-parse", "--symbolic-full-name", "@{upstream}")?.trim()

            val state = if (localRef.isNullOrEmpty() || remoteRef.isNullOrEmpty()) {
                null // Detached HEAD or no upstream: nothing to compare with
            } else {
                val upstreamIsContained = run(
                    listOf("git", "-C", worktree, "merge-base", "--is-ancestor", remoteRef, "HEAD"),
                    worktree,
                ) is Either.Ok

                if (upstreamIsContained) null else PushBehindState(localRef, remoteRef)
            }

            Either.Ok(state)
        }.okOrNull()
    }

    /**
     * Commits each local branch is ahead/behind its upstream, by full ref name (e.g. "refs/heads/main").
     */
    suspend fun branchesAheadBehind(): Map<String, AheadBehind> {
        return useCaseExecutor.execute { gitDirectory ->
            val worktree = worktreeOf(gitDirectory)
            val output = git(
                worktree, "for-each-ref", "--format=%(refname)$FIELD_SEPARATOR%(upstream:track,nobracket)", "refs/heads",
            ).orEmpty()

            val result = output.lines().mapNotNull { line ->
                val (ref, track) = line.split(FIELD_SEPARATOR).takeIf { it.size == 2 } ?: return@mapNotNull null
                // "ahead 1, behind 2", "behind 1", "gone" or empty
                val ahead = Regex("""ahead (\d+)""").find(track)?.groupValues?.get(1)?.toInt() ?: 0
                val behind = Regex("""behind (\d+)""").find(track)?.groupValues?.get(1)?.toInt() ?: 0
                if (ahead == 0 && behind == 0) null else ref to AheadBehind(ahead, behind)
            }.toMap()

            Either.Ok(result)
        }.okOrNull().orEmpty()
    }

    /**
     * Whether the repository uses LFS (it has LFS objects or tracks files with the LFS filter).
     */
    suspend fun isLfsRepository(): Boolean {
        return useCaseExecutor.execute { gitDirectory ->
            val usesLfs = withContext(Dispatchers.IO) {
                val worktree = worktreeOf(gitDirectory)
                File(gitDirectory, "lfs").isDirectory ||
                        File(worktree, ".gitattributes").let { it.isFile && it.readText().contains("filter=lfs") }
            }
            Either.Ok(usesLfs)
        }.okOrNull() ?: false
    }

    fun runLfs(operation: LfsOperation) = useCaseExecutor.executeLaunch(
        taskType = TaskType.Generic(operation.runningTitle, operation.successMessage),
        dataToRefresh = arrayOf(DataToRefresh.STATUS),
    ) { gitDirectory ->
        val worktree = worktreeOf(gitDirectory)
        val gitLfs = ProcessEnvironment.resolve("git-lfs")
            ?: return@executeLaunch Either.Err(
                GenericError("git-lfs is not installed. Install it (e.g. \"brew install git-lfs\") and try again.")
            )

        val args = when (operation) {
            LfsOperation.FETCH -> listOf("fetch", "--all")
            LfsOperation.CHECKOUT -> listOf("checkout")
            LfsOperation.PULL -> listOf("pull")
            LfsOperation.PUSH -> listOf("push", "--all", pushRemote(worktree))
            LfsOperation.PRUNE -> listOf("prune")
        }

        withContext(Dispatchers.IO) { run(listOf(gitLfs) + args, worktree, timeoutMinutes = 60) }
    }

    /** Remote of the current branch's upstream, or "origin" */
    private fun pushRemote(worktree: String): String {
        val branch = git(worktree, "symbolic-ref", "--short", "-q", "HEAD")?.trim()
        val remote = branch?.let { git(worktree, "config", "--get", "branch.$it.remote")?.trim() }
        return remote?.takeIf { it.isNotEmpty() } ?: "origin"
    }

    private suspend fun worktreeOf(gitDirectory: String): String {
        return getWorktreePathGitAction(gitDirectory).okOrNull()
            ?: File(gitDirectory).let { if (it.name == ".git") it.parentFile.absolutePath else gitDirectory }
    }

    /** Runs git and returns its output, or null if it failed */
    private fun git(worktree: String, vararg args: String): String? {
        return (run(listOf("git", "-C", worktree) + args, worktree) as? Either.Ok)?.value
    }

    private fun gitOrError(worktree: String, vararg args: String): Either<String, GenericError> {
        return run(listOf("git", "-C", worktree) + args, worktree)
    }

    private fun run(command: List<String>, workingDirectory: String, timeoutMinutes: Long = 2): Either<String, GenericError> {
        return try {
            val processBuilder = ProcessEnvironment.processBuilder(command).directory(File(workingDirectory))

            val process = processBuilder.start()
            var error = ""
            val errorReader = thread { error = process.errorStream.bufferedReader().readText() }
            val output = process.inputStream.bufferedReader().readText()

            if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                return Either.Err(GenericError("${command.first()} took too long and was stopped"))
            }

            errorReader.join()

            if (process.exitValue() == 0) {
                Either.Ok(output)
            } else {
                Either.Err(GenericError(error.ifBlank { output }.trim().ifBlank { "Command failed" }))
            }
        } catch (ex: Exception) {
            Either.Err(GenericError(ex.message.orEmpty(), ex))
        }
    }

    private fun String.ellipsize(max: Int = 40): String = if (length > max) take(max - 1) + "…" else this
}
