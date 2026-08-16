package com.github.wood.prosemirror.compose.model

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.atlassian.prosemirror.history.closeHistory
import com.atlassian.prosemirror.model.util.resolveSafe
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.transform.canSplit
import com.atlassian.prosemirror.transform.split
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.schema.liftListItem
import com.github.wood.prosemirror.compose.schema.removeEmptyContainers
import com.github.wood.prosemirror.compose.schema.sinkListItem
import com.github.wood.prosemirror.compose.schema.splitListItem
import com.github.wood.prosemirror.compose.schema.wrapInList

// ---------------------------------------------------------------------------
// 列表状态查询
// ---------------------------------------------------------------------------

private fun ProseMirrorState.ancestorListNames(): Set<String> {
    val names = mutableSetOf<String>()
    val _from = editorState.selection._from
    val _to = editorState.selection._to
    for (d in _from.depth downTo 1) {
        val name = _from.node(d).type.name
        if (name == "bullet_list" || name == "ordered_list") names += name
    }
    for (d in _to.depth downTo 1) {
        val name = _to.node(d).type.name
        if (name == "bullet_list" || name == "ordered_list") names += name
    }
    return names
}

/** 选区是否位于无序列表中。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.isUnorderedList: Boolean
    get() = "bullet_list" in ancestorListNames()

/** 选区是否位于有序列表中。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.isOrderedList: Boolean
    get() = "ordered_list" in ancestorListNames()

/** 选区是否位于任意列表中。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.isList: Boolean
    get() = isUnorderedList || isOrderedList

/** 当前列表项是否可以继续嵌套（有前一个兄弟 item 可挂载）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.canIncreaseListLevel: Boolean
    get() {
        val _from = editorState.selection._from
        for (d in _from.depth downTo 1) {
            val node = _from.node(d)
            if (node.type.name == "list_item") {
                val parentName = _from.node(d - 1).type.name
                if (parentName == "bullet_list" || parentName == "ordered_list") {
                    return _from.index(d - 1) > 0
                }
                return false
            }
        }
        return false
    }

/** 当前列表项是否可以提升一级。 */
@OptIn(ExperimentalProseMirrorApi::class)
public val ProseMirrorState.canDecreaseListLevel: Boolean
    get() {
        val _from = editorState.selection._from
        for (d in _from.depth downTo 1) {
            val node = _from.node(d)
            if (node.type.name == "list_item") return true
        }
        return false
    }

// ---------------------------------------------------------------------------
// 列表操作
// ---------------------------------------------------------------------------

/** 切换无序列表。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toggleUnorderedList() {
    if (isUnorderedList) removeUnorderedList() else addUnorderedList()
}

/** 将选区包裹为无序列表；若已在有序列表中则直接转换列表类型。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addUnorderedList() {
    val listType = schema.nodeType("bullet_list")
    if (convertListTypeIfNeeded(toType = listType)) return
    val tr = editorState.tr
    closeHistory(tr)
    if (wrapInList(tr, listType)) dispatch(tr)
}

/** 将选区从无序列表中提出。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeUnorderedList() {
    val tr = editorState.tr
    closeHistory(tr)
    if (liftListItem(tr, schema.nodeType("list_item"))) dispatch(tr)
}

/** 切换有序列表。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.toggleOrderedList() {
    if (isOrderedList) removeOrderedList() else addOrderedList()
}

/** 将选区包裹为有序列表；若已在无序列表中则直接转换列表类型。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.addOrderedList() {
    val listType = schema.nodeType("ordered_list")
    if (convertListTypeIfNeeded(
            toType = listType,
            attrs = mapOf<String, Any?>("order" to 1),
        )
    ) return
    val tr = editorState.tr
    closeHistory(tr)
    if (wrapInList(tr, listType, mapOf<String, Any?>("order" to 1))) dispatch(tr)
}

/** 将选区从有序列表中提出。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.removeOrderedList() {
    val tr = editorState.tr
    closeHistory(tr)
    if (liftListItem(tr, schema.nodeType("list_item"))) dispatch(tr)
}

/**
 * 选区已在另一种列表中时，直接把覆盖选区的列表节点改成 [toType]。
 * 列表节点结构相同（list_item+），setNodeMarkup 会校验并保留内容。
 */
private fun ProseMirrorState.convertListTypeIfNeeded(
    toType: com.atlassian.prosemirror.model.NodeType,
    attrs: com.atlassian.prosemirror.model.Attrs? = null,
): Boolean {
    val list = listAncestorNode() ?: return false
    if (list.second == toType.name) return true
    if (list.second != "bullet_list" && list.second != "ordered_list") return false

    val tr = editorState.tr
    closeHistory(tr)
    tr.setNodeMarkup(list.first, toType, attrs, null)
    dispatch(tr)
    return true
}

/** 选区覆盖的首个列表祖先（列表节点位置 to 类型名）。 */
private fun ProseMirrorState.listAncestorNode(): Pair<Int, String>? {
    val _from = editorState.selection._from
    for (d in _from.depth downTo 1) {
        val node = _from.node(d)
        if (node.type.name == "bullet_list" || node.type.name == "ordered_list") {
            return _from.start(d) - 1 to node.type.name
        }
    }
    return null
}

/** 当前列表项嵌套一级（挂到前一个 item 之下）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.increaseListLevel() {
    if (!canIncreaseListLevel) return
    val tr = editorState.tr
    closeHistory(tr)
    if (sinkListItem(tr, schema.nodeType("list_item"))) dispatch(tr)
}

/** 当前列表项提升一级（顶层 → 文档层；嵌套 → 外层列表）。 */
@OptIn(ExperimentalProseMirrorApi::class)
public fun ProseMirrorState.decreaseListLevel() {
    if (!canDecreaseListLevel) return
    val tr = editorState.tr
    closeHistory(tr)
    if (liftListItem(tr, schema.nodeType("list_item"))) dispatch(tr)
}

// ---------------------------------------------------------------------------
// Enter / Tab 键盘处理
// ---------------------------------------------------------------------------

/**
 * Enter 键处理：列表内新建列表项 / 空列表项退出列表 / 普通段落拆分。
 * [deleteRange] 非空时先在同一事务中删除被换行替换的选区。
 * 返回 true 表示已消费该事件。
 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.handleEnter(
    pmPos: Int,
    deleteRange: Pair<Int, Int>? = null,
): Boolean {
    val itemType = schema.nodeType("list_item")
    val savedStoredMarks = editorState.storedMarks

    val tr = editorState.tr
    closeHistory(tr)
    if (deleteRange != null) {
        tr.deleteRange(deleteRange.first, deleteRange.second)
    }

    val _from = tr.doc.resolveSafe(pmPos) ?: return false
    val textblock = _from.parent
    val isEmpty = textblock.childCount == 0

    var itemDepth = -1
    for (d in _from.depth downTo 1) {
        if (_from.node(d).type == itemType) {
            itemDepth = d
            break
        }
    }
    val inList = itemDepth >= 0

    if (inList && isEmpty && config.exitListOnEmptyItem) {
        // 空列表项 → 提升出列表：删除空 item（及空容器），在原地插入新段落
        val itemStart = _from.start(itemDepth)
        val itemEnd = _from.end(itemDepth)
        tr.delete(itemStart, itemEnd)
        removeEmptyContainers(tr, itemStart)
        val insertPos = tr.doc.resolveSafe(itemStart)
            ?.pos
            ?: tr.doc.content.size
        val para = schema.nodeType("paragraph").createAndFill()!!
        tr.replaceWith(insertPos, insertPos, para)
        tr.setSelection(TextSelection.create(tr.doc, insertPos + 1, insertPos + 1))
        dispatch(tr)
        return true
    }

    if (inList) {
        if (!splitListItem(tr, itemType)) return false
        justInsertedListParagraph = true
        if (config.preserveStyleOnEmptyLine && savedStoredMarks != null) {
            tr.setStoredMarks(savedStoredMarks)
        }
        dispatch(tr)
        return true
    }

    // 普通 textblock：拆分为两个同类型块（heading 拆分保持 heading）
    if (!canSplit(tr.doc, pmPos, 1, null)) return false
    split(tr, pmPos, 1, null)
    if (config.preserveStyleOnEmptyLine && savedStoredMarks != null) {
        tr.setStoredMarks(savedStoredMarks)
    }
    dispatch(tr)
    return true
}

/** Enter/Tab 键盘处理（追加在 undo/redo 与 trigger 处理之后）。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.handleListKeyEvent(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    when (event.key) {
        Key.Enter, Key.NumPadEnter -> {
            if (event.isMetaPressed || event.isCtrlPressed || event.isAltPressed) return false
            // singleParagraphMode 下 Enter 只是文本换行，交给 BasicTextField 走普通插入路径。
            if (singleParagraphMode) return false

            val selection = textFieldValue.selection
            val deleteRange = if (selection.collapsed) null else pmRangeOf(selection)
            val caretPm = if (selection.collapsed) {
                textInputPosition(flatToPm(selection.min))
            } else {
                flatToPm(selection.min)
            }
            return handleEnter(caretPm, deleteRange)
        }

        Key.Tab -> {
            if (event.isMetaPressed || event.isCtrlPressed || event.isAltPressed) return false
            if (!isList) return false
            if (event.isShiftPressed && canDecreaseListLevel) {
                decreaseListLevel()
            } else if (!event.isShiftPressed && canIncreaseListLevel) {
                increaseListLevel()
            } else {
                return false
            }
            return true
        }

        else -> return false
    }
}
