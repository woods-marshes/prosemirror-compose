package com.github.wood.prosemirror.compose.model

import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * 标题级别（段落级概念）。
 *
 * 在本实现中对应 PM 的 `heading` 节点（level 属性 1..6）；[Normal] 对应 `paragraph` 节点。
 * 持久化 level 而非样式指纹，使标题身份在主题切换、字体定制与 HTML 往返后保持不变。
 *
 * 每个级别携带：
 *  - [level]（0 为 [Normal]，1..6 为 [H1]..[H6]）
 *  - [markdownPrefix]：序列化为 Markdown 时的前缀（`# `、`## `……）
 *  - [htmlTag]：序列化为 HTML 时的标签（`h1`..`h6`，[Normal] 为 null）
 *  - [defaultSpanStyle]：渲染时应用到段内所有文本的默认样式（em 基准，框架无关）
 */
public enum class HeadingStyle(
    public val level: Int,
    internal val markdownPrefix: String,
    internal val htmlTag: String?,
    internal val defaultSpanStyle: SpanStyle,
    internal val defaultParagraphStyle: ParagraphStyle = ParagraphStyle(),
) {
    /** 普通段落 */
    Normal(level = 0, markdownPrefix = "", htmlTag = null, defaultSpanStyle = SpanStyle()),

    /** 一级标题（`<h1>`、`# `） */
    H1(1, "# ", "h1", SpanStyle(fontSize = 2.em, fontWeight = FontWeight.Bold)),

    /** 二级标题（`<h2>`、`## `） */
    H2(2, "## ", "h2", SpanStyle(fontSize = 1.5.em, fontWeight = FontWeight.Bold)),

    /** 三级标题（`<h3>`、`### `） */
    H3(3, "### ", "h3", SpanStyle(fontSize = 1.17.em, fontWeight = FontWeight.Bold)),

    /** 四级标题（`<h4>`、`#### `） */
    H4(4, "#### ", "h4", SpanStyle(fontSize = 1.12.em, fontWeight = FontWeight.Bold)),

    /** 五级标题（`<h5>`、`##### `） */
    H5(5, "##### ", "h5", SpanStyle(fontSize = 0.83.em, fontWeight = FontWeight.Bold)),

    /** 六级标题（`<h6>`、`###### `） */
    H6(6, "###### ", "h6", SpanStyle(fontSize = 0.75.em, fontWeight = FontWeight.Bold));

    /** 该标题级别应用到段内文本的视觉 [SpanStyle]。 */
    internal fun getSpanStyle(): SpanStyle = defaultSpanStyle

    /** 该标题级别的视觉 [ParagraphStyle]。 */
    internal fun getParagraphStyle(): ParagraphStyle = defaultParagraphStyle

    public companion object {
        /** HTML 标题标签名集合（`h1`..`h6`）。 */
        internal val headingTags: Set<String> = setOf("h1", "h2", "h3", "h4", "h5", "h6")

        /** 返回 [level]（1..6）对应的 [HeadingStyle]，其他值返回 [Normal]。 */
        public fun fromLevel(level: Int): HeadingStyle =
            entries.firstOrNull { it.level == level } ?: Normal

        /** 返回 HTML 标题 [tag]（`h1`..`h6`）对应的 [HeadingStyle]，其他返回 [Normal]。 */
        internal fun fromHtmlTag(tag: String): HeadingStyle =
            entries.firstOrNull { it.htmlTag == tag } ?: Normal
    }
}
