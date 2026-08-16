package com.github.wood.prosemirror.compose.model.paragraph

import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi

/**
 * 无序列表 marker 样式。[prefixes] 按列表层级（1 起）取值，超出深度时使用最后一个。
 */
@ExperimentalProseMirrorApi
public data class UnorderedListStyleType internal constructor(
    internal val prefixes: List<String>,
) {
    public companion object {
        public fun from(vararg prefix: String): UnorderedListStyleType =
            UnorderedListStyleType(prefix.toList())

        public fun from(prefixes: List<String>): UnorderedListStyleType =
            UnorderedListStyleType(prefixes)

        public val Disc: UnorderedListStyleType = UnorderedListStyleType(listOf("•"))

        public val Circle: UnorderedListStyleType = UnorderedListStyleType(listOf("◦"))

        public val Square: UnorderedListStyleType = UnorderedListStyleType(listOf("▪"))
    }
}
