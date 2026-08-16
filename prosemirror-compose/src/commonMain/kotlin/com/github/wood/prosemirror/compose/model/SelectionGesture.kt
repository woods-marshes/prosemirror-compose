package com.github.wood.prosemirror.compose.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import com.atlassian.prosemirror.state.TextSelection
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SelectionGestureGrace = 1.seconds
private val SelectionGesturePointerFreshness = 500.milliseconds

/**
 * 按压调整选区（移动端长按/双击选择手感）。
 * 参考版 adjustSelection 的简化移植：按位置定位 + 块分隔符吸附 + 手势行钳制。
 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.adjustSelection(pressPosition: Offset) {
    val layout = textLayoutResult ?: return
    val flatPos = layout.getOffsetForPosition(pressPosition).coerceIn(0, textFieldValue.text.length)
    // 光标落在块分隔符（合成 "\n"）上 → 回退到前一块末尾。
    val separator = coordinateMap.blockSeparators.firstOrNull { it.flatIndex == flatPos }
    val selection = if (separator != null) TextRange(flatPos - 1) else TextRange(flatPos)
    applyAdjustedSelection(selection)
}

/** 手势是否活跃（平台标记或按下事件后的宽限期内）。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.isSelectionGestureLive(): Boolean =
    treatSelectionChangesAsGesture || selectionGesturePressed ||
        (selectionGestureLastActivity?.elapsedNow() ?: Duration.INFINITE) < SelectionGestureGrace

/**
 * 手势选区调整：块分隔符吸附 + 指针行钳制。
 * 纯逻辑（无 Compose 依赖），可单测。
 *
 * 与参考版一致，折叠选区直接放行；只有拖拽中“移动端”越过指针所在行、
 * 或落在块分隔符（下一段起点）上时才修正。
 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.adjustGestureSelection(selection: TextRange): TextRange {
    if (selection.collapsed || !isSelectionGestureLive()) return selection

    selectionGestureLastActivity = kotlin.time.TimeSource.Monotonic.markNow()

    // 只有移动端可以被修正；另一端是拖拽锚点，不能动。
    val oldSelection = textFieldValue.selection
    val movingEdgeIsMax = when {
        selection.start == oldSelection.start && selection.end != oldSelection.end ->
            selection.end > selection.start

        selection.end == oldSelection.end && selection.start != oldSelection.start ->
            selection.start > selection.end

        else -> false
    }
    if (!movingEdgeIsMax) return selection

    val max = selection.max
    if (max <= 0 || max >= textFieldValue.text.length) return selection

    // 拖拽进入了短行末尾的空白区：平台会把 max 放到下一段起点，
    // 这里按指针所在行把它钳回该行可见末尾。
    val pointer = selectionGesturePointer
    val pointerFresh = selectionGesturePointerMark?.let {
        it.elapsedNow() < SelectionGesturePointerFreshness
    } == true
    val layout = textLayoutResult
    if (pointer != null && pointerFresh && layout != null &&
        layout.layoutInput.text.length == textFieldValue.text.length
    ) {
        val pointerLine = layout.getLineForVerticalPosition(
            pointer.y.coerceIn(0f, layout.size.height.toFloat()),
        )
        val maxLine = layout.getLineForOffset(max)
        if (maxLine > pointerLine) {
            val clampedMax = layout.getLineEnd(pointerLine, visibleEnd = true)
            if (clampedMax > selection.min && clampedMax < max) {
                return if (selection.start > selection.end) {
                    TextRange(clampedMax, selection.end)
                } else {
                    TextRange(selection.start, clampedMax)
                }
            }
        }
    }

    // max 落在下一段起点（块分隔符之后）时回退到分隔符前，避免高亮到下一行。
    val isParagraphStart = coordinateMap.blockSeparators.any { it.flatIndex + 1 == max }
    if (!isParagraphStart) return selection

    val newMax = max - 1
    return if (selection.start > selection.end) {
        TextRange(newMax, selection.end)
    } else {
        TextRange(selection.start, newMax)
    }
}

/** TextRange 钳制到 [0, length]（Compose 此版本无 coerceIn）。 */
internal fun TextRange.clampTo(length: Int): TextRange {
    val min = min.coerceIn(0, length)
    val max = max.coerceIn(0, length)
    return TextRange(min, max)
}

/** 应用调整后的选区（clamp + 纯选区事务）。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.applyAdjustedSelection(selection: TextRange) {
    val clamped = selection.clampTo(textFieldValue.text.length)
    val pmAnchor = flatToPm(clamped.min)
    val pmHead = flatToPm(clamped.max)
    val tr = editorState.tr
    tr.setSelection(TextSelection.create(tr.doc, pmAnchor, pmHead))
    dispatch(tr)
}

/** 记录按压位置（300ms 后清除），并用于 IME 边界补空格判定排除按压导航。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal suspend fun ProseMirrorState.registerLastPressPosition(pressPosition: Offset): Unit =
    kotlinx.coroutines.coroutineScope {
        registerLastPressPositionJob?.cancel()
        registerLastPressPositionJob = launch {
            lastPressPosition = pressPosition
            delay(300.milliseconds)
            lastPressPosition = null
        }
    }
