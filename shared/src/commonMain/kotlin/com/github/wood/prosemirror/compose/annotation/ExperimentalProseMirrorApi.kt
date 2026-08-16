package com.github.wood.prosemirror.compose.annotation

@RequiresOptIn(
    "This ProseMirror Compose API is experimental and is likely to change or to be removed in" +
            " the future.",
    level = RequiresOptIn.Level.WARNING
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY
)
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalProseMirrorApi