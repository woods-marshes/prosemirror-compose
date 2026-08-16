package com.github.wood.prosemirror.compose.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkType
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.util.resolveSafe
import com.github.wood.prosemirror.compose.utils.MarkMapper

/** Color → 规范化的 `#RRGGBB` 十六进制字符串（textStyle mark 的存储格式）。 */
internal fun Color.toHexString(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** textStyle mark 的 color 属性 → [Color]。 */
internal fun parseColorAttr(value: Any?): Color =
    (value as? String)?.let { hex ->
        runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }
    }?.getOrNull() ?: Color.Unspecified

/**
 * 将可表示的 [SpanStyle] 字段映射为 PM marks。
 *
 * 可映射：bold→strong、italic→em、underline→underline、lineThrough→strike、
 * color→textStyle、fontSize→textStyle。
 *
 * 静默丢弃（无法在默认 schema 中表达）：fontFamily（含 Monospace——code 只能通过
 * [addCodeSpan] 设置）、background、shadow、letterSpacing、baselineShift 等。
 */
internal fun SpanStyle.toMarksToAdd(schema: Schema): List<Mark> {
    val marks = mutableListOf<Mark>()
    if (fontWeight == FontWeight.Bold) marks += schema.mark("strong")
    if (fontStyle == FontStyle.Italic) marks += schema.mark("em")
    if (textDecoration?.contains(TextDecoration.Underline) == true) marks += schema.mark("underline")
    if (textDecoration?.contains(TextDecoration.LineThrough) == true) marks += schema.mark("strike")
    if (color.isSpecified) marks += schema.mark("textStyle", mapOf<String, Any?>("color" to color.toHexString()))
    if (fontSize != TextUnit.Unspecified) {
        marks += schema.mark("textStyle", mapOf<String, Any?>("fontSize" to fontSize.value))
    }
    return marks
}

/** [toMarksToAdd] 的逆映射——需要移除的 mark 类型。 */
internal fun SpanStyle.toMarkTypesToRemove(schema: Schema): List<MarkType> {
    val types = mutableListOf<MarkType>()
    if (fontWeight == FontWeight.Bold) types += schema.mark("strong").type
    if (fontStyle == FontStyle.Italic) types += schema.mark("em").type
    if (textDecoration?.contains(TextDecoration.Underline) == true) types += schema.mark("underline").type
    if (textDecoration?.contains(TextDecoration.LineThrough) == true) types += schema.mark("strike").type
    if (color.isSpecified) types += schema.mark("textStyle").type
    if (fontSize != TextUnit.Unspecified) types += schema.mark("textStyle").type
    return types
}

/** marks → 合并的 [SpanStyle]（复用 [MarkMapper]，含 textStyle 分支）。 */
internal fun List<Mark>.toSpanStyle(config: ProseMirrorConfig? = null): SpanStyle =
    fold(SpanStyle()) { acc, mark ->
        val style = MarkMapper.map(mark, config)
        if (style == SpanStyle()) acc else acc.merge(style)
    }

/** 扁平选区 → PM [from, to]。端点经坐标映射自动吸附原子/边界。 */
internal fun ProseMirrorState.pmRangeOf(range: TextRange): Pair<Int, Int> {
    val length = textFieldValue.text.length
    return flatToPm(range.min.coerceIn(0, length)) to flatToPm(range.max.coerceIn(0, length))
}

/** 折叠选区处的活跃 mark 集合（storedMarks 优先，其次光标解析位置）。 */
internal fun ProseMirrorState.caretMarkSet(): List<Mark> {
    editorState.storedMarks?.let { return it }
    val pos = flatToPm(textFieldValue.selection.min)
    val resolved = doc.resolveSafe(pos) ?: return emptyList()
    return resolved.marks()
}

/** 非折叠选区覆盖范围内共有的 mark 集合。 */
internal fun ProseMirrorState.selectionMarkSet(): List<Mark> {
    val (from, to) = pmRangeOf(textFieldValue.selection)
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
    return common
}

/**
 * 选区对应的 mark 集合：折叠取 [caretMarkSet]，否则取 [selectionMarkSet]。
 */
internal fun ProseMirrorState.activeMarkSet(): List<Mark> =
    if (textFieldValue.selection.collapsed) caretMarkSet() else selectionMarkSet()
