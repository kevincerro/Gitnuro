package com.jetpackduba.gitnuro.avatarproviders


class NoneAvatarProvider : AvatarProvider {
    override fun getAvatarUrl(email: String): String? {
        return null
    }
}
