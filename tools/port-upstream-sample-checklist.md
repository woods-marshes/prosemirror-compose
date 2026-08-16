# Upstream sample port checklist

Run `bash tools/port-upstream-sample.sh` from the repo root first. The script
copies all upstream `sample/common` Kotlin files and binary compose resources,
then applies mechanical package/API renames.

After the script finishes, fix the following by hand:

## 1. Generated Compose resources

- Upstream fonts live in `composeApp/src/commonMain/composeResources/font/`.
- The generated resources package may not be
  `com.github.wood.prosemirror.compose.app.generated.resources`; verify the
  generated `Res` package after the first Gradle sync and update
  `ui.theme/Typography.kt`.

## 2. API differences that cannot be fixed by sed

- `state.setMarkdown`, `toMarkdown`, `insertMarkdown` now exist in
  `com.github.wood.prosemirror.compose.model` (extensions, import them).
- `RichTextEditorDefaults` is still named `RichTextEditorDefaults` in the
  target project, but the composables are `ProseMirrorEditor` /
  `OutlinedProseMirrorEditor`.
- `RichTextState.copy()`/seeding code in Slack/GitHub/Claude demos should use
  `rememberProseMirrorState(initialHtml = ...)` or
  `rememberProseMirrorState(initialMarkdown...)` patterns; target
  `rememberProseMirrorState` currently supports `initialDoc` and `initialHtml`
  only, so Markdown seeds should call `setMarkdown`.
- `SpellCheckSpan.kt` imports target `getBoundingBoxes`; verify the target
  utility has the same extension visibility (`internal` in the library is not
  visible to `composeApp` — expose it or inline the sample helper).
- `InlineContentPlaceholder` is internal in the target library; sample image
  cards that embed a placeholder should use their own constant.
- `LocalImageLoader` / `Coil3ImageLoader` now come from
  `com.github.wood.prosemirror.compose.model` /
  `com.github.wood.prosemirror.compose.coil3`.

## 3. Dependencies already prepared

`gradle/libs.versions.toml` and `composeApp/build.gradle.kts` now include:

- compose material + material icons extended
- navigation compose
- coil compose / svg / network-ktor3
- ktor client core + okhttp/darwin
- lifecycle viewmodel compose

Run a Gradle sync after the port; remove any dependency that turns out unused.

## 4. Compile order

```bash
./gradlew :composeApp:compileKotlinJvm
./gradlew :composeApp:compileAndroidMain
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:run
```

Fix errors screen by screen. The expected screens are:

home, rich editor, HTML editor, Markdown editor, Slack, Mentions, Undo/Redo,
Lists config, Real examples, Links, Images, GitHub, Notion, Headings, Claude,
Expandable text.
