# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

`prosemirror-compose` is a Kotlin Multiplatform rich-text editor library (with sample apps) built on the Atlassian Kotlin port of ProseMirror (`com.atlassian.prosemirror:*`, v1.1.17 — model, state, transform, history, collab, test-builder). The editor source lives in the `shared` module and is a vendored/adapted version of the Atlassian `prosemirror-compose` (compose-rich-editor) library, re-packaged under `com.github.wood.prosemirror.compose`.

Platforms: Android, Desktop (JVM), iOS (via Xcode app consuming the `Shared` static framework), and Web (JS/Wasm — scaffolding only; see caveats below).

## Commands

All commands run from the repo root with the Gradle wrapper. Note: `settings.gradle.kts` configures Aliyun Maven mirrors (Maven Central is unstable in mainland China — large downloads intermittently fail with TLS handshake errors; retry or rely on the mirrors). `com.atlassian.prosemirror` is excluded from the mirror (not synced) and goes straight to Maven Central.

- Build Android: `./gradlew :androidApp:assembleDebug`
- Run desktop: `./gradlew :desktopApp:run` (hot reload: `./gradlew :desktopApp:hotRun --auto`)
- Run web: `./gradlew :webApp:wasmJsBrowserDevelopmentRun` or `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS: open `iosApp/` in Xcode (consumes the `Shared` framework from `shared`; iOS targets cannot build on Windows hosts)
- Tests:
  - Desktop: `./gradlew :shared:jvmTest` (primary; 15+ tests in `ProseMirrorStateTest`)
  - Android host: `./gradlew :shared:testAndroidHostTest`
  - iOS simulator: `./gradlew :shared:iosSimulatorArm64Test` (macOS only)
- Single test: `./gradlew :shared:jvmTest --tests "com.example.ClassName.methodName"`
- Compile checks: `./gradlew :shared:compileKotlinJvm :shared:compileAndroidMain` (iOS compile check unavailable on Windows)

## Architecture

### The dual document model (core concept)

The editor's source of truth is an immutable ProseMirror document tree — `ProseMirrorState.editorState` (`PMEditorState` holding `Node`/`Mark`/`Schema` from `com.atlassian.prosemirror.model`). Everything Compose sees is a *flattened projection* of that tree:

- `model/ProseMirrorState.kt` — the state hub. Holds `editorState`, the derived flat `annotatedString` (Compose `AnnotatedString` with `RichSpanStyle` spans), the `textFieldValue` bound to `BasicTextField`, `visualTransformation`, `inlineContentMap` (placeholder `InlineTextContent` for atomic nodes like images), plus trigger/token, selection-gesture, and undo/redo (via the history plugin) state.
- `model/RichSpanStyle.kt` — the styling types (link, code, token, header, etc.) used when flattening.
- `utils/PositionCoordinateMap.kt` — maps between ProseMirror structural positions (PM Pos) and flat Compose text indices, in both directions. Every edit and selection sync goes through it.

### The edit pipeline (how edits flow back)

1. User edits the `BasicTextField` → `ProseMirrorState.onTextFieldValueChange()`.
2. `utils/TextDiff.kt` (`calculateTextDiff`) converts the flat text change into a ProseMirror `Transaction` (insertText/delete/replaceRange), mapping indices through `PositionCoordinateMap`.
3. **The selection is NOT set manually in the text-edit branch** — PM steps map the caret automatically. The old code set it via the stale coordinate map, which snapped the caret to 0. Pure selection moves go through the else-branch with a fresh `setSelection`.
4. `dispatch(transaction)` applies it to `editorState` (runs ProseMirror plugins/steps) and calls `updateAnnotatedString()`.
5. `updateAnnotatedString()` rebuilds the flat `AnnotatedString` (via `appendProseMirrorDoc` in `utils/AnnotatedStringExt.kt`), rebuilds the coordinate map, prunes unused `inlineContentMap` keys, and syncs `textFieldValue`.

Inverse path (selection/caret moves, composition): flat → PM mapping, same cycle. IME composition is tracked via the `"composition"` transaction meta.

### Key invariants (learned the hard way — do not break)

- **`ProseMirrorState` init moves the caret to the first textblock content start** (PM's default selection sits at the doc level; typing there inserts a new block instead of into the paragraph). Also: `appendProseMirrorDoc` deliberately does NOT register a `boundary(0,0)` for the doc start — that would make `flatToPm(0)` resolve to the doc-level position 0 instead of the textblock start 1.
- **Synthesized flat characters**: block `"\n"` separators are added only before non-first-child-of-parent blocks that aren't `list_item` (list items are separated by their markers). `PositionCoordinateMap.blockSeparators` records them so deleting a separator merges blocks (`pmBefore..pmAfter`).
- **Coordinate lookups are linear scans, not binary search** — list markers create overlapping flat ranges (zero-width pm range for the marker vs. text ranges), which break binary search. `flatToPm`/`pmToFlat` iterate in registration order and take the first containing range.
- **Every programmatic operation calls `closeHistory(tr)`** — otherwise the PM history plugin merges the formatting step into the preceding typing group and undo rewinds the text too.
- **Collapsed-selection formatting = stored marks** (`tr.addStoredMark`/`removeStoredMark`): PM keeps stored marks only for collapsed `TextSelection`s, which exactly matches the reference's "staged style" semantics. `addStep` clears them — re-apply after `split` in `handleEnter` when `preserveStyleOnEmptyLine`.
- **`setHtml`/`setText`/`clear` rebuild the whole `PMEditorState`** (`replaceWholeDoc` in `TextOps.kt`) to clear undo history — PM has no public history-reset API.
- **`ParagraphStyle.textAlign` is non-null in this Compose version** (use `TextAlign.Unspecified`); `TextRange` has no `coerceIn` (write a clamp helper); `Mark` equality is `==` (there is no `eq()`); `TextSelection`/`PMEditorState`/`EmptyEditorStateConfig` live in `com.atlassian.prosemirror.state`, not `.model`.
- **The `*Impl` spec classes (`NodeSpecImpl` etc.) exist only in the repo's `.ts` reference files, not in the compiled library** — schemas are built with anonymous `object : NodeSpec`/`MarkSpec`/`AttributeSpec` implementations (see `schema/NodeSpecs.kt`, `schema/MarkSpecs.kt`).
- **`nodesBetween(from, to, f = ...)` must use the named `f` parameter** — trailing-lambda syntax binds to the last parameter (`terminate`).

### UI layers

- `ui/BasicProseMirrorText.kt` — read-only rendering (`BasicText`): link/token tap and hover interactions, image loading via `ImageLoader`.
- `ui/BasicProseMirrorEditor.kt` — editable wrapper around `BasicTextField` with the full edit loop, clipboard (`clipboard/` package — `ProseMirrorClipboardManager` + `ClipboardEventEffect` for HTML copy/paste), selection-gesture handling, and keyboard interception (`onPreviewKeyEvent`).
- `ui/material3/` — Material3 design-system layer: `ProseMirrorText` (read-only), `ProseMirrorEditor` (filled), `OutlinedProseMirrorEditor`, `ExpandableProseMirrorText`/`ExpandableBasicProseMirrorText`; `RichTextEditorImpl.kt` (shared `CommonDecorationBox`), `RichTextEditorDefaults.kt`, and custom tokens under `ui/material3/tokens/`.
- `ui/TriggerSuggestions.kt` — dropdown UI for the trigger system.
- `model/trigger/` — mention-style trigger system (`Trigger`, `TriggerQuery`, `TriggerDetector`): registering triggers, detecting an active query at the caret, and inserting token nodes via `ProseMirrorState.insertToken()`.
- `model/TokenInteractionHandlers.kt`, `model/ImageLoader.kt` — `CompositionLocal`s for token hover/click and image loading.
- `platform/Platform.kt` — expect/actual (android/ios/jvm) for platform-specific behavior.

### App entry points

- `androidApp`/`desktopApp`/`webApp`/`iosApp` all call a shared `App()` composable defined in `shared/src/commonMain/.../App.kt` — a Material3 demo with a formatting toolbar (bold/italic/underline/strike/code/link/heading dropdown/undo/redo/list toggles/indent), an `@mention` trigger demo (`TriggerSuggestions` + `insertToken`), a read-only preview, and the exported HTML.
- `webApp/main.kt` wraps `App()` in `WithFontResourcesLoaded` (compose resources fonts).

## Conventions & caveats

- Source code comments are written in **Chinese** — match that style when adding comments.
- The library API uses `@ExperimentalProseMirrorApi` / `@InternalProseMirrorApi` annotations (`annotation/`) for stability markers; public composables often require the experimental opt-in.
- Web caveats: `shared/build.gradle.kts` has its `js`/`wasmJs` targets **commented out**, and `webApp` does **not** depend on `:shared` (also commented out) — web apps currently only run the placeholder UI. Re-enable both to use the editor on web.
- `prosemirror-test-builder` is available for building test documents (`builders(schema)` + string syntax).
- Kotlin 2.4.10, Compose Multiplatform 1.11.1, AGP 9.1.1, minSdk 24, compileSdk 37, Android JVM target 11. `shared` iOS framework is static, baseName `Shared`.
- `ProseMirrorConfig.listIndent` etc. are `Int` (sp convention), matching the reference — not `Dp`.

## Implemented feature surface (as of 2026-07)

Schema (`schema/`): doc/paragraph/heading(level)/bullet_list/ordered_list(order)/list_item/image/token/hard_break/text + marks strong/em/underline/strike/code/link(href, inclusive=false)/textStyle(color,fontSize). Formatting API (`model/Formatting.kt`): span-style toggles with collapsed-selection staging, link/code/heading/paragraph-style ops. Lists (`model/Lists.kt` + `schema/ListCommands.kt`): wrap/lift/split commands ported from prosemirror-schema-list on the transform primitives, Enter/Tab key handling, marker rendering with indent gutters in the flattener, ordered numbers derived at render time. Text ops (`model/TextOps.kt`): replace/remove/insert/setText/copy/clear, HTML insert + paste via `replaceRange(Slice)`. Tokens: `insertToken` creates real `token` atom nodes rendered as styled text. Undo/redo via the PM history plugin (Ctrl/Cmd+Z wired in `onPreviewKeyEvent`).

Known divergences from the reference: `toText()` uses `doc.textBetween` (no list markers, no trailing separators); `updateLink` updates all links in the selection; no join-before optimization when wrapping next to an existing list; generic `SpanStyle` fields beyond color/fontSize are dropped (see `MarkMapper`).
