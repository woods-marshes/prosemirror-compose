package com.github.wood.prosemirror.compose.app

import com.github.wood.prosemirror.compose.model.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.*

@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            App()
        }
    }
}
