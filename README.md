# ProseMirror Compose

[![CI](https://github.com/woods-marshes/prosemirror-compose/actions/workflows/ci.yml/badge.svg)](https://github.com/woods-marshes/prosemirror-compose/actions/workflows/ci.yml)

A Kotlin Multiplatform rich-text editor library for [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform), built on [ProseMirror](https://prosemirror.net) through its Kotlin port (`com.atlassian.prosemirror`).

The ProseMirror document model is the single source of truth — Compose renders a flattened projection of the immutable tree, and every edit flows back through a diff → transaction pipeline.

**Platforms:** Android · Desktop (JVM) · iOS · Web (scaffolding only)

## Features

- **ProseMirror document model** — immutable `Node` tree as source of truth, with `AnnotatedString` + span styles as the flat rendering projection
- **Formatting** — bold, italic, underline, strikethrough, inline code, links, headings, text color/size; collapsed-selection staging (type, then format)
- **Lists** — bullet and ordered lists with wrap / lift / split, Enter/Tab handling, indent gutters, ordered numbers derived at render time
- **Undo / redo** via the ProseMirror history plugin
- **HTML clipboard** — copy and paste rich HTML through the ProseMirror `Slice` pipeline
- **Markdown import / export** — `setMarkdown`, `insertMarkdown`, `toMarkdown` (JetBrains markdown/GFM parser → HTML → ProseMirror DOM parser)
- **Trigger system** — `@mention`-style triggers with token nodes inserted into the document
- **Inline images** as atomic nodes, loaded via a pluggable `ImageLoader`
- **Material 3 components** — `ProseMirrorEditor` (filled), `OutlinedProseMirrorEditor`, read-only `ProseMirrorText`, expandable variants
- **Stability markers** — `@ExperimentalProseMirrorApi` / `@InternalProseMirrorApi` on the public API surface

## Platform support

| Platform | Status |
| --- | --- |
| Android (minSdk 24) | ✅ |
| Desktop (JVM) | ✅ |
| iOS (static framework `ComposeApp`) | ✅ |
| Web (JS/Wasm) | 🚧 scaffolding only — targets are commented out in the core module; the web apps currently run a placeholder UI |

## Modules

Following the recommended Kotlin Multiplatform structure:

| Module | Role |
| --- | --- |
| `prosemirror-compose` | Editor core library |
| `prosemirror-compose-coil3` | Optional Coil3 `ImageLoader` integration |
| `composeApp` | Shared Compose UI and demo `App()` consumed by the platform entry points |
| `androidApp` / `desktopApp` / `webApp` / `iosApp` | Platform entry points |

```text
androidApp ─┐
desktopApp ─┼──▶ composeApp ──▶ prosemirror-compose
iosApp ─────┘
webApp ──────┘   (once web targets are enabled)

prosemirror-compose-coil3 ──▶ prosemirror-compose
```

## Architecture in brief

The editor keeps an immutable ProseMirror document tree as its state of truth. Compose never renders the tree directly:

- `ProseMirrorState` holds the `PMEditorState`, the derived flat `AnnotatedString`, and the `BasicTextField` state.
- `PositionCoordinateMap` maps between ProseMirror structural positions and flat Compose text indices, in both directions.
- Typing goes `TextField` → diff → ProseMirror `Transaction` → plugins/steps → rebuilt `AnnotatedString`; selection moves and IME composition take the inverse path.

See `CLAUDE.md` for the full detail on the edit pipeline and its invariants.

## Sample apps

`androidApp`, `desktopApp`, `iosApp` and `webApp` all run the same demo `App()` composable from `composeApp`, ported from the upstream `compose-rich-editor` sample: Home, rich editor, HTML editor, Markdown editor, Slack, Mentions, Undo/Redo, Lists config, real examples, Links, Images, GitHub, Notion, Headings, Claude, and Expandable text.

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
./gradlew :prosemirror-compose:jvmTest               # primary suite (Desktop/JVM)
./gradlew :prosemirror-compose:testAndroidHostTest   # Android host
./gradlew :prosemirror-compose:iosSimulatorArm64Test # iOS simulator (macOS only)
```

## Usage

The library modules are published to Maven Central under the `io.github.woods-marshes` group:

```kotlin
// In a Kotlin Multiplatform commonMain source set
dependencies {
    implementation("io.github.woods-marshes:prosemirror-compose:0.1.0")
    implementation("io.github.woods-marshes:prosemirror-compose-coil3:0.1.0") // optional Coil3 images
}
```

Gradle module metadata is published for the `kotlinMultiplatform` artifact, so Android / JVM / iOS variants are resolved automatically from the same coordinates.

## Publishing

Publishing is driven by GitHub Actions through the [Maven Central Portal](https://central.sonatype.com/):

- Push to `main` → publishes `0.1.0-SNAPSHOT` to the Central Portal snapshot repository.
- Push a `v*` tag (for example `v0.1.0`) → uploads `0.1.0` to the Central Portal and automatically publishes it after validation.

Required GitHub Actions secrets:

| Secret | Purpose |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` | Central Portal user token (generate it on central.sonatype.com after registering and claiming the `io.github.woods-marshes` namespace) |
| `SIGNING_KEY_ID` | Last 8 characters of the GPG public key ID |
| `SIGNING_KEY` | ASCII-armored GPG private key |
| `SIGNING_PASSWORD` | GPG private key passphrase |

Local publishing for smoke-testing:

```bash
./gradlew :prosemirror-compose:publishToMavenLocal :prosemirror-compose-coil3:publishToMavenLocal
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).

This project is adapted from [`compose-rich-editor`](https://github.com/MohamedRejeb/compose-rich-editor) (Apache-2.0, © Mohamed Rejeb) and builds on the Kotlin port of ProseMirror, [`prosemirror-kotlin`](https://github.com/atlassian-labs/prosemirror-kotlin) / `com.atlassian.prosemirror:*` (v1.1.20, Apache-2.0, © Atlassian Pty Ltd). The editor is re-packaged under `com.github.wood.prosemirror.compose`.
