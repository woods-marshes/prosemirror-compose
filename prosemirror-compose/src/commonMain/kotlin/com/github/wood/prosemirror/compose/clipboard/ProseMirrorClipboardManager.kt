package com.github.wood.prosemirror.compose.clipboard

import androidx.compose.ui.platform.Clipboard
import com.github.wood.prosemirror.compose.model.ProseMirrorState

/**
 * Creates a new instance of [ProseMirrorClipboardManager]
 * @param proseMirrorState The [ProseMirrorState] to be used for clipboard operations
 * @param clipboard The Compose [Clipboard] for handling clipboard operations
 * @return A new instance of [ProseMirrorClipboardManager]
 */
internal expect fun createProseMirrorClipboardManager(
    proseMirrorState: ProseMirrorState,
    clipboard: Clipboard,
): ProseMirrorClipboardManager

/**
 * Platform-specific clipboard manager that intercepts [Clipboard] operations
 * to support rich text (HTML) copy/paste.
 *
 * Each platform implementation reads HTML from the native clipboard in [getClipEntry]
 * and writes HTML + plain text in [setClipEntry].
 */
internal interface ProseMirrorClipboardManager : Clipboard
