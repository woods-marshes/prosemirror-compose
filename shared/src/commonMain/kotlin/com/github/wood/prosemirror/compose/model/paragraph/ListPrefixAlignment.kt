package com.github.wood.prosemirror.compose.model.paragraph

import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi

/**
 * 控制列表 marker（"1."、"10."、"•" 等）相对缩进槽的位置。
 */
@ExperimentalProseMirrorApi
public enum class ListPrefixAlignment {
    /**
     * HTML 默认：marker 位于缩进槽内并在内容起点处结束，
     * 不同宽度的 marker 右对齐（"1."、"10."、"11." 的点竖直对齐）。
     *
     * 当配置的缩进小于 marker 宽度时，该段回退到 [Start]，保证 marker 可见。
     */
    End,

    /**
     * 每个列表项的 marker 从同一条左边缘开始；
     * 因此个位数与多位数编号后的文本起点 x 不同。
     */
    Start,
}
