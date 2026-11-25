package com.jetpackduba.gitnuro.domain.usecases

import com.jetpackduba.gitnuro.FileType
import com.jetpackduba.gitnuro.common.printDebug
import com.jetpackduba.gitnuro.common.printError
import com.jetpackduba.gitnuro.common.systemSeparator
import com.jetpackduba.gitnuro.domain.GitConstants
import com.jetpackduba.gitnuro.domain.TabCoroutineScope
import com.jetpackduba.gitnuro.domain.errors.okOrNull
import com.jetpackduba.gitnuro.domain.interfaces.IFileChangesWatcher
import com.jetpackduba.gitnuro.domain.interfaces.IGetStatusGitAction
import com.jetpackduba.gitnuro.domain.models.WatcherEvent
import com.jetpackduba.gitnuro.domain.repositories.RepositoryDataRepository
import com.jetpackduba.gitnuro.domain.repositories.RepositoryStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

private const val TAG = "ObserveRepositoryToRefreshUseCase"

private const val REFRESH_TIME_SINCE_LAST_OPERATION = 1_500L
private const val PENDING_REFRESH_RETRY_DELAY = 250L

class ObserveRepositoryToRefreshUseCase @Inject constructor(
    private val tabCoroutineScope: TabCoroutineScope,
    private val fileChangesWatcher: IFileChangesWatcher,
    private val repositoryDataRepository: RepositoryDataRepository,
    private val getWorktreeUseCase: GetWorktreeUseCase,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val repositoryStateRepository: RepositoryStateRepository,
    private val getStatusGitAction: IGetStatusGitAction,
) {
    /**
     * Sometimes external apps can run filesystem multiple operations in a fraction of a second.
     * To prevent excessive updates, we add a slight delay between updates emission to prevent slowing down
     * the app by constantly running "git status" or even full refreshes.
     *
     */
    operator fun invoke() {
        // TODO add some logging?
        val repositoryPath = repositoryDataRepository.repositoryPath ?: return
        val gitDirPrefix = repositoryPath.removeSuffix(systemSeparator) + systemSeparator

        // null means no pending refresh. true/false indicates if the git dir has changed (requiring a full refresh)
        val pendingRefresh = AtomicReference<Boolean?>(null)
        val refreshSignal = Channel<Unit>(Channel.CONFLATED)

        tabCoroutineScope.launch {
            val worktreeDir = getWorktreeUseCase().okOrNull() ?: return@launch

            // Changes detected while another operation is running (or right after it) used to be dropped, which caused
            // the status and log to become outdated until a manual refresh/fetch was triggered. Instead, keep them as
            // pending and refresh once it's safe to do so.
            launch {
                for (signal in refreshSignal) {
                    while (!canRefreshData()) {
                        delay(PENDING_REFRESH_RETRY_DELAY)
                    }

                    val hasGitDirChanged = pendingRefresh.getAndSet(null) ?: continue

                    if (hasGitDirChanged) {
                        refreshDataUseCase(DataToRefresh.ALL).join()
                    } else {
                        refreshDataUseCase(DataToRefresh.STATUS, DataToRefresh.LOG, DataToRefresh.REPO_STATE).join()
                    }
                }
            }

            launch {
                fileChangesWatcher
                    .observeEvents()
                    .flowOn(Dispatchers.IO)
                    .collect { event ->
                        when (event) {
                            is WatcherEvent.ChangesDetected -> {
                                // TODO Does this filtering work properly on Windows?
                                // Remove probe files that may temporarily be created by JGit
                                val changes = event.changes
                                    .filter {
                                        !it.path.startsWith("${gitDirPrefix}.probe-")
                                    }

                                if (changes.isEmpty()) {
                                    return@collect
                                }

                                printDebug(TAG, "Changes detected: ${changes.toList()}")

                                val containsOnlyEditMessageChanges = changes.all {
                                    it.path == "$gitDirPrefix${GitConstants.COMMIT_MSG}" ||
                                            it.path == "$gitDirPrefix${GitConstants.MERGE_MSG}" ||
                                            it.path == "$gitDirPrefix${GitConstants.SQUASH_MSG}"
                                }

                                if (containsOnlyEditMessageChanges) {
                                    return@collect
                                }

                                val hasGitDirChanged = changes.any {
                                    it.path == repositoryPath || it.path.startsWith(gitDirPrefix)
                                }

                                updateWatchedDirectories(event, repositoryPath, worktreeDir + systemSeparator)

                                pendingRefresh.updateAndGet { previous -> (previous ?: false) || hasGitDirChanged }
                                refreshSignal.trySend(Unit)
                            }

                            is WatcherEvent.WatchInitError -> {
                                printDebug(TAG, "Watch init error: ${event.code}")
                            }
                        }
                    }
            }

            fileChangesWatcher.addPathToWatch(worktreeDir, false)
            fileChangesWatcher.addPathToWatch(repositoryPath, false)
            fileChangesWatcher.addPathToWatch("$repositoryPath${systemSeparator}refs", true)
            fileChangesWatcher.addPathToWatch("$repositoryPath${systemSeparator}modules", true)

            val status = getStatusGitAction(repositoryPath).okOrNull()

            val worktreeDirFile = File(worktreeDir)

            val dirs = getDirsToWatch(
                worktreeDir = worktreeDir + systemSeparator,
                excludedRelativePaths = HashSet(listOf(".git")),
                dirFile = worktreeDirFile,
                ignoreList = status?.ignored.orEmpty()
            )

            if (status != null) {
                for (child in dirs) {
                    if (!status.ignored.contains(child.absolutePath.removePrefix(repositoryPath))) {
                        fileChangesWatcher.addPathToWatch(child.absolutePath, false)
                    }
                }
            }
        }.invokeOnCompletion {
            fileChangesWatcher.close()
        }
    }

    private suspend fun canRefreshData(): Boolean {
        val canRefresh = if (repositoryStateRepository.currentTask.value != null) {
            false
        } else {
            val timeDiffInMs =
                System.currentTimeMillis() - repositoryStateRepository.lastOperationTimestamp.first()

            printDebug(TAG, "time diff in ms: $timeDiffInMs")

            timeDiffInMs > REFRESH_TIME_SINCE_LAST_OPERATION
        }

        printDebug(TAG, "canRefresh: $canRefresh")

        return canRefresh
    }

    private suspend fun updateWatchedDirectories(
        event: WatcherEvent.ChangesDetected,
        repositoryPath: String,
        worktreeDirPath: String,
    ) {
        val directories = event
            .changes
            .filter { it.fileType == FileType.DIRECTORY }

        if (directories.isNotEmpty()) {
            val groupedDirs = directories
                .groupBy { File(it.path).exists() }

            val newDirs = groupedDirs[true].orEmpty()
            val removedDirs = groupedDirs[false].orEmpty()

            if (newDirs.isNotEmpty()) {
                val status = getStatusGitAction(
                    repositoryPath,
                    newDirs.map { it.path.removePrefix(worktreeDirPath) },
                ).okOrNull()

                for (dir in newDirs) {
                    if (status != null && !status.ignored.contains(dir.path.removePrefix(worktreeDirPath))) {
                        fileChangesWatcher.addPathToWatch(dir.path, false)
                    }
                }
            }

            for (dir in removedDirs) {
                fileChangesWatcher.removePathFromWatch(dir.path)
            }
        }
    }

    private fun getDirsToWatch(
        worktreeDir: String,
        excludedRelativePaths: HashSet<String>,
        dirFile: File,
        ignoreList: List<String>
    ): List<File> {
        val childrenDirs = try {
            dirFile.listFiles { file -> file.isDirectory }
        } catch (e: Exception) {
            printError(TAG, e.message.orEmpty(), e)
            emptyArray()
        }
            .filter {
                val relativePath = it.absolutePath.removePrefix(worktreeDir)
                !ignoreList.contains(relativePath) &&
                        !excludedRelativePaths.contains(relativePath)
            }

        return childrenDirs + childrenDirs.flatMap {
            getDirsToWatch(
                worktreeDir,
                excludedRelativePaths,
                it,
                ignoreList
            )
        }
    }
}
