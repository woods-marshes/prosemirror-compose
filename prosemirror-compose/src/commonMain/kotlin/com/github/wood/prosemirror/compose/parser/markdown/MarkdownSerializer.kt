package com.github.wood.prosemirror.compose.parser.markdown

import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node

/**
 * 把 ProseMirror fragment 序列化为 Markdown。
 *
 * 支持：段落、ATX 标题、无序/有序列表（含嵌套与 start 序号）、strong/em/
 * strike/code/link/underline、token（`[label](trigger:id:id)` 伪链接）与 image。
 */
internal fun Fragment.toMarkdown(): String = buildString {
    appendBlockFragment(this@toMarkdown, indent = 0)
}

private fun StringBuilder.appendBlockFragment(fragment: Fragment, indent: Int) {
    var first = true
    fragment.forEach { node, _, _ ->
        if (!first) append('\n')
        appendBlockNode(node, indent)
        first = false
    }
}

private fun StringBuilder.appendBlockNode(node: Node, indent: Int) {
    when (node.type.name) {
        "paragraph" -> {
            appendIndent(indent)
            appendInlineFragment(node.content)
        }

        "heading" -> {
            appendIndent(indent)
            val level = (node.attrs["level"] as? Number)?.toInt() ?: 1
            append("#".repeat(level.coerceIn(1, 6)))
            append(' ')
            appendInlineFragment(node.content)
        }

        "bullet_list" -> appendList(node, indent, ordered = false, start = 1)
        "ordered_list" -> appendList(
            node,
            indent,
            ordered = true,
            start = (node.attrs["order"] as? Number)?.toInt() ?: 1,
        )

        "list_item" -> appendListItem(node, indent, ordered = false, number = 1)
        else -> append(node.textContent)
    }
}

private fun StringBuilder.appendList(node: Node, indent: Int, ordered: Boolean, start: Int) {
    var number = start
    var first = true
    node.content.forEach { item, _, _ ->
        if (!first) append('\n')
        appendListItem(item, indent, ordered, number)
        first = false
        number++
    }
}

private fun StringBuilder.appendListItem(node: Node, indent: Int, ordered: Boolean, number: Int) {
    val marker = if (ordered) "$number." else "-"
    val childIndent = indent + if (ordered) 3 else 2
    var childIndex = 0
    node.content.forEach { child, _, _ ->
        if (childIndex == 0 && child.isTextblock) {
            appendIndent(indent)
            append(marker)
            append(' ')
            appendInlineFragment(child.content)
        } else {
            append('\n')
            appendBlockNode(child, childIndent)
        }
        childIndex++
    }
}

private fun StringBuilder.appendInlineFragment(fragment: Fragment) {
    fragment.forEach { node, _, _ ->
        when {
            node.isText -> appendMarkdownText(node.text.orEmpty(), node.marks)
            node.type.name == "token" || node.type.name == "mention" -> {
                val label = node.attrs["label"] as? String ?: node.textContent
                val triggerId = node.attrs["triggerId"] as? String ?: ""
                val id = node.attrs["id"] as? String ?: ""
                append("[${label.escapeMarkdown()}](trigger:${triggerId.escapeMarkdown()}:${id.escapeMarkdown()})")
            }

            node.type.name == "image" -> {
                val src = node.attrs["src"] as? String ?: ""
                val alt = node.attrs["alt"] as? String ?: ""
                append("![${alt.escapeMarkdown()}]($src)")
            }

            node.type.name == "hard_break" -> append("<br>")
            else -> append(node.textContent.escapeMarkdown())
        }
    }
}

private fun StringBuilder.appendMarkdownText(text: String, marks: List<Mark>) {
    val link = marks.firstOrNull { it.type.name == "link" }
    val hasCode = marks.any { it.type.name == "code" }
    val hasStrong = marks.any { it.type.name == "strong" || it.type.name == "bold" }
    val hasEm = marks.any { it.type.name == "em" || it.type.name == "italic" }
    val hasStrike = marks.any { it.type.name == "strike" || it.type.name == "strikethrough" }
    val hasUnderline = marks.any { it.type.name == "underline" }

    var content = if (hasCode) text else text.escapeMarkdown()

    if (hasCode) content = "`$content`"
    if (hasStrong) content = "**$content**"
    if (hasEm) content = "*$content*"
    if (hasStrike) content = "~~$content~~"
    if (hasUnderline) content = "<u>$content</u>"

    if (link != null) {
        val href = link.attrs["href"] as? String ?: ""
        append("[$content]($href)")
    } else {
        append(content)
    }
}

private fun StringBuilder.appendIndent(indent: Int) {
    if (indent > 0) append("  ".repeat(indent))
}

private fun String.escapeMarkdown(): String = buildString {
    this@escapeMarkdown.forEach { char ->
        if (char in MarkdownEscapableChars) append('\\')
        append(char)
    }
}

private val MarkdownEscapableChars = setOf(
    '\\', '`', '*', '_', '{', '}', '[', ']', '<', '>',
    '(', ')', '#', '+', '-', '.', '!', '|',
)
