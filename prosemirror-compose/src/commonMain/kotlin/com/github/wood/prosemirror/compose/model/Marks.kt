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
internal fun Color.toHexString(): String =
    "#" + (toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** textStyle mark 的 color 属性 → [Color]。 */
internal fun parseColorAttr(value: Any?): Color =
    (value as? String)?.let { hex ->
        runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }
    }?.getOrNull() ?: Color.Unspecified

/**
 * 将可表示的 [SpanStyle] 正向字段映射为简单 PM marks（strong/em/underline/strike）。
 * textStyle（color/fontSize/background）单独由 [textStyleAttrsToAdd] 处理，
 * 因为 textStyle mark 同类互斥，必须与已有 attrs 合并后再写入。
 *
 * 静默丢弃（无法在默认 schema 中表达）：fontFamily（含 Monospace——code 只能通过
 * [addCodeSpan] 设置）、shadow、letterSpacing、baselineShift 等。
 */
internal fun SpanStyle.toSimpleMarksToAdd(schema: Schema): List<Mark> {
    val marks = mutableListOf<Mark>()
    if (fontWeight == FontWeight.Bold) marks += schema.mark("strong")
    if (fontStyle == FontStyle.Italic) marks += schema.mark("em")
    if (textDecoration?.contains(TextDecoration.Underline) == true) marks += schema.mark("underline")
    if (textDecoration?.contains(TextDecoration.LineThrough) == true) marks += schema.mark("strike")
    return marks
}

/**
 * [toSimpleMarksToAdd] 的逆映射——[removeSpanStyle] 需要移除的简单 mark 类型。
 * 仅当 [SpanStyle] 显式指定了对应正向值时移除（与参考版 isSpecifiedFieldsEquals
 * 一致：值为 Normal/null 的字段不属于“命中”字段）。
 */
internal fun SpanStyle.toSimpleMarkTypesToRemove(schema: Schema): List<MarkType> {
    val types = mutableListOf<MarkType>()
    if (fontWeight == FontWeight.Bold) types += schema.mark("strong").type
    if (fontStyle == FontStyle.Italic) types += schema.mark("em").type
    if (textDecoration?.contains(TextDecoration.Underline) == true) types += schema.mark("underline").type
    if (textDecoration?.contains(TextDecoration.LineThrough) == true) types += schema.mark("strike").type
    return types
}

/**
 * 显式指定的负向字段需要移除的简单 mark 类型。
 * 例如 `SpanStyle(fontWeight = Normal)` 等价于清除 bold。
 */
internal fun SpanStyle.toSimpleMarkTypesToRemoveForAdd(schema: Schema): List<MarkType> {
    val types = mutableListOf<MarkType>()
    if (fontWeight != null && fontWeight != FontWeight.Bold) types += schema.mark("strong").type
    if (fontStyle != null && fontStyle != FontStyle.Italic) types += schema.mark("em").type
    if (textDecoration != null && textDecoration?.contains(TextDecoration.Underline) != true) {
        types += schema.mark("underline").type
    }
    if (textDecoration != null && textDecoration?.contains(TextDecoration.LineThrough) != true) {
        types += schema.mark("strike").type
    }
    return types
}

/** textStyle mark 上需要新增/覆盖的 attrs。 */
internal fun SpanStyle.textStyleAttrsToAdd(): Map<String, Any?> = buildMap {
    if (color.isSpecified) put("color", color.toHexString())
    if (background.isSpecified) put("background", background.toHexString())
    if (fontSize != TextUnit.Unspecified) put("fontSize", fontSize.value)
}

/** textStyle mark 上需要移除的 attr 名（仅移除指定字段，保留同 mark 上的其它字段）。 */
internal fun SpanStyle.textStyleAttrNamesToRemove(): Set<String> = buildSet {
    if (color.isSpecified) add("color")
    if (background.isSpecified) add("background")
    if (fontSize != TextUnit.Unspecified) add("fontSize")
}

/** marks → 合并的 [SpanStyle]（复用 [MarkMapper]，含 textStyle 分支）。 */
internal fun List<Mark>.toSpanStyle(config: ProseMirrorConfig? = null): SpanStyle =
    fold(SpanStyle()) { acc, mark ->
        val style = MarkMapper.map(mark, config)
        if (style == SpanStyle()) acc else acc.merge(style)
    }

/**
 * 多段文本的共有 [SpanStyle]。与参考版 `getCommonStyle` 一致：
 * 逐字段比较，某字段不一致时置为 unspecified，而不是要求整段 SpanStyle 完全相等。
 * 这样 color 不同的选区仍能识别出共有的 fontSize（反之亦然）。
 */
internal fun List<SpanStyle>.commonSpanStyle(): SpanStyle {
    val first = firstOrNull() ?: return SpanStyle()
    var color = first.color
    var background = first.background
    var fontSize = first.fontSize
    var fontWeight = first.fontWeight
    var fontStyle = first.fontStyle
    var textDecoration = first.textDecoration

    for (index in 1 until size) {
        val other = this[index]
        if (other.color != color) color = Color.Unspecified
        if (other.background != background) background = Color.Unspecified
        if (other.fontSize != fontSize) fontSize = TextUnit.Unspecified
        if (other.fontWeight != fontWeight) fontWeight = null
        if (other.fontStyle != fontStyle) fontStyle = null
        if (other.textDecoration != textDecoration) {
            val firstDecoration = textDecoration
            textDecoration = if (firstDecoration != null && other.textDecoration != null) {
                val commonUnderline = firstDecoration.contains(
                    androidx.compose.ui.text.style.TextDecoration.Underline,
                ) && other.textDecoration!!.contains(
                    androidx.compose.ui.text.style.TextDecoration.Underline,
                )
                val commonLineThrough = firstDecoration.contains(
                    androidx.compose.ui.text.style.TextDecoration.LineThrough,
                ) && other.textDecoration!!.contains(
                    androidx.compose.ui.text.style.TextDecoration.LineThrough,
                )
                when {
                    commonUnderline && commonLineThrough ->
                        androidx.compose.ui.text.style.TextDecoration.Underline +
                            androidx.compose.ui.text.style.TextDecoration.LineThrough

                    commonUnderline -> androidx.compose.ui.text.style.TextDecoration.Underline
                    commonLineThrough -> androidx.compose.ui.text.style.TextDecoration.LineThrough
                    else -> androidx.compose.ui.text.style.TextDecoration.None
                }.takeIf { it != androidx.compose.ui.text.style.TextDecoration.None }
            } else {
                null
            }
        }
    }

    return SpanStyle(
        color = color,
        background = background,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textDecoration = textDecoration,
    )
}

/** 指定 PM 范围内的字段级共有 [SpanStyle]。 */
internal fun ProseMirrorState.spanStyleOverRange(from: Int, to: Int): SpanStyle {
    val styles = mutableListOf<SpanStyle>()
    doc.nodesBetween(from, to, f = { node, _, _, _ ->
        if (node.isText) {
            styles += node.marks.toSpanStyle(config)
        }
        true
    })
    return styles.commonSpanStyle()
}

/** 非折叠选区的字段级共有 [SpanStyle]（折叠选区回退到光标 marks）。 */
internal fun ProseMirrorState.selectionSpanStyle(): SpanStyle {
    if (textFieldValue.selection.collapsed) {
        return caretMarkSet().toSpanStyle(config)
    }
    val (from, to) = pmRangeOf(textFieldValue.selection)
    return spanStyleOverRange(from, to)
}

/** 扁平选区 → PM [from, to]。端点经坐标映射自动吸附原子/边界。 */
internal fun ProseMirrorState.pmRangeOf(range: TextRange): Pair<Int, Int> {
    val length = textFieldValue.text.length
    return flatToPm(range.min.coerceIn(0, length)) to flatToPm(range.max.coerceIn(0, length))
}

/** 折叠选区处的活跃 mark 集合（storedMarks 优先，其次光标前一个字符的 marks）。 */
internal fun ProseMirrorState.caretMarkSet(): List<Mark> {
    editorState.storedMarks?.let { return it }
    if (textFieldValue.selection.min <= 0) return emptyList()
    return marksBeforeFlatPosition(textFieldValue.selection.min - 1)
}

/** 指定扁平位置前一个字符的 marks（折叠选区样式查询与参考版一致）。 */
internal fun ProseMirrorState.marksBeforeFlatPosition(flatPos: Int): List<Mark> {
    if (flatPos < 0) return emptyList()
    val pos = textInputPosition(flatToPm(flatPos))
    val resolved = doc.resolveSafe(pos) ?: return emptyList()
    if (resolved.textOffset > 0) {
        return resolved.parent.child(resolved.index()).marks
    }
    val before = resolved.nodeBefore
    if (before != null && before.isText) {
        return before.marks
    }
    return emptyList()
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
