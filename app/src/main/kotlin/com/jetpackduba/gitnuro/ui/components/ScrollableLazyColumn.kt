package com.jetpackduba.gitnuro.ui.components

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetpackduba.gitnuro.theme.scrollbarHover
import com.jetpackduba.gitnuro.theme.scrollbarNormal

@Composable
fun ScrollableLazyColumn(
    modifier: Modifier,
    state: LazyListState = rememberLazyListState(),
    /** Space left above the scrollbar, e.g. the height of a sticky header that it shouldn't cover */
    scrollbarTopPadding: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    val pointerPosition = remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier.trackListPointer(pointerPosition),
    ) {
        CompositionLocalProvider(LocalListPointerPosition provides pointerPosition) {
            LazyColumn(
                state = state,
                content = content
            )
        }
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = scrollbarTopPadding, end = 2.dp),
            style = LocalScrollbarStyle.current.copy(
                unhoverColor = MaterialTheme.colors.scrollbarNormal,
                hoverColor = MaterialTheme.colors.scrollbarHover,
            ),
            adapter = rememberScrollbarAdapter(
                scrollState = state
            )
        )
    }
}