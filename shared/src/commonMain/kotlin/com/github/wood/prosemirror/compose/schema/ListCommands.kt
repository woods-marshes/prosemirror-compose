package com.github.wood.prosemirror.compose.schema

import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.model.NodeRange
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.model.util.resolveSafe
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.state.Transaction
import com.atlassian.prosemirror.transform.findWrapping
import com.atlassian.prosemirror.transform.lift
import com.atlassian.prosemirror.transform.liftTarget
import com.atlassian.prosemirror.transform.split
import com.atlassian.prosemirror.transform.canSplit
import com.atlassian.prosemirror.transform.wrap

/**
 * 手写移植 prosemirror-schema-list 的列表命令。
 *
 * 原版算法基于 JS 版 prosemirror-schema-list；此处以 prosemirror-kotlin
 * transform 模块的 findWrapping/wrap/lift/split 原语实现。
 *
 * 与上游的已知差异：
 * - 不实现 wrap 后与相邻同类型列表合并（joinBefore）——连续列表保持独立节点；
 * - lift 嵌套列表项时直接提升到外层列表层级（语义一致，实现简化）。
 */

/** 选区是否位于 [nodeType] 类型的祖先内。 */
internal fun Transaction.selectionInside(nodeType: NodeType): Boolean {
    val _from = selection._from
    val _to = selection._to
    for (d in _from.depth downTo 1) {
        if (_from.node(d).type == nodeType) return true
    }
    for (d in _to.depth downTo 1) {
        if (_to.node(d).type == nodeType) return true
    }
    return false
}

/** 将选区包裹进 [listType] 列表（已在同类型列表中时返回 false）。 */
internal fun wrapInList(tr: Transaction, listType: NodeType, attrs: Attrs? = null): Boolean {
    if (tr.selectionInside(listType)) return false

    val range = tr.selection._from.blockRange(
        tr.selection._to,
        pred = { node -> node.isBlock && !node.isTextblock },
    ) ?: return false

    val wrappers = findWrapping(range, listType, attrs) ?: return false
    wrap(tr, range, wrappers)
    // 调整光标：wrap 后 selection 映射可能落在列表末尾（非 textblock 内），
    // 往回找到最近的 textblock 内部位置
    var caret = tr.selection.from
    while (caret > 0) {
        val resolved = tr.doc.resolveSafe(caret) ?: break
        if (resolved.parent.isTextblock) break
        caret--
    }
    if (caret > 0) {
        tr.setSelection(TextSelection.create(tr.doc, caret, caret))
    }
    return true
}

/** 将选区所在列表项提升一级（顶层列表 → 文档层；嵌套列表 → 外层列表）。 */
internal fun liftListItem(tr: Transaction, itemType: NodeType): Boolean {
    val _from = tr.selection._from
    val _to = tr.selection._to

    // 找到覆盖选区的列表节点（最深匹配：firstChild 是 itemType 的节点）
    val range = _from.blockRange(
        _to,
        pred = { node -> node.childCount > 0 && node.firstChild?.type == itemType },
    ) ?: return false

    if (_from.node(range.depth - 1).type == itemType) {
        // 列表嵌套在 item 内 → 提升到外层列表
        liftToOuterList(tr, itemType, range)
    } else {
        // 顶层列表 → 提出到文档层
        liftOutOfList(tr, range)
    }
    return true
}

/** range.parent 中 [index] 个 item 的绝对起始位置。 */
private fun NodeRange.itemStart(index: Int): Int {
    var pos = start
    for (j in startIndex until index) pos += parent.child(j).nodeSize
    return pos
}

/** 提升列表项到外层列表（内层列表的 item 并入外层列表）。 */
private fun liftToOuterList(tr: Transaction, itemType: NodeType, range: NodeRange) {
    val outerListDepth = range.depth - 2
    // 从后往前逐个提升，避免位置失效
    for (i in range.endIndex - 1 downTo range.startIndex) {
        val item = range.parent.child(i)
        if (item.type != itemType) continue
        val itemStart = range.itemStart(i)
        val itemEnd = itemStart + item.nodeSize
        val itemRange = itemContentRange(tr, itemStart, itemEnd, range.depth + 1)
        lift(tr, itemRange, outerListDepth)
    }
    removeEmptyContainers(tr, range.start)
}

/** 将选区内的列表项提出到文档层。 */
private fun liftOutOfList(tr: Transaction, range: NodeRange) {
    for (i in range.endIndex - 1 downTo range.startIndex) {
        val itemStart = range.itemStart(i)
        val itemEnd = itemStart + range.parent.child(i).nodeSize
        val itemRange = itemContentRange(tr, itemStart, itemEnd, range.depth + 1)
        val target = liftTarget(itemRange) ?: continue
        lift(tr, itemRange, target)
    }
    removeEmptyContainers(tr, range.start)
}

/**
 * 构造列表项内容的 NodeRange。
 * 注意：必须用 item 的"内容边界"（start+1 / end-1）解析位置——
 * item 边界位置本身 resolve 后 depth 不足（位于 item 之前），
 * 会导致 NodeRange.parent（node(depth)）越界。
 */
private fun itemContentRange(tr: Transaction, itemStart: Int, itemEnd: Int, depth: Int): NodeRange {
    val resolvedStart = tr.doc.resolve(itemStart + 1)
    val resolvedEnd = tr.doc.resolve((itemEnd - 1).coerceAtLeast(itemStart + 1))
    return NodeRange(resolvedStart, resolvedEnd, depth)
}

/** 删除光标位置向上所有空的 list_item / bullet_list / ordered_list 容器。 */
internal fun removeEmptyContainers(tr: Transaction, startPos: Int) {
    var pos = startPos
    while (true) {
        val resolved = tr.doc.resolveSafe(pos) ?: break
        var changed = false
        for (d in resolved.depth downTo 1) {
            val node = resolved.node(d)
            if ((node.type.name == "list_item" || node.type.name == "bullet_list" || node.type.name == "ordered_list") &&
                node.childCount == 0
            ) {
                tr.delete(resolved.start(d), resolved.end(d))
                pos = resolved.start(d)
                changed = true
                break
            }
        }
        if (!changed) break
    }
}

/** 在光标处拆分列表项（Enter）：内容一分为二，后半段包进新的 list_item。 */
internal fun splitListItem(tr: Transaction, itemType: NodeType, itemAttrs: Attrs? = null): Boolean {
    val _from = tr.selection._from

    // 找到光标所在的 list_item
    var itemDepth = -1
    for (d in _from.depth downTo 1) {
        if (_from.node(d).type == itemType) {
            itemDepth = d
            break
        }
    }
    if (itemDepth < 0) return false

    // 需要拆分的层级：textblock + item（item 直接包含 textblock 时为 2）
    val splitDepth = _from.depth - itemDepth + 1
    val typesAfter = List<NodeBase?>(splitDepth) { i ->
        when {
            i == 0 -> NodeBase(itemType, itemAttrs)
            i == splitDepth - 1 -> null // 最深层 textblock 保持原类型
            else -> null
        }
    }
    if (canSplit(tr.doc, _from.pos, splitDepth, typesAfter)) {
        split(tr, _from.pos, splitDepth, typesAfter)
        return true
    }

    // canSplit 失败：光标位于 item 内容末尾（after-part 为空导致 replaceChild 越界）。
    // fallback：在 item 之后直接插入一个包含空 textblock 的新 item。
    if (_from.parentOffset != _from.parent.content.size) return false
    val itemEnd = _from.end(itemDepth)
    val emptyBlock = _from.parent.type.createAndFill() ?: return false
    val newItem = itemType.createAndFill(itemAttrs, emptyBlock, null) ?: return false
    tr.replaceWith(itemEnd, itemEnd, newItem)
    tr.setSelection(
        TextSelection.create(tr.doc, itemEnd + 1 + 1, itemEnd + 1 + 1) // item 开销 + textblock 起点
    )
    return true
}
