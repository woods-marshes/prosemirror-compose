package com.github.wood.prosemirror.compose.clipboard

import androidx.compose.ui.platform.Clipboard
import androidx.compose.runtime.Composable
import com.github.wood.prosemirror.compose.model.ProseMirrorState

/**
 * Registers platform-specific clipboard event handlers for keyboard shortcuts (Ctrl+C/V/X).
 *
 * On web platforms (JS/WasmJS), the browser handles Ctrl+C/V/X at the DOM level,
 * bypassing Compose's [Clipboard] interface. This effect intercepts those DOM clipboard
 * events to handle rich text copy/paste with HTML formatting.
 *
 * On native platforms (Android/iOS/Desktop), this is a no-op since the OS routes clipboard
 * operations through the Compose framework.
 */
@Composable
internal expect fun ClipboardEventEffect(proseMirrorState: ProseMirrorState)