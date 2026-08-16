package com.github.wood.prosemirror.compose.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.HeadingStyle
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import com.github.wood.prosemirror.compose.model.RichSpanStyle
import com.github.wood.prosemirror.compose.model.paragraph.ListMarkerStyleBehavior
import com.github.wood.prosemirror.compose.model.paragraph.ListPrefixAlignment

/** 扁平化时维护的列表上下文。 */
private class ListContext(
    val typeName: String,
    val order: Int,
    val level: Int,
)

@OptIn(ExperimentalProseMirrorApi::class)
internal fun AnnotatedString.Builder.appendProseMirrorDoc(
    state: ProseMirrorState,
    doc: Node,
    mapBuilder: PositionCoordinateMapBuilder,
    styledRanges: MutableList<Pair<RichSpanStyle, TextRange>>
) {
    // 注意：不注册 doc 起点的 boundary(0,0)——第一个块（textblock）开始的
    // boundary 已覆盖 flat 0 并映射到 pm 1（textblock 内容起点）。
    // 若注册 (0,0)→(0,0)，flatToPm(0) 会命中 doc 层位置，导致输入在 doc 层插入新块。
    var pmCursor = 0
    // 上一块的 PM 结束位置（用于块分隔符注册；null 表示第一个块）
    var lastBlockEndPm: Int? = null
    val listStack = mutableListOf<ListContext>()

    fun compileNode(
        node: Node,
        blockSpanStyle: SpanStyle = SpanStyle(),
        listMarker: String? = null,
        isFirstChildOfParent: Boolean = true,
    ) {
        if (node.isText) {
            val textValue = node.text ?: ""
            val flatStart = length
            val flatEnd = flatStart + textValue.length

            mapBuilder.registerRange(pmCursor, pmCursor + node.nodeSize, flatStart, flatEnd)

            // code mark → styledRanges 供 Canvas 重绘圆角胶囊
            if (node.marks.any { it.type.name == "code" }) {
                styledRanges.add(RichSpanStyle.Code() to TextRange(flatStart, flatEnd))
            }

            val mergedStyle = node.marks
                .map { mark -> MarkMapper.map(mark, state.config) }
                .fold(blockSpanStyle) { acc, style -> acc.merge(style) }

            pushStyle(mergedStyle)

            val linkMark = node.marks.firstOrNull { it.type.name == "link" }
            if (linkMark != null) {
                val href = linkMark.attrs["href"] as? String ?: ""
                addStringAnnotation("URL", href, flatStart, flatEnd)
            }

            append(textValue)
            pop()

            pmCursor += node.nodeSize
            return
        }

        val isBlock = node.isBlock
        val isDoc = node.type == state.schema.topNodeType
        var pushedIndentStyle = false
        var pushedTextAlignStyle = false

        if (isBlock && !isDoc) {
            // 块起始开销
            pmCursor += 1
            // 块分隔符（合成 "\n"）：除父容器首个子块外，每个块前都要有分隔符
            //（包括 list_item——marker 负责列表符号，块间换行仍属于扁平文本）。
            if (lastBlockEndPm != null && !isFirstChildOfParent) {
                // 记录 pmBefore/pmAfter 供跨块删除映射
                val pmBefore = lastBlockEndPm ?: pmCursor
                append("\n")
                mapBuilder.registerBlockSeparator(length - 1, pmBefore, pmCursor)
            }
            mapBuilder.registerBoundary(pmPos = pmCursor, flatPos = length)

            // 列表项：输出 marker + 缩进
            if (node.type.name == "list_item" && listStack.isNotEmpty() && listMarker != null) {
                pushedIndentStyle = appendListItemMarker(state, node, listStack.last(), listMarker, pmCursor, mapBuilder)
            }

            // textAlign 段落属性 → ParagraphStyle（无该属性时保持默认对齐）
            val textAlign = (node.attrs["textAlign"] as? String)?.let { attr ->
                when (attr) {
                    "left", "start" -> TextAlign.Left
                    "center" -> TextAlign.Center
                    "right", "end" -> TextAlign.Right
                    "justify" -> TextAlign.Justify
                    else -> null
                }
            }
            if (textAlign != null) {
                pushStyle(ParagraphStyle(textAlign = textAlign))
                pushedTextAlignStyle = true
            }
        }

        if (node.isLeaf && !node.isText) {
            val flatStart = length
            when (node.type.name) {
                "image" -> {
                    val src = node.attrs["src"] as? String ?: ""
                    val alt = node.attrs["alt"] as? String ?: ""
                    val width = (node.attrs["width"] as? Number)?.toFloat() ?: 0f
                    val height = (node.attrs["height"] as? Number)?.toFloat() ?: 0f

                    val imageStyle = RichSpanStyle.Image(src, width.sp, height.sp, alt)
                    with(imageStyle) {
                        appendCustomContent(state)
                    }
                }

                "token", "mention" -> {
                    // token：label 渲染为真实样式文本（styledRanges 供 drawStyle 绘制）
                    val triggerId = node.attrs["triggerId"] as? String ?: ""
                    val id = node.attrs["id"] as? String ?: ""
                    val label = node.attrs["label"] as? String ?: node.textContent
                    val tokenStyle = RichSpanStyle.Token(triggerId, id, label)
                    styledRanges.add(tokenStyle to TextRange(flatStart, flatStart + label.length))
                    val trigger = state.triggers.firstOrNull { it.id == triggerId }
                    val tokenSpanStyle = (trigger?.style?.invoke(state.config) ?: SpanStyle(color = state.config.linkColor))
                        .merge(blockSpanStyle)
                    pushStyle(tokenSpanStyle)
                    append(label)
                    pop()
                }

                "hard_break" -> append("\n")
                else -> {
                    if (node.type.spec.linebreakReplacement == true) append("\n")
                }
            }

            // 叶子节点的 PM 宽度（nodeSize）与扁平字符数通常相等（image 占位符、
            // hard_break），但 token/mention 的 label 可能长于 1。后者必须注册为
            // “多个扁平字符 → 单个 PM 位置”的常量映射，否则 flatToPm 会把 label
            // 中间的偏移量错误地加到 PM 坐标上。
            val flatEnd = length
            if (flatEnd - flatStart == node.nodeSize) {
                mapBuilder.registerRange(pmCursor, pmCursor + node.nodeSize, flatStart, flatEnd)
            } else {
                mapBuilder.registerRange(pmCursor, pmCursor, flatStart, flatEnd)
            }
            pmCursor += node.nodeSize
        } else {
            val isList = node.type.name == "bullet_list" || node.type.name == "ordered_list"
            if (isList) {
                listStack.add(
                    ListContext(
                        typeName = node.type.name,
                        order = (node.attrs["order"] as? Number)?.toInt() ?: 1,
                        level = listStack.size + 1,
                    )
                )
            }

            val effectiveBlockStyle = if (node.type.name == "heading") {
                val level = (node.attrs["level"] as? Number)?.toInt() ?: 1
                blockSpanStyle.merge(HeadingStyle.fromLevel(level).defaultSpanStyle)
            } else {
                blockSpanStyle
            }

            node.content.forEach { child, _, index ->
                val marker = if (isList && child.type.name == "list_item") {
                    buildListMarker(state, listStack.last(), index)
                } else {
                    null
                }
                compileNode(
                    child,
                    blockSpanStyle = effectiveBlockStyle,
                    listMarker = marker,
                    isFirstChildOfParent = index == 0,
                )
            }

            if (isList) {
                listStack.removeAt(listStack.size - 1)
            }
        }

        if (isBlock && !isDoc) {
            if (pushedTextAlignStyle) pop()
            if (pushedIndentStyle) pop()
            // 块结束开销
            pmCursor += 1
            mapBuilder.registerBoundary(pmPos = pmCursor, flatPos = length)
            lastBlockEndPm = pmCursor
        }
    }

    compileNode(doc)
    mapBuilder.registerBoundary(pmPos = doc.nodeSize, flatPos = length)
}

/** 计算列表项 marker 文本。序号 = list 的 order 属性 + 兄弟索引（渲染时推导，节点不存序号）。 */
@OptIn(ExperimentalProseMirrorApi::class)
private fun buildListMarker(state: ProseMirrorState, context: ListContext, siblingIndex: Int): String {
    return when (context.typeName) {
        "bullet_list" -> {
            val prefixes = state.config.unorderedListStyleType.prefixes
            prefixes[(context.level - 1).coerceIn(prefixes.indices)] + " "
        }

        "ordered_list" -> {
            val number = context.order + siblingIndex
            state.config.orderedListStyleType.format(number, context.level) +
                state.config.orderedListStyleType.getSuffix(context.level)
        }

        else -> ""
    }
}

/**
 * 输出列表项 marker（"• " / "1. "）与缩进 ParagraphStyle。
 * 返回是否 push 了缩进样式（由块结束处 pop 配对）。
 */
@OptIn(ExperimentalProseMirrorApi::class)
private fun AnnotatedString.Builder.appendListItemMarker(
    state: ProseMirrorState,
    item: Node,
    context: ListContext,
    markerText: String,
    pmItemStart: Int,
    mapBuilder: PositionCoordinateMapBuilder,
): Boolean {
    // 缩进：marker 槽 + TextIndent（宽度来自 onTextLayout 测量缓存，首帧回退 0）。
    // ParagraphStyle 必须在 marker 之前 push，这样 marker 与后续内容同属一个段落；
    // 否则 marker 会落在默认段落样式中，形成隐式段落边界。
    val indentPerLevel = when (context.typeName) {
        "bullet_list" -> state.config.unorderedListIndent
        else -> state.config.orderedListIndent
    }
    val base = (indentPerLevel * context.level).toFloat()
    val prefixWidth = state.listMarkerWidthCache[markerText]?.value ?: 0f
    val textIndent = when (state.config.listPrefixAlignment) {
        ListPrefixAlignment.End -> TextIndent(
            firstLine = (base - prefixWidth).coerceAtLeast(0f).sp,
            restLine = base.sp,
        )

        ListPrefixAlignment.Start -> TextIndent(
            firstLine = base.sp,
            restLine = (base + prefixWidth).sp,
        )
    }
    pushStyle(ParagraphStyle(textIndent = textIndent))

    val markerStart = length
    pushStyle(markerSpanStyle(state, item))
    append(markerText)
    pop()
    val markerEnd = length
    // marker 映射到 item 内容起点（pm 零宽区间）
    mapBuilder.registerRange(pmItemStart, pmItemStart, markerStart, markerEnd)
    mapBuilder.registerListMarkerRange(markerStart, markerEnd)

    return true
}

/** marker 的 SpanStyle：InheritFromText 时取 item 首个文本后代的样式（去掉内容装饰）。 */
@OptIn(ExperimentalProseMirrorApi::class)
private fun markerSpanStyle(state: ProseMirrorState, item: Node): SpanStyle {
    if (state.config.listMarkerStyleBehavior == ListMarkerStyleBehavior.AlwaysDefault) return SpanStyle()
    val firstTextMarks = findFirstTextMarks(item) ?: return SpanStyle()
    return firstTextMarks
        .map { mark -> MarkMapper.map(mark, state.config) }
        .fold(SpanStyle()) { acc, style -> acc.merge(style) }
        .copy(
            textDecoration = null,
            background = Color.Unspecified,
            baselineShift = androidx.compose.ui.text.style.BaselineShift.None,
            shadow = null,
            textGeometricTransform = null,
        )
}

/** 深度优先查找 item 内第一个文本节点的 marks。 */
private fun findFirstTextMarks(node: Node): List<Mark>? {
    if (node.isText) return node.marks
    var found: List<Mark>? = null
    node.content.forEach { child, _, _ ->
        if (found != null) return@forEach
        found = findFirstTextMarks(child)
    }
    return found
}
