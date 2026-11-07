package com.jetpackduba.gitnuro.domain.models

/**
 * Operation run by the toolbar's Pull button (like GitKraken, it's chosen from the button's dropdown).
 */
enum class ToolbarPullMode {
    FETCH_ALL,
    PULL_FAST_FORWARD_IF_POSSIBLE,
    PULL_FAST_FORWARD_ONLY,
    PULL_REBASE,
}
