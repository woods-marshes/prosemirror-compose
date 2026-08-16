package com.github.wood.prosemirror.compose.app

import com.github.wood.prosemirror.compose.model.*

import androidx.compose.runtime.*
import com.github.wood.prosemirror.compose.app.navigation.NavGraph
import com.github.wood.prosemirror.compose.app.ui.theme.ComposeRichEditorTheme

@Composable
fun App() {
    ComposeRichEditorTheme {
        NavGraph()
    }
}