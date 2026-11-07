package com.jetpackduba.gitnuro.avatarproviders

interface AvatarProvider {
    fun getAvatarUrl(email: String): String?
}
