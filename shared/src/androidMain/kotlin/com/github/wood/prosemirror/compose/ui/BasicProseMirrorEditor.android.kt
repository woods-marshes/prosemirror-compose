package com.github.wood.prosemirror.compose.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import kotlinx.coroutines.CoroutineScope

internal actual fun Modifier.adjustTextIndicatorOffset(
    state: ProseMirrorState,
    contentPadding: PaddingValues,
    density: Density,
    layoutDirection: LayoutDirection,
    scope: CoroutineScope,
): Modifier = this
