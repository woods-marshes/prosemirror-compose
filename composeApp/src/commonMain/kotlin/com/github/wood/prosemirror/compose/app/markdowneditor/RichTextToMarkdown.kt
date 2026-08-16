package com.github.wood.prosemirror.compose.app.markdowneditor

import com.github.wood.prosemirror.compose.model.*

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import com.github.wood.prosemirror.compose.app.components.RichTextStyleRow
import com.github.wood.prosemirror.compose.ui.material3.OutlinedProseMirrorEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichTextToMarkdown(
    richTextState: ProseMirrorState,
    modifier: Modifier = Modifier,
) {
    val markdown by remember(richTextState.annotatedString) {
        mutableStateOf(richTextState.toMarkdown())
    }

    Row(
        modifier = modifier
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            Text(
                text = "Rich Text Editor:",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            RichTextStyleRow(
                state = richTextState,
                modifier = Modifier
                    .fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedProseMirrorEditor(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = richTextState,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            )
        }

        Spacer(Modifier.width(8.dp))

        HorizontalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            Text(
                text = "Markdown code:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                item {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = markdown,
                            style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}