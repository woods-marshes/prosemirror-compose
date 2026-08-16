package com.github.wood.prosemirror.compose.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.util.fastForEach
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.ProseMirrorState

@OptIn(ExperimentalProseMirrorApi::class)
internal fun Modifier.drawRichSpanStyle(
    state: ProseMirrorState,
    topPadding: Float = 0f,
    startPadding: Float = 0f,
): Modifier {
    return this
        .drawBehind {
            state.styledRichSpanList.fastForEach { (style, textRange) ->
                state.textLayoutResult?.let { textLayoutResult ->
                    with(style) {
                        val textLength = state.annotatedString.length
                        val measuredTextLength = textLayoutResult.multiParagraph.intrinsics.annotatedString.length
                        if (textLength == measuredTextLength) {
                            drawCustomStyle(
                                layoutResult = textLayoutResult,
                                textRange = textRange,
                                richTextConfig = state.config,
                                topPadding = topPadding,
                                startPadding = startPadding
                            )
                        }
                    }
                }
            }
        }
}