package com.jetpackduba.gitnuro.data.git.credentials

import com.jetpackduba.gitnuro.Session
import com.jetpackduba.gitnuro.domain.credentials.SshProcess
import com.jetpackduba.gitnuro.domain.exceptions.SshException
import com.jetpackduba.gitnuro.domain.extensions.throwIfSshMessage
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteSession
import org.eclipse.jgit.transport.URIish
import javax.inject.Inject


private const val NOT_EXPLICIT_PORT = -1

class SshRemoteSession @Inject constructor() : RemoteSession {
    private lateinit var session: Session
    private lateinit var process: SshProcess
    override fun exec(commandName: String, timeout: Int): Process {
        println("Running command $commandName")

        process = SshProcess()

        process.setup(session, commandName)

        return process
    }

    override fun disconnect() {
        process.closeChannel()
        session.disconnect()
        session.close()
    }

    fun setup(uri: URIish, sshCredentialsProvider: CredentialsProvider) {
        val session = Session()
            ?: throw SshException("Could not obtain the session, this is likely a bug. Please file a report.")

        val port = if (uri.port == NOT_EXPLICIT_PORT) {
            null
        } else
            uri.port

        session.setup(uri.host, uri.user ?: "", port).throwIfSshMessage()

        loadKeychainKeysIntoAgent()

        // libssh tries the ssh-agent first and then the default keys
        var result = session.publicKeyAuth("")

        if (result == 2) {//AuthStatus.DENIED) {
            val passwordCredentialItem = CredentialItem.Password()
            sshCredentialsProvider.get(uri, passwordCredentialItem)

            val password = passwordCredentialItem.value.joinToString("")

            result = session.publicKeyAuth(password)

            if (result != 1) {//AuthStatus.SUCCESS) {
                result = session.passwordAuth(password)
            }
        }

        if (result != 1) {//AuthStatus.SUCCESS)
            throw Exception("Something went wrong with authentication. Code $result")
        }

        this.session = session
    }
}

/**
 * On macOS, keys whose passphrase was saved in the Keychain (ssh "UseKeychain yes") are only added to the agent by
 * Apple's ssh when it's used. libssh can't read the Keychain, so ask Apple's ssh-add to load them into the agent
 * (it doesn't prompt for anything). This way, like with the terminal or GitKraken, the passphrase isn't asked.
 */
private fun loadKeychainKeysIntoAgent() {
    val isMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    val sshAdd = java.io.File("/usr/bin/ssh-add")

    if (!isMac || !sshAdd.canExecute() || System.getenv("SSH_AUTH_SOCK").isNullOrEmpty()) {
        return
    }

    try {
        val process = ProcessBuilder(sshAdd.absolutePath, "-q", "--apple-load-keychain")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()

        process.outputStream.close()

        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    } catch (ex: Exception) {
        println("Loading the Keychain SSH keys into the agent failed: ${ex.message}")
    }
}
