package com.jetpackduba.gitnuro.ui.dialogs.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.refresh
import com.jetpackduba.gitnuro.data.git.signers.GpgBinary
import com.jetpackduba.gitnuro.extensions.handMouseClickable
import com.jetpackduba.gitnuro.extensions.setClipboardText
import com.jetpackduba.gitnuro.theme.onBackgroundSecondary
import com.jetpackduba.gitnuro.ui.components.AdjustableOutlinedTextField
import com.jetpackduba.gitnuro.ui.components.AppSwitch
import com.jetpackduba.gitnuro.ui.components.PrimaryButton
import com.jetpackduba.gitnuro.ui.components.ScrollableColumn
import com.jetpackduba.gitnuro.ui.context_menu.ContextMenuElement
import com.jetpackduba.gitnuro.ui.context_menu.DropDownMenuText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.concurrent.TimeUnit

private const val FORMAT_OPENPGP = "openpgp"
private const val FORMAT_SSH = "ssh"
private val CONTROL_WIDTH = 340.dp

/**
 * Global git configuration used for signing (the same values git and other clients read).
 */
private data class SigningConfig(
    val format: String = FORMAT_OPENPGP,
    val program: String = "",
    val signingKey: String = "",
    val signCommits: Boolean = false,
    val signTags: Boolean = false,
    val userName: String = "",
    val userEmail: String = "",
)

@Composable
fun CommitSigning() {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    var config by remember { mutableStateOf<SigningConfig?>(null) }
    var programField by remember { mutableStateOf("") }
    var keys by remember { mutableStateOf<List<GpgBinary.SecretKey>>(emptyList()) }
    var gpgPath by remember { mutableStateOf<String?>(null) }
    var newKeyPassphrase by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text to isError
    var isBusy by remember { mutableStateOf(false) }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) { readSigningConfig() }
        val resolvedGpg = GpgBinary.resolve(loaded.program)
        val loadedKeys = withContext(Dispatchers.IO) {
            if (resolvedGpg != null) GpgBinary.listSigningKeys(resolvedGpg) else emptyList()
        }

        config = loaded
        programField = loaded.program
        gpgPath = resolvedGpg
        keys = loadedKeys
    }

    fun setConfig(key: String, value: String?) = scope.launch {
        val error = withContext(Dispatchers.IO) { writeGlobalConfig(key, value) }
        message = error?.let { it to true }
        reload()
    }

    LaunchedEffect(Unit) { reload() }

    val currentConfig = config ?: return

    ScrollableColumn(modifier = Modifier.fillMaxSize()) {
        // GPG Format
        SigningRow("GPG Format", "Sign with an OpenPGP key or with an SSH key") {
            DropDownMenuText(
                showIcons = false,
                width = CONTROL_WIDTH,
                currentValue = currentConfig.format.uppercase(),
                items = {
                    listOf(FORMAT_OPENPGP, FORMAT_SSH).map { format ->
                        ContextMenuElement.ContextTextEntry(
                            label = format.uppercase(),
                            onClick = { setConfig("gpg.format", format.takeIf { it != FORMAT_OPENPGP }) },
                        )
                    }
                },
            )
        }

        if (currentConfig.format == FORMAT_OPENPGP) {
            // GPG Program
            SigningRow(
                title = "GPG Program",
                subtitle = "This is usually \"gpg\" or \"gpg2\" depending on the version you are using. " +
                        "You can also browse for the path directly.",
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdjustableOutlinedTextField(
                            value = programField,
                            onValueChange = { programField = it },
                            singleLine = true,
                            maxLines = 1,
                            hint = "gpg",
                            modifier = Modifier.weight(1f),
                        )

                        PrimaryButton(
                            text = "Save",
                            enabled = programField.trim() != currentConfig.program,
                            modifier = Modifier.padding(start = 8.dp),
                            onClick = { setConfig("gpg.program", programField.trim().ifEmpty { null }) },
                        )

                        PrimaryButton(
                            text = "Browse",
                            modifier = Modifier.padding(start = 8.dp),
                            onClick = {
                                scope.launch {
                                    val path = chooseFile("Select the GPG program")
                                    if (path != null) {
                                        programField = path
                                        setConfig("gpg.program", path)
                                    }
                                }
                            },
                        )
                    }

                    Text(
                        text = gpgPath?.let { "Using $it" } ?: "gpg not found. Gitnuro will use its built-in signer.",
                        style = MaterialTheme.typography.caption,
                        color = if (gpgPath != null) MaterialTheme.colors.onBackgroundSecondary else MaterialTheme.colors.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Signing Key
            SigningRow("Signing Key", "Key used to sign commits and tags") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val selectedKey = keys.firstOrNull { it.matches(currentConfig.signingKey) }
                        val currentLabel = when {
                            selectedKey != null -> selectedKey.displayName
                            currentConfig.signingKey.isNotEmpty() -> currentConfig.signingKey
                            else -> "No signing key"
                        }

                        DropDownMenuText(
                            showIcons = false,
                            width = CONTROL_WIDTH - 40.dp,
                            currentValue = currentLabel,
                            items = {
                                keys.map { key ->
                                    ContextMenuElement.ContextTextEntry(
                                        label = key.displayName,
                                        // Like git, "!" forces the exact subkey instead of the primary key
                                        onClick = { setConfig("user.signingkey", key.keyId + if (key.isSubkey) "!" else "") },
                                    )
                                }
                            },
                        )

                        Icon(
                            painter = painterResource(Res.drawable.refresh),
                            contentDescription = "Reload keys",
                            tint = MaterialTheme.colors.onBackground,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(20.dp)
                                .handMouseClickable { scope.launch { reload() } },
                        )
                    }

                    val gpg = gpgPath
                    val keyId = currentConfig.signingKey.removeSuffix("!")

                    if (gpg != null && keyId.isNotEmpty()) {
                        LinkText("Copy GPG Public Key") {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { GpgBinary.exportPublicKey(gpg, keyId) }

                                message = if (result.isSuccess && result.output.isNotEmpty()) {
                                    clipboard.setClipboardText(result.outputText)
                                    "Public key copied to the clipboard" to false
                                } else {
                                    GpgBinary.readableError(result.error) to true
                                }
                            }
                        }
                    }
                }
            }

            // Generate new GPG Key
            SigningRow(
                title = "Generate new GPG Key",
                subtitle = "This will create a 4096 bit key with the name and email of your git configuration " +
                        "(${currentConfig.userName} <${currentConfig.userEmail}>) that expires in 2 years. " +
                        "It will also be set as your active signing key.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AdjustableOutlinedTextField(
                        value = newKeyPassphrase,
                        onValueChange = { newKeyPassphrase = it },
                        singleLine = true,
                        maxLines = 1,
                        hint = "Passphrase (optional)",
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )

                    PrimaryButton(
                        text = if (isBusy) "Generating…" else "Generate",
                        enabled = !isBusy && gpgPath != null &&
                                currentConfig.userName.isNotBlank() && currentConfig.userEmail.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = {
                            val gpg = gpgPath ?: return@PrimaryButton
                            isBusy = true

                            scope.launch {
                                val userId = "${currentConfig.userName} <${currentConfig.userEmail}>"
                                val result = withContext(Dispatchers.IO) {
                                    GpgBinary.generateKey(gpg, userId, newKeyPassphrase)
                                }
                                val fingerprint = GpgBinary.createdKeyFingerprint(result)

                                if (result.isSuccess && fingerprint != null) {
                                    newKeyPassphrase = ""
                                    message = "New key created: $fingerprint" to false
                                    setConfig("user.signingkey", fingerprint)
                                } else {
                                    message = GpgBinary.readableError(result.error) to true
                                }

                                isBusy = false
                            }
                        },
                    )
                }
            }
        } else {
            // SSH signing key (path to the public key)
            SigningRow("Signing Key", "Path to the SSH public key (.pub) used to sign") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentConfig.signingKey.ifEmpty { "No signing key" },
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onBackground,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )

                    PrimaryButton(
                        text = "Browse",
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = {
                            scope.launch {
                                val path = chooseFile(
                                    "Select your SSH public key",
                                    File(System.getProperty("user.home"), ".ssh").absolutePath,
                                )
                                if (path != null) setConfig("user.signingkey", path)
                            }
                        },
                    )
                }
            }
        }

        SigningRow("Sign Commits by Default", "commit.gpgsign") {
            AppSwitch(
                isChecked = currentConfig.signCommits,
                onValueChanged = { setConfig("commit.gpgsign", if (it) "true" else null) },
            )
        }

        SigningRow("Sign Tags by Default", "tag.gpgsign") {
            AppSwitch(
                isChecked = currentConfig.signTags,
                onValueChanged = { setConfig("tag.gpgsign", if (it) "true" else null) },
            )
        }

        message?.let { (text, isError) ->
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                color = if (isError) MaterialTheme.colors.error else MaterialTheme.colors.onBackgroundSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SigningRow(
    title: String,
    subtitle: String,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            FieldTitles(title, subtitle)
        }

        Box(modifier = Modifier.width(CONTROL_WIDTH), contentAlignment = Alignment.CenterStart) {
            control()
        }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.primaryVariant,
        modifier = Modifier
            .padding(top = 6.dp)
            .handMouseClickable(onClick = onClick),
    )
}

private fun GpgBinary.SecretKey.matches(configuredKey: String): Boolean {
    val key = configuredKey.removeSuffix("!").removePrefix("0x").uppercase()
    if (key.isEmpty()) return false

    return keyId.uppercase() == key || fingerprint.uppercase() == key || fingerprint.uppercase().endsWith(key)
}

private suspend fun chooseFile(title: String, directory: String? = null): String? = withContext(Dispatchers.Main) {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
        if (directory != null) this.directory = directory
        isVisible = true
    }

    val dir = dialog.directory
    val file = dialog.file

    if (dir != null && file != null) File(dir, file).absolutePath else null
}

private fun readSigningConfig(): SigningConfig {
    return SigningConfig(
        format = readGlobalConfig("gpg.format")?.lowercase() ?: FORMAT_OPENPGP,
        program = readGlobalConfig("gpg.program").orEmpty(),
        signingKey = readGlobalConfig("user.signingkey").orEmpty(),
        signCommits = readGlobalConfig("commit.gpgsign").toGitBoolean(),
        signTags = readGlobalConfig("tag.gpgsign").toGitBoolean(),
        userName = readGlobalConfig("user.name").orEmpty(),
        userEmail = readGlobalConfig("user.email").orEmpty(),
    )
}

private fun String?.toGitBoolean(): Boolean = this?.lowercase() in setOf("true", "yes", "on", "1")

private fun gitBinary(): String = GpgBinary.resolve("git") ?: "git"

private fun readGlobalConfig(key: String): String? {
    return try {
        val process = ProcessBuilder(gitBinary(), "config", "--global", "--get", key).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(10, TimeUnit.SECONDS)
        output.takeIf { process.exitValue() == 0 && it.isNotEmpty() }
    } catch (ex: Exception) {
        null
    }
}

/**
 * Sets (or unsets when [value] is null) a key in the global git config. Returns an error message if it fails.
 */
private fun writeGlobalConfig(key: String, value: String?): String? {
    val args = if (value == null) {
        listOf(gitBinary(), "config", "--global", "--unset", key)
    } else {
        listOf(gitBinary(), "config", "--global", key, value)
    }

    return try {
        val process = ProcessBuilder(args).start()
        val error = process.errorStream.bufferedReader().readText().trim()
        process.waitFor(10, TimeUnit.SECONDS)

        // Exit code 5 when unsetting a key that doesn't exist
        if (process.exitValue() == 0 || (value == null && process.exitValue() == 5)) null
        else error.ifEmpty { "Could not update $key" }
    } catch (ex: Exception) {
        ex.message ?: "Could not update $key"
    }
}
