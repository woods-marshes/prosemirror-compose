package com.github.wood.prosemirror.compose.model

import androidx.compose.ui.text.TextRange
import com.atlassian.prosemirror.history.closeHistory
import com.atlassian.prosemirror.model.DOMParser
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.state.EmptyEditorStateConfig
import com.atlassian.prosemirror.state.PMEditorState
import com.atlassian.prosemirror.state.TextSelection
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi

// ---------------------------------------------------------------------------
// 文本操作 API（全部经 PM Transaction 落树）
// ---------------------------------------------------------------------------

/** 删除选区文本（折叠选区无操作）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeSelectedText() {
    removeTextRange(textFieldValue.selection)
}

/** 删除指定扁平选区范围的文本。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeTextRange(textRange: TextRange) {
    if (textRange.collapsed) return
    val (from, to) = pmRangeOf(textRange)
    val tr = editorState.tr
    closeHistory(tr)
    tr.delete(from, to)
    dispatch(tr)
}

/** 用文本替换选区。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.replaceSelectedText(text: String) {
    replaceTextRange(textFieldValue.selection, text)
}

/** 用文本替换指定扁平选区范围。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.replaceTextRange(textRange: TextRange, text: String) {
    val (from, to) = pmRangeOf(textRange)
    val tr = editorState.tr
    closeHistory(tr)
    if (text.isEmpty()) {
        tr.delete(from, to)
    } else {
        tr.replaceWith(from, to, schema.text(text))
    }
    tr.setSelection(TextSelection.create(tr.doc, from + text.length, from + text.length))
    dispatch(tr)
}

/** 在选区之后插入文本。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addTextAfterSelection(text: String) {
    addTextAtIndex(textFieldValue.selection.max, text)
}

/** 在指定扁平索引处插入文本。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addTextAtIndex(index: Int, text: String) {
    if (text.isEmpty()) return
    val pmPos = flatToPm(index.coerceIn(0, textFieldValue.text.length))
    val tr = editorState.tr
    closeHistory(tr)
    tr.insertText(text, pmPos, pmPos)
    tr.setSelection(TextSelection.create(tr.doc, pmPos + text.length, pmPos + text.length))
    dispatch(tr)
}

/**
 * 程序化替换整个文档为纯文本（清空 undo/redo 历史——重建 state，与参考版契约一致）。
 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.setText(text: String, selection: TextRange = TextRange(text.length)): ProseMirrorState {
    val newDoc = if (text.isEmpty()) {
        schema.topNodeType.createAndFill()!!
    } else {
        val para = schema.node("paragraph", null, listOf(schema.text(text)))
        schema.topNodeType.createAndFill(content = para, marks = null)!!
    }
    replaceWholeDoc(newDoc)
    applyFlatSelection(selection)
    return this
}

/**
 * 程序化替换整个文档为 HTML（清空 undo/redo 历史）。
 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.setHtml(html: String): ProseMirrorState {
    val newDoc = DOMParser.fromSchema(schema).parseHtml(html)
    replaceWholeDoc(newDoc)
    return this
}

/** 在指定扁平索引处插入 HTML 片段（不清历史）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.insertHtml(html: String, position: Int) {
    val parsed = DOMParser.fromSchema(schema).parseHtml(html)
    val pmPos = flatToPm(position.coerceIn(0, textFieldValue.text.length))
    val tr = editorState.tr
    closeHistory(tr)
    tr.replaceWith(pmPos, pmPos, parsed.content)
    dispatch(tr)
}

/** 在选区之后插入 HTML 片段（不清历史）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.insertHtmlAfterSelection(html: String) {
    insertHtml(html, textFieldValue.selection.max)
}

/** 清空文档（空段落）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.clear() {
    setText("")
}

/** 复制当前文档与配置（历史不复制——与参考版一致）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.copy(): ProseMirrorState {
    val copy = ProseMirrorState(
        schema = schema,
        initialDoc = editorState.doc,
    )
    copy.copyConfigFrom(this)
    return copy
}

// ---------------------------------------------------------------------------
// 内部实现
// ---------------------------------------------------------------------------

/** 整体重建 PMEditorState（清空 undo/redo 历史），并重算扁平投影。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.replaceWholeDoc(newDoc: Node) {
    editorState = PMEditorState.create(
        EmptyEditorStateConfig(schema = schema, doc = newDoc, plugins = effectivePlugins)
    )
    updateAnnotatedString()
}

/** 应用扁平选区（经坐标映射后走纯选区事务）。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.applyFlatSelection(selection: TextRange) {
    val length = textFieldValue.text.length
    val start = selection.min.coerceIn(0, length)
    val end = selection.max.coerceIn(0, length)
    val tr = editorState.tr
    tr.setSelection(TextSelection.create(tr.doc, flatToPm(start), flatToPm(end)))
    dispatch(tr)
}

@OptIn(ExperimentalProseMirrorApi::class)
private fun ProseMirrorState.copyConfigFrom(other: ProseMirrorState) {
    config.linkColor = other.config.linkColor
    config.linkTextDecoration = other.config.linkTextDecoration
    config.codeSpanColor = other.config.codeSpanColor
    config.codeSpanBackgroundColor = other.config.codeSpanBackgroundColor
    config.codeSpanStrokeColor = other.config.codeSpanStrokeColor
    config.listIndent = other.config.listIndent
    config.unorderedListStyleType = other.config.unorderedListStyleType
    config.orderedListStyleType = other.config.orderedListStyleType
    config.listMarkerStyleBehavior = other.config.listMarkerStyleBehavior
    config.listPrefixAlignment = other.config.listPrefixAlignment
    config.preserveStyleOnEmptyLine = other.config.preserveStyleOnEmptyLine
    config.exitListOnEmptyItem = other.config.exitListOnEmptyItem
}
