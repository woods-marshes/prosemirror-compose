package com.github.wood.prosemirror.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        WithFontResourcesLoaded {
            App()
        }
    }
}