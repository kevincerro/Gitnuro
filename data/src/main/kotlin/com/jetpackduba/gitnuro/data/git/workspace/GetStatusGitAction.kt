package com.jetpackduba.gitnuro.data.git.workspace

import com.jetpackduba.gitnuro.common.extensions.lockAndUse
import com.jetpackduba.gitnuro.data.git.JGit
import com.jetpackduba.gitnuro.data.git.diff.GetDiffEntryFromStatusEntryGitAction
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.bind
import com.jetpackduba.gitnuro.domain.errors.either
import com.jetpackduba.gitnuro.domain.extensions.flatListOf
import com.jetpackduba.gitnuro.domain.interfaces.IGetStatusGitAction
import com.jetpackduba.gitnuro.domain.models.EntryType
import com.jetpackduba.gitnuro.domain.models.Status
import com.jetpackduba.gitnuro.domain.models.StatusEntry
import com.jetpackduba.gitnuro.domain.models.StatusType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RenameDetector
import javax.inject.Inject
import org.eclipse.jgit.api.Status as JGitStatus

/**
 * Renames are only looked for when the deleted + new files are at most this many (git's default "diff.renameLimit").
 */
private const val RENAME_DETECTION_MAX_ENTRIES = 1000

class GetStatusGitAction @Inject constructor(
    private val jgit: JGit,
    private val getDiffEntryFromStatusEntryGitAction: GetDiffEntryFromStatusEntryGitAction,
) : IGetStatusGitAction {
    private val mutex = Mutex()

    override suspend operator fun invoke(repository: String, paths: List<String>) = either {
        var staged: List<StatusEntry> = emptyList()
        var unstaged: List<StatusEntry> = emptyList()
        var status: JGitStatus? = null

        mutex.lockAndUse {
            withContext(Dispatchers.IO) {
                jgit.provide(repository) { git ->
                    status = git
                        .status()
                        .apply {
                            for (path in paths) {
                                addPath(path)
                            }
                        }
                        .call()

                    staged = getStaged(git, status)
                    unstaged = getUnstaged(git, status)

                }
            }.bind()
        }

        Either.Ok(Status(staged, unstaged, status?.ignoredNotInIndex?.toList().orEmpty()))
    }

    private suspend fun getUnstaged(git: Git, status: JGitStatus): List<StatusEntry> {
        return getStatusEntries(status, git, EntryType.UNSTAGED)
    }

    private suspend fun getStaged(git: Git, status: JGitStatus): List<StatusEntry> {
        return getStatusEntries(status, git, EntryType.STAGED)
    }

    private suspend fun getStatusEntries(
        status: JGitStatus,
        git: Git,
        entryType: EntryType
    ): List<StatusEntry> {
        val updatedPaths = when (entryType) {
            EntryType.STAGED -> status.changed
            EntryType.UNSTAGED -> status.modified
        }
        val modified = updatedPaths.map {
            StatusEntry(oldPath = it, newPath = it, StatusType.MODIFIED, entryType = entryType)
        }

        val conflictingPaths = when (entryType) {
            EntryType.STAGED -> emptyList()
            EntryType.UNSTAGED -> status.conflicting
        }

        val conflicting = conflictingPaths.map {
            StatusEntry(oldPath = it, newPath = it, StatusType.CONFLICTING, entryType = entryType)
        }

        val deletedPaths = when (entryType) {
            EntryType.STAGED -> status.removed
            EntryType.UNSTAGED -> status.missing
        }

        val newPaths = when (entryType) {
            EntryType.STAGED -> status.added
            EntryType.UNSTAGED -> status.untracked
        }

        // A rename is a deleted file plus a new one, so without both there is nothing to detect. Like git
        // (diff.renameLimit), it's also skipped when there are too many candidates: with thousands of new files
        // (e.g. a "node_modules" that isn't ignored in the branch that has just been checked out) it would take
        // minutes and block every other operation meanwhile.
        val canHaveRenames = deletedPaths.isNotEmpty() && newPaths.isNotEmpty() &&
                deletedPaths.size + newPaths.size <= RENAME_DETECTION_MAX_ENTRIES

        val entries = if (canHaveRenames) {
            try {
                getEntriesAfterRenameDetection(git, deletedPaths, newPaths, entryType)
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                null // The status is still valid without the renames
            }
        } else {
            null
        }

        val untracked: List<StatusEntry>
        val missing: List<StatusEntry>
        val renamed: List<StatusEntry>

        if (entries != null) {
            untracked = entries[DiffEntry.ChangeType.ADD].getStatusEntries(StatusType.ADDED, entryType)
            missing = entries[DiffEntry.ChangeType.DELETE].getStatusEntries(StatusType.REMOVED, entryType)
            renamed = entries[DiffEntry.ChangeType.RENAME].getStatusEntries(StatusType.RENAMED, entryType)
        } else {
            untracked = newPaths.map { StatusEntry(oldPath = null, newPath = it, StatusType.ADDED, entryType) }
            missing = deletedPaths.map { StatusEntry(oldPath = it, newPath = null, StatusType.REMOVED, entryType) }
            renamed = emptyList()
        }

        return flatListOf(
            untracked,
            modified,
            missing,
            conflicting,
            renamed,
        ).sortedBy { it.filePath }
    }

    private fun List<DiffEntry?>?.getStatusEntries(statusType: StatusType, entryType: EntryType): List<StatusEntry> {
        return this.orEmpty().filterNotNull().map { entry ->
            val oldPath = if (entry.changeType == DiffEntry.ChangeType.ADD) {
                null
            } else {
                entry.oldPath
            }

            val newPath = if (entry.changeType == DiffEntry.ChangeType.DELETE) {
                null
            } else {
                entry.newPath
            }


            StatusEntry(oldPath, newPath, statusType, entryType)
        }
    }

    private suspend fun getEntriesAfterRenameDetection(
        git: Git,
        deletedPaths: Set<String>,
        newPaths: Set<String>,
        entryType: EntryType,
    ): Map<DiffEntry.ChangeType?, List<DiffEntry?>> {
        // A single diff with every change, from which the deleted and the new files are picked
        val diffEntries = getDiffEntryFromStatusEntryGitAction
            .getAll(git, isCached = entryType == EntryType.STAGED)
            .filter { entry ->
                when (entry.changeType) {
                    DiffEntry.ChangeType.ADD -> entry.newPath in newPaths
                    DiffEntry.ChangeType.DELETE -> entry.oldPath in deletedPaths
                    else -> false
                }
            }

        val rename = RenameDetector(git.repository)
            .apply {
                this.addAll(diffEntries)
            }

        return rename.compute().groupBy { it.changeType }
    }
}