package com.jetpackduba.gitnuro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.jetpackduba.gitnuro.LocalAvatarProvider
import com.jetpackduba.gitnuro.domain.models.Identity

@Composable
fun AvatarImage(
    modifier: Modifier = Modifier,
    personIdent: Identity,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colors.primary,
) {
    val current = LocalAvatarProvider.current

    Box(
        modifier = modifier
            .clip(shape)
    ) {
        val avatarProviderUrl = personIdent
            .email
            ?.takeIf { it.isNotBlank() }
            ?.let { email -> current.getAvatarUrl(email) }

        var isSuccessfulLoad by remember(avatarProviderUrl) { mutableStateOf(false) }

        AsyncImage(
            model = avatarProviderUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(),
            onState = {
                isSuccessfulLoad = it is AsyncImagePainter.State.Success
            }
        )

        if (avatarProviderUrl == null || !isSuccessfulLoad) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = personIdent.name?.firstOrNull()?.uppercase() ?: "#",
                    color = Color.White,
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}