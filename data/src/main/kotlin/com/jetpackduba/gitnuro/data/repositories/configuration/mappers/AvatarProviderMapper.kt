package com.jetpackduba.gitnuro.data.repositories.configuration.mappers

import com.jetpackduba.gitnuro.data.mappers.DataMapper
import com.jetpackduba.gitnuro.domain.models.AvatarProviderType
import javax.inject.Inject

private const val NONE = "none"
private const val GRAVATAR = "gravatar"
private const val GITHUB = "github"

class AvatarProviderMapper @Inject constructor(): DataMapper<AvatarProviderType?, String?>  {
    override fun toData(value: AvatarProviderType?): String? {
        return when (value) {
            AvatarProviderType.None -> NONE
            AvatarProviderType.Gravatar -> GRAVATAR
            AvatarProviderType.GitHub -> GITHUB
            null -> null
        }
    }

    override fun toDomain(value: String?): AvatarProviderType? {
        return when (value) {
            NONE -> AvatarProviderType.None
            GRAVATAR -> AvatarProviderType.Gravatar
            GITHUB -> AvatarProviderType.GitHub
            null -> null
            else -> throw IllegalStateException("Unhandled avatar provider $value")
        }
    }
}
