package com.github.wood.prosemirror.compose.clipboard

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.awtClipboard
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual fun createProseMirrorClipboardManager(
    proseMirrorState: ProseMirrorState,
    clipboard: Clipboard,
): ProseMirrorClipboardManager =
    DesktopProseMirrorClipboardManager(
        proseMirrorState = proseMirrorState,
        clipboard = clipboard
    )

/**
 * Desktop (JVM) implementation of [ProseMirrorClipboardManager].
 * 使用 AWT 剪贴板的 fragmentHtmlFlavor 处理富文本复制粘贴。
 */
@OptIn(ExperimentalComposeUiApi::class)
internal class DesktopProseMirrorClipboardManager(
    private val proseMirrorState: ProseMirrorState,
    private val clipboard: Clipboard,
) : ProseMirrorClipboardManager, Clipboard by clipboard {

    override suspend fun getClipEntry(): ClipEntry? {
        try {
            val transferable = awtClipboard?.getContents(null)
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.fragmentHtmlFlavor)) {
                val rawHtmlText =
                    withContext(Dispatchers.IO) {
                        transferable.getTransferData(DataFlavor.fragmentHtmlFlavor)
                    } as String
                proseMirrorState.pendingClipboardHtml = rawHtmlText
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

        val copySelection = proseMirrorState.copySelection

        if (copySelection == null || copySelection.collapsed) {
            clipboard.setClipEntry(null)
            return
        }

        val html = proseMirrorState.toHtml(copySelection)
        val text = proseMirrorState.toText(copySelection)

        val htmlSelection = object : StringSelection(html), Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> =
                arrayOf(DataFlavor.fragmentHtmlFlavor, DataFlavor.stringFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean =
                flavor in getTransferDataFlavors()

            override fun getTransferData(flavor: DataFlavor?): Any = when (flavor) {
                DataFlavor.fragmentHtmlFlavor -> html
                DataFlavor.stringFlavor -> text
                else -> throw UnsupportedOperationException("Unsupported flavor: $flavor")
            }
        }

        awtClipboard?.setContents(htmlSelection, null)
    }
}
