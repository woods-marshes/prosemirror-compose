package com.github.wood.prosemirror.compose.app.richeditor

import com.github.wood.prosemirror.compose.model.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.RichSpanStyle
import com.github.wood.prosemirror.compose.model.ProseMirrorConfig
import com.github.wood.prosemirror.compose.utils.getBoundingBoxes

@OptIn(ExperimentalProseMirrorApi::class)
object SpellCheck: RichSpanStyle {
    override val spanStyle: (ProseMirrorConfig) -> SpanStyle = {
        SpanStyle()
    }

    override fun DrawScope.drawCustomStyle(
        layoutResult: TextLayoutResult,
        textRange: TextRange,
        richTextConfig: ProseMirrorConfig,
        topPadding: Float,
        startPadding: Float,
    ) {
        val path = Path()
        val strokeColor = Color.Red
        val boxes = layoutResult.getBoundingBoxes(
            startOffset = textRange.start,
            endOffset = textRange.end,
            flattenForFullParagraphs = true,
        )

        boxes.fastForEach { box ->
            path.moveTo(box.left + startPadding, box.bottom + topPadding)
            path.lineTo(box.right + startPadding, box.bottom + topPadding)

            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                )
            )
        }
    }

    override val acceptNewTextInTheEdges: Boolean = false
}
