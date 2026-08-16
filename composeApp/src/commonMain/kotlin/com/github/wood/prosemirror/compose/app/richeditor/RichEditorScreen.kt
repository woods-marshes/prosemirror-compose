package com.github.wood.prosemirror.compose.app.richeditor

import com.github.wood.prosemirror.compose.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.wood.prosemirror.compose.model.rememberProseMirrorState
import com.github.wood.prosemirror.compose.app.components.RichTextStyleRow
import com.github.wood.prosemirror.compose.app.components.SampleScaffold
import com.github.wood.prosemirror.compose.app.ui.theme.SampleAccents
import com.github.wood.prosemirror.compose.ui.BasicProseMirrorEditor
import com.github.wood.prosemirror.compose.ui.material3.OutlinedProseMirrorEditor
import com.github.wood.prosemirror.compose.ui.material3.ProseMirrorText
import com.github.wood.prosemirror.compose.ui.material3.ProseMirrorEditor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RichEditorScreen(
    navigateBack: () -> Unit
) {
    val basicRichTextState = rememberProseMirrorState()
    val richTextState = rememberProseMirrorState()
    val outlinedRichTextState = rememberProseMirrorState()

    LaunchedEffect(Unit) {
        richTextState.setHtml(
            """
            <p><b>ProseMirrorEditor</b> is a <i>composable</i> that allows you to edit <u>rich text</u> content.</p>
            """.trimIndent()
        )
    }

    SampleScaffold(
        title = "Editor variants",
        navigateBack = navigateBack,
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime),
        ) {
            item {
                IntroBlock(
                    description = "All four surfaces share a single ProseMirrorState. Toggle the toolbar above " +
                        "each editor to see formatting flow through.",
                )
            }

            item {
                EditorSection(
                    label = "BasicProseMirrorEditor",
                    caption = "The minimal primitive - bring your own chrome.",
                ) {
                    Spacer(Modifier.height(8.dp))
                    RichTextStyleRow(
                        modifier = Modifier.fillMaxWidth(),
                        state = basicRichTextState,
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicProseMirrorEditor(
                        modifier = Modifier.fillMaxWidth(),
                        state = basicRichTextState,
                        textStyle = TextStyle.Default.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            item {
                EditorSection(
                    label = "ProseMirrorEditor",
                    caption = "Material3 filled text field with full editor parity.",
                ) {
                    Spacer(Modifier.height(8.dp))
                    RichTextStyleRow(
                        modifier = Modifier.fillMaxWidth(),
                        state = richTextState,
                    )
                    Spacer(Modifier.height(8.dp))
                    ProseMirrorEditor(
                        modifier = Modifier.fillMaxWidth(),
                        state = richTextState,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            item {
                EditorSection(
                    label = "OutlinedProseMirrorEditor",
                    caption = "Outlined Material3 variant - works great as a form input.",
                ) {
                    Spacer(Modifier.height(8.dp))
                    RichTextStyleRow(
                        modifier = Modifier.fillMaxWidth(),
                        state = outlinedRichTextState,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedProseMirrorEditor(
                        modifier = Modifier.fillMaxWidth(),
                        state = outlinedRichTextState,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            item {
                EditorSection(
                    label = "ProseMirrorText (read-only)",
                    caption = "Render the same state as immutable text - perfect for chat bubbles, comments, etc.",
                ) {
                    Spacer(Modifier.height(8.dp))
                    ProseMirrorText(
                        modifier = Modifier.fillMaxWidth(),
                        state = richTextState,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroBlock(description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
    ) {
        Text(
            text = "How it works",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = SampleAccents.Indigo,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EditorSection(
    label: String,
    caption: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
