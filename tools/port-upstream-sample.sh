#!/usr/bin/env bash
set -euo pipefail

# Port compose-rich-editor's sample/common demo into composeApp.
# Run from the prosemirror-compose repository root:
#   bash tools/port-upstream-sample.sh
#
# The script copies Kotlin sources and binary compose resources, then applies
# package/API renames. After running it, review the remaining manual fixes in
# tools/port-upstream-sample-checklist.md and compile with:
#   ./gradlew :composeApp:compileKotlinJvm :composeApp:compileAndroidMain

UPSTREAM="$(pwd)/../compose-rich-editor/sample/common/src/commonMain"
TARGET="$(pwd)/composeApp/src/commonMain"

if [ ! -d "$UPSTREAM" ]; then
  echo "Upstream sample not found: $UPSTREAM" >&2
  exit 1
fi

mkdir -p "$TARGET"

# 1. Copy sources and binary resources.
cp -R "$UPSTREAM/kotlin" "$TARGET/kotlin"
if [ -d "$UPSTREAM/composeResources" ]; then
  cp -R "$UPSTREAM/composeResources" "$TARGET/composeResources"
fi

# 2. Rename source directories: sample/common -> compose app package.
OLD_PKG_DIR="com/mohamedrejeb/richeditor/sample/common"
NEW_PKG_DIR="com/github/wood/prosemirror/compose/app"
find "$TARGET/kotlin" -type d -path "*/$OLD_PKG_DIR" -print0 |
  while IFS= read -r -d '' dir; do
    mkdir -p "${dir%/$OLD_PKG_DIR}/$NEW_PKG_DIR"
    cp -R "$dir/." "${dir%/$OLD_PKG_DIR}/$NEW_PKG_DIR/"
    rm -rf "$dir"
  done

# 3. Mechanical package and API renames.
sed_replace() {
  local from="$1"
  local to="$2"
  find "$TARGET/kotlin" -name '*.kt' -print0 |
    xargs -0 sed -i "s|$from|$to|g"
}

sed_replace 'package com.mohamedrejeb.richeditor.sample.common' 'package com.github.wood.prosemirror.compose.app'
sed_replace 'com.mohamedrejeb.richeditor.sample.common' 'com.github.wood.prosemirror.compose.app'

sed_replace 'com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi' 'com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi'
sed_replace 'com.mohamedrejeb.richeditor.annotation.InternalRichTextApi' 'com.github.wood.prosemirror.compose.annotation.InternalProseMirrorApi'

sed_replace 'com.mohamedrejeb.richeditor.model.RichTextState' 'com.github.wood.prosemirror.compose.model.ProseMirrorState'
sed_replace 'RichTextState' 'ProseMirrorState'
sed_replace 'rememberRichTextState' 'rememberProseMirrorState'

sed_replace 'com.mohamedrejeb.richeditor.model.trigger' 'com.github.wood.prosemirror.compose.model.trigger'
sed_replace 'com.mohamedrejeb.richeditor.model' 'com.github.wood.prosemirror.compose.model'

sed_replace 'com.mohamedrejeb.richeditor.paragraph.type' 'com.github.wood.prosemirror.compose.model.paragraph'
sed_replace 'com.mohamedrejeb.richeditor.paragraph' 'com.github.wood.prosemirror.compose.model.paragraph'

sed_replace 'com.mohamedrejeb.richeditor.ui.material3.RichTextEditor' 'com.github.wood.prosemirror.compose.ui.material3.ProseMirrorEditor'
sed_replace 'com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor' 'com.github.wood.prosemirror.compose.ui.material3.OutlinedProseMirrorEditor'
sed_replace 'com.mohamedrejeb.richeditor.ui.material3.RichText' 'com.github.wood.prosemirror.compose.ui.material3.ProseMirrorText'
sed_replace 'com.mohamedrejeb.richeditor.ui.material3.ExpandableRichText' 'com.github.wood.prosemirror.compose.ui.material3.ExpandableProseMirrorText'
sed_replace 'com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults' 'com.github.wood.prosemirror.compose.ui.material3.RichTextEditorDefaults'
sed_replace 'com.mohamedrejeb.richeditor.ui.material3.TriggerSuggestions' 'com.github.wood.prosemirror.compose.ui.material3.TriggerSuggestions'
sed_replace 'com.mohamedrejeb.richeditor.ui.material3' 'com.github.wood.prosemirror.compose.ui.material3'

sed_replace 'com.mohamedrejeb.richeditor.ui.BasicRichTextEditor' 'com.github.wood.prosemirror.compose.ui.BasicProseMirrorEditor'
sed_replace 'com.mohamedrejeb.richeditor.ui.BasicRichText' 'com.github.wood.prosemirror.compose.ui.BasicProseMirrorText'
sed_replace 'com.mohamedrejeb.richeditor.ui.ExpandableBasicRichText' 'com.github.wood.prosemirror.compose.ui.ExpandableBasicProseMirrorText'
sed_replace 'com.mohamedrejeb.richeditor.ui' 'com.github.wood.prosemirror.compose.ui'

sed_replace 'com.mohamedrejeb.richeditor.coil3.Coil3ImageLoader' 'com.github.wood.prosemirror.compose.coil3.Coil3ImageLoader'
sed_replace 'com.mohamedrejeb.richeditor.utils.getBoundingBoxes' 'com.github.wood.prosemirror.compose.utils.getBoundingBoxes'
sed_replace 'com.mohamedrejeb.richeditor.utils' 'com.github.wood.prosemirror.compose.utils'

sed_replace 'com.mohamedrejeb.richeditor.common.generated.resources' 'com.github.wood.prosemirror.compose.app.generated.resources'

# 4. Composite names that survived package-level replacement.
sed_replace 'OutlinedRichTextEditor' 'OutlinedProseMirrorEditor'
sed_replace 'BasicRichTextEditor' 'BasicProseMirrorEditor'
sed_replace 'ExpandableBasicRichText' 'ExpandableBasicProseMirrorText'
sed_replace 'ExpandableRichText' 'ExpandableProseMirrorText'
sed_replace 'RichTextEditor' 'ProseMirrorEditor'
sed_replace 'BasicRichText' 'BasicProseMirrorText'
sed_replace 'RichText' 'ProseMirrorText'

echo "Sample sources copied and mechanically renamed."
echo "Review tools/port-upstream-sample-checklist.md, then compile."
