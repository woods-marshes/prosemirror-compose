# ProseMirror Compose

A Kotlin Multiplatform rich-text editor library for [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform), built on [ProseMirror](https://prosemirror.net) through its Kotlin port (`com.atlassian.prosemirror`).

The ProseMirror document model is the single source of truth — Compose renders a flattened projection of the immutable tree, and every edit flows back through a diff → transaction pipeline.

**Platforms:** Android · Desktop (JVM) · iOS · Web (scaffolding only)

## Features

- **ProseMirror document model** — immutable `Node` tree as source of truth, with `AnnotatedString` + span styles as the flat rendering projection
- **Formatting** — bold, italic, underline, strikethrough, inline code, links, headings, text color/size; collapsed-selection staging (type, then format)
- **Lists** — bullet and ordered lists with wrap / lift / split, Enter/Tab handling, indent gutters, ordered numbers derived at render time
- **Undo / redo** via the ProseMirror history plugin
- **HTML clipboard** — copy and paste rich HTML through the ProseMirror `Slice` pipeline
- **Trigger system** — `@mention`-style triggers with token nodes inserted into the document
- **Inline images** as atomic nodes, loaded via a pluggable `ImageLoader`
- **Material 3 components** — `ProseMirrorEditor` (filled), `OutlinedProseMirrorEditor`, read-only `ProseMirrorText`, expandable variants
- **Stability markers** — `@ExperimentalProseMirrorApi` / `@InternalProseMirrorApi` on the public API surface

## Platform support

| Platform | Status |
| --- | --- |
| Android (minSdk 24) | ✅ |
| Desktop (JVM) | ✅ |
| iOS (static framework `Shared`) | ✅ |
| Web (JS/Wasm) | 🚧 scaffolding only — targets are commented out in `shared/build.gradle.kts`; the web apps currently run a placeholder UI |

## Architecture in brief

The editor keeps an immutable ProseMirror document tree as its state of truth. Compose never renders the tree directly:

- `ProseMirrorState` holds the `PMEditorState`, the derived flat `AnnotatedString`, and the `BasicTextField` state.
- `PositionCoordinateMap` maps between ProseMirror structural positions and flat Compose text indices, in both directions.
- Typing goes `TextField` → diff → ProseMirror `Transaction` → plugins/steps → rebuilt `AnnotatedString`; selection moves and IME composition take the inverse path.

See `CLAUDE.md` for the full detail on the edit pipeline and its invariants.

## Sample apps

`androidApp`, `desktopApp`, `iosApp` and `webApp` all run the same demo `App()` composable: a formatting toolbar (bold/italic/underline/strike/code/link/heading/undo/redo/lists/indent), an `@mention` trigger demo, a read-only preview, and the exported HTML.

## Building & testing

Requirements: JDK 17+, Android SDK (Android targets only).

```bash
# Android debug APK
./gradlew :androidApp:assembleDebug

# Desktop app
./gradlew :desktopApp:run             # standard run
./gradlew :desktopApp:hotRun --auto   # hot reload

# Web (placeholder UI only)
./gradlew :webApp:wasmJsBrowserDevelopmentRun
./gradlew :webApp:jsBrowserDevelopmentRun

# iOS — open iosApp/ in Xcode and run from there (iOS targets build on macOS only)
```

Tests:

```bash
./gradlew :shared:jvmTest               # primary suite (Desktop/JVM)
./gradlew :shared:testAndroidHostTest   # Android host
./gradlew :shared:iosSimulatorArm64Test # iOS simulator (macOS only)
```

> Note: `settings.gradle.kts` routes Maven dependencies through Aliyun mirrors (Maven Central is unreliable from mainland China). `com.atlassian.prosemirror` is excluded from the mirrors and fetched directly from Maven Central.

## Usage

The library is not yet published to a Maven repository. To use it, include the `shared` module in your Gradle build (as the sample apps do) and consume the `com.github.wood.prosemirror.compose` package. Maven publication is planned.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

This project is adapted from [`compose-rich-editor`](https://github.com/MohamedRejeb/compose-rich-editor) (Apache-2.0, © Mohamed Rejeb) and builds on the Kotlin port of ProseMirror, [`prosemirror-kotlin`](https://github.com/atlassian/prosemirror-kotlin) / `com.atlassian.prosemirror:*` (v1.1.17, Apache-2.0, © Atlassian Pty Ltd). The editor is re-packaged under `com.github.wood.prosemirror.compose`.
