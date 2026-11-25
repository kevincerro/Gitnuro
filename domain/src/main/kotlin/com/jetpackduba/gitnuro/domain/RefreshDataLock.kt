package com.jetpackduba.gitnuro.domain

import com.jetpackduba.gitnuro.common.TabScope
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

/**
 * Tab-wide lock used to serialize data refreshes. Without it, multiple refreshes launched concurrently (for example
 * one triggered by an operation and another one by the file watcher) could finish in a different order than they
 * started, overwriting fresh data (like the log graph) with stale data.
 */
@TabScope
class RefreshDataLock @Inject constructor() {
    val mutex = Mutex()
}
