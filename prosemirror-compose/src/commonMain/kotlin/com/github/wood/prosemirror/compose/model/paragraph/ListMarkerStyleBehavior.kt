package com.github.wood.prosemirror.compose.model.paragraph

import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi

/**
 * 控制列表项前缀（"•"、"1." 等）如何继承段落文本的样式。
 *
 * @see com.github.wood.prosemirror.compose.model.ProseMirrorConfig.listMarkerStyleBehavior
 */
@ExperimentalProseMirrorApi
public enum class ListMarkerStyleBehavior {
    /**
     * marker 继承段落的排版（颜色、字号、字体族、字重、斜体、字距），
     * 但丢弃与文本内容本身绑定的装饰（下划线、删除线、背景高亮、基线偏移、阴影、几何变换）。
     *
     * 默认值。与 Google Docs 等编辑器一致：粗体和字号作用于圆点，下划线不作用于圆点。
     */
    InheritFromText,

    /**
     * marker 始终使用默认 SpanStyle 渲染，无论段落文本带什么格式。
     */
    AlwaysDefault,
}
