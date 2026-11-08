package com.jetpackduba.gitnuro.data.git.log

import com.jetpackduba.gitnuro.data.git.JGit
import com.jetpackduba.gitnuro.data.git.signers.AppGpgSigner
import com.jetpackduba.gitnuro.data.git.signers.SshSigner
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.GitError
import com.jetpackduba.gitnuro.domain.interfaces.IRewordCommitGitAction
import com.jetpackduba.gitnuro.domain.models.Commit
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.GpgConfig
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.Signer
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import javax.inject.Inject
import javax.inject.Provider

/**
 * Rewords a commit without running git: only the message changes, so every rewritten commit keeps its tree and only
 * its parents change (no rebase, conflicts or working directory changes). Signing uses the app's signers (gpg binary
 * + in-app passphrase dialog), so it doesn't need a terminal for the pinentry.
 */
class RewordCommitGitAction @Inject constructor(
    private val jgit: JGit,
    private val sshSigner: Provider<SshSigner>,
    private val gpgSigner: Provider<AppGpgSigner>,
) : IRewordCommitGitAction {
    override suspend operator fun invoke(
        repositoryPath: String,
        commit: Commit,
        message: String,
    ): Either<Unit, GitError> = jgit.provide(repositoryPath) { git ->
        val repository = git.repository
        val headId = repository.resolve("HEAD") ?: throw IllegalStateException("The repository has no commits.")
        val targetId = ObjectId.fromString(commit.hash)
        val commitSigner = commitSigner(repository)
        val rewritten = mutableMapOf<ObjectId, ObjectId>()

        RevWalk(repository).use { walk ->
            val target = walk.parseCommit(targetId)

            if (!walk.isMergedInto(target, walk.parseCommit(headId))) {
                throw IllegalStateException("The commit is not part of the current branch's history.")
            }

            // From the target (included) up to HEAD, parents always before their children
            walk.reset()
            walk.sort(RevSort.TOPO)
            walk.sort(RevSort.REVERSE, true)
            walk.markStart(walk.parseCommit(headId))
            target.parents.forEach { walk.markUninteresting(it) }

            repository.newObjectInserter().use { inserter ->
                for (old in walk) {
                    val isTarget = old.id == targetId

                    // Commits merged from other branches don't descend from the target, they are kept
                    if (!isTarget && old.parents.none { rewritten.containsKey(it.id) }) continue

                    val builder = CommitBuilder().apply {
                        setTreeId(old.tree.id)
                        setParentIds(old.parents.map { rewritten[it.id] ?: it.id })
                        setAuthor(old.authorIdent)
                        setCommitter(old.committerIdent)
                        setMessage(if (isTarget) message else old.fullMessage)
                        encoding = old.encoding
                    }

                    commitSigner?.let { (signer, config, signingKey) ->
                        builder.gpgSignature = signer.sign(repository, config, builder.build(), old.committerIdent, signingKey, null)
                    }

                    rewritten[old.id] = inserter.insert(builder)
                }

                inserter.flush()
            }
        }

        val newHeadId = rewritten[headId] ?: throw IllegalStateException("The commit couldn't be rewritten.")

        // Moves the current branch (or the detached HEAD) to the rewritten commit
        val result = repository.updateRef("HEAD").run {
            setNewObjectId(newHeadId)
            setExpectedOldObjectId(headId)
            setRefLogMessage("reword: ${message.lineSequence().first()}", false)
            forceUpdate()
        }

        if (result != RefUpdate.Result.FORCED && result != RefUpdate.Result.FAST_FORWARD) {
            throw IllegalStateException("HEAD couldn't be updated: $result")
        }
    }

    private data class CommitSigner(val signer: Signer, val config: GpgConfig, val signingKey: String?)

    /** Null when commits aren't signed ("commit.gpgSign" disabled). */
    private fun commitSigner(repository: Repository): CommitSigner? {
        val config = repository.config

        if (!config.getBoolean("commit", null, "gpgSign", false)) return null

        val signer: Signer = if (config.getString("gpg", null, "format") == "ssh") sshSigner.get() else gpgSigner.get()

        return CommitSigner(signer, GpgConfig(config), config.getString("user", null, "signingKey"))
    }
}
