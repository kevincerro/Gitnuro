package com.jetpackduba.gitnuro.common

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val TAG = "ProcessEnvironment"
private const val PATH_MARKER = "__GITNURO_PATH__"

/**
 * Environment for every external program the app runs (git, gpg, git-lfs, hooks, ssh-keygen...).
 *
 * Apps launched from Finder/the Dock (or a desktop launcher) don't get the shell's environment, only a minimal PATH
 * like "/usr/bin:/bin:/usr/sbin:/sbin", so programs installed with Homebrew & co. aren't found. That's why things
 * that work when running the app from a terminal fail in the packaged app. The PATH of the user's login shell is read
 * once and used for every process.
 */
object ProcessEnvironment {
    /** Usual install locations, in case the login shell doesn't add them */
    private val extraDirectories = listOf(
        "/opt/homebrew/bin",
        "/opt/homebrew/sbin",
        "/usr/local/bin",
        "/usr/local/MacGPG2/bin",
        "/usr/bin",
        "/bin",
        "C:\\Program Files\\Git\\cmd",
        "C:\\Program Files (x86)\\GnuPG\\bin",
        "C:\\Program Files\\GnuPG\\bin",
        "C:\\Program Files\\Git\\usr\\bin",
    )

    private val isWindows = currentOs.isWindows()

    val path: String by lazy {
        val directories = System.getenv("PATH").orEmpty().split(File.pathSeparator) +
                loginShellPath().orEmpty().split(File.pathSeparator) +
                extraDirectories

        directories
            .filter { it.isNotBlank() && File(it).isDirectory }
            .distinct()
            .joinToString(File.pathSeparator)
    }

    /** Reads the login shell's PATH in the background, so the first process doesn't wait for it */
    fun preload() {
        thread(isDaemon = true, name = "ProcessEnvironment") { path }
    }

    /**
     * Absolute path of [program] (a name like "git" or a path), or null if it can't be found.
     */
    fun resolve(program: String): String? {
        val file = File(program)

        if (file.isAbsolute || program.contains(File.separatorChar)) {
            return file.takeIf { it.isFile && it.canExecute() }?.absolutePath
        }

        val candidates = if (isWindows && !program.endsWith(".exe")) listOf(program, "$program.exe") else listOf(program)

        return path.split(File.pathSeparator)
            .asSequence()
            .flatMap { directory -> candidates.asSequence().map { File(directory, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    /**
     * ProcessBuilder with the full PATH. The program is resolved here because Java looks it up in the JVM's own
     * (minimal) PATH, not in the one given to the process.
     */
    fun processBuilder(command: List<String>): ProcessBuilder {
        val program = command.firstOrNull()?.let { resolve(it) ?: it }
        return apply(ProcessBuilder(listOfNotNull(program) + command.drop(1)))
    }

    fun processBuilder(vararg command: String) = processBuilder(command.toList())

    /** Gives the full PATH to a ProcessBuilder created somewhere else (JGit's hooks and filters) */
    fun apply(processBuilder: ProcessBuilder): ProcessBuilder {
        processBuilder.environment()["PATH"] = path
        return processBuilder
    }

    private fun loginShellPath(): String? {
        if (isWindows) return null

        val shell = System.getenv("SHELL")?.takeIf { File(it).canExecute() } ?: "/bin/sh"

        return try {
            // Interactive too, many setups only add Homebrew's PATH in .zshrc/.bashrc. The markers skip anything
            // the shell's startup files print.
            val process = ProcessBuilder(shell, "-l", "-i", "-c", "printf '$PATH_MARKER%s$PATH_MARKER' \"\$PATH\"")
                .redirectErrorStream(true)
                .start()

            process.outputStream.close()
            var output = ""
            val reader = thread(isDaemon = true) { output = process.inputStream.bufferedReader().readText() }

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                printError(TAG, "The login shell took too long to report its PATH")
                return null
            }

            reader.join(1_000)
            output.substringAfter(PATH_MARKER, "").substringBefore(PATH_MARKER, "").ifBlank { null }
        } catch (ex: Exception) {
            printError(TAG, "Couldn't read the login shell's PATH: ${ex.message}")
            null
        }
    }
}
