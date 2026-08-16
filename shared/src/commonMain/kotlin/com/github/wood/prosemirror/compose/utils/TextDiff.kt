package com.github.wood.prosemirror.compose.utils

public data class TextDiff(val text: String, val start: Int, val end: Int)

/**
 * 经典双端比对对齐法。不论是由于删除、粘贴、连续按键还是系统自动纠错(Autocorrect)，
 * 均能准确识别最小字符变动差异，彻底消除偏移定位不准产生的假字符问题。
 */
public fun calculateTextDiff(oldText: String, newText: String): TextDiff? {
    if (oldText == newText) return null

    var prefix = 0
    val maxPrefix = minOf(oldText.length, newText.length)
    while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) {
        prefix++
    }

    var suffix = 0
    val maxSuffix = maxPrefix - prefix
    while (suffix < maxSuffix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) {
        suffix++
    }

    val removedLength = oldText.length - prefix - suffix
    val insertedText = newText.substring(prefix, newText.length - suffix)

    return TextDiff(
        text = insertedText,
        start = prefix,
        end = prefix + removedLength
    )
}