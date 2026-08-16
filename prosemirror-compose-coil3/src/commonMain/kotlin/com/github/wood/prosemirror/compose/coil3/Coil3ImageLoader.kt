package com.github.wood.prosemirror.compose.coil3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi
import com.github.wood.prosemirror.compose.model.ImageData
import com.github.wood.prosemirror.compose.model.ImageLoader

/**
 * Coil3 版本的 [ImageLoader]，对应 compose-rich-editor 的 richeditor-compose-coil3 模块。
 */
@ExperimentalProseMirrorApi
public object Coil3ImageLoader : ImageLoader {

    @Composable
    override fun load(model: Any): ImageData? {
        val painter = rememberAsyncImagePainter(model = model)

        var imageData by remember {
            mutableStateOf<ImageData?>(null)
        }

        LaunchedEffect(painter.state) {
            painter.state.collect { state ->
                imageData =
                    if (state is AsyncImagePainter.State.Success) {
                        ImageData(painter = state.painter)
                    } else {
                        null
                    }
            }
        }

        return imageData
    }
}
