package com.jetpackduba.gitnuro.data.git.repository

import com.jetpackduba.gitnuro.data.extensions.isMerging
import com.jetpackduba.gitnuro.data.extensions.isReverting
import com.jetpackduba.gitnuro.data.git.JGit
import com.jetpackduba.gitnuro.domain.GitConstants
import com.jetpackduba.gitnuro.domain.interfaces.IPersistCommitMessageGitAction
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import java.io.File
import javax.inject.Inject

class PersistCommitMessageGitAction @Inject constructor(
    private val jgit: JGit,
) : IPersistCommitMessageGitAction {
    override suspend operator fun invoke(repositoryPath: String, message: String?) =
        jgit.provide(repositoryPath) { git ->
            val state = git.repository.repositoryState
            if (state.isMerging || state.isRebasing || state.isReverting) {
                git.repository.writeMergeCommitMsg(message)
            } else if (state == RepositoryState.SAFE) {
                writeCommitDraft(git.repository, message)
            }
        }
}

internal fun readCommitDraft(repository: Repository): String? {
    val file = File(repository.directory, GitConstants.COMMIT_DRAFT)

    return if (file.isFile) file.readText().ifEmpty { null } else null
}

internal fun writeCommitDraft(repository: Repository, message: String?) {
    val file = File(repository.directory, GitConstants.COMMIT_DRAFT)

    if (message.isNullOrEmpty()) {
        file.delete()
    } else {
        file.writeText(message)
    }
}
