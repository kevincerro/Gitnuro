package com.jetpackduba.gitnuro.avatarproviders

import com.jetpackduba.gitnuro.domain.extensions.sha256

class GravatarAvatarProvider : AvatarProvider {
    override fun getAvatarUrl(email: String): String {
        return "https://www.gravatar.com/avatar/${email.sha256}?s=60&d=404"
    }
}
