package com.jetpackduba.gitnuro.data.git.tags

import com.jetpackduba.gitnuro.data.git.JGit
import com.jetpackduba.gitnuro.data.git.remote_operations.HandleTransportGitAction
import com.jetpackduba.gitnuro.domain.errors.bind
import com.jetpackduba.gitnuro.domain.interfaces.IPushTagGitAction
import com.jetpackduba.gitnuro.domain.models.Tag
import com.jetpackduba.gitnuro.domain.models.isRejected
import com.jetpackduba.gitnuro.domain.models.statusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.RefSpec
import javax.inject.Inject

private const val DEFAULT_REMOTE = "origin"

class PushTagGitAction @Inject constructor(
    private val handleTransportGitAction: HandleTransportGitAction,
    private val jgit: JGit,
) : IPushTagGitAction {
    override suspend operator fun invoke(repositoryPath: String, tag: Tag) = jgit.provide(repositoryPath) { git ->
        val remote = getRemoteToPush(git) ?: throw Exception("There are no remotes configured to push the tag to")

        val tagRefName = if (tag.name.startsWith(Constants.R_TAGS)) {
            tag.name
        } else {
            "${Constants.R_TAGS}${tag.name}"
        }

        handleTransportGitAction(repositoryPath) {
            withContext(Dispatchers.IO) {
                val pushResult = git
                    .push()
                    .setRemote(remote)
                    .setRefSpecs(RefSpec("$tagRefName:$tagRefName"))
                    .setTransportConfigCallback { handleTransport(it) }
                    .call()

                val rejectedUpdates = pushResult
                    .flatMap { it.remoteUpdates }
                    .filter { it.status.isRejected }

                if (rejectedUpdates.isNotEmpty()) {
                    throw Exception(rejectedUpdates.joinToString("\n") { it.statusMessage })
                }
            }
        }.bind()

        Unit
    }

    /**
     * Uses the remote of the upstream branch of the current branch. If there isn't any, fallbacks to "origin" or to the
     * first remote available.
     */
    private fun getRemoteToPush(git: Git): String? {
        val repository = git.repository
        val remotes = repository.remoteNames

        val currentBranch: String? = repository.branch
        val trackingRemote = currentBranch?.let { repository.config.getString("branch", it, "remote") }

        return when {
            trackingRemote != null && remotes.contains(trackingRemote) -> trackingRemote
            remotes.contains(DEFAULT_REMOTE) -> DEFAULT_REMOTE
            else -> remotes.sorted().firstOrNull()
        }
    }
}
