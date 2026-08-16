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
import com.atlassian.prosemirror.model.DOMParser
import com.atlassian.prosemirror.model.DOMSerializer
import com.atlassian.prosemirror.model.Fragment
import com.atlassian.prosemirror.model.Mark
import com.atlassian.prosemirror.model.Node
import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.Slice
import com.atlassian.prosemirror.model.util.resolveSafe
import com.github.wood.prosemirror.compose.schema.DefaultProseMirrorSchema
import com.atlassian.prosemirror.state.EmptyEditorStateConfig
import com.atlassian.prosemirror.state.PMEditorState
import com.atlassian.prosemirror.state.Plugin
import com.atlassian.prosemirror.state.TextSelection
import com.atlassian.prosemirror.state.Transaction
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
import com.github.wood.prosemirror.compose.utils.extractRangeFragment
import com.github.wood.prosemirror.compose.utils.toFlatText
import kotlinx.coroutines.Job

// 物理按键之后 300ms 内的光标移动视为键盘导航，而不是 IME 补空格 (#779)。
private const val PhysicalKeyNavigationWindowMs = 300L

// IME 编辑后 300ms 内跨段落分隔符的光标步进视为同一次建议词提交的后续刷新 (#779)。
private const val ImeEditFollowUpWindowMs = 300L

private val ProseMirrorStateClockStart = kotlin.time.TimeSource.Monotonic.markNow()

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

    /** 与参考版一致的公开选区访问器。 */
    public var selection: TextRange
        get() = textFieldValue.selection
        set(value) {
            if (value.min >= 0 && value.max <= textFieldValue.text.length) {
                onTextFieldValueChange(textFieldValue.copy(selection = value))
            }
        }

    public val composition: TextRange? get() = textFieldValue.composition

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

    /**
     * 最近一次非折叠选区。Android 等平台在 [setClipEntry] 被调用前会把选区折叠，
     * 复制时优先用当前非折叠选区，否则回退到这里（与参考版一致）。
     */
    internal var lastNonCollapsedSelection: TextRange = TextRange.Zero

    // 复制时使用的选区；折叠选区时回退到最近的非折叠选区，仍不可用则返回 null。
    internal val copySelection: TextRange?
        get() {
            val selection = textFieldValue.selection
            if (!selection.collapsed) return selection
            if (!lastNonCollapsedSelection.collapsed) return lastNonCollapsedSelection
            return null
        }

    // --- 列表 marker 宽度缓存（onTextLayout 测量；首帧回退 0.sp） ---
    internal val listMarkerWidthCache: MutableMap<String, TextUnit> = mutableMapOf()

    // --- 选区手势相关变量 ---
    internal var selectionGesturePressed = false
    internal var selectionGestureLastActivity: kotlin.time.TimeMark? = null
    internal var treatSelectionChangesAsGesture: Boolean = currentPlatform.isAndroid || currentPlatform.isIOS
    internal var selectionGesturePointer: Offset? = null
        private set
    internal var selectionGesturePointerMark: kotlin.time.TimeMark? = null

    /** 最近一次按压位置；用于排除按压驱动的光标移动（#779）。 */
    internal var lastPressPosition: Offset? = null

    /** 最近一次物理按键时间戳；用于排除硬件键盘导航（#779）。 */
    private var lastPhysicalKeyEventMs: Long? = null

    /** 最近一次 IME 文本编辑/组合提交后的光标与时间戳（#779）。 */
    private var lastImeEditCaret: Int = -1
    private var lastImeEditMs: Long? = null

    /**
     * Enter 在列表里刚生成的新 item 的 marker 可能被 Samsung 等 IME echo 回删；
     * 折叠光标下删除整段 marker 时优先按 echo 吸收（PR #722）。用户实际输入后清除。
     */
    internal var justInsertedListParagraph: Boolean = false

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
    internal fun findFirstTextblockContentStart(doc: Node): Int {
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

        if (!textFieldValue.selection.collapsed) {
            lastNonCollapsedSelection = textFieldValue.selection
        }

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
        val previousText = textFieldValue.text
        val previousComposition = textFieldValue.composition
        try {
            onTextFieldValueChangeInner(newValue)
        } finally {
            // 记录最近一次 IME 文本编辑/组合提交，供 #779 的边界补空格判定使用。
            if (
                newValue.text != previousText ||
                (previousComposition != null && newValue.composition == null)
            ) {
                lastImeEditCaret = textFieldValue.selection.min
                lastImeEditMs = currentMonotonicMs()
            }
        }
    }

    private fun onTextFieldValueChangeInner(newValue: TextFieldValue) {
        val oldValue = textFieldValue
        val tr = editorState.tr

        if (newValue.composition != null) {
            tr.setMeta("composition", newValue.composition.hashCode())
        }

        // 剪贴板 HTML 只有在“文本确实变长”时才可能是粘贴；否则只是平台
        // 剪贴板可用性检查留下的过期标记。无论哪种情况，本帧都要消费掉，避免污染下一次编辑。
        val pasteHtml = pendingClipboardHtml
        val isPaste = pasteHtml != null && newValue.text.length > oldValue.text.length
        if (pasteHtml != null) {
            pendingClipboardHtml = null
        }

        if (newValue.text != oldValue.text) {
            // A. 侦测文本编辑情况并转化为 ProseMirror Steps
            // 注意：不在本分支手动 setSelection——文本变化后旧坐标图已失效，
            // 光标/选区由 PM 步骤（insertText/delete/replaceRange）自动映射。
            val diff = calculateTextDiff(
                oldText = oldValue.text,
                newText = newValue.text,
                oldSelection = oldValue.selection,
                newSelection = newValue.selection,
            )
            if (diff != null) {
                // 用户实际输入/替换后，之前 Enter 产生的 marker echo 标记失效。
                if (diff.text.isNotEmpty()) {
                    justInsertedListParagraph = false
                }

                val pmFrom = coordinateMap.flatToPm(diff.start)
                val pmTo = coordinateMap.flatToPm(diff.end)

                // 列表 marker 是原子 prefix：
                // - 选中整段 marker 删除 → 退出列表；
                // - 折叠光标下整段 marker 消失 → IME echo，吸收掉这次回删；
                // - 部分裁剪/替换 marker → 移除整段 prefix（退列表）后再应用剩余文本变更。
                val clippedMarker = coordinateMap.flatListMarkerRanges.firstOrNull {
                    diff.start < it.max && diff.end > it.min
                }
                if (clippedMarker != null && diff.start >= clippedMarker.min) {
                    val exact = diff.start == clippedMarker.min && diff.end == clippedMarker.max
                    val isCollapsedMarkerEcho =
                        exact && diff.text.isEmpty() && oldValue.selection.collapsed
                    if (isCollapsedMarkerEcho && justInsertedListParagraph) {
                        // Samsung 等 IME 把程序化插入的 marker echo 成一次删除：吸收。
                        return
                    }

                    decreaseListLevel()
                    val removedAfterMarker = (diff.end - clippedMarker.max).coerceAtLeast(0)
                    if (removedAfterMarker > 0) {
                        removeTextRange(TextRange(0, removedAfterMarker))
                    }
                    if (diff.text.isNotEmpty()) {
                        addTextAtIndex(0, diff.text)
                    }
                    return
                }

                // 软键盘/IME 的换行：不写入 "\n"，走列表/段落拆分逻辑。
                // 若换行同时替换了选区，先把选区删除，再在同一事务中拆分。
                if (diff.text == "\n" && !singleParagraphMode) {
                    val deleteRange = if (diff.end > diff.start) pmFrom to pmTo else null
                    val enterPos = if (deleteRange == null) textInputPosition(pmFrom) else pmFrom
                    if (handleEnter(enterPos, deleteRange)) return
                }

                if (diff.text.isNotEmpty()) {
                    if (isPaste) {
                        val html = checkNotNull(pasteHtml) { "Paste HTML was consumed before it could be parsed" }
                        val parsed = DOMParser.fromSchema(schema).parseHtml(html)
                        if (parsed.content.size == 0) {
                            // HTML 解析为空（例如剪贴板只有纯文本），退回 BasicTextField 给出的纯文本。
                            replacePlainText(
                                tr = tr,
                                text = diff.text,
                                pureInsertion = diff.start == diff.end,
                                pmFrom = pmFrom,
                                pmTo = pmTo,
                            )
                        } else {
                            tr.replaceRange(pmFrom, pmTo, Slice(parsed.content, 0, 0))
                        }
                    } else {
                        replacePlainText(
                            tr = tr,
                            text = diff.text,
                            pureInsertion = diff.start == diff.end,
                            pmFrom = pmFrom,
                            pmTo = pmTo,
                        )
                    }
                } else {
                    // 删除直接映射为 PM 区间。块分隔符（合成的 "\n"）虽然没有自己的
                    // PM 位置，但前后边界映射出来的 [pmFrom, pmTo] 恰好就是相邻两块的
                    // join 区间；deleteRange 会按 schema 扩展落点，安全处理跨块删除。
                    tr.deleteRange(pmFrom, pmTo)
                }
            }
            dispatch(tr, activeComposition = newValue.composition)
        } else if (newValue.selection != oldValue.selection) {
            // C. 纯选区或光标位置移动

            // #779：建议词提交时，IME 会把光标跨过段落分隔符“刷新”一步。
            // 那不是用户移动光标，而是 IME 认为自己在段尾补了一个空格；
            // 这里把空格真实写入 PM 文档，保持富文本结构与 IME 视图一致。
            if (isImeBoundarySpaceRefresh(oldValue, newValue)) {
                val boundary = oldValue.selection.min
                val pmPos = flatToPm(boundary)
                justInsertedListParagraph = false
                tr.insertText(" ", pmPos, pmPos)
                dispatch(tr)
                return
            }

            val adjustedSelection = adjustGestureSelection(newValue.selection)
            val pmAnchor = coordinateMap.flatToPm(adjustedSelection.start)
            val pmHead = coordinateMap.flatToPm(adjustedSelection.end)
            tr.setSelection(TextSelection.create(tr.doc, pmAnchor, pmHead))
            dispatch(tr, activeComposition = newValue.composition)
        } else if (newValue.composition != oldValue.composition) {
            // 纯 composition 状态变化（文本/光标都未变）：只更新 TextFieldValue，
            // PM 文档没有可映射的结构变更。
            textFieldValue = textFieldValue.copy(composition = newValue.composition)
        }
    }

    /** [isImeBoundarySpaceRefresh] 判定：见 compose-rich-editor #779。 */
    private fun isImeBoundarySpaceRefresh(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
    ): Boolean {
        if (singleParagraphMode) return false
        if (lastPressPosition != null) return false
        if (!oldValue.selection.collapsed || !newValue.selection.collapsed) return false

        val boundary = oldValue.selection.min
        if (newValue.selection.min != boundary + 1) return false
        if (newValue.composition != null) return false

        val composition = oldValue.composition
        val commitsCompositionAtBoundary =
            composition != null && composition.max == boundary
        val lastEditMs = lastImeEditMs
        val followsImeEditAtBoundary =
            composition == null &&
                lastImeEditCaret == boundary &&
                lastEditMs != null &&
                currentMonotonicMs() - lastEditMs <= ImeEditFollowUpWindowMs
        if (!commitsCompositionAtBoundary && !followsImeEditAtBoundary) return false

        val lastKeyMs = lastPhysicalKeyEventMs
        if (lastKeyMs != null && currentMonotonicMs() - lastKeyMs <= PhysicalKeyNavigationWindowMs) {
            return false
        }

        return coordinateMap.blockSeparators.any { it.flatIndex == boundary }
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

    /** 纯文本输入位置：若 PM 坐标位于 textblock 外（如列表 marker 映射到的 item 内容起点），向前吸附到最近的 textblock 内容起点。 */
    internal fun textInputPosition(pmPos: Int): Int {
        val resolved = doc.resolveSafe(pmPos) ?: return pmPos
        if (resolved.parent.isTextblock) return pmPos
        return if (resolved.nodeAfter?.isTextblock == true) pmPos + 1 else pmPos
    }

    /**
     * 用扁平文本变更替换 [pmFrom, pmTo]。
     * 单个换行由 Enter 路径处理；其余含换行的纯文本（多行粘贴/输入）拆成多个段落，
     * 避免把 "\n" 留在单个 text node 里。
     */
    internal fun replacePlainText(
        tr: Transaction,
        text: String,
        pureInsertion: Boolean,
        pmFrom: Int,
        pmTo: Int,
    ) {
        val from = if (pureInsertion) textInputPosition(pmFrom) else pmFrom
        val to = if (pureInsertion) from else pmTo

        if (!singleParagraphMode && text != "\n" && text.contains('\n')) {
            tr.replaceRange(from, to, plainTextBlockSlice(text, from, to, tr))
        } else {
            tr.insertText(text, from, to)
        }
    }

    /** 把纯文本按行构造成段落 Fragment，并让文本继承插入位置的 marks。 */
    private fun plainTextBlockSlice(
        text: String,
        from: Int,
        to: Int,
        tr: Transaction,
    ): Slice {
        val marks: List<Mark>? = tr.storedMarks ?: run {
            val resolvedFrom = tr.doc.resolveSafe(from) ?: return@run null
            val resolvedTo = tr.doc.resolveSafe(to) ?: return@run null
            if (to == from) resolvedFrom.marks() else resolvedFrom.marksAcross(resolvedTo)
        }

        val paragraphType = schema.nodeType("paragraph")
        val paragraphs = text.split("\n").map { line ->
            if (line.isEmpty()) {
                paragraphType.create()
            } else {
                paragraphType.create(
                    attrs = null,
                    content = listOf(schema.text(line, marks)),
                )
            }
        }

        // 起点/终点在同一 textblock 内时，首尾两行应接续替换位置前后的行内文本；
        // 因此把 slice 两侧的 paragraph 都打开，让 replaceRange 合并周围内容。
        val resolvedFrom = tr.doc.resolveSafe(from)
        val resolvedTo = tr.doc.resolveSafe(to)
        val sameTextblock = resolvedFrom != null &&
            resolvedTo != null &&
            resolvedFrom.parent.isTextblock &&
            resolvedTo.parent.isTextblock &&
            resolvedFrom.start(resolvedFrom.depth) == resolvedTo.start(resolvedTo.depth)

        return Slice(
            Fragment.from(paragraphs),
            if (sameTextblock) 1 else 0,
            if (sameTextblock) 1 else 0,
        )
    }

    // --- 键盘按键事件拦截 ---
    private fun currentMonotonicMs(): Long =
        ProseMirrorStateClockStart.elapsedNow().inWholeMilliseconds

    internal fun notePhysicalKeyEvent() {
        lastPhysicalKeyEventMs = currentMonotonicMs()
    }

    internal fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown) {
            notePhysicalKeyEvent()
        }

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
        val charCollision = triggers.firstOrNull { it.char == trigger.char && it.id != trigger.id }
        require(charCollision == null) {
            "Trigger char '${trigger.char}' is already registered by trigger id='${charCollision?.id}'"
        }
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
        val query = checkNotNull(_activeTriggerQuery) { "No active trigger query to commit" }
        check(query.triggerId == triggerId) {
            "Active query is for '${query.triggerId}', not '$triggerId'"
        }
        val trigger = triggers.firstOrNull { it.id == triggerId }
            ?: throw IllegalArgumentException("Trigger '$triggerId' is not registered")
        require(label.isNotEmpty() && label.first() == trigger.char) {
            "Token label must start with trigger char '${trigger.char}', got '$label'"
        }

        val pmFrom = flatToPm(query.range.min)
        val pmTo = flatToPm(query.range.max)

        // 先清空查询，避免 dispatch 重建 flat 文本时把已提交的 token 再次识别为查询。
        _activeTriggerQuery = null
        suppressedTriggerRange = null

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
    }

    private fun refreshActiveTriggerQuery() {
        if (triggers.isEmpty()) {
            _activeTriggerQuery = null
            suppressedTriggerRange = null
            return
        }

        val selection = textFieldValue.selection
        if (!selection.collapsed) {
            _activeTriggerQuery = null
            return
        }

        // 光标离开被抑制的范围后解除抑制。
        val suppress = suppressedTriggerRange
        if (suppress != null) {
            val caret = selection.min
            if (caret < suppress.min || caret > suppress.max) {
                suppressedTriggerRange = null
            }
        }

        // token 是原子节点，内部不能嵌套新的 trigger query。
        val caret = selection.min
        val nodeBefore = doc.nodeAt(flatToPm((caret - 1).coerceAtLeast(0)))
        if (nodeBefore?.type?.name == "mention" || nodeBefore?.type?.name == "token") {
            _activeTriggerQuery = null
            return
        }

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
        // 与参考版一致：导出的是用户看到的扁平文本（含列表 marker 与块间 "\n"）。
        return textFieldValue.text
    }

    /**
     * 导出指定扁平选区范围的纯文本。
     * 范围提取会保留列表/段落包装，因此选中的列表内容会带上 marker，
     * 与参考版 `extractRangeState` 的语义一致。
     */
    public fun toText(range: TextRange): String {
        val pmFrom = flatToPm(range.min.coerceIn(0, textFieldValue.text.length))
        val pmTo = flatToPm(range.max.coerceIn(0, textFieldValue.text.length))
        return doc.extractRangeFragment(pmFrom, pmTo).toFlatText()
    }

    /**
     * 导出指定扁平选区范围的 HTML。
     * 递归保留部分覆盖段的段落/列表包装，因此跨段、跨列表的选区也能得到
     * 与参考版一致的块级上下文。
     */
    public fun toHtml(range: TextRange): String {
        val pmFrom = flatToPm(range.min.coerceIn(0, textFieldValue.text.length))
        val pmTo = flatToPm(range.max.coerceIn(0, textFieldValue.text.length))
        val fragment = doc.extractRangeFragment(pmFrom, pmTo)
        return DOMSerializer.fromSchema(schema).serializeFragmentToHtml(fragment)
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

    /** 清空 undo/redo 历史，保留当前文档与选区。 */
    public fun clearHistory() {
        val currentDoc = editorState.doc
        val currentSelection = textFieldValue.selection
        replaceWholeDoc(currentDoc)
        applyFlatSelection(currentSelection)
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