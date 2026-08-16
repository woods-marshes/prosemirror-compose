package com.github.wood.prosemirror.compose

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.wood.prosemirror.compose.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "prosemirror-compose",
    ) {
        App()
    }
}