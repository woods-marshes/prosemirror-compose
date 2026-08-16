package com.github.wood.prosemirror.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.HeadingStyle
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import com.github.wood.prosemirror.compose.model.RichSpanStyle
import com.github.wood.prosemirror.compose.model.addLinkToSelection
import com.github.wood.prosemirror.compose.model.canDecreaseListLevel
import com.github.wood.prosemirror.compose.model.canIncreaseListLevel
import com.github.wood.prosemirror.compose.model.currentHeadingStyle
import com.github.wood.prosemirror.compose.model.currentSpanStyle
import com.github.wood.prosemirror.compose.model.decreaseListLevel
import com.github.wood.prosemirror.compose.model.increaseListLevel
import com.github.wood.prosemirror.compose.model.isCodeSpan
import com.github.wood.prosemirror.compose.model.isLink
import com.github.wood.prosemirror.compose.model.isOrderedList
import com.github.wood.prosemirror.compose.model.isUnorderedList
import com.github.wood.prosemirror.compose.model.rememberProseMirrorState
import com.github.wood.prosemirror.compose.model.setHeadingStyle
import com.github.wood.prosemirror.compose.model.toggleCodeSpan
import com.github.wood.prosemirror.compose.model.toggleOrderedList
import com.github.wood.prosemirror.compose.model.toggleSpanStyle
import com.github.wood.prosemirror.compose.model.toggleUnorderedList
import com.github.wood.prosemirror.compose.model.trigger.Trigger
import com.github.wood.prosemirror.compose.ui.material3.ProseMirrorEditor
import com.github.wood.prosemirror.compose.ui.material3.ProseMirrorText
import com.github.wood.prosemirror.compose.ui.material3.TriggerSuggestions
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration

private val SampleHtml = """
    <h1>ProseMirror Compose 演示</h1>
    <p>这是一个基于 <strong>prosemirror-kotlin</strong> 数据结构的 <em>富文本编辑器</em>。</p>
    <ul>
      <li>支持 <strong>粗体</strong>、<em>斜体</em>、<u>下划线</u>、<s>删除线</s>、<code>代码</code> 与 <a href="https://prosemirror.net">链接</a></li>
      <li>支持无序/有序列表与嵌套层级</li>
      <li>支持 <span data-token-trigger-id="mention" data-token-id="alice" data-token-label="@Alice">@Alice</span> 类型的 @提及 token</li>
    </ul>
    <ol>
      <li>第一项</li>
      <li>第二项</li>
    </ol>
    <p>输入 @ 试试提及功能。</p>
""".trimIndent()

private val DemoUsers = listOf("Alice", "Bob", "Charlie", "Dave", "Eve")

@OptIn(ExperimentalProseMirrorApi::class)
@Composable
public fun App() {
    MaterialTheme {
        val state = rememberProseMirrorState(initialHtml = SampleHtml)

        // 注册 @mention trigger
        val mentionTrigger = remember {
            Trigger(
                id = "mention",
                char = '@',
                style = { SpanStyle(color = it.linkColor, fontWeight = FontWeight.Bold) },
            )
        }
        LaunchedEffect(state) {
            state.registerTrigger(mentionTrigger)
        }
        DisposableEffect(Unit) {
            onDispose { state.unregisterTrigger("mention") }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            EditorToolbar(state)
            Spacer(Modifier.height(8.dp))

            Box {
                ProseMirrorEditor(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    label = { Text("富文本编辑器") },
                    placeholder = { Text("开始输入…") },
                )
                TriggerSuggestions(
                    state = state,
                    triggerId = "mention",
                    suggestions = { query ->
                        DemoUsers.filter { it.contains(query, ignoreCase = true) }
                    },
                    onSelect = { name ->
                        state.insertToken("mention", name.lowercase(), "@$name")
                        RichSpanStyle.Token("mention", name.lowercase(), "@$name")
                    },
                    modifier = Modifier.align(Alignment.TopStart),
                ) { name ->
                    Text(
                        text = name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("只读预览", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            ProseMirrorText(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text("导出 HTML", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.toHtml(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalProseMirrorApi::class)
@Composable
private fun EditorToolbar(state: ProseMirrorState) {
    var showLinkDialog by remember { mutableStateOf(false) }
    var headingMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarToggleButton("B", active = state.currentSpanStyle.fontWeight == FontWeight.Bold) {
            state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        }
        ToolbarToggleButton("I", active = state.currentSpanStyle.fontStyle == FontStyle.Italic) {
            state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        }
        ToolbarToggleButton("U", active = state.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true) {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
        }
        ToolbarToggleButton("S", active = state.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true) {
            state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        }
        ToolbarToggleButton("代码", active = state.isCodeSpan) {
            state.toggleCodeSpan()
        }
        ToolbarToggleButton("链接", active = state.isLink) {
            showLinkDialog = true
        }
        Spacer(Modifier.width(8.dp))

        // 标题下拉
        Box {
            TextButton(onClick = { headingMenuOpen = true }) {
                Text("标题: ${state.currentHeadingStyle.level}")
            }
            DropdownMenu(expanded = headingMenuOpen, onDismissRequest = { headingMenuOpen = false }) {
                HeadingStyle.entries.forEach { heading ->
                    DropdownMenuItem(
                        text = { Text(if (heading == HeadingStyle.Normal) "正文" else "H${heading.level}") },
                        onClick = {
                            state.setHeadingStyle(heading)
                            headingMenuOpen = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))

        ToolbarToggleButton("•列表", active = state.isUnorderedList) {
            state.toggleUnorderedList()
        }
        ToolbarToggleButton("1.列表", active = state.isOrderedList) {
            state.toggleOrderedList()
        }
        ToolbarToggleButton("↪", active = false, enabled = state.canIncreaseListLevel) {
            state.increaseListLevel()
        }
        ToolbarToggleButton("↩", active = false, enabled = state.canDecreaseListLevel) {
            state.decreaseListLevel()
        }
        Spacer(Modifier.width(8.dp))

        ToolbarToggleButton("撤销", active = false, enabled = state.canUndo) {
            state.undo()
        }
        ToolbarToggleButton("重做", active = false, enabled = state.canRedo) {
            state.redo()
        }
    }

    if (showLinkDialog) {
        var url by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("插入链接") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (url.isNotBlank()) state.addLinkToSelection(url)
                        showLinkDialog = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ToolbarToggleButton(
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Text(
            text = label,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !enabled -> MaterialTheme.colorScheme.outlineVariant
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
