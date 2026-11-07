package com.jetpackduba.gitnuro.avatarproviders

import java.net.URLEncoder

private const val AVATAR_SIZE = 64

/**
 * GitHub's no-reply addresses look like `12345+username@users.noreply.github.com` (or `username@users.noreply.github.com`
 * for old accounts), which lets us build the avatar URL directly. Any other e-mail is resolved by GitHub's avatar
 * service, which maps public commit e-mails to their account.
 */
private val noReplyEmailRegex = Regex("""^(?:(\d+)\+)?([^@]+)@users\.noreply\.github\.com$""", RegexOption.IGNORE_CASE)

class GitHubAvatarProvider : AvatarProvider {
    override fun getAvatarUrl(email: String): String {
        val noReplyMatch = noReplyEmailRegex.matchEntire(email.trim())

        if (noReplyMatch != null) {
            val (userId, username) = noReplyMatch.destructured

            return if (userId.isNotEmpty()) {
                "https://avatars.githubusercontent.com/u/$userId?s=$AVATAR_SIZE&v=4"
            } else {
                "https://github.com/$username.png?size=$AVATAR_SIZE"
            }
        }

        val encodedEmail = URLEncoder.encode(email.trim().lowercase(), Charsets.UTF_8)
        return "https://avatars.githubusercontent.com/u/e?email=$encodedEmail&s=$AVATAR_SIZE"
    }
}
