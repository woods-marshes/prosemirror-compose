package com.github.wood.prosemirror.compose.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.util.fastForEach
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import com.github.wood.prosemirror.compose.model.RichSpanStyle

@OptIn(ExperimentalProseMirrorApi::class)
internal fun Modifier.drawRichSpanStyle(
    state: ProseMirrorState,
    topPadding: Float = 0f,
    startPadding: Float = 0f,
    overContent: Boolean = false,
): Modifier {
    if (!overContent) {
        return this.drawBehind {
            state.styledRichSpanList.fastForEach { (style, textRange) ->
                state.textLayoutResult?.let { textLayoutResult ->
                    val textLength = state.annotatedString.length
                    val measuredTextLength =
                        textLayoutResult.multiParagraph.intrinsics.annotatedString.length
                    if (textLength == measuredTextLength) {
                        with(style) {
                            drawCustomStyle(
                                layoutResult = textLayoutResult,
                                textRange = textRange,
                                richTextConfig = state.config,
                                topPadding = topPadding,
                                startPadding = startPadding,
                            )
                        }
                    }
                }
            }
        }
    }

    // Material3 的 decorationBox 容器/选区背景会盖住 drawBehind 的内容（Compose
    // Multiplatform 1.12.0-alpha 系列的行为），导致编辑器里的 code 胶囊看不见。
    // 编辑器走 drawWithContent：背景画在 drawContent 之前（容器之上、文字之下），
    // code 描边画在 drawContent 之后，保证边框始终可见。
    return this.drawWithContent {
        state.styledRichSpanList.fastForEach { (style, textRange) ->
            state.textLayoutResult?.let { textLayoutResult ->
                val textLength = state.annotatedString.length
                val measuredTextLength =
                    textLayoutResult.multiParagraph.intrinsics.annotatedString.length
                if (textLength == measuredTextLength) {
                    if (style is RichSpanStyle.Code) {
                        with(style) {
                            drawCodeFill(
                                layoutResult = textLayoutResult,
                                textRange = textRange,
                                richTextConfig = state.config,
                                topPadding = topPadding,
                                startPadding = startPadding,
                            )
                        }
                    } else {
                        with(style) {
                            drawCustomStyle(
                                layoutResult = textLayoutResult,
                                textRange = textRange,
                                richTextConfig = state.config,
                                topPadding = topPadding,
                                startPadding = startPadding,
                            )
                        }
                    }
                }
            }
        }

        drawContent()

        state.styledRichSpanList.fastForEach { (style, textRange) ->
            state.textLayoutResult?.let { textLayoutResult ->
                val textLength = state.annotatedString.length
                val measuredTextLength =
                    textLayoutResult.multiParagraph.intrinsics.annotatedString.length
                if (textLength == measuredTextLength && style is RichSpanStyle.Code) {
                    with(style) {
                        drawCodeStroke(
                            layoutResult = textLayoutResult,
                            textRange = textRange,
                            richTextConfig = state.config,
                            topPadding = topPadding,
                            startPadding = startPadding,
                        )
                    }
                }
            }
        }
    }
}