package com.jetpackduba.gitnuro.data.git.signers

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Helpers to work with the system's GnuPG binary (the same one git uses), instead of reading the keyrings with
 * BouncyCastle. Using the binary supports every GnuPG setup (keyboxd, smartcards, gpg-agent cache...).
 */
object GpgBinary {
    /**
     * Apps launched from the desktop don't get the shell's PATH, so look in the usual install locations too.
     */
    private val extraSearchDirectories = listOf(
        "/opt/homebrew/bin",
        "/usr/local/bin",
        "/usr/local/MacGPG2/bin",
        "/usr/bin",
        "C:\\Program Files (x86)\\GnuPG\\bin",
        "C:\\Program Files\\GnuPG\\bin",
        "C:\\Program Files\\Git\\usr\\bin",
    )

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /**
     * Returns the absolute path of the gpg program (by default "gpg") or null if it can't be found.
     */
    fun resolve(program: String?): String? {
        val name = program?.trim().takeUnless { it.isNullOrEmpty() } ?: "gpg"
        val file = File(name)

        if (file.isAbsolute || name.contains(File.separatorChar)) {
            return file.takeIf { it.isFile && it.canExecute() }?.absolutePath
        }

        val directories = System.getenv("PATH").orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() } + extraSearchDirectories

        val candidates = if (isWindows && !name.endsWith(".exe")) listOf(name, "$name.exe") else listOf(name)

        return directories
            .asSequence()
            .flatMap { directory -> candidates.asSequence().map { File(directory, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    class Result(val exitCode: Int, val output: ByteArray, val error: String) {
        val isSuccess get() = exitCode == 0
        val outputText get() = output.decodeToString()
    }

    fun run(gpg: String, args: List<String>, stdin: ByteArray? = null, timeoutSeconds: Long = 120): Result {
        val process = ProcessBuilder(listOf(gpg) + args).start()

        var output = ByteArray(0)
        var error = ""
        val outputReader = thread { output = process.inputStream.readBytes() }
        val errorReader = thread { error = process.errorStream.bufferedReader().readText() }

        process.outputStream.use { stream ->
            if (stdin != null) {
                stream.write(stdin)
            }
        }

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            outputReader.join(1_000)
            errorReader.join(1_000)
            return Result(-1, output, "gpg did not finish after $timeoutSeconds seconds")
        }

        outputReader.join()
        errorReader.join()

        return Result(process.exitValue(), output, error)
    }

    /**
     * Error lines printed by gpg, without the "[GNUPG:]" status lines.
     */
    fun readableError(error: String): String {
        return error.lines()
            .filter { it.isNotBlank() && !it.startsWith("[GNUPG:]") }
            .joinToString("\n")
            .ifBlank { "gpg failed" }
    }

    data class SecretKey(
        val keyId: String,
        val fingerprint: String,
        val userId: String,
        val isSubkey: Boolean,
        val algorithm: String,
    ) {
        val displayName: String
            get() = if (isSubkey) "$userId · subkey $keyId ($algorithm)" else "$userId · $keyId ($algorithm)"
    }

    /**
     * Lists the secret keys (and subkeys) that can be used to sign and aren't expired or revoked.
     */
    fun listSigningKeys(gpg: String): List<SecretKey> {
        val result = run(gpg, listOf("--batch", "--with-colons", "--fixed-list-mode", "--list-secret-keys"))

        if (!result.isSuccess) {
            return emptyList()
        }

        val keys = mutableListOf<SecretKey>()
        var primaryUserId = ""
        var pending: SecretKey? = null
        var pendingCanSign = false

        fun flush() {
            val key = pending
            if (key != null && pendingCanSign) {
                keys.add(key.copy(userId = primaryUserId))
            }
            pending = null
        }

        for (line in result.outputText.lines()) {
            val fields = line.split(":")
            when (fields.firstOrNull()) {
                "sec", "ssb" -> {
                    flush()
                    if (fields.first() == "sec") {
                        primaryUserId = ""
                    }

                    val validity = fields.getOrNull(1).orEmpty()
                    val capabilities = fields.getOrNull(11).orEmpty()
                    pendingCanSign = capabilities.contains('s') && validity != "e" && validity != "r"
                    pending = SecretKey(
                        keyId = fields.getOrNull(4).orEmpty(),
                        fingerprint = "",
                        userId = "",
                        isSubkey = fields.first() == "ssb",
                        algorithm = algorithmName(fields.getOrNull(3).orEmpty(), fields.getOrNull(2).orEmpty()),
                    )
                }

                "fpr" -> pending = pending?.let { key ->
                    if (key.fingerprint.isEmpty()) key.copy(fingerprint = fields.getOrNull(9).orEmpty()) else key
                }

                "uid" -> if (primaryUserId.isEmpty()) {
                    primaryUserId = unescape(fields.getOrNull(9).orEmpty())
                }
            }
        }

        // Keys are added when the next key starts (or here), once their user id has been read
        flush()

        return keys
    }

    fun exportPublicKey(gpg: String, keyId: String): Result {
        return run(gpg, listOf("--batch", "--armor", "--export", keyId))
    }

    /**
     * Creates a 4096 bit RSA signing key that expires in 2 years. Returns the fingerprint of the new key.
     */
    fun generateKey(gpg: String, userId: String, passphrase: String): Result {
        return run(
            gpg,
            listOf(
                "--batch",
                "--status-fd", "2",
                "--pinentry-mode", "loopback",
                "--passphrase-fd", "0",
                "--quick-gen-key", userId, "rsa4096", "sign", "2y",
            ),
            stdin = (passphrase + "\n").toByteArray(),
        )
    }

    fun createdKeyFingerprint(result: Result): String? {
        // [GNUPG:] KEY_CREATED P <fingerprint>
        return result.error.lines()
            .firstOrNull { it.startsWith("[GNUPG:] KEY_CREATED") }
            ?.split(" ")
            ?.lastOrNull()
    }

    private fun algorithmName(algorithm: String, length: String): String {
        return when (algorithm) {
            "1", "2", "3" -> "rsa$length"
            "17" -> "dsa$length"
            "19" -> "ecdsa"
            "22" -> "ed25519"
            else -> "algo $algorithm"
        }
    }

    /** gpg escapes some characters in colon listings as \xNN */
    private fun unescape(value: String): String {
        return Regex("""\\x([0-9a-fA-F]{2})""").replace(value) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
    }
}
