package com.github.wood.prosemirror.compose.clipboard

import androidx.compose.runtime.Composable
import com.github.wood.prosemirror.compose.model.ProseMirrorState

@Composable
internal actual fun ClipboardEventEffect(proseMirrorState: ProseMirrorState) {
    // No-op: Android 剪贴板操作经由 Compose 框架路由
}
