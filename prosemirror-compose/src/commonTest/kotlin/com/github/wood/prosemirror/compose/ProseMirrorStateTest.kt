package com.github.wood.prosemirror.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.HeadingStyle
import com.github.wood.prosemirror.compose.model.ProseMirrorState
import com.github.wood.prosemirror.compose.model.RichSpanStyle
import com.github.wood.prosemirror.compose.model.addLink
import com.github.wood.prosemirror.compose.model.addLinkToSelection
import com.github.wood.prosemirror.compose.model.addParagraphStyle
import com.github.wood.prosemirror.compose.model.addSpanStyle
import com.github.wood.prosemirror.compose.model.addTextAtIndex
import com.github.wood.prosemirror.compose.model.currentHeadingStyle
import com.github.wood.prosemirror.compose.model.currentSpanStyle
import com.github.wood.prosemirror.compose.model.handleEnter
import com.github.wood.prosemirror.compose.model.isLink
import com.github.wood.prosemirror.compose.model.isUnorderedList
import com.github.wood.prosemirror.compose.model.replaceTextRange
import com.github.wood.prosemirror.compose.model.selectedLinkText
import com.github.wood.prosemirror.compose.model.selectedLinkUrl
import com.github.wood.prosemirror.compose.model.setHeadingStyle
import com.github.wood.prosemirror.compose.model.setHtml
import com.github.wood.prosemirror.compose.model.setMarkdown
import com.github.wood.prosemirror.compose.model.setText
import com.github.wood.prosemirror.compose.model.toMarkdown
import com.github.wood.prosemirror.compose.model.toggleCodeSpan
import com.github.wood.prosemirror.compose.model.toggleSpanStyle
import com.github.wood.prosemirror.compose.model.toggleUnorderedList
import com.github.wood.prosemirror.compose.model.updateLink
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
        assertEquals("a\n• b\n3. c", state.toText())

        // 往返
        val html = state.toHtml()
        val state2 = ProseMirrorState()
        state2.setHtml(html)
        assertEquals("a\n• b\n3. c", state2.toText())
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
    fun repeatedCharacterInsertionUsesCaretPosition() {
        val state = ProseMirrorState()
        state.type("aaa")
        state.placeCaret(1)
        state.type("a")
        assertEquals("aaaa", state.toText())
        assertEquals(2, state.textFieldValue.selection.min, "caret should stay after the inserted character")
    }

    @Test
    fun deletingBlockSeparatorJoinsParagraphs() {
        val state = ProseMirrorState()
        state.setText("a\nb")
        assertEquals(2, state.doc.childCount)
        state.onTextFieldValueChange(TextFieldValue("ab", TextRange(1)))
        assertEquals(1, state.doc.childCount)
        assertEquals("ab", state.toText())
    }

    @Test
    fun setTextSplitsParagraphsOnNewlines() {
        val state = ProseMirrorState()
        state.setText("a\nb\nc")
        assertEquals(3, state.doc.childCount)
        assertEquals("a\nb\nc", state.toText())

        state.setText("a\n\nb")
        assertEquals(3, state.doc.childCount)
        assertEquals("a\n\nb", state.toText())

        state.setText("\nb")
        assertEquals(2, state.doc.childCount)
        assertEquals("\nb", state.toText())
    }

    @Test
    fun multilinePlainTextInputSplitsParagraphs() {
        val state = ProseMirrorState()
        state.type("a")
        state.type("\nb\nc")
        assertEquals(3, state.doc.childCount)
        assertEquals("a\nb\nc", state.toText())

        state.setText("a")
        state.type("b\n")
        assertEquals(2, state.doc.childCount)
        assertEquals("ab\n", state.toText())
    }

    @Test
    fun multilinePlainTextReplacementKeepsTrailingInlineText() {
        val state = ProseMirrorState()
        state.setText("axb")
        state.onTextFieldValueChange(TextFieldValue("a1\n2b", TextRange(4)))
        assertEquals(2, state.doc.childCount)
        assertEquals("a1\n2b", state.toText())
    }

    @Test
    fun programmaticMultilineTextOpsSplitParagraphs() {
        val state = ProseMirrorState()
        state.addTextAtIndex(0, "a\nb")
        assertEquals(2, state.doc.childCount)
        assertEquals("a\nb", state.toText())

        state.replaceTextRange(TextRange(0, 1), "x\ny")
        assertEquals("x\ny\nb", state.toText())
        assertEquals(3, state.doc.childCount)
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
    fun boldTogglesBackOffSelection() {
        val state = ProseMirrorState()
        state.type("bold")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 4)))
        state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        assertTrue(state.doc.child(0).child(0).marks.any { it.type.name == "strong" })
        state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        assertTrue(state.doc.child(0).child(0).marks.none { it.type.name == "strong" })
    }

    @Test
    fun formattingIsUndoable() {
        val state = ProseMirrorState()
        state.type("bold")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 4)))
        state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        assertTrue(state.doc.child(0).child(0).marks.any { it.type.name == "strong" })

        state.undo()
        assertTrue(state.doc.child(0).child(0).marks.none { it.type.name == "strong" })

        state.redo()
        assertTrue(state.doc.child(0).child(0).marks.any { it.type.name == "strong" })
    }

    @Test
    fun underlineTogglesBackOffSelection() {
        val state = ProseMirrorState()
        state.type("text")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 4)))
        state.toggleSpanStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
        assertTrue(state.doc.child(0).child(0).marks.any { it.type.name == "underline" })
        state.toggleSpanStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
        assertTrue(state.doc.child(0).child(0).marks.none { it.type.name == "underline" })
    }

    @Test
    fun fontSizeAndColorCoexist() {
        val state = ProseMirrorState()
        state.type("abc")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 3)))
        state.toggleSpanStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Red))
        state.toggleSpanStyle(SpanStyle(fontSize = 28.sp))
        val textStyle = state.doc.child(0).child(0).marks.firstOrNull { it.type.name == "textStyle" }
        assertNotNull(textStyle)
        assertTrue(textStyle.attrs["fontSize"] != null)
        assertTrue(textStyle.attrs["color"] != null)
    }

    @Test
    fun textStyleFieldsToggleIndependently() {
        val state = ProseMirrorState()
        state.type("abc")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 3)))
        state.toggleSpanStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Red))
        state.toggleSpanStyle(SpanStyle(fontSize = 28.sp))

        // 移除 color 后 fontSize 必须保留（字段级 toggle，而不是整类 mark 互斥）。
        state.toggleSpanStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Red))
        val textStyle = state.doc.child(0).child(0).marks.firstOrNull { it.type.name == "textStyle" }
        assertNotNull(textStyle)
        assertTrue(textStyle.attrs["fontSize"] != null)
        assertTrue(textStyle.attrs["color"] == null)

        // 再移除 fontSize 后 textStyle mark 整体消失。
        state.toggleSpanStyle(SpanStyle(fontSize = 28.sp))
        assertTrue(state.doc.child(0).child(0).marks.none { it.type.name == "textStyle" })
    }

    @Test
    fun addFontSizeKeepsPerSegmentColors() {
        val state = ProseMirrorState()
        state.type("ab")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 1)))
        state.addSpanStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Red))
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(1, 2)))
        state.addSpanStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Blue))
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 2)))
        state.addSpanStyle(SpanStyle(fontSize = 28.sp))

        val para = state.doc.child(0)
        val colors = (0 until para.childCount).mapNotNull { i ->
            para.child(i).marks.firstOrNull { it.type.name == "textStyle" }?.attrs?.get("color")
        }
        assertEquals(listOf("#FF0000", "#0000FF"), colors)
        (0 until para.childCount).forEach { i ->
            val mark = para.child(i).marks.firstOrNull { it.type.name == "textStyle" }
            assertNotNull(mark)
            assertTrue(mark.attrs["fontSize"] != null)
        }

        // color 不同时，共有的 fontSize 仍应能被识别并 toggle 掉（参考版逐字段共有样式）。
        assertEquals(28.sp, state.currentSpanStyle.fontSize)
        state.toggleSpanStyle(SpanStyle(fontSize = 28.sp))
        val updatedPara = state.doc.child(0)
        (0 until updatedPara.childCount).forEach { i ->
            val mark = updatedPara.child(i).marks.firstOrNull { it.type.name == "textStyle" }
            assertNotNull(mark)
            assertTrue(mark.attrs["fontSize"] == null)
            assertTrue(mark.attrs["color"] != null)
        }
    }

    @Test
    fun backgroundToggleAppliesAndRenders() {
        val state = ProseMirrorState()
        state.type("abc")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 3)))
        state.toggleSpanStyle(SpanStyle(background = androidx.compose.ui.graphics.Color.Yellow))
        val textStyle = state.doc.child(0).child(0).marks.firstOrNull { it.type.name == "textStyle" }
        assertNotNull(textStyle)
        assertTrue(textStyle.attrs["background"] != null)
        assertEquals(androidx.compose.ui.graphics.Color.Yellow, state.currentSpanStyle.background)

        state.toggleSpanStyle(SpanStyle(background = androidx.compose.ui.graphics.Color.Yellow))
        assertTrue(state.doc.child(0).child(0).marks.none { it.type.name == "textStyle" })
    }

    @Test
    fun cjkCompositionLikeEditsRoundTrip() {
        val state = ProseMirrorState()
        // IME 典型序列：输入拼音字符 → 替换为候选词（仍在 composition 中）→ 提交。
        state.type("n")
        state.onTextFieldValueChange(
            TextFieldValue("你", TextRange(1), composition = TextRange(0, 1)),
        )
        state.onTextFieldValueChange(TextFieldValue("你", TextRange(1)))
        assertEquals("你", state.toText())
        assertEquals(1, state.textFieldValue.selection.min)
    }

    @Test
    fun codeTogglesBackOffSelection() {
        val state = ProseMirrorState()
        state.type("abc")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 3)))
        state.toggleCodeSpan()
        assertTrue(state.doc.child(0).child(0).marks.any { it.type.name == "code" })
        state.toggleCodeSpan()
        assertTrue(state.doc.child(0).child(0).marks.none { it.type.name == "code" })
    }

    @Test
    fun codeOnMixedMarksMergesIntoOneRangePerParagraph() {
        val state = ProseMirrorState()
        state.setHtml(
            "<p><b>ProseMirrorEditor</b> is a <i>composable</i> that allows you to edit " +
                "<u>rich text</u> content.</p>",
        )
        val length = state.textFieldValue.text.length
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, length)))
        state.toggleCodeSpan()
        val codeRanges = state.styledRichSpanList.filter {
            it.first is com.github.wood.prosemirror.compose.model.RichSpanStyle.Code
        }
        assertEquals(listOf(TextRange(0, length)), codeRanges.map { it.second })
        assertEquals(
            "ProseMirrorEditor is a composable that allows you to edit rich text content.",
            state.toText(),
        )
    }

    @Test
    fun codeOnCjkTextKeepsTextAndSingleRange() {
        val state = ProseMirrorState()
        state.type("这是一段中文内容。")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 9)))
        state.toggleCodeSpan()
        assertEquals("这是一段中文内容。", state.toText())
        val codeRanges = state.styledRichSpanList.filter {
            it.first is com.github.wood.prosemirror.compose.model.RichSpanStyle.Code
        }
        assertEquals(listOf(TextRange(0, 9)), codeRanges.map { it.second })
        assertTrue(state.doc.child(0).child(0).marks.any { it.type.name == "code" })
    }

    @Test
    fun codeOnMultiParagraphSelectionKeepsStructure() {
        val state = ProseMirrorState()
        state.setText("alpha beta\ngamma delta")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, 10)))
        state.toggleCodeSpan()
        assertEquals(2, state.doc.childCount)
        assertEquals("alpha beta\ngamma delta", state.toText())
        val codeRanges = state.styledRichSpanList.filter { it.first is com.github.wood.prosemirror.compose.model.RichSpanStyle.Code }
        assertEquals(listOf(TextRange(0, 10)), codeRanges.map { it.second })
        state.toggleCodeSpan()
        assertTrue(state.styledRichSpanList.none { it.first is com.github.wood.prosemirror.compose.model.RichSpanStyle.Code })
    }

    @Test
    fun paragraphTextAlignAppliesAndRenders() {
        val state = ProseMirrorState()
        state.type("abc")
        state.placeCaret(3)
        state.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Center))
        assertEquals("center", state.doc.child(0).attrs["textAlign"])
        assertTrue(state.annotatedString.paragraphStyles.any { it.item.textAlign == TextAlign.Center })

        state.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Left))
        assertEquals("left", state.doc.child(0).attrs["textAlign"])
        assertTrue(state.annotatedString.paragraphStyles.any { it.item.textAlign == TextAlign.Left })
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
        assertEquals("• item1\n• ", state.toText())
    }

    @Test
    fun deletingWholeListMarkerExitsList() {
        val state = ProseMirrorState()
        state.type("item1")
        state.toggleUnorderedList()
        val markerEnd = state.textFieldValue.text.indexOf("item1")
        state.onTextFieldValueChange(state.textFieldValue.copy(selection = TextRange(0, markerEnd)))
        state.onTextFieldValueChange(
            TextFieldValue(state.textFieldValue.text.substring(markerEnd), TextRange(0))
        )
        assertEquals("item1", state.toText())
        assertEquals("paragraph", state.editorState.doc.child(0).type.name)
    }

    @Test
    fun partialMarkerDeletionDemotesListItem() {
        val state = ProseMirrorState()
        state.type("item1")
        state.toggleUnorderedList()
        val markerEnd = state.textFieldValue.text.indexOf("item1")
        state.placeCaret(markerEnd)
        state.onTextFieldValueChange(
            TextFieldValue(
                state.textFieldValue.text.removeRange(markerEnd - 1, markerEnd),
                TextRange(markerEnd - 1),
            )
        )
        assertEquals("item1", state.toText())
        assertEquals("paragraph", state.editorState.doc.child(0).type.name)
    }

    @Test
    fun collapsedFullMarkerDeletionIsTreatedAsImeEcho() {
        val state = ProseMirrorState()
        state.type("item1")
        state.toggleUnorderedList()
        state.justInsertedListParagraph = true
        val markerEnd = state.textFieldValue.text.indexOf("item1")
        state.placeCaret(markerEnd)
        state.onTextFieldValueChange(
            TextFieldValue(state.textFieldValue.text.substring(markerEnd), TextRange(0))
        )
        assertEquals("• item1", state.toText())
        assertEquals("bullet_list", state.editorState.doc.child(0).type.name)
    }

    @Test
    fun imeBoundarySpaceRefreshMaterializesSpace() {
        val state = ProseMirrorState()
        state.setText("a\nb")
        state.placeCaret(1)

        // 模拟建议词组合提交：composition 结束在段落分隔符边界，随后 IME 把光标跨过边界。
        state.onTextFieldValueChange(state.textFieldValue.copy(composition = TextRange(1, 1)))
        state.onTextFieldValueChange(state.textFieldValue.copy(composition = null, selection = TextRange(2)))

        assertEquals("a \nb", state.toText())
        assertEquals("a ", state.doc.child(0).textContent)
    }

    @Test
    fun linkStyleIsReadableAtCaretAfterLinkAndUpdatable() {
        val state = ProseMirrorState()
        state.addLink("ab", "https://old.example")
        state.placeCaret(2)
        assertTrue(state.isLink)
        assertEquals("ab", state.selectedLinkText)
        state.updateLink("https://new.example")
        assertTrue(state.toHtml().contains("href=\"https://new.example\""))
    }

    @Test
    fun markdownRoundTripPreservesBlockStructure() {
        val state = ProseMirrorState()
        state.setMarkdown("# Title\n\n- **bold** item\n- second\n\n1. one\n2. two")
        assertEquals("heading", state.doc.child(0).type.name)
        assertEquals("bullet_list", state.doc.child(1).type.name)
        assertEquals("ordered_list", state.doc.child(2).type.name)

        val markdown = state.toMarkdown()
        assertTrue(markdown.contains("# Title"), markdown)
        assertTrue(markdown.contains("- "), markdown)
        assertTrue(markdown.contains("1. one"), markdown)

        val roundTrip = ProseMirrorState().apply { setMarkdown(markdown) }
        assertEquals("heading", roundTrip.doc.child(0).type.name)
        assertEquals("bullet_list", roundTrip.doc.child(1).type.name)
        assertEquals("ordered_list", roundTrip.doc.child(2).type.name)
    }

    @Test
    fun markdownTokenRoundTripsAsAtomicToken() {
        val state = ProseMirrorState()
        state.setMarkdown("[@alice](trigger:mention:alice)")
        val paragraph = state.doc.child(0)
        assertEquals("token", paragraph.child(0).type.name)
        assertEquals("mention", paragraph.child(0).attrs["triggerId"])
        assertEquals("alice", paragraph.child(0).attrs["id"])

        val markdown = state.toMarkdown()
        assertTrue(markdown.contains("trigger:mention:alice"), markdown)
        val roundTrip = ProseMirrorState().apply { setMarkdown(markdown) }
        assertEquals("token", roundTrip.doc.child(0).child(0).type.name)
    }

    @Test
    fun partialRangeHtmlKeepsBlockWrappers() {
        val state = ProseMirrorState()
        state.setText("ab")
        assertEquals("<p>a</p>", state.toHtml(TextRange(0, 1)))

        state.setHtml("<ul><li>one</li><li>two</li></ul>")
        val oneStart = state.textFieldValue.text.indexOf("one")
        val html = state.toHtml(TextRange(oneStart, oneStart + 3))
        assertTrue(html.startsWith("<ul>"), html)
        assertTrue(html.contains("<li><p>one</p></li>"), html)
        assertTrue(!html.contains("two"), html)

        assertEquals("- one", state.toText(TextRange(oneStart, oneStart + 3)))
        assertTrue(state.toMarkdown(TextRange(oneStart, oneStart + 3)).contains("- one"))
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
