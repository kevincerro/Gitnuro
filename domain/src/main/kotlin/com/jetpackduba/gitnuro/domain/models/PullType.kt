package com.jetpackduba.gitnuro.domain.models

enum class PullType {
    REBASE,
    MERGE,
    /** Merge that only succeeds when the branch can be fast-forwarded */
    FAST_FORWARD_ONLY,
    DEFAULT
}