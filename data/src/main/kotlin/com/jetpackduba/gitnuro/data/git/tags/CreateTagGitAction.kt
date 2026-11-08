package com.jetpackduba.gitnuro.data.git.tags

import com.jetpackduba.gitnuro.data.git.JGit
import com.jetpackduba.gitnuro.data.git.signers.AppGpgSigner
import com.jetpackduba.gitnuro.data.git.signers.SshSigner
import com.jetpackduba.gitnuro.domain.interfaces.ICreateTagGitAction
import com.jetpackduba.gitnuro.domain.models.Commit
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import javax.inject.Inject
import javax.inject.Provider

/**
 * Creates an annotated tag, signed with the app's signers when "tag.gpgSign" is enabled (gpg binary + in-app
 * passphrase dialog, or SSH), so signing never needs a terminal for the pinentry.
 */
class CreateTagGitAction @Inject constructor(
    private val jgit: JGit,
    private val sshSigner: Provider<SshSigner>,
    private val gpgSigner: Provider<AppGpgSigner>,
) : ICreateTagGitAction {
    override suspend operator fun invoke(repositoryPath: String, tag: String, commit: Commit, message: String?) =
        jgit.provide(repositoryPath) { git ->
            val target = RevWalk(git.repository).use { it.parseCommit(ObjectId.fromString(commit.hash)) }
            val signingKey = tagSigningKey(git.repository)

            git
                .tag()
                .setAnnotated(true)
                .setName(tag)
                .setObjectId(target)
                .apply { if (message != null) setMessage(message) }
                .apply {
                    if (signingKey != null) {
                        val format = git.repository.config.getString("gpg", null, "format")
                        setSigned(true)
                        setSigner(if (format == "ssh") sshSigner.get() else gpgSigner.get())
                        signingKey.key?.let { setSigningKey(it) }
                    } else {
                        setSigned(false)
                    }
                }
                .call()

            Unit
        }

    private class SigningKey(val key: String?)

    /** Null when tags aren't signed. With gpg the key is optional (like git, the committer's email is used). */
    private fun tagSigningKey(repository: Repository): SigningKey? {
        val config = repository.config

        if (!config.getBoolean("tag", null, "gpgSign", false)) return null

        val key = config.getString("user", null, "signingKey")

        if (key == null && config.getString("gpg", null, "format") == "ssh") {
            throw IllegalStateException("Can't sign the tag: user.signingKey is not set.")
        }

        return SigningKey(key)
    }
}
