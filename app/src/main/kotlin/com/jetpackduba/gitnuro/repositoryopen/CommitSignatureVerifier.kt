package com.jetpackduba.gitnuro.repositoryopen

import com.jetpackduba.gitnuro.common.ProcessEnvironment

import com.jetpackduba.gitnuro.domain.IShellManager
import com.jetpackduba.gitnuro.domain.UseCaseExecutor
import com.jetpackduba.gitnuro.domain.errors.Either
import com.jetpackduba.gitnuro.domain.errors.okOrNull
import com.jetpackduba.gitnuro.domain.interfaces.IGetWorktreePathGitAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.thread

enum class SignatureState {
    /** Valid signature */
    GOOD,

    /** Signature can't be fully checked (unknown/expired key, missing public key...) */
    UNKNOWN,

    /** Bad signature or revoked key */
    BAD,
}

enum class SignatureType { GPG, SSH }

data class CommitSignature(
    val commitHash: String,
    val type: SignatureType,
    val state: SignatureState,
    /** gpg's status keyword, like GitKraken shows it (GOODSIG, BADSIG, ERRSIG...). Empty for SSH */
    val keyword: String,
    val description: String,
    /** Only for SSH signatures (RSA, ED25519...) */
    val keyType: String = "",
    val keyId: String,
    val trust: String = "",
    val signer: String = "",
)

private const val FIELD_SEPARATOR = "\u001f"
private const val SSH_SIGNATURE_BEGIN = "-----BEGIN SSH SIGNATURE-----"

/**
 * Verifies commit signatures with the git CLI, so it uses the same gpg/ssh setup as git itself.
 */
class CommitSignatureVerifier @Inject constructor(
    private val useCaseExecutor: UseCaseExecutor,
    private val shellManager: IShellManager,
    private val getWorktreePathGitAction: IGetWorktreePathGitAction,
) {
    /**
     * Returns null when the commit is not signed (or it couldn't be checked at all).
     */
    suspend fun verify(commitHash: String): CommitSignature? {
        return useCaseExecutor.execute { gitDirectory ->
            val worktree = getWorktreePathGitAction(gitDirectory).okOrNull() ?: gitDirectory
            Either.Ok(withContext(Dispatchers.IO) { readSignature(worktree, commitHash) })
        }.okOrNull()
    }

    private fun readSignature(worktree: String, commitHash: String): CommitSignature? {
        // %G? status, %GK key used, %GP primary key fingerprint, %GF key fingerprint, %GT trust, %GS signer
        val format = listOf("%G?", "%GK", "%GP", "%GF", "%GT", "%GS").joinToString("%x1f")
        val output = runGit(worktree, listOf("log", "-1", "--format=$format", commitHash))?.decodeToString()?.trim()
            ?: return null

        val fields = output.split(FIELD_SEPARATOR)
        val code = fields.getOrNull(0).orEmpty()

        val rawCommit = runGit(worktree, listOf("cat-file", "commit", commitHash))
        val sshSignature = rawCommit?.let { extractSignature(it.decodeToString()) }
            ?.takeIf { it.signature.contains(SSH_SIGNATURE_BEGIN) }

        if (sshSignature != null) {
            return readSshSignature(commitHash, code, fields, sshSignature)
        }

        val key = fields.getOrNull(1).orEmpty()
        val primaryFingerprint = fields.getOrNull(2).orEmpty()
        val fingerprint = fields.getOrNull(3).orEmpty()

        val (state, keyword, description) = when (code) {
            "G" -> Triple(SignatureState.GOOD, "GOODSIG", "Verification completed successfully")
            "U" -> Triple(SignatureState.GOOD, "GOODSIG", "Good signature, but the key's validity is unknown")
            "X" -> Triple(SignatureState.UNKNOWN, "EXPSIG", "Good signature, but the signature has expired")
            "Y" -> Triple(SignatureState.UNKNOWN, "EXPKEYSIG", "Good signature, but the key has expired")
            "E" -> Triple(SignatureState.UNKNOWN, "ERRSIG", "The signature can't be checked (missing public key?)")
            "R" -> Triple(SignatureState.BAD, "REVKEYSIG", "The signature was made by a revoked key")
            "B" -> Triple(SignatureState.BAD, "BADSIG", "The signature is not valid")
            else -> return null // "N": not signed
        }

        return CommitSignature(
            commitHash = commitHash,
            type = SignatureType.GPG,
            state = state,
            keyword = keyword,
            description = description,
            keyId = primaryFingerprint.ifEmpty { fingerprint }.ifEmpty { key },
            trust = fields.getOrNull(4).orEmpty().uppercase(),
            signer = fields.getOrNull(5).orEmpty(),
        )
    }

    private fun readSshSignature(
        commitHash: String,
        gitCode: String,
        fields: List<String>,
        signature: ExtractedSignature,
    ): CommitSignature {
        val keyInfo = parseSshSignatureKey(signature.signature)
        val signer = fields.getOrNull(5).orEmpty()

        // When gpg.ssh.allowedSignersFile is configured git verifies it, otherwise it reports "N" (not signed)
        val (state, description) = when (gitCode) {
            "G" -> SignatureState.GOOD to "Verification completed successfully"
            "B" -> SignatureState.BAD to "The signature is not valid"
            "N", "E", "U" -> when (checkSshSignatureWithoutTrust(signature)) {
                true -> SignatureState.UNKNOWN to "Verification failed because the signature is valid but unknown"
                false -> SignatureState.BAD to "The signature is not valid"
                null -> SignatureState.UNKNOWN to "The signature can't be checked"
            }

            else -> SignatureState.UNKNOWN to "The signature can't be checked"
        }

        return CommitSignature(
            commitHash = commitHash,
            type = SignatureType.SSH,
            state = state,
            keyword = "",
            description = description,
            keyType = keyInfo?.type.orEmpty(),
            keyId = keyInfo?.fingerprint ?: fields.getOrNull(1).orEmpty(),
            signer = signer,
        )
    }

    /**
     * Checks that the signature matches the commit, without checking who signed it (that requires the allowed
     * signers file). Returns null if ssh-keygen isn't available.
     */
    private fun checkSshSignatureWithoutTrust(signature: ExtractedSignature): Boolean? {
        val signatureFile = File.createTempFile("gitnuro-sshsig", ".sig")

        return try {
            signatureFile.writeText(signature.signature + "\n")

            val process = ProcessEnvironment.processBuilder(
                "ssh-keygen", "-Y", "check-novalidate", "-n", "git", "-s", signatureFile.absolutePath,
            ).start()

            val drainOutput = thread { process.inputStream.readBytes() }
            val drainError = thread { process.errorStream.readBytes() }
            process.outputStream.use { it.write(signature.payload.toByteArray()) }

            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }

            drainOutput.join()
            drainError.join()
            process.exitValue() == 0
        } catch (ex: Exception) {
            null
        } finally {
            signatureFile.delete()
        }
    }

    private class ExtractedSignature(val signature: String, val payload: String)

    /**
     * Splits a raw commit object in its signature ("gpgsig" header) and the signed payload (the commit without it).
     */
    private fun extractSignature(rawCommit: String): ExtractedSignature? {
        val headersEnd = rawCommit.indexOf("\n\n").takeIf { it >= 0 } ?: rawCommit.length
        val headers = rawCommit.substring(0, headersEnd).split("\n")
        val body = rawCommit.substring(headersEnd)

        val signatureLines = mutableListOf<String>()
        val payloadHeaders = mutableListOf<String>()
        var inSignature = false

        for (line in headers) {
            when {
                line.startsWith("gpgsig ") || line.startsWith("gpgsig-sha256 ") -> {
                    inSignature = true
                    signatureLines.add(line.substringAfter(" "))
                }

                inSignature && line.startsWith(" ") -> signatureLines.add(line.substring(1))
                else -> {
                    inSignature = false
                    payloadHeaders.add(line)
                }
            }
        }

        if (signatureLines.isEmpty()) return null

        return ExtractedSignature(
            signature = signatureLines.joinToString("\n"),
            payload = payloadHeaders.joinToString("\n") + body,
        )
    }

    private class SshKeyInfo(val type: String, val fingerprint: String)

    /**
     * Reads the public key embedded in an SSHSIG signature
     * (https://github.com/openssh/openssh-portable/blob/master/PROTOCOL.sshsig).
     */
    private fun parseSshSignatureKey(armoredSignature: String): SshKeyInfo? {
        return try {
            val base64 = armoredSignature.lines()
                .filter { it.isNotBlank() && !it.startsWith("-----") }
                .joinToString("")
            val buffer = ByteBuffer.wrap(Base64.getDecoder().decode(base64))

            val magic = ByteArray(6).also { buffer.get(it) }
            if (magic.decodeToString() != "SSHSIG") return null

            buffer.int // version
            val publicKey = buffer.readSshString()
            val keyType = ByteBuffer.wrap(publicKey).readSshString().decodeToString()

            val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
            val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)

            SshKeyInfo(type = sshKeyTypeName(keyType), fingerprint = fingerprint)
        } catch (ex: Exception) {
            null
        }
    }

    private fun ByteBuffer.readSshString(): ByteArray {
        val length = int
        return ByteArray(length).also { get(it) }
    }

    private fun sshKeyTypeName(keyType: String): String = when {
        keyType == "ssh-rsa" -> "RSA"
        keyType == "ssh-ed25519" -> "ED25519"
        keyType.startsWith("ecdsa-sha2-") -> "ECDSA"
        keyType.startsWith("sk-ssh-ed25519") -> "ED25519-SK"
        keyType.startsWith("sk-ecdsa") -> "ECDSA-SK"
        keyType == "ssh-dss" -> "DSA"
        else -> keyType
    }

    private fun runGit(worktree: String, args: List<String>): ByteArray? {
        return try {
            val process = shellManager.runCommandProcess(listOf("git", "-C", worktree) + args)
            val output = ByteArrayOutputStream()
            val drainError = thread { process.errorStream.readBytes() }
            process.inputStream.copyTo(output)
            drainError.join()

            if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) output.toByteArray() else null
        } catch (ex: Exception) {
            null
        }
    }
}
