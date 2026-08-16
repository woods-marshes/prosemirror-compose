package com.github.wood.prosemirror.compose.schema

import com.atlassian.prosemirror.model.AttributeSpec
import com.atlassian.prosemirror.model.DOMOutputSpec
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeSpec
import com.atlassian.prosemirror.model.ParseRuleMatch
import com.atlassian.prosemirror.model.TagParseRuleImpl
import com.atlassian.prosemirror.model.Whitespace

private val paragraphDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("p", 0))
private val bulletListDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("ul", 0))
private val listItemDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("li", 0))
private val imageDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("img"))
private val hardBreakDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("br"))

private fun attr(default: Any?, validate: String? = null): AttributeSpec = object : AttributeSpec {
    override val default: Any? = default
    override val hasDefault: Boolean = true
    override val validateString: String? = validate
    override val validateFunction: ((value: Any?) -> Unit)? = null
}

/**
 * 默认 schema 的节点类型定义。
 * 与参考版（richeditor-compose）支持的富文本能力对齐：
 * 段落、标题（1-6 级）、无序/有序列表、图片、token（@提及）、硬换行。
 */
internal val DefaultNodeSpecs: Map<String, NodeSpec> = mapOf(
    // 顶层文档节点
    "doc" to object : NodeSpec {
        override val content: String? = "block+"
        override val marks: String? = null
        override val group: String? = null
        override val inline: Boolean = false
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = null
        override val selectable: Boolean = true
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = null
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = null
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? = null
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = null
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // 普通段落，textAlign 支持对齐
    "paragraph" to object : NodeSpec {
        override val content: String? = "inline*"
        override val marks: String? = null
        override val group: String? = "block"
        override val inline: Boolean = false
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = mapOf("textAlign" to attr(null, "String|null"))
        override val selectable: Boolean = true
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = null
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = { paragraphDOM }
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? =
            listOf(TagParseRuleImpl(tag = "p"))
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = null
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // 标题，level 1..6，解析/序列化为 <h1>..<h6>
    "heading" to object : NodeSpec {
        override val content: String? = "inline*"
        override val marks: String? = null
        override val group: String? = "block"
        override val inline: Boolean = false
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = mapOf(
            "level" to attr(1, "Int"),
            "textAlign" to attr(null, "String|null"),
        )
        override val selectable: Boolean = true
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = true
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = { node ->
            DOMOutputSpec.ArrayDOMOutputSpec(listOf("h" + node.attrs["level"], 0))
        }
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? = listOf(
            TagParseRuleImpl(tag = "h1", attrs = mapOf("level" to 1)),
            TagParseRuleImpl(tag = "h2", attrs = mapOf("level" to 2)),
            TagParseRuleImpl(tag = "h3", attrs = mapOf("level" to 3)),
            TagParseRuleImpl(tag = "h4", attrs = mapOf("level" to 4)),
            TagParseRuleImpl(tag = "h5", attrs = mapOf("level" to 5)),
            TagParseRuleImpl(tag = "h6", attrs = mapOf("level" to 6)),
        )
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = null
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // 无序列表
    "bullet_list" to object : NodeSpec {
        override val content: String? = "list_item+"
        override val marks: String? = null
        override val group: String? = "block"
        override val inline: Boolean = false
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = null
        override val selectable: Boolean = true
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = null
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = { bulletListDOM }
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? =
            listOf(TagParseRuleImpl(tag = "ul"))
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = null
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // 有序列表，order 为起始序号（HTML <ol start>）
    "ordered_list" to object : NodeSpec {
        override val content: String? = "list_item+"
        override val marks: String? = null
        override val group: String? = "block"
        override val inline: Boolean = false
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = mapOf("order" to attr(1, "Int"))
        override val selectable: Boolean = true
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = null
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = { node ->
            val order = node.attrs["order"]
            if (order != null && order != 1) {
                DOMOutputSpec.ArrayDOMOutputSpec(listOf("ol", mapOf("start" to order), 0))
            } else {
                DOMOutputSpec.ArrayDOMOutputSpec(listOf("ol", 0))
            }
        }
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? = listOf(
            TagParseRuleImpl(tag = "ol", getNodeAttrs = { dom ->
                val start = dom.attribute("start")?.value
                ParseRuleMatch(mapOf<String, Any?>("order" to (start?.toIntOrNull() ?: 1)), matches = true)
            })
        )
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = null
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // 列表项，block+ 允许列表项内包含标题等块级节点
    "list_item" to object : NodeSpec {
        override val content: String? = "block+"
        override val marks: String? = null
        override val group: String? = null
        override val inline: Boolean = false
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = null
        override val selectable: Boolean = true
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = true
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = { listItemDOM }
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? =
            listOf(TagParseRuleImpl(tag = "li"))
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = null
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // 内联图片
    "image" to object : NodeSpec {
        override val content: String? = null
        override val marks: String? = null
        override val group: String? = "inline"
        override val inline: Boolean = true
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = mapOf(
            "src" to attr("", "String"),
            "alt" to attr(null, "String|null"),
            "width" to attr(null, "Number|null"),
            "height" to attr(null, "Number|null"),
        )
        override val selectable: Boolean = true
        override val draggable: Boolean = true
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = null
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = { node ->
            DOMOutputSpec.ArrayDOMOutputSpec(
                listOf(
                    "img",
                    mapOf(
                        "src" to node.attrs["src"],
                        "alt" to node.attrs["alt"],
                        "width" to node.attrs["width"],
                        "height" to node.attrs["height"],
                    ),
                )
            )
        }
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? = listOf(
            TagParseRuleImpl(tag = "img[src]", getNodeAttrs = { dom ->
                ParseRuleMatch(
                    mapOf<String, Any?>(
                        "src" to dom.attribute("src")?.value,
                        "alt" to dom.attribute("alt")?.value,
                        "width" to dom.attribute("width")?.value?.toDoubleOrNull(),
                        "height" to dom.attribute("height")?.value?.toDoubleOrNull(),
                    ),
                    matches = true,
                )
            })
        )
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = null
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // token（@提及）原子节点，HTML 以 span[data-token-*] 序列化
    "token" to object : NodeSpec {
        override val content: String? = null
        override val marks: String? = null
        override val group: String? = "inline"
        override val inline: Boolean = true
        override val atom: Boolean = true
        override val attrs: Map<String, AttributeSpec>? = mapOf(
            "triggerId" to attr("", "String"),
            "id" to attr("", "String"),
            "label" to attr("", "String"),
        )
        override val selectable: Boolean = false
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = null
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = { node ->
            DOMOutputSpec.ArrayDOMOutputSpec(
                listOf(
                    "span",
                    mapOf(
                        "data-token-trigger-id" to node.attrs["triggerId"],
                        "data-token-id" to node.attrs["id"],
                        "data-token-label" to node.attrs["label"],
                    ),
                )
            )
        }
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? = listOf(
            TagParseRuleImpl(tag = "span[data-token-trigger-id]", getNodeAttrs = { dom ->
                ParseRuleMatch(
                    mapOf<String, Any?>(
                        "triggerId" to dom.attribute("data-token-trigger-id")?.value,
                        "id" to dom.attribute("data-token-id")?.value,
                        "label" to dom.attribute("data-token-label")?.value,
                    ),
                    matches = true,
                )
            })
        )
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = { (it.attrs["label"] as? String) ?: "" }
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // 硬换行
    "hard_break" to object : NodeSpec {
        override val content: String? = null
        override val marks: String? = null
        override val group: String? = "inline"
        override val inline: Boolean = true
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = null
        override val selectable: Boolean = false
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = null
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = { hardBreakDOM }
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? =
            listOf(TagParseRuleImpl(tag = "br"))
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = { "\n" }
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },

    // 文本节点
    "text" to object : NodeSpec {
        override val content: String? = null
        override val marks: String? = null
        override val group: String? = "inline"
        override val inline: Boolean = true
        override val atom: Boolean = false
        override val attrs: Map<String, AttributeSpec>? = null
        override val selectable: Boolean = true
        override val draggable: Boolean = false
        override val code: Boolean = false
        override val whitespace: Whitespace? = null
        override val definingAsContext: Boolean? = null
        override val definingForContent: Boolean? = null
        override val defining: Boolean? = null
        override val isolating: Boolean? = null
        override val toDOM: ((node: Node) -> DOMOutputSpec)? = null
        override val parseDOM: List<com.atlassian.prosemirror.model.TagParseRule>? = null
        override val toDebugString: ((node: Node) -> String)? = null
        override val leafText: ((node: Node) -> String)? = null
        override val linebreakReplacement: Boolean? = null
        override val autoFocusable: Boolean? = null
    },
)
