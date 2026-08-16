package com.github.wood.prosemirror.compose.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.paragraph.ListMarkerStyleBehavior
import com.github.wood.prosemirror.compose.model.paragraph.ListPrefixAlignment
import com.github.wood.prosemirror.compose.model.paragraph.OrderedListStyleType
import com.github.wood.prosemirror.compose.model.paragraph.UnorderedListStyleType

public class ProseMirrorConfig internal constructor(
    private val updateText: () -> Unit = {}
) {
    public var linkColor: Color = Color.Blue
        set(value) {
            field = value
            updateText()
        }

    public var linkTextDecoration: TextDecoration = TextDecoration.Underline
        set(value) {
            field = value
            updateText()
        }

    public var codeSpanColor: Color = Color.Unspecified
        set(value) {
            field = value
            updateText()
        }

    public var codeSpanBackgroundColor: Color = Color.Transparent
        set(value) {
            field = value
            updateText()
        }

    public var codeSpanStrokeColor: Color = Color.LightGray
        set(value) {
            field = value
            updateText()
        }

    /** 有序列表每层缩进（sp）。 */
    public var orderedListIndent: Int = DefaultListIndent
        set(value) {
            field = value
            updateText()
        }

    /** 无序列表每层缩进（sp）。 */
    public var unorderedListIndent: Int = DefaultListIndent
        set(value) {
            field = value
            updateText()
        }

    /** 同时设置 [orderedListIndent] 与 [unorderedListIndent] 的快捷方式。 */
    public var listIndent: Int = DefaultListIndent
        set(value) {
            field = value
            orderedListIndent = value
            unorderedListIndent = value
            updateText()
        }

    /** 无序列表 marker 样式（按层级取前缀）。 */
    @ExperimentalProseMirrorApi
    public var unorderedListStyleType: UnorderedListStyleType =
        UnorderedListStyleType.from("•", "◦", "▪")
        set(value) {
            field = value
            updateText()
        }

    /** 有序列表 marker 样式。 */
    @ExperimentalProseMirrorApi
    public var orderedListStyleType: OrderedListStyleType =
        OrderedListStyleType.Multiple(
            OrderedListStyleType.Decimal,
            OrderedListStyleType.LowerRoman,
            OrderedListStyleType.LowerAlpha,
        )
        set(value) {
            field = value
            updateText()
        }

    /** marker 是否继承段落文本的排版。 */
    @ExperimentalProseMirrorApi
    public var listMarkerStyleBehavior: ListMarkerStyleBehavior = ListMarkerStyleBehavior.InheritFromText
        set(value) {
            field = value
            updateText()
        }

    /** marker 相对缩进槽的对齐方式。 */
    @ExperimentalProseMirrorApi
    public var listPrefixAlignment: ListPrefixAlignment = ListPrefixAlignment.End
        set(value) {
            field = value
            updateText()
        }

    /** 在空行上换行时保留前一段的样式（Enter 后新段继承 marks）。 */
    public var preserveStyleOnEmptyLine: Boolean = true
        set(value) {
            field = value
            updateText()
        }

    /** 在空列表项上按 Enter 时退出列表（该项变为普通段落）。 */
    public var exitListOnEmptyItem: Boolean = true
        set(value) {
            field = value
            updateText()
        }
}

internal const val DefaultListIndent = 38