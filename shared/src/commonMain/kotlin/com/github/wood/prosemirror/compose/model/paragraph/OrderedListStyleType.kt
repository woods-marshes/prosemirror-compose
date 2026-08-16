package com.github.wood.prosemirror.compose.model.paragraph

import com.github.wood.prosemirror.compose.annotation.ExperimentalProseMirrorApi

/**
 * 有序列表 marker 样式接口。
 *
 * 内置实现见 companion object；也可自定义实现。
 *
 * 示例：
 * ```kotlin
 * richTextState.config.orderedListStyleType = OrderedListStyleType.UpperRoman
 *
 * val customStyle = object : OrderedListStyleType {
 *     override fun format(number: Int, listLevel: Int) = "Item $number"
 *     override fun getSuffix(listLevel: Int) = ") "
 * }
 * ```
 */
@ExperimentalProseMirrorApi
public interface OrderedListStyleType {
    /**
     * 将序号格式化为字符串。
     *
     * @param number 序号（1 起）
     * @param listLevel 列表层级（1 起）
     */
    public fun format(number: Int, listLevel: Int): String = number.toString()

    /**
     * 格式化后的后缀。默认 ". "。
     *
     * @param listLevel 列表层级（1 起）
     */
    public fun getSuffix(listLevel: Int): String = ". "

    public object Decimal : OrderedListStyleType {
        override fun format(number: Int, listLevel: Int): String = number.toString()
    }

    public object ArabicIndic : OrderedListStyleType {
        override fun format(number: Int, listLevel: Int): String =
            number
                .toString()
                .map { ch ->
                    when (ch) {
                        '0' -> '٠'
                        '1' -> '١'
                        '2' -> '٢'
                        '3' -> '٣'
                        '4' -> '٤'
                        '5' -> '٥'
                        '6' -> '٦'
                        '7' -> '٧'
                        '8' -> '٨'
                        '9' -> '٩'
                        else -> ch
                    }
                }
                .joinToString("")
    }

    public object Arabic : OrderedListStyleType {
        internal val arabicLetters = charArrayOf(
            'أ', 'ب', 'ج', 'د', 'ه', 'و', 'ز', 'ح', 'ط', 'ي', 'ك', 'ل', 'م',
            'ن', 'س', 'ع', 'ف', 'ص', 'ق', 'ر', 'ش', 'ت', 'ث', 'خ', 'ذ', 'ض', 'ظ', 'غ'
        )

        override fun format(number: Int, listLevel: Int): String =
            formatToArabic(number = number)
    }

    public object LowerAlpha : OrderedListStyleType {
        override fun format(number: Int, listLevel: Int): String =
            formatToAlpha(number = number, base = 'a')
    }

    public object UpperAlpha : OrderedListStyleType {
        override fun format(number: Int, listLevel: Int): String =
            formatToAlpha(number = number, base = 'A')
    }

    public object LowerRoman : OrderedListStyleType {
        private val romanNumerals = arrayOf(
            "m" to 1000, "cm" to 900, "d" to 500, "cd" to 400, "c" to 100,
            "xc" to 90, "l" to 50, "xl" to 40, "x" to 10, "ix" to 9,
            "v" to 5, "iv" to 4, "i" to 1
        )

        override fun format(number: Int, listLevel: Int): String =
            formatToRomanNumber(
                number = number,
                romanNumerals = romanNumerals,
                defaultValue = "i"
            )
    }

    public object UpperRoman : OrderedListStyleType {
        private val romanNumerals = arrayOf(
            "M" to 1000, "CM" to 900, "D" to 500, "CD" to 400, "C" to 100,
            "XC" to 90, "L" to 50, "XL" to 40, "X" to 10, "IX" to 9,
            "V" to 5, "IV" to 4, "I" to 1
        )

        override fun format(number: Int, listLevel: Int): String =
            formatToRomanNumber(
                number = number,
                romanNumerals = romanNumerals,
                defaultValue = "I"
            )
    }

    /**
     * 按层级选择样式：层级 N 使用第 N 个样式，超出时钳制到最后一个。
     */
    public class Multiple(
        public vararg val styles: OrderedListStyleType,
    ) : OrderedListStyleType {
        override fun format(number: Int, listLevel: Int): String {
            if (styles.isEmpty()) return Decimal.format(number, listLevel)
            val style = styles[(listLevel - 1).coerceIn(styles.indices)]
            return style.format(number, listLevel)
        }

        override fun getSuffix(listLevel: Int): String {
            if (styles.isEmpty()) return Decimal.getSuffix(listLevel)
            val style = styles[(listLevel - 1).coerceIn(styles.indices)]
            return style.getSuffix(listLevel)
        }
    }

    private companion object {
        private fun formatToArabic(number: Int): String {
            if (number <= 0) return Arabic.arabicLetters.first().toString()
            val result = StringBuilder()
            var n = number
            while (n > 0) {
                val remainder = (n - 1) % 28
                result.insert(0, Arabic.arabicLetters[remainder])
                n = (n - 1) / 28
            }
            return result.toString()
        }

        private fun formatToAlpha(number: Int, base: Char): String {
            if (number <= 0) return base.toString()
            val result = StringBuilder()
            var n = number
            while (n > 0) {
                val remainder = (n - 1) % 26
                result.insert(0, base + remainder)
                n = (n - 1) / 26
            }
            return result.toString()
        }

        private fun formatToRomanNumber(
            number: Int,
            romanNumerals: Array<Pair<String, Int>>,
            defaultValue: String,
        ): String {
            if (number <= 0) return defaultValue
            val result = StringBuilder()
            var n = number
            for ((symbol, value) in romanNumerals) {
                while (n >= value) {
                    result.append(symbol)
                    n -= value
                }
            }
            return result.toString()
        }
    }
}
