---
trigger: manual
---
# prosemirror-compose 开发指南

## 1. 项目定位与架构设计
- 本项目 `prosemirror-compose` 旨在 1:1 继承 `richeditor-compose` 的结构设计、UI 渲染、手势拦截、M3 装饰盒与 `RichSpanStyle` 多态绘制体系。
- 底层数据引擎完全由 `prosemirror-kotlin` 的 `PMEditorState` / `Node` / `Mark` / `Transaction` 替换原版的 `RichParagraph` 段落树。
- 严禁创建任何额外的 `DocumentCompiler` 编译类或全局变量！文本与样式编译直接在 `ProseMirrorState.updateAnnotatedString()` 中通过 `utils/AnnotatedStringExt.kt` 扩展函数完成。

## 2. 编码规范与 1:1 映射准则
- **1:1 包与文件对应**：
    - `model/RichTextState.kt` -> `model/ProseMirrorState.kt` (使用 PMEditorState)
    - `model/RichTextConfig.kt` -> `model/ProseMirrorConfig.kt`
    - `model/RichSpanStyle.kt` -> 1:1 复刻 (包含 Link, Code, Image, Token, Default 子类)
    - `ui/ModifierExt.kt` -> 1:1 复刻 `Modifier.drawRichSpanStyle` (Canvas drawBehind)
    - `utils/AnnotatedStringExt.kt` -> 遍历 PM Node 树构建 AnnotatedString 与坐标映射
- **内存与缓存封装**：图片调整尺寸的缓存必须封装在 `RichSpanStyle.Image.Companion.resolvedDimensionsCache` 内部，严禁在顶层文件声明全局变量。
- **坐标转换桥梁**：在 `utils/` 中使用 `PositionCoordinateMap`，负责 PM 绝对结构坐标（`pmPos`）与 Compose 一维文本索引（`flatPos`）之间的 $O(\log N)$ 二分查找转换。
- **历史撤销**：不要手写撤销栈，直接使用 `prosemirror-history` 插件命令（`undo`/`redo`）。

## 3. 参考代码位置
- ProseMirror 数据/事务/Schema/History: `reference/prosemirror-kotlin-compressed.xml`
- UI/手势/M3/RichSpanStyle/Trigger/剪贴板 1:1 结构参考: `reference/modules-compressed.xml`

