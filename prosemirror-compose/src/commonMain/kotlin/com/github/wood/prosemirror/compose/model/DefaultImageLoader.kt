package com.github.wood.prosemirror.compose.model

import androidx.compose.runtime.Composable
import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi

@ExperimentalProseMirrorApi
public object DefaultImageLoader: ImageLoader {

    @Composable
    override fun load(model: Any): ImageData? = null

}