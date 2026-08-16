package com.github.wood.prosemirror.compose.parser.markdown

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

private val TokenLinkRegex = Regex("""<a href="trigger:([^":]+):([^"]+)">([^<]*)</a>""")

/**
 * Markdown → HTML，随后交给 ProseMirror 的 DOMParser 落树。
 * 使用与 compose-rich-editor 相同的 JetBrains markdown AST/HTML 生成器（GFM），
 * 因此标题、列表、链接、代码、删除线等块级与行内结构无需重复实现。
 *
 * `[label](trigger:triggerId:id)` 伪链接会先转换成 token 原子节点，
 * 保证 `toMarkdown` 导出的 mention token 可以往返。
 */
internal fun markdownToHtml(markdown: String): String {
    val flavour = GFMFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
    val html = HtmlGenerator(markdown, parsedTree, flavour, false).generateHtml()
    return TokenLinkRegex.replace(html) { match ->
        val triggerId = match.groupValues[1]
        val id = match.groupValues[2]
        val label = match.groupValues[3]
        buildString {
            append("<span data-token-trigger-id=\"")
            append(triggerId)
            append("\" data-token-id=\"")
            append(id)
            append("\" data-token-label=\"")
            append(label)
            append("\">")
            append(label)
            append("</span>")
        }
    }
}
