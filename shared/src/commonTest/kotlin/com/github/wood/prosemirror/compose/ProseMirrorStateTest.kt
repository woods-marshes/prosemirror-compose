package com.github.wood.prosemirror.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.HeadingStyle
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import com.github.wood.prosemirror.compose.model.addLinkToSelection
import com.github.wood.prosemirror.compose.model.currentHeadingStyle
import com.github.wood.prosemirror.compose.model.handleEnter
import com.github.wood.prosemirror.compose.model.isLink
import com.github.wood.prosemirror.compose.model.isUnorderedList
import com.github.wood.prosemirror.compose.model.selectedLinkUrl
import com.github.wood.prosemirror.compose.model.setHeadingStyle
import com.github.wood.prosemirror.compose.model.setHtml
import com.github.wood.prosemirror.compose.model.toggleCodeSpan
import com.github.wood.prosemirror.compose.model.toggleSpanStyle
import com.github.wood.prosemirror.compose.model.toggleUnorderedList
import com.github.wood.prosemirror.compose.model.trigger.Trigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalProseMirrorApi::class, ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ProseMirrorStateTest {

    /** 在光标处输入文本（模拟 BasicTextField 回调）。 */
    private fun ProseMirrorState.type(text: String) {
        val current = textFieldValue.text
        val sel = textFieldValue.selection.min
        val newText = current.substring(0, sel) + text + current.substring(sel)
        onTextFieldValueChange(TextFieldValue(newText, TextRange(sel + text.length)))
    }

    /** 移动光标。 */
    private fun ProseMirrorState.placeCaret(index: Int) {
        onTextFieldValueChange(textFieldValue.copy(selection = TextRange(index)))
    }

    @Test
    fun schemaRoundTripPreservesStructure() {
        val state = ProseMirrorState()
        state.setHtml("<h1>a</h1><ul><li>b</li></ul><ol start=\"3\"><li>c</li></ol>")
        assertEquals("a\nb\nc", state.toText())

        // 往返
        val html = state.toHtml()
        val state2 = ProseMirrorState()
        state2.setHtml(html)
        assertEquals("a\nb\nc", state2.toText())
        assertEquals("bullet_list", state2.editorState.doc.child(1).type.name)
        assertEquals("ordered_list", state2.editorState.doc.child(2).type.name)
    }

    @Test
    fun typingAndUndoRedo() {
        val state = ProseMirrorState()
        state.type("hello")
        assertEquals("hello", state.toText())
        assertTrue(state.canUndo)

        state.undo()
        assertEquals("", state.toText())
        assertTrue(state.canRedo)

        state.redo()
        assertEquals("hello", state.toText())
    }

    @Test
    fun ctrlZShortcutTriggersUndo() {
        val state = ProseMirrorState()
        state.type("hello")
        // Ctrl+Z KeyEvent
        val handled = state.onPreviewKeyEvent(
            androidx.compose.ui.input.key.KeyEvent(
                type = androidx.compose.ui.input.key.KeyEventType.KeyDown,
                key = androidx.compose.ui.input.key.Key.Z,
                isCtrlPressed = true,
            )
        )
        assertTrue(handled)
        assertEquals("", state.toText())
    }

    @Test
    fun stagedBoldAppliesToTypedText() {
        val state = ProseMirrorState()
        // 折叠选区 toggle bold → 暂存到 storedMarks
        state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        state.type("bold")
        val para = state.editorState.doc.child(0)
        val textNode = para.firstChild!!
        assertTrue(textNode.marks.any { it.type.name == "strong" }, "typed text should inherit bold")
    }

    @Test
    fun boldOnSelectionAppliesImmediately() {
        val state = ProseMirrorState()
        state.type("hello")
        state.placeCaret(5)
        // 选中 1..4
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(1, 4)))
        state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        // addMark 会把文本节点按选区拆分（"h" + "ell"(strong) + "o"）
        val para = state.editorState.doc.child(0)
        val hasBold = (0 until para.childCount).any { i ->
            para.child(i).marks.any { it.type.name == "strong" }
        }
        assertTrue(hasBold)
    }

    @Test
    fun italicAndCodeToggle() {
        val state = ProseMirrorState()
        state.type("x")
        state.placeCaret(1)
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 1)))
        state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
        state.toggleCodeSpan()
        val textNode = state.editorState.doc.child(0).child(0)
        assertTrue(textNode.marks.any { it.type.name == "em" })
        assertTrue(textNode.marks.any { it.type.name == "code" })
    }

    @Test
    fun linkOnSelection() {
        val state = ProseMirrorState()
        state.type("click me")
        state.placeCaret(9)
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 5)))
        state.addLinkToSelection("https://example.com")
        assertTrue(state.isLink)
        assertEquals("https://example.com", state.selectedLinkUrl)
        assertTrue(state.toHtml().contains("href=\"https://example.com\""))
    }

    @Test
    fun headingStyleSetAndQuery() {
        val state = ProseMirrorState()
        state.type("title")
        state.setHeadingStyle(HeadingStyle.H1)
        assertEquals(HeadingStyle.H1, state.currentHeadingStyle)
        assertEquals("heading", state.editorState.doc.child(0).type.name)
        // 普通段落
        state.setHeadingStyle(HeadingStyle.Normal)
        assertEquals("paragraph", state.editorState.doc.child(0).type.name)
    }

    @Test
    fun unorderedListWrapAndMarker() {
        val state = ProseMirrorState()
        state.type("item1")
        state.toggleUnorderedList()
        assertTrue(state.isUnorderedList)
        assertEquals("bullet_list", state.editorState.doc.child(0).type.name)
        // marker 渲染到扁平文本
        assertTrue(state.textFieldValue.text.startsWith("• "), "flat text should start with marker, got: ${state.textFieldValue.text}")
        // 取消
        state.toggleUnorderedList()
        assertEquals("paragraph", state.editorState.doc.child(0).type.name)
    }

    @Test
    fun enterInListCreatesNewItem() {
        val state = ProseMirrorState()
        state.type("item1")
        state.toggleUnorderedList()
        // 光标在末尾，Enter
        assertTrue(state.handleEnter(state.flatToPm(state.textFieldValue.selection.min)))
        assertEquals("bullet_list", state.editorState.doc.child(0).type.name)
        assertEquals(2, state.editorState.doc.child(0).childCount)
    }

    @Test
    fun setHtmlClearsHistory() {
        val state = ProseMirrorState()
        state.type("x")
        state.setHtml("<p>y</p>")
        state.undo()
        assertEquals("y", state.toText(), "setHtml should clear undo history")
    }

    @Test
    fun insertTokenCreatesTokenNode() {
        val state = ProseMirrorState()
        state.registerTrigger(Trigger(id = "mention", char = '@'))
        state.type("@ali")
        assertNotNull(state.activeTriggerQuery, "typing '@ali' should activate the mention query")
        state.insertToken("mention", "alice", "@alice")
        val html = state.toHtml()
        assertTrue(html.contains("data-token-id=\"alice\""), "HTML should contain token data attrs: $html")
        // token label 出现在扁平文本中
        assertTrue(state.textFieldValue.text.contains("@alice"))
    }
}
