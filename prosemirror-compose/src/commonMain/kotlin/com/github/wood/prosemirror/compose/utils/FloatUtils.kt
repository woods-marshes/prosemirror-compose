package com.github.wood.prosemirror.compose.utils

import kotlin.math.pow
import kotlin.math.roundToInt

public fun Float.maxDecimals(decimals: Int): Float {
    val multiplier = 10.0.pow(decimals.toDouble()).toFloat()
    return (this * multiplier).roundToInt() / multiplier
}