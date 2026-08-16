package com.github.wood.prosemirror.compose.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.atlassian.prosemirror.history.closeHistory
import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkType
import com.atlassian.prosemirror.model.util.resolveSafe
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.transform.setBlockType
import com.atlassian.prosemirror.transform.setNodeMarkup
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import kotlin.reflect.KClass

// ---------------------------------------------------------------------------
// 查询 API
// ---------------------------------------------------------------------------

@Deprecated(
    message = "Use config instead",
    replaceWith = ReplaceWith("config"),
    level = DeprecationLevel.WARNING,
)
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.setConfig(
    linkColor: Color = Color.Unspecified,
    linkTextDecoration: TextDecoration? = null,
    codeColor: Color = Color.Unspecified,
    codeBackgroundColor: Color = Color.Unspecified,
    codeStrokeColor: Color = Color.Unspecified,
    listIndent: Int = -1,
) {
    if (linkColor.isSpecified) config.linkColor = linkColor
    if (linkTextDecoration != null) config.linkTextDecoration = linkTextDecoration
    if (codeColor.isSpecified) config.codeSpanColor = codeColor
    if (codeBackgroundColor.isSpecified) config.codeSpanBackgroundColor = codeBackgroundColor
    if (codeStrokeColor.isSpecified) config.codeSpanStrokeColor = codeStrokeColor
    if (listIndent > -1) config.listIndent = listIndent
    updateAnnotatedString()
}

/** 选区处合并后的 [SpanStyle]（折叠选区取光标 marks）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.currentSpanStyle: SpanStyle
    get() = activeMarkSet().toSpanStyle(config)

/** 选区处最相关的 [RichSpanStyle]（link > code > Default）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.currentRichSpanStyle: RichSpanStyle
    get() {
        val marks = activeMarkSet()
        val link = marks.firstOrNull { it.type.name == "link" }
        if (link != null) return RichSpanStyle.Link(url = link.attrs["href"] as? String ?: "")
        if (marks.any { it.type.name == "code" }) return RichSpanStyle.Code()
        return RichSpanStyle.Default
    }

/** 选区处是否命中 link mark。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.isLink: Boolean
    get() = activeMarkSet().any { it.type.name == "link" }

/** 选区处是否命中 code mark。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.isCodeSpan: Boolean
    get() = activeMarkSet().any { it.type.name == "code" }

@Deprecated(
    message = "Use isCodeSpan instead",
    replaceWith = ReplaceWith("isCodeSpan"),
    level = DeprecationLevel.WARNING
)
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.isCode: Boolean
    get() = isCodeSpan

/** 非折叠选区中链接的文本内容；折叠光标位于链接内时返回整个链接文本。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.selectedLinkText: String?
    get() {
        val range = linkRangeAtCaret() ?: return null
        return doc.textBetween(range.first, range.second)
    }

/** 选区处链接的 URL（无链接时为 null）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.selectedLinkUrl: String?
    get() = (currentRichSpanStyle as? RichSpanStyle.Link)?.url

/** [spanStyle] 是否命中当前选区。仅 link/code 可表示，其余返回 false。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.isRichSpan(spanStyle: RichSpanStyle): Boolean = when (spanStyle) {
    is RichSpanStyle.Link -> isLink
    is RichSpanStyle.Code -> isCodeSpan
    else -> false
}

/** [kClass] 对应的 RichSpanStyle 是否命中当前选区。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.isRichSpan(kClass: KClass<out RichSpanStyle>): Boolean = when (kClass) {
    RichSpanStyle.Link::class -> isLink
    RichSpanStyle.Code::class -> isCodeSpan
    else -> false
}

@OptIn(ExperimentalProseMirrorApi::class)
public inline fun <reified A1 : RichSpanStyle> ProseMirrorState.isRichSpan(): Boolean =
    isRichSpan(A1::class)

/** 指定扁平选区覆盖范围内的共有 [SpanStyle]。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.getSpanStyle(textRange: TextRange): SpanStyle {
    if (textRange.collapsed) {
        return marksBeforeFlatPosition(textRange.min - 1).toSpanStyle(config)
    }
    val (from, to) = pmRangeOf(textRange)
    val common = mutableListOf<Mark>()
    doc.nodesBetween(from, to, f = { node, _, _, _ ->
        if (node.isText) {
            if (common.isEmpty()) {
                node.marks.forEach { if (common.none { c -> c == it }) common += it }
            } else {
                common.retainAll { c -> node.marks.any { it == c } }
            }
        }
        true
    })
    return common.toSpanStyle(config)
}

/** 指定扁平选区范围内的 [RichSpanStyle]（link > code > Default）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.getRichSpanStyle(textRange: TextRange): RichSpanStyle {
    if (textRange.collapsed) {
        val marks = marksBeforeFlatPosition(textRange.min - 1)
        val link = marks.firstOrNull { it.type.name == "link" }
        if (link != null) return RichSpanStyle.Link(link.attrs["href"] as? String ?: "")
        if (marks.any { it.type.name == "code" }) return RichSpanStyle.Code()
        return RichSpanStyle.Default
    }
    val (from, to) = pmRangeOf(textRange)
    val common = mutableListOf<Mark>()
    doc.nodesBetween(from, to, f = { node, _, _, _ ->
        if (node.isText) {
            if (common.isEmpty()) {
                node.marks.forEach { if (common.none { m -> m == it }) common += it }
            } else {
                common.retainAll { c -> node.marks.any { it == c } }
            }
        }
        true
    })
    val link = common.firstOrNull { it.type.name == "link" }
    if (link != null) return RichSpanStyle.Link(link.attrs["href"] as? String ?: "")
    if (common.any { it.type.name == "code" }) return RichSpanStyle.Code()
    return RichSpanStyle.Default
}

// ---------------------------------------------------------------------------
// SpanStyle 格式化（折叠选区 → storedMarks 暂存；非折叠 → 选区 addMark/removeMark）
// ---------------------------------------------------------------------------

/**
 * 切换 [spanStyle]。折叠选区切换"将要输入的文字"的样式；非折叠选区切换选区样式。
 * 可表示字段映射见 [com.github.wood.prosemirror.compose.model.toMarksToAdd]。
 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toggleSpanStyle(spanStyle: SpanStyle) {
    val toAdd = spanStyle.toMarksToAdd(schema)
    if (toAdd.isEmpty()) return
    val hasAny = activeMarkSet().any { current -> toAdd.any { it == current } }
    if (hasAny) removeSpanStyle(spanStyle) else addSpanStyle(spanStyle)
}

/** 添加 [spanStyle] 到选区（折叠选区暂存到 storedMarks，后续输入继承）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addSpanStyle(spanStyle: SpanStyle) {
    applyMarkChange(
        toAdd = spanStyle.toMarksToAdd(schema),
        toRemove = emptyList(),
        range = textFieldValue.selection,
    )
}

/** 添加 [spanStyle] 到指定扁平选区范围。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addSpanStyle(spanStyle: SpanStyle, textRange: TextRange) {
    if (textRange.collapsed) return
    applyMarkChange(
        toAdd = spanStyle.toMarksToAdd(schema),
        toRemove = emptyList(),
        range = textRange,
    )
}

/** 从选区移除 [spanStyle]（折叠选区从 storedMarks 移除）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeSpanStyle(spanStyle: SpanStyle) {
    applyMarkChange(
        toAdd = emptyList(),
        toRemove = spanStyle.toMarkTypesToRemove(schema),
        range = textFieldValue.selection,
    )
}

/** 从指定扁平选区范围移除 [spanStyle]。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeSpanStyle(spanStyle: SpanStyle, textRange: TextRange) {
    if (textRange.collapsed) return
    applyMarkChange(
        toAdd = emptyList(),
        toRemove = spanStyle.toMarkTypesToRemove(schema),
        range = textRange,
    )
}

/** 清除选区的全部 span marks（strong/em/underline/strike/textStyle）。code/link 保留。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.clearSpanStyles() {
    clearMarks(spanMarkTypes())
}

/** 清除指定扁平选区范围的 span marks。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.clearSpanStyles(textRange: TextRange) {
    if (textRange.collapsed) return
    clearMarks(spanMarkTypes(), textRange)
}

// ---------------------------------------------------------------------------
// RichSpanStyle 格式化（仅 link/code 可表示）
// ---------------------------------------------------------------------------

/** 切换 [spanStyle]（link/code）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toggleRichSpan(spanStyle: RichSpanStyle) {
    when (spanStyle) {
        is RichSpanStyle.Link -> {
            if (isLink) removeLink() else addLinkToSelection(spanStyle.url)
        }

        is RichSpanStyle.Code -> toggleCodeSpan()
        else -> Unit
    }
}

/** 添加 [spanStyle]（link/code）到选区。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addRichSpan(spanStyle: RichSpanStyle) {
    when (spanStyle) {
        is RichSpanStyle.Link -> addLinkToSelection(spanStyle.url)
        is RichSpanStyle.Code -> addCodeSpan()
        else -> Unit
    }
}

/** 添加 [spanStyle]（link/code）到指定扁平选区范围。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addRichSpan(spanStyle: RichSpanStyle, textRange: TextRange) {
    if (textRange.collapsed) return
    when (spanStyle) {
        is RichSpanStyle.Link -> addLinkToTextRange(spanStyle.url, textRange)
        is RichSpanStyle.Code -> applyMarkChange(
            toAdd = listOf(schema.mark("code")),
            toRemove = emptyList(),
            range = textRange,
        )

        else -> Unit
    }
}

/** 从选区移除 [spanStyle]（link/code）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeRichSpan(spanStyle: RichSpanStyle) {
    when (spanStyle) {
        is RichSpanStyle.Link -> removeLink()
        is RichSpanStyle.Code -> removeCodeSpan()
        else -> Unit
    }
}

/** 从指定扁平选区范围移除 [spanStyle]（link/code）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeRichSpan(spanStyle: RichSpanStyle, textRange: TextRange) {
    if (textRange.collapsed) return
    when (spanStyle) {
        is RichSpanStyle.Link -> applyMarkChange(
            toAdd = emptyList(),
            toRemove = listOf(schema.mark("link").type),
            range = textRange,
        )

        is RichSpanStyle.Code -> applyMarkChange(
            toAdd = emptyList(),
            toRemove = listOf(schema.mark("code").type),
            range = textRange,
        )

        else -> Unit
    }
}

/** 清除选区的 link/code marks。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.clearRichSpans() {
    clearMarks(listOf(schema.mark("link").type, schema.mark("code").type))
}

/** 清除指定扁平选区范围的 link/code marks。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.clearRichSpans(textRange: TextRange) {
    if (textRange.collapsed) return
    clearMarks(listOf(schema.mark("link").type, schema.mark("code").type), textRange)
}

// ---------------------------------------------------------------------------
// Link
// ---------------------------------------------------------------------------

/**
 * 在光标处插入 [text] 并应用链接样式，光标落在插入文本之后。
 * 插入文本继承 storedMarks（与参考版 staged 样式行为一致）。
 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addLink(text: String, url: String) {
    if (text.isEmpty()) return
    val tr = editorState.tr
    closeHistory(tr)
    val pos = textInputPosition(flatToPm(textFieldValue.selection.min))
    tr.insertText(text, pos, pos)
    tr.addMark(pos, tr.mapping.map(pos, 1), schema.mark("link", mapOf<String, Any?>("href" to url)))
    val insertionEnd = tr.mapping.map(pos, 1)
    tr.setSelection(TextSelection.create(tr.doc, insertionEnd, insertionEnd))
    dispatch(tr)
}

/** 给选区添加链接（折叠选区暂存 link storedMark，后续输入为链接）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addLinkToSelection(url: String) {
    val linkMark = schema.mark("link", mapOf<String, Any?>("href" to url))
    applyMarkChange(
        toAdd = listOf(linkMark),
        toRemove = emptyList(),
        range = textFieldValue.selection,
        alsoStage = true,
    )
}

/** 给指定扁平选区范围添加链接。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addLinkToTextRange(url: String, textRange: TextRange) {
    if (textRange.collapsed) return
    applyMarkChange(
        toAdd = listOf(schema.mark("link", mapOf<String, Any?>("href" to url))),
        toRemove = emptyList(),
        range = textRange,
    )
}

/**
 * 更新光标所在链接的 URL（与参考版一致：只更新当前 RichSpan 对应的连续链接范围）。
 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.updateLink(url: String) {
    val (from, to) = linkRangeAtCaret() ?: return
    val tr = editorState.tr
    closeHistory(tr)
    tr.removeMark(from, to, schema.mark("link").type)
    tr.addMark(from, to, schema.mark("link", mapOf<String, Any?>("href" to url)))
    tr.setSelection(TextSelection.create(tr.doc, from, to))
    dispatch(tr)
}

/** 移除光标所在链接（折叠光标位于链接内时也移除整段链接，与参考版一致）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeLink() {
    // 折叠光标且 link 只是 staged mark（尚未落到文档）时，清除 staged mark。
    if (textFieldValue.selection.collapsed &&
        editorState.storedMarks?.any { it.type.name == "link" } == true
    ) {
        applyMarkChange(
            toAdd = emptyList(),
            toRemove = listOf(schema.mark("link").type),
            range = textFieldValue.selection,
        )
        return
    }

    val (from, to) = linkRangeAtCaret() ?: return
    val tr = editorState.tr
    closeHistory(tr)
    tr.removeMark(from, to, schema.mark("link").type)
    tr.setSelection(TextSelection.create(tr.doc, from, to))
    dispatch(tr)
}

// ---------------------------------------------------------------------------
// Code
// ---------------------------------------------------------------------------

/** 切换选区 code mark（折叠选区切换后续输入的 code 样式）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toggleCodeSpan() {
    if (isCodeSpan) removeCodeSpan() else addCodeSpan()
}

/** 给选区添加 code mark。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addCodeSpan() {
    applyMarkChange(
        toAdd = listOf(schema.mark("code")),
        toRemove = emptyList(),
        range = textFieldValue.selection,
    )
}

/** 移除选区 code mark。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeCodeSpan() {
    applyMarkChange(
        toAdd = emptyList(),
        toRemove = listOf(schema.mark("code").type),
        range = textFieldValue.selection,
    )
}

@Deprecated(
    message = "Use toggleCodeSpan instead",
    replaceWith = ReplaceWith("toggleCodeSpan()"),
    level = DeprecationLevel.ERROR
)
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toggleCode(): Unit = toggleCodeSpan()

@Deprecated(
    message = "Use addCodeSpan instead",
    replaceWith = ReplaceWith("addCodeSpan()"),
    level = DeprecationLevel.ERROR
)
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addCode(): Unit = addCodeSpan()

@Deprecated(
    message = "Use removeCodeSpan instead",
    replaceWith = ReplaceWith("removeCodeSpan()"),
    level = DeprecationLevel.ERROR
)
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeCode(): Unit = removeCodeSpan()

// ---------------------------------------------------------------------------
// Heading / 段落级
// ---------------------------------------------------------------------------

/** 选区所在段落的标题级别。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.currentHeadingStyle: HeadingStyle
    get() {
        if (textFieldValue.selection.min <= 0) return HeadingStyle.Normal
        val pos = textInputPosition(flatToPm(textFieldValue.selection.min - 1))
        val resolved = doc.resolveSafe(pos) ?: return HeadingStyle.Normal
        for (depth in resolved.depth downTo 0) {
            val node = resolved.node(depth)
            if (node.type.isTextblock) {
                return if (node.type.name == "heading") {
                    HeadingStyle.fromLevel((node.attrs["level"] as? Number)?.toInt() ?: 1)
                } else {
                    HeadingStyle.Normal
                }
            }
        }
        return HeadingStyle.Normal
    }

/**
 * 设置选区所在段落的标题级别。
 * 折叠选区作用于当前段落；非折叠选区作用于覆盖的所有段落。
 * [HeadingStyle.Normal] 将段落变回普通段落。
 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.setHeadingStyle(headingStyle: HeadingStyle) {
    val range = expandedBlockRange() ?: return
    val tr = editorState.tr
    closeHistory(tr)
    if (headingStyle == HeadingStyle.Normal) {
        setBlockType(tr, range.first, range.second, schema.nodeType("paragraph"), null)
    } else {
        setBlockType(
            tr,
            range.first,
            range.second,
            schema.nodeType("heading"),
            mapOf<String, Any?>("level" to headingStyle.level),
        )
    }
    dispatch(tr)
}

/** 选区所在段落的 [ParagraphStyle]（仅 textAlign 可表示）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.currentParagraphStyle: ParagraphStyle
    get() = getParagraphStyle(textFieldValue.selection)

/** 指定扁平选区所在段落的 [ParagraphStyle]（仅 textAlign 可表示；跨段取共有值）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.getParagraphStyle(textRange: TextRange): ParagraphStyle {
    if (textRange.collapsed) {
        if (textRange.min <= 0) return ParagraphStyle()
        val pos = textInputPosition(flatToPm(textRange.min - 1))
        val resolved = doc.resolveSafe(pos) ?: return ParagraphStyle()
        for (depth in resolved.depth downTo 0) {
            val node = resolved.node(depth)
            if (node.type.isTextblock) {
                val textAlign = (node.attrs["textAlign"] as? String)?.let { textAlignFromAttr(it) }
                return ParagraphStyle(textAlign = textAlign ?: TextAlign.Unspecified)
            }
        }
        return ParagraphStyle()
    }

    val (from, to) = pmRangeOf(textRange)
    var commonAlign: String? = null
    var firstBlock = true
    doc.nodesBetween(from, to, f = { node, _, _, _ ->
        if (node.isTextblock) {
            val align = node.attrs["textAlign"] as? String
            commonAlign = when {
                firstBlock -> align
                commonAlign != align -> null
                else -> commonAlign
            }
            firstBlock = false
            false
        } else {
            true
        }
    })
    return ParagraphStyle(textAlign = commonAlign?.let { textAlignFromAttr(it) } ?: TextAlign.Unspecified)
}

/** 切换段落样式（仅 textAlign 可表示；与当前值相同则移除）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toggleParagraphStyle(paragraphStyle: ParagraphStyle) {
    val align = paragraphStyle.textAlign ?: return
    if (getParagraphStyle(textFieldValue.selection).textAlign == align) {
        removeParagraphStyle(paragraphStyle)
    } else {
        addParagraphStyle(paragraphStyle)
    }
}

/** 添加段落样式（仅 textAlign 可表示）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addParagraphStyle(paragraphStyle: ParagraphStyle) {
    val align = paragraphStyle.textAlign ?: return
    if (getParagraphStyle(textFieldValue.selection).textAlign == align) return
    setParagraphTextAlign(textAlignToAttr(align))
}

/** 移除段落样式（仅 textAlign 可表示）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeParagraphStyle(paragraphStyle: ParagraphStyle) {
    val align = paragraphStyle.textAlign ?: return
    if (getParagraphStyle(textFieldValue.selection).textAlign == align) {
        setParagraphTextAlign(null)
    }
}

// ---------------------------------------------------------------------------
// 内部实现
// ---------------------------------------------------------------------------

/**
 * 光标处连续 link mark 覆盖的 PM 范围。
 * 折叠光标读取前一个字符（与参考版 getSelectedLinkRichSpan 一致），
 * 然后向左/右扩展到相邻且带同一类型 mark 的文本节点。
 */
private fun ProseMirrorState.linkRangeAtCaret(): Pair<Int, Int>? {
    val flatPos = textFieldValue.selection.min - 1
    if (flatPos < 0) return null
    val pmPos = textInputPosition(flatToPm(flatPos))
    val resolved = doc.resolveSafe(pmPos) ?: return null
    if (!resolved.parent.isTextblock) return null

    var childIndex = resolved.index()
    if (resolved.textOffset == 0 &&
        resolved.parent.maybeChild(childIndex - 1)?.isText == true
    ) {
        childIndex--
    }

    val first = resolved.parent.maybeChild(childIndex) ?: return null
    if (!first.isText || first.marks.none { it.type.name == "link" }) return null

    var startIndex = childIndex
    var endIndex = childIndex + 1
    while (startIndex > 0) {
        val previous = resolved.parent.child(startIndex - 1)
        if (!previous.isText || previous.marks.none { it.type.name == "link" }) break
        startIndex--
    }
    while (endIndex < resolved.parent.childCount) {
        val next = resolved.parent.child(endIndex)
        if (!next.isText || next.marks.none { it.type.name == "link" }) break
        endIndex++
    }

    val contentStart = resolved.start(resolved.depth)
    var from = contentStart
    for (i in 0 until startIndex) {
        from += resolved.parent.child(i).nodeSize
    }
    var to = from
    for (i in startIndex until endIndex) {
        to += resolved.parent.child(i).nodeSize
    }
    return from to to
}

private fun ProseMirrorState.spanMarkTypes(): List<MarkType> =
    listOf("strong", "em", "underline", "strike", "textStyle").map { schema.mark(it).type }

/**
 * 应用 mark 变更。折叠选区 → storedMarks（暂存给后续输入）；
 * 非折叠 → 选区 addMark/removeMark + 重建选区。
 * [alsoStage] 时非折叠也把 mark 追加到 storedMarks（参考版 staged 语义）。
 */
@OptIn(ExperimentalProseMirrorApi::class)
private fun ProseMirrorState.applyMarkChange(
    toAdd: List<Mark>,
    toRemove: List<MarkType>,
    range: TextRange,
    alsoStage: Boolean = false,
) {
    val tr = editorState.tr
    closeHistory(tr)
    if (range.collapsed) {
        toAdd.forEach { tr.addStoredMark(it) }
        toRemove.forEach { tr.removeStoredMark(it) }
    } else {
        val (from, to) = pmRangeOf(range)
        toAdd.forEach { tr.addMark(from, to, it) }
        toRemove.forEach { tr.removeMark(from, to, it) }
        tr.setSelection(TextSelection.create(tr.doc, from, to))
        // setSelection 会清空 storedMarks，因此 staged marks 必须在选区落定后再追加。
        if (alsoStage) toAdd.forEach { tr.addStoredMark(it) }
    }
    dispatch(tr)
}

@OptIn(ExperimentalProseMirrorApi::class)
private fun ProseMirrorState.clearMarks(types: List<MarkType>, range: TextRange? = null) {
    val target = range ?: textFieldValue.selection
    val tr = editorState.tr
    closeHistory(tr)
    if (target.collapsed) {
        types.forEach { tr.removeStoredMark(it) }
    } else {
        val (from, to) = pmRangeOf(target)
        types.forEach { tr.removeMark(from, to, it) }
        tr.setSelection(TextSelection.create(tr.doc, from, to))
    }
    dispatch(tr)
}

/** 选区扩展到的块级范围（textblock 起止）。 */
internal fun ProseMirrorState.expandedBlockRange(): Pair<Int, Int>? {
    val (from, to) = pmRangeOf(textFieldValue.selection)
    val resolvedFrom = doc.resolveSafe(from) ?: return null
    val resolvedTo = doc.resolveSafe(to) ?: return null
    return resolvedFrom.start(resolvedFrom.depth) to resolvedTo.end(resolvedTo.depth)
}

/** 为选区覆盖的每个 textblock 设置 textAlign 属性（null 移除）。 */
@OptIn(ExperimentalProseMirrorApi::class)
private fun ProseMirrorState.setParagraphTextAlign(align: String?) {
    val range = expandedBlockRange() ?: return
    val targets = mutableListOf<Triple<Int, com.atlassian.prosemirror.model.NodeType, Attrs>>()
    doc.nodesBetween(range.first, range.second, f = { node, pos, _, _ ->
        if (node.isTextblock) {
            val newAttrs: Attrs = when (align) {
                null -> node.attrs - "textAlign"
                else -> node.attrs + mapOf<String, Any?>("textAlign" to align)
            }
            targets += Triple(pos, node.type, newAttrs)
            false
        } else {
            true
        }
    })
    if (targets.isEmpty()) return
    val tr = editorState.tr
    closeHistory(tr)
    targets.forEach { (pos, type, attrs) ->
        setNodeMarkup(tr, pos, type, attrs, null)
    }
    dispatch(tr)
}

private fun textAlignToAttr(textAlign: TextAlign): String = when (textAlign) {
    TextAlign.Left, TextAlign.Start -> "left"
    TextAlign.Right, TextAlign.End -> "right"
    TextAlign.Center -> "center"
    TextAlign.Justify -> "justify"
    else -> "left"
}

private fun textAlignFromAttr(value: String): TextAlign? = when (value) {
    "left" -> TextAlign.Left
    "center" -> TextAlign.Center
    "right" -> TextAlign.Right
    "justify" -> TextAlign.Justify
    else -> null
}
