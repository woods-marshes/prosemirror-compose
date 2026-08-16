package com.github.wood.prosemirror.compose.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.atlassian.prosemirror.model.util.resolveSafe
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.gesture.detectTapGestures
import com.github.wood.prosemirror.compose.model.ImageLoader
import com.github.wood.prosemirror.compose.model.LocalImageLoader
import com.github.wood.prosemirror.compose.model.LocalRichTextMaxImageWidthProvider
import com.github.wood.prosemirror.compose.model.LocalTokenClickHandler
import com.github.wood.prosemirror.compose.model.LocalTokenHoverHandler
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import com.github.wood.prosemirror.compose.model.RichSpanStyle
import com.github.wood.prosemirror.compose.model.RichTextMaxImageWidthProvider
import com.github.wood.prosemirror.compose.model.TokenClickHandler
import com.github.wood.prosemirror.compose.model.TokenHoverHandler

/**
 * ProseMirror 只读富文本展示组件 (对应 compose-rich-editor 的 BasicRichText)。
 * 适用于无需唤起软键盘、仅作格式化内容渲染与点击交互的场景。
 */
@OptIn(ExperimentalProseMirrorApi::class)
@Composable
public fun BasicProseMirrorText(
    state: ProseMirrorState,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    imageLoader: ImageLoader = LocalImageLoader.current,
    onTokenClick: TokenClickHandler? = null,
    onTokenHover: TokenHoverHandler? = null,
) {
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val localTokenClick = LocalTokenClickHandler.current
    val localTokenHover = LocalTokenHoverHandler.current
    val effectiveTokenClick = onTokenClick ?: localTokenClick
    val effectiveTokenHover = onTokenHover ?: localTokenHover

    val pointerIcon = remember {
        mutableStateOf(PointerIcon.Default)
    }
    val maxImageWidthProvider = remember { RichTextMaxImageWidthProvider() }

    val text = remember(
        state.visualTransformation,
        state.annotatedString,
    ) {
        state.visualTransformation.filter(state.annotatedString).text
    }

    CompositionLocalProvider(
        LocalImageLoader provides imageLoader,
        LocalRichTextMaxImageWidthProvider provides maxImageWidthProvider,
    ) {
        BasicText(
            text = text,
            modifier = modifier
                .drawRichSpanStyle(state)
                .pointerHoverIcon(pointerIcon.value)
                .pointerInput(state, effectiveTokenClick != null, effectiveTokenHover) {
                    var lastHoveredToken: RichSpanStyle.Token? = null
                    try {
                        awaitEachGesture {
                            val event = awaitPointerEvent()
                            val position = event.changes.first().position
                            val exited = event.type == PointerEventType.Exit

                            val tokenUnderPointer =
                                if (exited) null else state.getTokenByOffset(position)
                            val isInteractive = !exited && (
                                    state.isLink(position) ||
                                            (effectiveTokenClick != null && tokenUnderPointer != null)
                                    )

                            pointerIcon.value =
                                if (isInteractive)
                                    PointerIcon.Hand
                                else
                                    PointerIcon.Default

                            if (effectiveTokenHover != null && tokenUnderPointer != lastHoveredToken) {
                                lastHoveredToken = tokenUnderPointer
                                effectiveTokenHover.invoke(tokenUnderPointer, position)
                            }
                        }
                    } catch (_: Exception) {

                    }
                }
                .pointerInput(state, effectiveTokenClick) {
                    detectTapGestures(
                        onTap = { offset ->
                            val token = state.getTokenByOffset(offset)
                            if (token != null && effectiveTokenClick != null) {
                                effectiveTokenClick.invoke(token, offset)
                                return@detectTapGestures
                            }
                            state.getLinkByOffset(offset)?.let { url ->
                                try {
                                    uriHandler.openUri(url)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        consumeDown = { offset ->
                            state.isLink(offset) ||
                                    (effectiveTokenClick != null && state.isToken(offset))
                        },
                    )
                }
                .onSizeChanged { size ->
                    val newWidth = with(density) { size.width.toSp() }
                    if (newWidth != maxImageWidthProvider.maxWidth) {
                        maxImageWidthProvider.maxWidth = newWidth
                    }
                },
            style = style,
            onTextLayout = {
                state.onTextLayout(
                    textLayoutResult = it,
                    density = density,
                )
                onTextLayout(it)
            },
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            inlineContent = remember(inlineContent, state.inlineContentMap.toMap()) {
                inlineContent + state.inlineContentMap
            }
        )
    }
}