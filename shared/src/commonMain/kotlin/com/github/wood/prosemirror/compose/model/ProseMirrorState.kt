package com.github.wood.prosemirror.compose.model

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import com.atlassian.prosemirror.history.HistoryOptionsConfig
import com.atlassian.prosemirror.history.HistoryPlugin
import com.atlassian.prosemirror.history.closeHistory
import com.atlassian.prosemirror.history.redo
import com.atlassian.prosemirror.history.redoDepth
import com.atlassian.prosemirror.history.undo
import com.atlassian.prosemirror.history.undoDepth
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.atlassian.prosemirror.model.Attrs
import com.atlassian.prosemirror.model.DOMParser
import com.atlassian.prosemirror.model.DOMSerializer
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.MarkType
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.NodeType
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.model.util.resolveSafe
import com.github.wood.prosemirror.compose.schema.DefaultProseMirrorSchema
import com.atlassian.prosemirror.state.EmptyEditorStateConfig
import com.atlassian.prosemirror.state.PMEditorState
import com.atlassian.prosemirror.state.Plugin
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.state.Transaction
import com.atlassian.prosemirror.transform.setBlockType
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.annotation.InternalProseMirrorApi
import com.github.wood.prosemirror.compose.model.trigger.Trigger
import com.github.wood.prosemirror.compose.model.trigger.TriggerQuery
import com.github.wood.prosemirror.compose.model.trigger.detectActiveTrigger
import com.github.wood.prosemirror.compose.platform.currentPlatform
import com.github.wood.prosemirror.compose.utils.PositionCoordinateMap
import com.github.wood.prosemirror.compose.utils.PositionCoordinateMapBuilder
import com.github.wood.prosemirror.compose.utils.appendProseMirrorDoc
import com.github.wood.prosemirror.compose.utils.calculateTextDiff
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val SelectionGestureGrace = 1.seconds
private val SelectionGesturePointerFreshness = 500.milliseconds

@OptIn(ExperimentalProseMirrorApi::class)
@Stable
public class ProseMirrorState(
    public val schema: Schema = DefaultProseMirrorSchema,
    initialDoc: Node = schema.topNodeType.createAndFill()!!,
    plugins: List<Plugin<*>> = emptyList(),
    historyLimit: Int = 100,
    coalesceWindowMs: Long = 500L,
) {
    private val historyPlugin = HistoryPlugin(
        HistoryOptionsConfig(depth = historyLimit, newGroupDelay = coalesceWindowMs.toInt())
    )

    internal val effectivePlugins: List<Plugin<*>> = run {
        require(plugins.none { it is HistoryPlugin }) {
            "ProseMirrorState installs its own HistoryPlugin internally; " +
                    "passing one in plugins is not allowed (duplicate plugin keys throw in Configuration)."
        }
        plugins + historyPlugin
    }

    // 底层持有的、Atlassian 的核心不可变编辑器状态
    public var editorState: PMEditorState by mutableStateOf(
        PMEditorState.create(
            EmptyEditorStateConfig(schema = schema, doc = initialDoc, plugins = effectivePlugins)
        )
    )
        internal set

    // 带有样式的扁平 AnnotatedString 缓存
    public var annotatedString: AnnotatedString by mutableStateOf(AnnotatedString(""))
        private set

    // 与 BasicTextField 绑定的数据源
    public var textFieldValue by mutableStateOf(TextFieldValue())
        private set

    public var visualTransformation: VisualTransformation by mutableStateOf(VisualTransformation.None)
        private set

    public var textLayoutResult: TextLayoutResult? by mutableStateOf(null)

    // 图片等原子行内节点的占位画笔集
    public var inlineContentMap: SnapshotStateMap<String, InlineTextContent> = mutableStateMapOf()

    internal val usedInlineContentMapKeys: MutableSet<String> = mutableSetOf()

    internal val styledRichSpanList = mutableStateListOf<Pair<RichSpanStyle, TextRange>>()

    public val config: ProseMirrorConfig = ProseMirrorConfig(
        updateText = { updateAnnotatedString() }
    )
    public val doc: Node
        get() = editorState.doc

    internal var coordinateMap: PositionCoordinateMap = PositionCoordinateMap.EMPTY

    // --- 状态控制变量 ---
    public var singleParagraphMode: Boolean by mutableStateOf(false)
    public var suppressUndoShortcuts: Boolean by mutableStateOf(false)
    public var isFocused: Boolean by mutableStateOf(false)

    // --- 剪贴板相关 ---
    // 粘贴时由平台剪贴板管理器存入，onTextFieldValueChange 检测到文本新增时消费
    internal var pendingClipboardHtml: String? = null

    // 复制时使用的选区；折叠选区返回 null（参考版契约：折叠时清空剪贴板）
    internal val copySelection: TextRange?
        get() = textFieldValue.selection.takeIf { !it.collapsed }

    // --- 列表 marker 宽度缓存（onTextLayout 测量；首帧回退 0.sp） ---
    internal val listMarkerWidthCache: MutableMap<String, TextUnit> = mutableMapOf()

    // --- 选区手势相关变量 ---
    internal var selectionGesturePressed = false
    internal var selectionGestureLastActivity: kotlin.time.TimeMark? = null
    internal var treatSelectionChangesAsGesture: Boolean = currentPlatform.isAndroid || currentPlatform.isIOS
    internal var selectionGesturePointer: Offset? = null
        private set
    internal var selectionGesturePointerMark: kotlin.time.TimeMark? = null

    public var textFieldWindowPosition: Offset by mutableStateOf(Offset.Zero)

    // --- Trigger 触发器相关 ---
    public val triggers: List<Trigger>
        field: MutableList<Trigger> = mutableStateListOf()

    private var _activeTriggerQuery: TriggerQuery? by mutableStateOf(null)
    public val activeTriggerQuery: TriggerQuery? get() = _activeTriggerQuery
    private var suppressedTriggerRange: TextRange? = null
    internal var triggerKeyHandler: ((KeyEvent) -> Boolean)? = null


    init {
        // 初始光标定位到第一个 textblock 的内容起点。
        // PMEditorState 默认 selection 在 doc 层（depth 0），直接输入会在 doc 层
        // 创建新块而不是写入段落内。
        val firstTextblockPos = findFirstTextblockContentStart(initialDoc)
        val initTr = editorState.tr
        initTr.setSelection(TextSelection.create(initialDoc, firstTextblockPos, firstTextblockPos))
        editorState = editorState.apply(initTr)
        updateAnnotatedString()
    }

    /** 找到文档中第一个 textblock 的内容起点位置（找不到时回退 1）。 */
    private fun findFirstTextblockContentStart(doc: Node): Int {
        var pos = 1
        while (pos < doc.content.size) {
            val resolved = doc.resolveSafe(pos)
            if (resolved?.parent?.isTextblock == true) return pos
            pos++
        }
        return 1
    }

    internal fun updateAnnotatedString(activeComposition: TextRange? = null) {
        val mapBuilder = PositionCoordinateMapBuilder()
        val newStyledRanges = mutableListOf<Pair<RichSpanStyle, TextRange>>()
        usedInlineContentMapKeys.clear()

        annotatedString = buildAnnotatedString {
            appendProseMirrorDoc(
                state = this@ProseMirrorState,
                doc = editorState.doc,
                mapBuilder = mapBuilder,
                styledRanges = newStyledRanges
            )
        }

        coordinateMap = mapBuilder.build()
        styledRichSpanList.clear()
        styledRichSpanList.addAll(newStyledRanges)

        // 清理当前帧未使用的 inlineContentKey
        inlineContentMap.keys.toList().forEach { key ->
            if (key !in usedInlineContentMapKeys) {
                inlineContentMap.remove(key)
            }
        }

        val flatSelectionStart = coordinateMap.pmToFlat(editorState.selection.anchor)
        val flatSelectionEnd = coordinateMap.pmToFlat(editorState.selection.head)

        textFieldValue = TextFieldValue(
            annotatedString = annotatedString,
            selection = TextRange(flatSelectionStart, flatSelectionEnd),
            composition = activeComposition
        )

        val transformed = annotatedString
        visualTransformation = VisualTransformation {
            TransformedText(transformed, OffsetMapping.Identity)
        }
        refreshActiveTriggerQuery()
    }

    public fun dispatch(transaction: Transaction, activeComposition: TextRange? = null) {
        val nextState = editorState.apply(transaction)
        if (nextState != editorState) {
            editorState = nextState
            updateAnnotatedString(activeComposition)
        }
    }

    public fun onTextFieldValueChange(newValue: TextFieldValue) {
        val oldValue = textFieldValue
        val tr = editorState.tr

        if (newValue.composition != null) {
            tr.setMeta("composition", newValue.composition.hashCode())
        }

        if (newValue.text != oldValue.text) {
            // A. 侦测文本编辑情况并转化为 ProseMirror Steps
            // 注意：不在本分支手动 setSelection——文本变化后旧坐标图已失效，
            // 光标/选区由 PM 步骤（insertText/delete/replaceRange）自动映射。
            val diff = calculateTextDiff(
                oldValue.text,
                newValue.text,
            )
            if (diff != null) {
                val pmFrom = coordinateMap.flatToPm(diff.start)
                val pmTo = coordinateMap.flatToPm(diff.end)

                // 软键盘/IME 的换行：不写入 "\n"，走列表/段落拆分逻辑
                if (diff.text == "\n" && handleEnter(pmFrom)) return

                if (diff.text.isNotEmpty()) {
                    // 粘贴：pendingClipboardHtml 由平台剪贴板管理器在 getClipEntry 时写入
                    val pending = pendingClipboardHtml
                    if (pending != null) {
                        pendingClipboardHtml = null
                        val parsed = DOMParser.fromSchema(schema).parseHtml(pending)
                        tr.replaceRange(pmFrom, pmTo, Slice(parsed.content, 0, 0))
                    } else {
                        tr.insertText(diff.text, pmFrom, pmTo)
                    }
                } else {
                    // 删除恰好命中块分隔符（合成的 "\n"）→ 合并相邻块（pmBefore..pmAfter）
                    val separator = coordinateMap.blockSeparators.firstOrNull {
                        it.flatIndex >= diff.start && it.flatIndex < diff.end
                    }
                    if (separator != null) {
                        tr.delete(separator.pmBefore, separator.pmAfter)
                    } else {
                        tr.delete(pmFrom, pmTo)
                    }
                }
            }
            dispatch(tr, activeComposition = newValue.composition)
        } else if (newValue.selection != oldValue.selection) {
            // C. 纯选区或光标位置移动
            val pmAnchor = coordinateMap.flatToPm(newValue.selection.start)
            val pmHead = coordinateMap.flatToPm(newValue.selection.end)
            tr.setSelection(TextSelection.create(tr.doc, pmAnchor, pmHead))
            dispatch(tr, activeComposition = newValue.composition)
        }
    }

    /**
     * 将 Compose 扁平字符索引转换为 ProseMirror 内部结构坐标
     */
    public fun flatToPm(flatPos: Int): Int {
        return coordinateMap.flatToPm(flatPos)
    }

    /**
     * 将 ProseMirror 内部结构坐标转换为 Compose 扁平字符索引
     */
    public fun pmToFlat(pmPos: Int): Int {
        return coordinateMap.pmToFlat(pmPos)
    }

    // --- 键盘按键事件拦截 ---
    internal fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        // 1. Undo/redo 快捷键 - 在 BasicTextField 内置处理之前拦截，
        //    使 PM history 插件回退富文本模型而不是纯文本 TextFieldValue 状态。
        //    即使栈为空也消费事件（阻止 BasicTextField 的原生 undo 作用于扁平文本）。
        if (!suppressUndoShortcuts && event.type == KeyEventType.KeyDown && !event.isAltPressed) {
            val modifier = event.isMetaPressed || event.isCtrlPressed
            if (modifier) {
                when (event.key) {
                    Key.Z if !event.isShiftPressed -> {
                        undo()
                        return true
                    }

                    Key.Z if event.isShiftPressed -> {
                        redo()
                        return true
                    }
                }
            }
        }

        // 2. 给予 TriggerSuggestions 下拉菜单最高优先级的键盘拦截权 (↑/↓/Enter/Esc)
        triggerKeyHandler?.invoke(event)?.let { if (it) return true }

        // 3. Enter/Tab 列表处理
        return handleListKeyEvent(event)
    }

    // --- 选区与手势追踪 ---
    internal fun onSelectionGestureStart() {
        selectionGesturePressed = true
        selectionGestureLastActivity = kotlin.time.TimeSource.Monotonic.markNow()
    }

    internal fun onSelectionGestureEnd() {
        selectionGesturePressed = false
        selectionGestureLastActivity = kotlin.time.TimeSource.Monotonic.markNow()
    }

    internal fun onSelectionGesturePointerMove(position: Offset) {
        selectionGesturePointer = position
        selectionGesturePointerMark = kotlin.time.TimeSource.Monotonic.markNow()
    }

    internal var registerLastPressPositionJob: Job? = null

    /** 按压时调整选区（块分隔符吸附 + 行钳制），并注册按压位置。 */
    internal suspend fun adjustSelectionAndRegisterPressPosition(pressPosition: Offset) {
        adjustSelection(pressPosition)
        registerLastPressPosition(pressPosition)
    }

    internal fun onTextLayout(textLayoutResult: TextLayoutResult, density: Density) {
        this.textLayoutResult = textLayoutResult
        refreshActiveTriggerCaretRect()
        measureListMarkerWidths(textLayoutResult, density)
    }

    /** 测量每行开头的列表 marker 宽度并缓存（供 TextIndent 对齐）。 */
    private fun measureListMarkerWidths(layout: TextLayoutResult, density: Density) {
        var line = 0
        while (line < layout.lineCount) {
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line, visibleEnd = true)
            if (end > start) {
                val lineText = textFieldValue.text.substring(start, end)
                val markerLen = listMarkerPrefixLength(lineText)
                if (markerLen > 0) {
                    val markerText = lineText.substring(0, markerLen)
                    if (markerText !in listMarkerWidthCache) {
                        val widthPx = runCatching {
                            layout.getBoundingBox(start + markerLen).left - layout.getBoundingBox(start).left
                        }.getOrNull()
                        if (widthPx != null) {
                            listMarkerWidthCache[markerText] = with(density) { widthPx.toSp() }
                        }
                    }
                }
            }
            line++
        }
    }

    /** 检测行首是否为列表 marker（"• " 等无序前缀，或 "1. "/"1) " 等有序标记）。 */
    private fun listMarkerPrefixLength(text: String): Int {
        // 无序 marker（来自 config 的前缀列表）
        config.unorderedListStyleType.prefixes.forEach { prefix ->
            val full = prefix + " "
            if (text.startsWith(full)) return full.length
        }
        // 有序 marker：数字/字母/罗马（1-4 字符）+ 后缀
        listOf(". ", ") ").forEach { suffix ->
            val idx = text.indexOf(suffix)
            if (idx in 1..4) {
                val head = text.substring(0, idx)
                if (head.all { it.isDigit() || it in 'a'..'z' || it in 'A'..'Z' || it in '٠'..'٩' }) {
                    return idx + suffix.length
                }
            }
        }
        return 0
    }

    // --- Trigger 触发器与 Token 相关方法 ---
    public fun registerTrigger(trigger: Trigger) {
        val existingIndex = triggers.indexOfFirst { it.id == trigger.id }
        if (existingIndex >= 0) {
            triggers.removeAt(existingIndex)
            triggers.add(existingIndex, trigger)
        } else {
            triggers.add(trigger)
        }
    }

    public fun unregisterTrigger(id: String) {
        triggers.removeAll { it.id == id }
        if (_activeTriggerQuery?.triggerId == id) _activeTriggerQuery = null
    }

    public fun cancelActiveTrigger() {
        val query = _activeTriggerQuery ?: return
        suppressedTriggerRange = query.range
        _activeTriggerQuery = null
    }

    public fun insertToken(triggerId: String, id: String, label: String) {
        val query = _activeTriggerQuery ?: return
        check(query.triggerId == triggerId) { "insertToken triggerId must match the active query" }
        val trigger = triggers.firstOrNull { it.id == triggerId }
            ?: throw IllegalArgumentException("Trigger '$triggerId' is not registered")
        require(label.isNotEmpty() && label.first() == trigger.char) {
            "Token label must start with the trigger char"
        }

        val pmFrom = flatToPm(query.range.min)
        val pmTo = flatToPm(query.range.max)

        val tr = editorState.tr
        closeHistory(tr)
        val tokenNode = schema.node(
            "token",
            mapOf<String, Any?>("triggerId" to triggerId, "id" to id, "label" to label),
        )
        tr.replaceWith(pmFrom, pmTo, listOf(tokenNode, schema.text(" ")))
        // 光标落在 token + 尾随空格之后
        tr.setSelection(TextSelection.create(tr.doc, pmFrom + 2, pmFrom + 2))
        dispatch(tr)
        cancelActiveTrigger()
    }

    private fun refreshActiveTriggerQuery() {
        if (triggers.isEmpty()) {
            _activeTriggerQuery = null
            return
        }
        val caret = textFieldValue.selection.min
        _activeTriggerQuery = detectActiveTrigger(
            text = textFieldValue.text,
            caretOffset = caret,
            triggers = triggers,
            textLayoutResult = textLayoutResult,
            suppressedRange = suppressedTriggerRange
        )
    }

    private fun refreshActiveTriggerCaretRect() {
        val query = _activeTriggerQuery ?: return
        val layout = textLayoutResult ?: return
        val caret = textFieldValue.selection.min
        val fresh = runCatching { layout.getCursorRect(caret) }.getOrNull()
        if (fresh != query.caretRect) {
            _activeTriggerQuery = query.copy(caretRect = fresh)
        }
    }

    // --- 屏幕像素 Offset 节点探针（用于只读文本点击手势）---
    public fun getLinkByOffset(offset: Offset): String? {
        val layout = textLayoutResult ?: return null
        val flatPos = layout.getOffsetForPosition(offset)
        val pmPos = flatToPm(flatPos)
        val resolved = doc.resolveSafe(pmPos) ?: return null
        val linkMark = resolved.marks().firstOrNull { it.type.name == "link" }
        return linkMark?.attrs?.get("href") as? String
    }

    public fun isLink(offset: Offset): Boolean = getLinkByOffset(offset) != null

    public fun getTokenByOffset(offset: Offset): RichSpanStyle.Token? {
        val layout = textLayoutResult ?: return null
        val flatPos = layout.getOffsetForPosition(offset)
        val pmPos = flatToPm(flatPos)
        val node = doc.nodeAt(pmPos) ?: return null
        if (node.type.name == "mention" || node.type.name == "token") {
            return RichSpanStyle.Token(
                triggerId = node.attrs["triggerId"] as? String ?: "",
                id = node.attrs["id"] as? String ?: "",
                label = node.attrs["label"] as? String ?: node.textContent
            )
        }
        return null
    }

    public fun isToken(offset: Offset): Boolean = getTokenByOffset(offset) != null

    @InternalProseMirrorApi
    public fun getTokenByTextIndex(textIndex: Int): RichSpanStyle.Token? {
        val pmPos = flatToPm(textIndex)
        val node = doc.nodeAt(pmPos) ?: return null
        if (node.type.name == "mention" || node.type.name == "token") {
            return RichSpanStyle.Token(
                triggerId = node.attrs["triggerId"] as? String ?: "",
                id = node.attrs["id"] as? String ?: "",
                label = node.attrs["label"] as? String ?: node.textContent
            )
        }
        return null
    }

    public fun toHtml(): String {
        return DOMSerializer.fromSchema(schema).serializeFragmentToHtml(doc.content)
    }

    public fun toText(): String {
        return doc.textBetween(0, doc.content.size, blockSeparator = "\n", leafText = null)
    }

    /**
     * 导出指定扁平选区范围的纯文本。
     * 块分隔符用 "\n"，叶子节点（image/token）输出其 textContent（如 token 的 label）。
     * 注意：与参考版不同，本实现不含列表 marker 文本。
     */
    public fun toText(range: TextRange): String {
        val pmFrom = flatToPm(range.min.coerceIn(0, textFieldValue.text.length))
        val pmTo = flatToPm(range.max.coerceIn(0, textFieldValue.text.length))
        return doc.textBetween(pmFrom, pmTo, blockSeparator = "\n", leafText = null)
    }

    /**
     * 导出指定扁平选区范围的 HTML。
     * 跨块边界时会产生 `<p>` 包裹的部分段落（与参考版按 span 边界提取的行为不同）。
     */
    public fun toHtml(range: TextRange): String {
        val pmFrom = flatToPm(range.min.coerceIn(0, textFieldValue.text.length))
        val pmTo = flatToPm(range.max.coerceIn(0, textFieldValue.text.length))
        val cutNode = doc.cut(pmFrom, pmTo)
        return DOMSerializer.fromSchema(schema).serializeFragmentToHtml(cutNode.content)
    }

    public val canUndo: Boolean
        get() = undoDepth(editorState) > 0

    public val canRedo: Boolean
        get() = redoDepth(editorState) > 0

    public fun undo() {
        undo(editorState) { dispatch(it) }
    }

    public fun redo() {
        redo(editorState) { dispatch(it) }
    }

    public companion object {
        /**
         * 保存为 HTML + 扁平选区；恢复时 [setHtml] 后重设选区。
         * 与参考版契约一致：恢复（setHtml）会清空 undo/redo 历史。
         */
        public val Saver: Saver<ProseMirrorState, *> = listSaver(
            save = { listOf(it.toHtml(), it.textFieldValue.selection.start.toString(), it.textFieldValue.selection.end.toString()) },
            restore = { saved ->
                val state = ProseMirrorState()
                state.setHtml(saved[0])
                val length = state.textFieldValue.text.length
                val start = saved[1].toInt().coerceIn(0, length)
                val end = saved[2].toInt().coerceIn(0, length)
                val tr = state.editorState.tr
                tr.setSelection(TextSelection.create(tr.doc, state.flatToPm(start), state.flatToPm(end)))
                state.dispatch(tr)
                state
            }
        )
    }
}

/**
 * 创建并记住一个 [ProseMirrorState]。
 * 使用 [rememberSaveable] 保证 Android 配置变更/进程重建时状态不丢失。
 * [initialDoc] 与 [initialHtml] 至少提供一个；均不提供时使用空文档。
 */
@Composable
public fun rememberProseMirrorState(
    initialDoc: Node? = null,
    initialHtml: String? = null,
    historyLimit: Int = 100,
    coalesceWindowMs: Long = 500L,
): ProseMirrorState = rememberSaveable(saver = ProseMirrorState.Saver) {
    val doc = initialDoc
        ?: if (initialHtml != null) DOMParser.fromSchema(DefaultProseMirrorSchema).parseHtml(initialHtml)
        else DefaultProseMirrorSchema.topNodeType.createAndFill()!!
    ProseMirrorState(
        schema = DefaultProseMirrorSchema,
        initialDoc = doc,
        historyLimit = historyLimit,
        coalesceWindowMs = coalesceWindowMs,
    )
}