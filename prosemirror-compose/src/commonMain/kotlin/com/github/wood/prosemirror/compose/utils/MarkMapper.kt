package com.github.wood.prosemirror.compose.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.atlassian.prosemirror.model.Mark
import com.github.wood.prosemirror.compose.model.ProseMirrorConfig
import com.github.wood.prosemirror.compose.model.parseColorAttr

public object MarkMapper {
    public fun map(mark: Mark, config: ProseMirrorConfig? = null): SpanStyle {
        val linkColor = config?.linkColor ?: Color(0xFF1A73E8)
        val codeColor = config?.codeSpanColor ?: Color(0xFFD63384)
        val linkDecoration = config?.linkTextDecoration ?: TextDecoration.Underline

        return when (mark.type.name) {
            "strong", "bold" -> SpanStyle(fontWeight = FontWeight.Bold)
            "em", "italic" -> SpanStyle(fontStyle = FontStyle.Italic)
            "underline" -> SpanStyle(textDecoration = TextDecoration.Underline)
            "strike", "strikethrough" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            "code" -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                color = codeColor
            )
            "link" -> SpanStyle(
                color = linkColor,
                textDecoration = linkDecoration
            )
            "textStyle" -> SpanStyle(
                color = parseColorAttr(mark.attrs["color"]),
                fontSize = (mark.attrs["fontSize"] as? Number)?.let { it.toFloat().sp } ?: TextUnit.Unspecified
            )
            else -> SpanStyle()
        }
    }
}