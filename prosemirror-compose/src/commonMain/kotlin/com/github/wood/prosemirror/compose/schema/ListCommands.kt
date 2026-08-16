package com.github.wood.prosemirror.compose.schema

import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.NodeBase
import com.atlassian.prosemirror.model.NodeRange
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.model.util.resolveSafe
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.state.Transaction
import com.atlassian.prosemirror.transform.ReplaceAroundStep
import com.atlassian.prosemirror.transform.canJoin
import com.atlassian.prosemirror.transform.canSplit
import com.atlassian.prosemirror.transform.findWrapping
import com.atlassian.prosemirror.transform.lift
import com.atlassian.prosemirror.transform.liftTarget
import com.atlassian.prosemirror.transform.split

/**
 * prosemirror-schema-list 的 Kotlin 移植（以 prosemirror-kotlin transform 原语实现）。
 * wrapInList / liftListItem / sinkListItem 按上游算法逐步对应，包括：
 * - 多段落包裹时每个段落拆成独立 list_item；
 * - wrap 到相邻同类型列表之前时合并（joinBefore）；
 * - 嵌套列表的 lift/sink。
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

    val _from = tr.selection._from
    val _to = tr.selection._to
    var range = _from.blockRange(_to) ?: return false
    var outerRange = range
    var doJoin = false

    // 选区位于现有兼容列表项的顶层：把包裹范围扩大/调整到相邻列表之前。
    if (range.depth >= 2 &&
        _from.node(range.depth - 1).type.compatibleContent(listType) &&
        range.startIndex == 0
    ) {
        // 已经是列表第一项，不需要（也无法）再包一层。
        if (_from.index(range.depth - 1) == 0) return false
        val insert = tr.doc.resolve(range.start - 2)
        outerRange = NodeRange(insert, insert, range.depth)
        if (range.endIndex < range.parent.childCount) {
            range = NodeRange(_from, tr.doc.resolve(_to.end(range.depth)), range.depth)
        }
        doJoin = true
    }

    val wrappers = findWrapping(outerRange, listType, attrs, range) ?: return false
    doWrapInList(tr, range, wrappers, doJoin, listType)
    return true
}

/** [wrapInList] 的实际步骤：包一层 wrapper，并把范围内多个块拆成多个 list_item。 */
private fun doWrapInList(
    tr: Transaction,
    range: NodeRange,
    wrappers: List<com.atlassian.prosemirror.model.NodeBase>,
    joinBefore: Boolean,
    listType: NodeType,
) {
    var content = Fragment.empty
    for (i in wrappers.size - 1 downTo 0) {
        content = Fragment.from(wrappers[i].type.create(wrappers[i].attrs, content))
    }

    tr.step(
        ReplaceAroundStep(
            range.start - if (joinBefore) 2 else 0,
            range.end,
            range.start,
            range.end,
            Slice(content, 0, 0),
            wrappers.size,
            true,
        )
    )

    var found = 0
    for (i in wrappers.indices) {
        if (wrappers[i].type == listType) found = i + 1
    }
    val splitDepth = wrappers.size - found

    var splitPos = range.start + wrappers.size - if (joinBefore) 2 else 0
    val parent = range.parent
    var first = true
    for (i in range.startIndex until range.endIndex) {
        if (!first && canSplit(tr.doc, splitPos, splitDepth)) {
            split(tr, splitPos, splitDepth, null)
            splitPos += 2 * splitDepth
        }
        first = false
        splitPos += parent.child(i).nodeSize
    }
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

    return if (_from.node(range.depth - 1).type == itemType) {
        // 列表嵌套在 item 内 → 提升到外层列表
        liftToOuterList(tr, itemType, range)
    } else {
        // 顶层列表 → 提出到文档层
        liftOutOfList(tr, range)
    }
}

/**
 * 提升嵌套列表项到外层列表。若被提升项后面还有兄弟 item，先把后半段挂到
 * 最后一个被提升 item 下，再 lift 整个范围。
 */
private fun liftToOuterList(tr: Transaction, itemType: NodeType, range: NodeRange): Boolean {
    var adjustedRange = range
    val end = range.end
    val endOfList = range.to.end(range.depth)
    if (end < endOfList) {
        tr.step(
            ReplaceAroundStep(
                end - 1,
                endOfList,
                end,
                endOfList,
                Slice(Fragment.from(itemType.create(attrs = null, content = range.parent.copy())), 1, 0),
                1,
                true,
            )
        )
        adjustedRange = NodeRange(tr.doc.resolve(range.from.pos), tr.doc.resolve(endOfList), range.depth)
    }

    val target = liftTarget(adjustedRange) ?: return false
    lift(tr, adjustedRange, target)

    val after = tr.mapping.map(end, -1) - 1
    if (canJoin(tr.doc, after)) tr.join(after)
    return true
}

/** 将选区内的列表项提出到文档层，并与同层相邻列表合并。 */
private fun liftOutOfList(tr: Transaction, range: NodeRange): Boolean {
    val list = range.parent

    // 把选中的多个 item 先合并成一个大 item（保留其中内容）。
    var pos = range.end
    for (i in range.endIndex - 1 downTo range.startIndex + 1) {
        pos -= list.child(i).nodeSize
        tr.delete(pos - 1, pos + 1)
    }

    val start = tr.doc.resolve(range.start)
    val item = start.nodeAfter ?: return false
    if (tr.mapping.map(range.end) != range.start + item.nodeSize) return false

    val atStart = range.startIndex == 0
    val atEnd = range.endIndex == list.childCount
    val parent = start.node(-1)
    val indexBefore = start.index(-1)
    val liftedContent = item.content.append(if (atEnd) Fragment.empty else Fragment.from(list))
    if (!parent.canReplace(indexBefore + if (atStart) 0 else 1, indexBefore + 1, liftedContent)) {
        return false
    }

    val startPos = start.pos
    val endPos = startPos + item.nodeSize
    val outerList = list.copy(Fragment.empty)
    val sliceContent = (if (atStart) Fragment.empty else Fragment.from(outerList))
        .append(if (atEnd) Fragment.empty else Fragment.from(outerList))
    tr.step(
        ReplaceAroundStep(
            startPos - if (atStart) 1 else 0,
            endPos + if (atEnd) 1 else 0,
            startPos + 1,
            endPos - 1,
            Slice(
                sliceContent,
                if (atStart) 0 else 1,
                if (atEnd) 0 else 1,
            ),
            if (atStart) 0 else 1,
            true,
        )
    )
    return true
}

/** 将选区所在列表项下沉一级（挂到前一个 list_item 的嵌套列表中）。 */
internal fun sinkListItem(tr: Transaction, itemType: NodeType): Boolean {
    val _from = tr.selection._from
    val _to = tr.selection._to
    val range = _from.blockRange(
        _to,
        pred = { node -> node.childCount > 0 && node.firstChild?.type == itemType },
    ) ?: return false

    val startIndex = range.startIndex
    if (startIndex == 0) return false
    val parent = range.parent
    val nodeBefore = parent.child(startIndex - 1)
    if (nodeBefore.type != itemType) return false

    val nestedBefore = nodeBefore.lastChild?.type == parent.type
    val inner = if (nestedBefore) Fragment.from(itemType.create()) else Fragment.empty
    val slice = Slice(
        Fragment.from(
            itemType.create(
                attrs = null,
                content = Fragment.from(parent.type.create(attrs = null, content = inner)),
            )
        ),
        if (nestedBefore) 3 else 1,
        0,
    )
    tr.step(
        ReplaceAroundStep(
            range.start - if (nestedBefore) 3 else 1,
            range.end,
            range.start,
            range.end,
            slice,
            1,
            true,
        )
    )
    return true
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
            i == 0 -> com.atlassian.prosemirror.model.NodeBase(itemType, itemAttrs)
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
