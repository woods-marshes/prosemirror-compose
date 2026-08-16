package com.github.wood.prosemirror.compose.schema

import com.atlassian.prosemirror.model.AttributeSpec
import com.atlassian.prosemirror.model.DOMOutputSpec
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkSpec
import com.atlassian.prosemirror.model.ParseRule
import com.atlassian.prosemirror.model.ParseRuleMatch
import com.atlassian.prosemirror.model.StyleParseRuleImpl
import com.atlassian.prosemirror.model.TagParseRuleImpl
import com.atlassian.prosemirror.model.styles

private val emDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("em", 0))
private val strongDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("strong", 0))
private val codeDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("code", 0))
private val strikeDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("s", 0))
private val underlineDOM: DOMOutputSpec = DOMOutputSpec.ArrayDOMOutputSpec(listOf("u", 0))

private fun attr(default: Any?, validate: String? = null): AttributeSpec = object : AttributeSpec {
    override val default: Any? = default
    override val hasDefault: Boolean = true
    override val validateString: String? = validate
    override val validateFunction: ((value: Any?) -> Unit)? = null
}

/**
 * 默认 schema 的 mark 类型定义。
 *
 * `link` 的 `inclusive = false` 复刻参考版 `RichSpanStyle.Link.acceptNewTextInTheEdges = false`：
 * 在链接边缘输入文字不会延续链接样式。
 *
 * `textStyle` 是通用文本样式 mark（仅 color/fontSize），
 * 承载参考版 `addSpanStyle(SpanStyle)` 可表示的字段子集；
 * 其余 SpanStyle 字段（background/shadow/fontFamily 等）在格式化 API 中被静默丢弃。
 */
internal val DefaultMarkSpecs: Map<String, MarkSpec> = mapOf(
    "link" to object : MarkSpec {
        override val attrs: Map<String, AttributeSpec>? = mapOf(
            "href" to attr("", "String"),
            "title" to attr(null, "String|null"),
        )
        override val inclusive: Boolean? = false
        override val excludes: String? = null
        override val group: String? = null
        override val spanning: Boolean? = null
        override val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)? = { mark, _ ->
            DOMOutputSpec.ArrayDOMOutputSpec(
                listOf(
                    "a",
                    mapOf(
                        "href" to mark.attrs["href"],
                        "title" to mark.attrs["title"],
                    ),
                    0,
                )
            )
        }
        override val parseDOM: List<ParseRule>? = listOf(
            TagParseRuleImpl(tag = "a[href]", getNodeAttrs = { dom ->
                ParseRuleMatch(
                    mapOf<String, Any?>(
                        "href" to dom.attribute("href")?.value,
                        "title" to dom.attribute("title")?.value,
                    )
                )
            })
        )
    },

    "em" to object : MarkSpec {
        override val attrs: Map<String, AttributeSpec>? = null
        override val inclusive: Boolean? = null
        override val excludes: String? = null
        override val group: String? = null
        override val spanning: Boolean? = null
        override val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)? = { _, _ -> emDOM }
        override val parseDOM: List<ParseRule>? = listOf(
            TagParseRuleImpl(tag = "i"),
            TagParseRuleImpl(tag = "em"),
            StyleParseRuleImpl(style = "font-style=italic"),
            StyleParseRuleImpl(style = "font-style=normal", clearMark = { m -> m.type.name == "em" }),
        )
    },

    // 包含 Google Docs 粘贴防护规则（<b> 包 normal font-weight 时不匹配）
    "strong" to object : MarkSpec {
        override val attrs: Map<String, AttributeSpec>? = null
        override val inclusive: Boolean? = null
        override val excludes: String? = null
        override val group: String? = null
        override val spanning: Boolean? = null
        override val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)? = { _, _ -> strongDOM }
        override val parseDOM: List<ParseRule>? = listOf(
            TagParseRuleImpl(tag = "strong"),
            TagParseRuleImpl(tag = "b", getNodeAttrs = { node ->
                ParseRuleMatch(null, node.styles()?.get("font-weight") != "normal")
            }),
            StyleParseRuleImpl(style = "font-weight=400", clearMark = { m -> m.type.name == "strong" }),
            StyleParseRuleImpl(style = "font-weight", getStyleAttrs = { value ->
                val regex = "^bold(er)?|[5-9]\\d{2,}".toRegex()
                ParseRuleMatch(null, regex.matches(value))
            }),
        )
    },

    "code" to object : MarkSpec {
        override val attrs: Map<String, AttributeSpec>? = null
        override val inclusive: Boolean? = null
        override val excludes: String? = null
        override val group: String? = null
        override val spanning: Boolean? = null
        override val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)? = { _, _ -> codeDOM }
        override val parseDOM: List<ParseRule>? = listOf(TagParseRuleImpl(tag = "code"))
    },

    "strike" to object : MarkSpec {
        override val attrs: Map<String, AttributeSpec>? = null
        override val inclusive: Boolean? = null
        override val excludes: String? = null
        override val group: String? = null
        override val spanning: Boolean? = null
        override val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)? = { _, _ -> strikeDOM }
        override val parseDOM: List<ParseRule>? = listOf(
            TagParseRuleImpl(tag = "s"),
            TagParseRuleImpl(tag = "strike"),
            TagParseRuleImpl(tag = "del"),
            StyleParseRuleImpl(style = "text-decoration=line-through"),
            StyleParseRuleImpl(style = "text-decoration=line-through", clearMark = { m -> m.type.name == "strike" }),
        )
    },

    "underline" to object : MarkSpec {
        override val attrs: Map<String, AttributeSpec>? = null
        override val inclusive: Boolean? = null
        override val excludes: String? = null
        override val group: String? = null
        override val spanning: Boolean? = null
        override val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)? = { _, _ -> underlineDOM }
        override val parseDOM: List<ParseRule>? = listOf(
            TagParseRuleImpl(tag = "u"),
            StyleParseRuleImpl(style = "text-decoration=underline"),
            StyleParseRuleImpl(style = "text-decoration=underline", clearMark = { m -> m.type.name == "underline" }),
        )
    },

    // 通用文本样式：仅 color（hex 字符串）与 fontSize（px 数值，Float）
    "textStyle" to object : MarkSpec {
        override val attrs: Map<String, AttributeSpec>? = mapOf(
            "color" to attr(null, "String|null"),
            "fontSize" to attr(null, "Number|null"),
        )
        override val inclusive: Boolean? = null
        override val excludes: String? = null
        override val group: String? = null
        override val spanning: Boolean? = null
        override val toDOM: ((mark: Mark, inline: Boolean) -> DOMOutputSpec)? = { mark, _ ->
            val color = mark.attrs["color"]
            val fontSize = mark.attrs["fontSize"]
            val styleParts = buildList {
                if (color != null) add("color: $color")
                if (fontSize != null) add("font-size: ${fontSize}px")
            }
            DOMOutputSpec.ArrayDOMOutputSpec(listOf("span", mapOf("style" to styleParts.joinToString(";"))))
        }
        override val parseDOM: List<ParseRule>? = listOf(
            StyleParseRuleImpl(style = "color", getStyleAttrs = { value ->
                ParseRuleMatch(mapOf<String, Any?>("color" to value))
            }),
            StyleParseRuleImpl(style = "font-size", getStyleAttrs = { value ->
                val px = value.removeSuffix("px").trim().toFloatOrNull()
                ParseRuleMatch(if (px != null) mapOf<String, Any?>("fontSize" to px) else null)
            }),
        )
    },
)
