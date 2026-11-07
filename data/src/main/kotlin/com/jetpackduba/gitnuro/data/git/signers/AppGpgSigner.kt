package com.jetpackduba.gitnuro.data.git.signers

import com.jetpackduba.gitnuro.data.git.credentials.GpgCredentialsProvider
import com.jetpackduba.gitnuro.domain.credentials.CredentialsStateManager
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.errors.JGitInternalException
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import org.bouncycastle.openpgp.PGPException
import org.eclipse.jgit.api.errors.CanceledException
import org.eclipse.jgit.gpg.bc.internal.BouncyCastleGpgSigner
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.transport.CredentialsProvider
import javax.inject.Inject

private const val INVALID_PASSWORD_MESSAGE = "Is the entered passphrase correct?"


/** GPG_ERR_BAD_PASSPHRASE, GPG_ERR_INV_PASSPHRASE and GPG_ERR_NO_PASSPHRASE */
private val PASSPHRASE_ERROR_CODES = setOf(11L, 31L, 177L)

class AppGpgSigner @Inject constructor(
    private val gpgCredentials: GpgCredentialsProvider,
    private val credentialsStateManager: CredentialsStateManager,
) : BouncyCastleGpgSigner() {

    override fun sign(
        repository: Repository?,
        config: GpgConfig?,
        data: ByteArray?,
        committer: PersonIdent?,
        signingKey: String?,
        credentialsProvider: CredentialsProvider?,
    ): GpgSignature {
        val gpg = GpgBinary.resolve(config?.program)

        if (gpg != null && data != null) {
            // gpg understands the trailing "!" (exact subkey), so the key is given as configured
            return signWithGpgBinary(gpg, data, signingKey?.trim()?.ifEmpty { null } ?: committer?.emailAddress)
        }

        return try {
            var gpgSignature: GpgSignature? = null
            retryIfWrongPassphrase { isRetry ->
                gpgCredentials.isRetry = isRetry
                gpgSignature = super.sign(repository, config, data, committer, signingKey.normalizeKeyId(), gpgCredentials)
                gpgCredentials.savePasswordInMemory()
            }

            gpgSignature!!
        } catch (ex: CanceledException) {
            println("Signing cancelled")
            throw ex
        }

    }

    override fun canLocateSigningKey(
        repository: Repository?,
        config: GpgConfig?,
        committer: PersonIdent?,
        signingKey: String?,
        credentialsProvider: CredentialsProvider?,
    ): Boolean {
        val gpg = GpgBinary.resolve(config?.program)

        if (gpg != null) {
            val key = signingKey?.trim()?.ifEmpty { null } ?: committer?.emailAddress
            val args = listOf("--batch", "--list-secret-keys") + listOfNotNull(key)
            return GpgBinary.run(gpg, args).isSuccess
        }

        return super.canLocateSigningKey(repository, config, committer, signingKey.normalizeKeyId(), gpgCredentials)
    }

    /**
     * Signs like git does ("gpg -bsau <key>"). The passphrase is asked in-app and given to gpg with the loopback
     * pinentry mode, so it works without a graphical pinentry.
     *
     * Gitnuro never keeps the passphrase: gpg-agent caches it (in locked memory, expiring after default-cache-ttl),
     * so the first attempt is always done without one and it's only asked when the agent doesn't have it.
     */
    private fun signWithGpgBinary(gpg: String, data: ByteArray, keyId: String?): GpgSignature {
        val dataFile = File.createTempFile("gitnuro-sign", ".tmp")

        try {
            dataFile.writeBytes(data)

            var passphrase: String? = null
            var hasAskedPassphrase = false

            while (true) {
                val args = buildList {
                    addAll(listOf("--batch", "--no-tty", "--status-fd", "2"))
                    addAll(listOf("--pinentry-mode", "loopback", "--passphrase-fd", "0"))
                    addAll(listOf("--armor", "--detach-sign"))
                    if (keyId != null) addAll(listOf("--local-user", keyId))
                    addAll(listOf("--output", "-", dataFile.absolutePath))
                }

                val result = GpgBinary.run(gpg, args, stdin = (passphrase.orEmpty() + "\n").toByteArray())

                if (result.isSuccess && result.output.isNotEmpty()) {
                    return GpgSignature(result.output)
                }

                if (!needsPassphrase(result)) {
                    throw JGitInternalException("GPG signing failed: ${GpgBinary.readableError(result.error)}")
                }

                passphrase = try {
                    runBlocking {
                        credentialsStateManager.requestGpgCredentials(
                            isRetry = hasAskedPassphrase,
                            password = "",
                        )
                    }.password
                } catch (ex: CancellationException) {
                    throw CanceledException("Signing cancelled")
                }

                hasAskedPassphrase = true
            }
        } finally {
            dataFile.delete()
        }
    }

    private fun needsPassphrase(result: GpgBinary.Result): Boolean {
        val error = result.error

        if (error.contains("[GNUPG:] BAD_PASSPHRASE") || error.contains("[GNUPG:] MISSING_PASSPHRASE")) {
            return true
        }

        // gpg's messages are localized, so rely on the error codes of the status lines instead:
        // "[GNUPG:] FAILURE sign 67109041" / "[GNUPG:] ERROR pkdecrypt_failed 67108875"
        return error.lines()
            .filter { it.startsWith("[GNUPG:] FAILURE") || it.startsWith("[GNUPG:] ERROR") }
            .mapNotNull { it.trim().split(" ").lastOrNull()?.toLongOrNull() }
            .any { code -> (code and 0xFFFF) in PASSPHRASE_ERROR_CODES }
    }

    override fun signObject(
        repository: Repository?,
        config: GpgConfig?,
        `object`: ObjectBuilder?,
        committer: PersonIdent?,
        signingKey: String?,
        credentialsProvider: CredentialsProvider?,
    ) {
        val gpgCredentialsProvider = gpgCredentials

        try {
            retryIfWrongPassphrase { isRetry ->
                gpgCredentialsProvider.isRetry = isRetry
                super.signObject(repository, config, `object`, committer, signingKey.normalizeKeyId(), credentialsProvider)
                gpgCredentialsProvider.savePasswordInMemory()
            }
        } catch (ex: CanceledException) {
            println("Signing cancelled")
        }
    }

}

/**
 * Git accepts a trailing "!" in user.signingkey to force the exact (sub)key, but JGit uses the value verbatim
 * when looking for the key, so it never matches. JGit already signs with the exact subkey whose id is given.
 */
private fun String?.normalizeKeyId(): String? = this?.trim()?.removeSuffix("!")

fun retryIfWrongPassphrase(block: (isRetry: Boolean) -> Unit) {
    var isPasswordCorrect = false
    var retries = 0

    while (!isPasswordCorrect) {
        isPasswordCorrect = true

        try {
            block(retries > 0)
        } catch (ex: Exception) {
            ex.printStackTrace()

            val pgpException = getPgpExceptionTypeOrNull(ex)
            val pgpMessage = pgpException?.message

            if (pgpMessage != null && pgpMessage.contains(INVALID_PASSWORD_MESSAGE)) {
                isPasswordCorrect = false // Only set it to false if we've got this specific message
                retries++
            } else
                throw ex
        }
    }
}

private fun getPgpExceptionTypeOrNull(ex: Throwable?): PGPException? {
    var currentException: Throwable? = ex

    while (currentException != null) {
        if (currentException is PGPException) {
            return currentException
        } else {
            currentException = when (currentException.cause) {
                currentException -> null // Cause can be the same as current exception, so just return null
                else -> currentException.cause
            }
        }
    }

    return null
}