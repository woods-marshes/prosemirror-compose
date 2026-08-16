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
    applyAdjustedSelection(adjustGestureSelection(TextRange(flatPos, flatPos)))
}

/** 手势是否活跃（平台标记或按下事件后的宽限期内）。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.isSelectionGestureLive(): Boolean =
    treatSelectionChangesAsGesture || selectionGesturePressed ||
        (selectionGestureLastActivity?.elapsedNow() ?: Duration.INFINITE) < SelectionGestureGrace

/**
 * 手势选区调整：块分隔符吸附 + 指针行钳制。
 * 纯逻辑（无 Compose 依赖），可单测。
 */
@OptIn(ExperimentalProseMirrorApi::class)
internal fun ProseMirrorState.adjustGestureSelection(selection: TextRange): TextRange {
    val layout = textLayoutResult ?: return selection
    val text = textFieldValue.text
    var newSelection = selection

    // 特例：光标落在块分隔符（合成 "\n"）上 → 回退到前一块末尾
    val separator = coordinateMap.blockSeparators.firstOrNull { it.flatIndex == newSelection.min }
    if (separator != null) {
        newSelection = TextRange(newSelection.min - 1)
    }

    // 特例：拖拽中指针位置钳制到行末端（光标吸附行尾）
    if (isSelectionGestureLive() && selectionGesturePointer != null &&
        (selectionGesturePointerMark?.elapsedNow() ?: Duration.INFINITE) < SelectionGesturePointerFreshness
    ) {
        val pointer = selectionGesturePointer!!
        val line = layout.getLineForVerticalPosition(pointer.y).coerceIn(0, layout.lineCount - 1)
        val lineEnd = layout.getLineEnd(line, visibleEnd = true)
        newSelection = TextRange(newSelection.min, lineEnd)
    }

    return newSelection.clampTo(text.length)
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

/** 记录按压位置（300ms 内用于手势判定）。 */
@OptIn(ExperimentalProseMirrorApi::class)
internal suspend fun ProseMirrorState.registerLastPressPosition(pressPosition: Offset) = kotlinx.coroutines.coroutineScope {
    registerLastPressPositionJob?.cancel()
    registerLastPressPositionJob = launch {
        delay(300.milliseconds)
    }
}
