package com.github.wood.prosemirror.compose.model

import androidx.compose.ui.text.TextRange
import com.atlassian.prosemirror.history.closeHistory
import com.atlassian.prosemirror.model.DOMParser
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.Slice
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
    require(textRange.min >= 0) { "The start index must be non-negative." }
    require(textRange.max <= textFieldValue.text.length) {
        "The end index must be within the text bounds. The text length is " +
            "${textFieldValue.text.length}, but the end index is ${textRange.max}."
    }
    if (textRange.collapsed) return
    if (coordinateMap.flatListMarkerRanges.any { textRange.min == it.min && textRange.max == it.max }) {
        decreaseListLevel()
        return
    }
    val (from, to) = pmRangeOf(textRange)
    val tr = editorState.tr
    closeHistory(tr)
    tr.deleteRange(from, to)
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
    require(textRange.min >= 0) { "The start index must be non-negative." }
    require(textRange.max <= textFieldValue.text.length) {
        "The end index must be within the text bounds. The text length is " +
            "${textFieldValue.text.length}, but the end index is ${textRange.max}."
    }

    if (coordinateMap.flatListMarkerRanges.any { textRange.min == it.min && textRange.max == it.max }) {
        decreaseListLevel()
        if (text.isNotEmpty()) addTextAtIndex(textRange.min, text)
        return
    }

    val (rawFrom, rawTo) = pmRangeOf(textRange)
    val from = if (textRange.collapsed) textInputPosition(rawFrom) else rawFrom
    val to = if (textRange.collapsed) from else rawTo

    // 单个 "\n" 复用 Enter 的段落/列表拆分逻辑。
    if (!singleParagraphMode && text == "\n") {
        val deleteRange = if (textRange.collapsed) null else from to to
        if (handleEnter(from, deleteRange)) return
    }

    val tr = editorState.tr
    closeHistory(tr)
    if (text.isEmpty()) {
        tr.deleteRange(from, to)
    } else {
        replacePlainText(
            tr = tr,
            text = text,
            pureInsertion = textRange.collapsed,
            pmFrom = from,
            pmTo = to,
        )
    }
    val insertionEnd = tr.mapping.map(from, 1)
    tr.setSelection(TextSelection.create(tr.doc, insertionEnd, insertionEnd))
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
    require(index >= 0) { "The index must be non-negative." }
    require(index <= textFieldValue.text.length) {
        "The index must be within the text bounds. The text length is " +
            "${textFieldValue.text.length}, but the index is $index."
    }
    if (text.isEmpty()) return
    val pmPos = textInputPosition(flatToPm(index))

    // 单个 "\n" 复用 Enter 的段落/列表拆分逻辑。
    if (!singleParagraphMode && text == "\n") {
        if (handleEnter(pmPos)) return
    }

    val tr = editorState.tr
    closeHistory(tr)
    replacePlainText(
        tr = tr,
        text = text,
        pureInsertion = true,
        pmFrom = pmPos,
        pmTo = pmPos,
    )
    val insertionEnd = tr.mapping.map(pmPos, 1)
    tr.setSelection(TextSelection.create(tr.doc, insertionEnd, insertionEnd))
    dispatch(tr)
}

/**
 * 程序化替换整个文档为纯文本（清空 undo/redo 历史——重建 state，与参考版契约一致）。
 * 换行符按段落拆分，与参考版 `setText("a\nb")` 产生多个段落的语义一致。
 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.setText(text: String, selection: TextRange = TextRange(text.length)): ProseMirrorState {
    val blocks = if (text.isEmpty()) {
        listOf(schema.node("paragraph"))
    } else {
        text.split("\n").map { line ->
            schema.node(
                "paragraph",
                null,
                if (line.isEmpty()) emptyList<Node>() else listOf(schema.text(line)),
            )
        }
    }
    val newDoc = schema.topNodeType.createAndFill(content = blocks, marks = null)!!
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
    // replaceRange（而非 replaceWith）会根据 schema 寻找合适的落点，
    // 支持在段落中间插入块级 HTML。
    tr.replaceRange(pmPos, pmPos, Slice(parsed.content, 0, 0))
    dispatch(tr)
}

/** 在选区之后插入 HTML 片段（不清历史）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.insertHtmlAfterSelection(html: String) {
    val position = textFieldValue.selection.max
    selection = TextRange(position)
    insertHtml(html, position)
}

/** 清空文档（空段落）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.clear() {
    setText("")
}

/** 复制当前文档与配置（历史不复制，但保留当前选区——与参考版一致）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.copy(): ProseMirrorState {
    val copy = ProseMirrorState(
        schema = schema,
        initialDoc = editorState.doc,
    )
    copy.copyConfigFrom(this)
    copy.applyFlatSelection(textFieldValue.selection)
    return copy
}

// ---------------------------------------------------------------------------
// 内部实现
// ---------------------------------------------------------------------------

/** 整体重建 PMEditorState（清空 undo/redo 历史），并重算扁平投影。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.replaceWholeDoc(newDoc: Node) {
    val newState = PMEditorState.create(
        EmptyEditorStateConfig(schema = schema, doc = newDoc, plugins = effectivePlugins)
    )
    // 与构造函数一致：把光标放进第一个 textblock 的内容起点，避免停留在 doc 层。
    val firstTextblockPos = findFirstTextblockContentStart(newState.doc)
    val tr = newState.tr
    tr.setSelection(TextSelection.create(newState.doc, firstTextblockPos, firstTextblockPos))
    editorState = newState.apply(tr)
    lastNonCollapsedSelection = TextRange.Zero
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
