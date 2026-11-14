package com.jetpackduba.gitnuro.domain

object GitConstants {
    const val COMMIT_MSG = "COMMIT_EDITMSG"

    /**
     * File of the git directory where the app keeps the commit message being written between sessions.
     *
     * It's a file of the app and not git's COMMIT_EDITMSG: git leaves there the message of the last commit made with
     * it (terminal, other clients), which would show up in the commit message field as if it was a draft.
     */
    const val COMMIT_DRAFT = "GITNURO_COMMIT_DRAFT"
    const val MERGE_MSG = "MERGE_MSG"
    const val SQUASH_MSG = "SQUASH_MSG"
}