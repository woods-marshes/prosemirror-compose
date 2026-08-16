package com.github.wood.prosemirror.compose.clipboard

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import com.github.wood.prosemirror.compose.model.ProseMirrorState

internal actual fun createProseMirrorClipboardManager(
    proseMirrorState: ProseMirrorState,
    clipboard: Clipboard,
): ProseMirrorClipboardManager =
    AndroidProseMirrorClipboardManager(
        proseMirrorState = proseMirrorState,
        clipboard = clipboard
    )

/**
 * Android implementation of [ProseMirrorClipboardManager].
 * 使用 Android ClipData 的 text/html MIME 类型处理富文本剪贴板操作。
 *
 * 粘贴时 [getClipEntry] 从剪贴板提取 HTML 存入 [ProseMirrorState.pendingClipboardHtml]，
 * 实际插入发生在 [ProseMirrorState.onTextFieldValueChange] 检测到文本新增时，
 * 避免把非粘贴场景的 [getClipEntry] 调用（如剪贴板可用性检查）误判为粘贴。
 */
internal class AndroidProseMirrorClipboardManager(
    private val proseMirrorState: ProseMirrorState,
    private val clipboard: Clipboard,
) : ProseMirrorClipboardManager, Clipboard by clipboard {

    override suspend fun getClipEntry(): ClipEntry? {
        try {
            val entry = clipboard.getClipEntry() ?: return null
            val clipData = entry.clipData
            if (clipData.itemCount > 0) {
                val htmlText = clipData.getItemAt(0).htmlText
                if (htmlText != null) {
                    proseMirrorState.pendingClipboardHtml = htmlText
                }
            }
            return entry
        } catch (e: Exception) {
            e.printStackTrace()
            return clipboard.getClipEntry()
        }
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
            val newClipData = ClipData.newHtmlText("rich text", text, html)
            clipboard.setClipEntry(ClipEntry(newClipData))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
