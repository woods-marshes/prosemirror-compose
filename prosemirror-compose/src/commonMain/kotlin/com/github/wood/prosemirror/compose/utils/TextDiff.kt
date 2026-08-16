package com.github.wood.prosemirror.compose.utils

import androidx.compose.ui.text.TextRange

public data class TextDiff(val text: String, val start: Int, val end: Int)

/**
 * 计算 [oldText] → [newText] 的最小字符变更区域。
 *
 * 纯前缀/后缀比对在重复字符（例如 `"aaa"` 中间插入 `"a"`）中存在二义性，
 * 因此当 [oldSelection] / [newSelection] 能完整解释这次变更时优先采用选区，
 * 与 compose-rich-editor 最新版的判定逻辑保持一致。
 */
public fun calculateTextDiff(
    oldText: String,
    newText: String,
    oldSelection: TextRange? = null,
    newSelection: TextRange? = null,
): TextDiff? {
    if (oldText == newText) return null

    var prefix = 0
    val maxPrefix = minOf(oldText.length, newText.length)
    while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) {
        prefix++
    }

    var suffix = 0
    val maxSuffix = maxPrefix - prefix
    while (suffix < maxSuffix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) {
        suffix++
    }

    val removedLength = oldText.length - prefix - suffix
    val insertedLength = newText.length - prefix - suffix

    when {
        // 纯插入：优先用旧选区消除重复字符位置的歧义。
        removedLength == 0 && insertedLength > 0 -> {
            val legacyStart = oldSelection?.min
            val legacyDescribesChange = legacyStart != null &&
                legacyStart in 0..oldText.length &&
                legacyStart + insertedLength <= newText.length &&
                newText.substring(0, legacyStart) == oldText.substring(0, legacyStart) &&
                newText.substring(legacyStart + insertedLength) == oldText.substring(legacyStart)

            val start = if (legacyDescribesChange) legacyStart ?: prefix else prefix
            return TextDiff(
                text = newText.substring(start, start + insertedLength),
                start = start,
                end = start,
            )
        }

        // 纯删除：优先用新选区消除歧义。
        insertedLength == 0 && removedLength > 0 -> {
            val legacyMin = newSelection?.min
            val legacyDescribesChange = legacyMin != null &&
                legacyMin in 0..newText.length &&
                legacyMin + removedLength <= oldText.length &&
                oldText.substring(0, legacyMin) == newText.substring(0, legacyMin) &&
                oldText.substring(legacyMin + removedLength) == newText.substring(legacyMin)

            val start = if (legacyDescribesChange) legacyMin ?: prefix else prefix
            return TextDiff(
                text = "",
                start = start,
                end = start + removedLength,
            )
        }

        // 替换：优先用旧选区（非折叠）作为被替换区间。
        else -> {
            val selMin = oldSelection?.min ?: prefix
            val selMax = oldSelection?.max ?: prefix
            val replacementLength = newText.length - selMin - (oldText.length - selMax)
            val selectionDescribesChange = oldSelection != null &&
                !oldSelection.collapsed &&
                replacementLength >= 0 &&
                selMin + replacementLength <= newText.length &&
                newText.substring(0, selMin) == oldText.substring(0, selMin) &&
                newText.substring(selMin + replacementLength) == oldText.substring(selMax)

            val removeStart: Int
            val removeEnd: Int
            if (selectionDescribesChange) {
                removeStart = selMin
                removeEnd = selMax
            } else {
                removeStart = prefix
                removeEnd = oldText.length - suffix
            }

            return TextDiff(
                text = newText.substring(
                    removeStart,
                    newText.length - (oldText.length - removeEnd),
                ),
                start = removeStart,
                end = removeEnd,
            )
        }
    }
}
