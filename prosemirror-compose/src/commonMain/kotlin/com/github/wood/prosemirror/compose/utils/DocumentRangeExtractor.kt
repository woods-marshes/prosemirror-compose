package com.github.wood.prosemirror.compose.utils

import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Node

/**
 * 从文档中提取 [from, to] 覆盖的内容，同时保留被部分覆盖节点的祖先包装。
 *
 * 与 `doc.cut` 不同：`cut` 只会切下 doc 的直接内容，因此跨段/跨列表的部分选区
 * 会得到没有 `<p>`/`<ul>` 包装的裸 fragment。这里递归复制部分覆盖的
 * textblock/list/list_item，使序列化结果与选区语义一致。
 */
internal fun Node.extractRangeFragment(from: Int, to: Int): Fragment {
    if (from >= to) return Fragment.empty
    return extractChildrenFragment(this, contentStart = 0, from = from, to = to)
}

private fun extractChildrenFragment(
    node: Node,
    contentStart: Int,
    from: Int,
    to: Int,
): Fragment {
    val selected = mutableListOf<Node>()
    node.content.forEach { child, offset, _ ->
        val childStart = contentStart + offset
        val childEnd = childStart + child.nodeSize
        if (childEnd <= from || childStart >= to) return@forEach

        // 完整覆盖：整节点保留。
        if (from <= childStart && childEnd <= to) {
            selected += child
            return@forEach
        }

        if (child.isText) {
            val localFrom = (from - childStart).coerceAtLeast(0)
            val localTo = (to - childStart).coerceAtMost(child.nodeSize)
            if (localTo > localFrom) {
                selected += child.cut(localFrom, localTo)
            }
            return@forEach
        }

        if (child.isLeaf) return@forEach

        // 部分覆盖的非叶子节点：递归提取其内容，再用原类型/attrs 包回来。
        val inner = extractChildrenFragment(
            node = child,
            contentStart = childStart + 1,
            from = from,
            to = to,
        )
        if (inner.childCount > 0) {
            selected += child.copy(content = inner)
        }
    }
    return Fragment.from(selected)
}
