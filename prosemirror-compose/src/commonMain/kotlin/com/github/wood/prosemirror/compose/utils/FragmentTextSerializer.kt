package com.github.wood.prosemirror.compose.utils

import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Node

/**
 * 把片段序列化为与编辑器扁平文本一致的纯文本：
 * 块间 "\n"、列表 marker（"- "/"1. "）、token label、image 占位符、hard_break "\n"。
 *
 * 用于 `toText(range)`：范围提取保留了部分覆盖段的列表/段落包装，
 * 因此选中的列表内容会像参考版一样带上自己的 marker。
 */
internal fun Fragment.toFlatText(): String = buildString {
    appendTextBlockFragment(this@toFlatText)
}

private fun StringBuilder.appendTextBlockFragment(fragment: Fragment) {
    var first = true
    fragment.forEach { node, _, _ ->
        if (!first) append('\n')
        appendTextBlockNode(node)
        first = false
    }
}

private fun StringBuilder.appendTextBlockNode(node: Node) {
    when (node.type.name) {
        "paragraph", "heading" -> appendInlineTextFragment(node.content)
        "bullet_list" -> appendTextList(node, ordered = false, start = 1)
        "ordered_list" -> appendTextList(
            node,
            ordered = true,
            start = (node.attrs["order"] as? Number)?.toInt() ?: 1,
        )

        "list_item" -> appendTextListItem(node, ordered = false, number = 1)
        else -> append(node.textContent)
    }
}

private fun StringBuilder.appendTextList(node: Node, ordered: Boolean, start: Int) {
    var number = start
    var first = true
    node.content.forEach { item, _, _ ->
        if (!first) append('\n')
        appendTextListItem(item, ordered, number)
        first = false
        number++
    }
}

private fun StringBuilder.appendTextListItem(node: Node, ordered: Boolean, number: Int) {
    var childIndex = 0
    node.content.forEach { child, _, _ ->
        if (childIndex == 0 && child.isTextblock) {
            append(if (ordered) "$number. " else "- ")
            appendInlineTextFragment(child.content)
        } else {
            append('\n')
            appendTextBlockNode(child)
        }
        childIndex++
    }
}

private fun StringBuilder.appendInlineTextFragment(fragment: Fragment) {
    fragment.forEach { node, _, _ ->
        when {
            node.isText -> append(node.text.orEmpty())
            node.type.name == "token" || node.type.name == "mention" ->
                append(node.attrs["label"] as? String ?: node.textContent)

            node.type.name == "image" -> append(InlineContentPlaceholder)
            node.type.name == "hard_break" -> append('\n')
            else -> append(node.textContent)
        }
    }
}
