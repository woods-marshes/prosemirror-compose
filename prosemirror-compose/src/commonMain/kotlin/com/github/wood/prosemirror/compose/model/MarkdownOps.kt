package com.github.wood.prosemirror.compose.model

import androidx.compose.ui.text.TextRange
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.parser.markdown.markdownToHtml
import com.github.wood.prosemirror.compose.parser.markdown.toMarkdown
import com.github.wood.prosemirror.compose.utils.extractRangeFragment

// ---------------------------------------------------------------------------
// Markdown 导入/导出
//
// 导入路径：Markdown -(JetBrains markdown/GFM)-> HTML -(PM DOMParser)-> doc。
// 复用 prosemirror-kotlin 已实现的成熟 DOM 解析与 schema 落树逻辑。
// 导出路径：PM doc/fragment -> Markdown。
// ---------------------------------------------------------------------------

/** 用 Markdown 替换整个文档（清空 undo/redo 历史）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.setMarkdown(markdown: String): ProseMirrorState =
    if (markdown.isBlank()) setText("") else setHtml(markdownToHtml(markdown))

/** 在指定扁平索引处插入 Markdown（不清历史）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.insertMarkdown(markdown: String, position: Int) {
    insertHtml(markdownToHtml(markdown), position)
}

/** 在选区之后插入 Markdown（不清历史）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.insertMarkdownAfterSelection(markdown: String) {
    insertHtmlAfterSelection(markdownToHtml(markdown))
}

/** 将整个文档导出为 Markdown。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toMarkdown(): String = doc.content.toMarkdown()

/** 将指定扁平选区导出为 Markdown（保留部分覆盖段的段落/列表包装）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toMarkdown(range: TextRange): String {
    val pmFrom = flatToPm(range.min.coerceIn(0, textFieldValue.text.length))
    val pmTo = flatToPm(range.max.coerceIn(0, textFieldValue.text.length))
    return doc.extractRangeFragment(pmFrom, pmTo).toMarkdown()
}
