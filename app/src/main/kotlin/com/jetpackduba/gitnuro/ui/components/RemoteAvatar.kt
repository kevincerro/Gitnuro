package com.jetpackduba.gitnuro.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.jetpackduba.gitnuro.LocalAvatarProvider
import com.jetpackduba.gitnuro.app.generated.resources.Res
import com.jetpackduba.gitnuro.app.generated.resources.cloud
import com.jetpackduba.gitnuro.avatarproviders.NoneAvatarProvider
import org.jetbrains.compose.resources.painterResource

/**
 * Avatar of each remote's owner by remote name, like GitKraken shows next to remote branches.
 */
val LocalRemoteAvatars = compositionLocalOf<Map<String, String>> { emptyMap() }

private val githubRemoteRegex = Regex("""github\.com[:/]+([^/]+)/""", RegexOption.IGNORE_CASE)

/**
 * Avatar URL of the owner of a GitHub remote (https or ssh URL), or null for other hosts.
 */
fun remoteOwnerAvatarUrl(remoteUrl: String): String? {
    val owner = githubRemoteRegex.find(remoteUrl)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: return null
    return "https://github.com/$owner.png?size=40"
}

/**
 * Shows the owner's avatar of [remoteName], or a cloud icon if it's unknown or can't be loaded (also when avatars are
 * disabled in the settings).
 */
@Composable
fun RemoteAvatar(
    remoteName: String,
    tint: Color,
    modifier: Modifier,
) {
    val avatarsEnabled = LocalAvatarProvider.current !is NoneAvatarProvider
    val url = LocalRemoteAvatars.current[remoteName]?.takeIf { avatarsEnabled }
    var isLoaded by remember(url) { mutableStateOf(false) }

    // Rounded square, like GitKraken
    Box(modifier = modifier.clip(RoundedCornerShape(3.dp))) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = remoteName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { isLoaded = it is AsyncImagePainter.State.Success },
            )
        }

        if (url == null || !isLoaded) {
            Icon(
                painter = painterResource(Res.drawable.cloud),
                contentDescription = remoteName,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
