package com.github.wood.prosemirror.compose.ui.material3


import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.ImageLoader
import com.github.wood.prosemirror.compose.model.LocalImageLoader
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import com.github.wood.prosemirror.compose.ui.ExpandableBasicProseMirrorText

/**
 * Material3 wrapper around [ExpandableBasicRichText] that pulls [LocalTextStyle] /
 * [LocalContentColor] / [MaterialTheme.colorScheme.primary] for default styling, mirroring how the
 * sibling [RichText] composable wraps [com.mohamedrejeb.richeditor.ui.BasicRichText].
 *
 * @see ExpandableBasicRichText for the underlying composable, the v1 limitations note, and
 * documentation on each parameter.
 */
@ExperimentalProseMirrorApi
@Composable
public fun ExpandableProseMirrorText(
    state: ProseMirrorState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    collapsedMaxLines: Int = 3,
    seeMoreLabel: String = "… See more",
    seeLessLabel: String = " See less",
    seeMoreColor: Color = MaterialTheme.colorScheme.primary,
    softWrap: Boolean = true,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    imageLoader: ImageLoader = LocalImageLoader.current,
) {
    val textColor = color.takeOrElse {
        style.color.takeOrElse {
            LocalContentColor.current
        }
    }
    val mergedStyle = style.merge(TextStyle(color = textColor))

    ExpandableBasicProseMirrorText(
        state = state,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        style = mergedStyle,
        collapsedMaxLines = collapsedMaxLines,
        seeMoreLabel = seeMoreLabel,
        seeLessLabel = seeLessLabel,
        seeMoreStyle = SpanStyle(
            color = seeMoreColor,
            textDecoration = TextDecoration.Underline,
        ),
        softWrap = softWrap,
        inlineContent = inlineContent,
        imageLoader = imageLoader,
    )
}
