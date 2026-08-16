package com.github.wood.prosemirror.compose.clipboard

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding

internal actual fun createProseMirrorClipboardManager(
    proseMirrorState: ProseMirrorState,
    clipboard: Clipboard,
): ProseMirrorClipboardManager =
    IosProseMirrorClipboardManager(
        proseMirrorState = proseMirrorState,
        clipboard = clipboard
    )

/**
 * iOS implementation of [ProseMirrorClipboardManager].
 * 使用 UIPasteboard 的 public.html UTI 处理富文本剪贴板操作。
 */
@OptIn(BetaInteropApi::class, ExperimentalComposeUiApi::class)
internal class IosProseMirrorClipboardManager(
    private val proseMirrorState: ProseMirrorState,
    private val clipboard: Clipboard,
) : ProseMirrorClipboardManager, Clipboard by clipboard {

    override suspend fun getClipEntry(): ClipEntry? {
        try {
            val pasteboard = clipboard.nativeClipboard
            val htmlData = pasteboard.dataForPasteboardType(HTML_UTI)
            if (htmlData != null) {
                val html = NSString.create(data = htmlData, encoding = NSUTF8StringEncoding)
                    ?.toString()
                if (html != null) {
                    proseMirrorState.pendingClipboardHtml = html
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return clipboard.getClipEntry()
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        if (clipEntry == null) {
            clipboard.setClipEntry(null)
            return
        }

        try {
            val copySelection = proseMirrorState.copySelection

            if (copySelection == null || copySelection.collapsed) {
                clipboard.setClipEntry(null)
                return
            }

            val html = proseMirrorState.toHtml(copySelection)
            val text = proseMirrorState.toText(copySelection)
            val pasteboard = clipboard.nativeClipboard

            val htmlData = NSString.create(string = html)
                .dataUsingEncoding(NSUTF8StringEncoding)
            val textData = NSString.create(string = text)
                .dataUsingEncoding(NSUTF8StringEncoding)

            if (htmlData != null && textData != null) {
                pasteboard.items = listOf(
                    mapOf(
                        HTML_UTI to htmlData,
                        PLAIN_TEXT_UTI to textData,
                    )
                )
            } else {
                pasteboard.string = text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private companion object {
        const val HTML_UTI = "public.html"
        const val PLAIN_TEXT_UTI = "public.utf8-plain-text"
    }
}
