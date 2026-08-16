package com.github.wood.prosemirror.compose.utils

import androidx.compose.ui.text.TextRange
import kotlin.math.abs

/**
 * 扁平文本中的块分隔符（合成的 "\n"）记录。
 * pmBefore = 前一块的结束位置，pmAfter = 下一块的起始位置。
 * 用于选区吸附与段落起点判定；删除分隔符时直接使用两侧映射出的 PM join 区间。
 */
public data class BlockSeparator(
    public val flatIndex: Int,
    public val pmBefore: Int,
    public val pmAfter: Int,
)

/**
 * 记录 ProseMirror 绝对结构位置（PM Pos）与 Compose 扁平文本索引（Flat Index）之间的映射关系。
 */
public class PositionCoordinateMap(
    private val pmToFlatRanges: List<RangeMapping>,
    private val flatToPmRanges: List<RangeMapping>,
    internal val blockSeparators: List<BlockSeparator> = emptyList(),
    internal val flatListMarkerRanges: List<TextRange> = emptyList(),
) {
    public fun pmToFlat(pmPos: Int): Int {
        // 线性扫描取首个包含该位置的映射（注册顺序 = 构建顺序）。
        // 区间在 PM 侧一般不重叠，但零宽 boundary 与内容区间相邻，
        // 二分查找对相邻/重叠区间不可靠。
        for (range in pmToFlatRanges) {
            if (pmPos >= range.sourceStart && pmPos <= range.sourceEnd) {
                return range.targetStart + (pmPos - range.sourceStart)
            }
        }
        val nearest = pmToFlatRanges.minByOrNull { abs(it.sourceStart - pmPos) }
        return nearest?.targetStart ?: 0
    }

    public fun flatToPm(flatPos: Int): Int {
        // 先查“多个扁平字符 → 单个 PM 位置”的常量映射（列表 marker / token）。
        // 它们必须优先于同起点的零宽 boundary，否则列表容器自身的 boundary
        // 会抢先把 flat 0 映射到 list 内容起点。
        for (range in flatToPmRanges) {
            if (range.mapsFlatRangeToSinglePmPosition &&
                flatPos >= range.sourceStart && flatPos < range.sourceEnd
            ) {
                return range.targetStart
            }
        }

        // 再查普通等长映射（含零宽 boundary）。
        for (range in flatToPmRanges) {
            if (!range.mapsFlatRangeToSinglePmPosition &&
                flatPos >= range.sourceStart && flatPos <= range.sourceEnd
            ) {
                return range.targetStart + (flatPos - range.sourceStart)
            }
        }
        val nearest = flatToPmRanges.minByOrNull { abs(it.sourceStart - flatPos) }
        return nearest?.targetStart ?: 0
    }

    public companion object {
        public val EMPTY: PositionCoordinateMap = PositionCoordinateMap(emptyList(), emptyList())
    }
}

public data class RangeMapping(
    val sourceStart: Int,
    val sourceEnd: Int,
    val targetStart: Int,
    val targetEnd: Int,
    val mapsFlatRangeToSinglePmPosition: Boolean = false
)

public class PositionCoordinateMapBuilder {
    private val pmToFlat = mutableListOf<RangeMapping>()
    private val flatToPm = mutableListOf<RangeMapping>()
    internal val blockSeparators = mutableListOf<BlockSeparator>()
    internal val flatListMarkerRanges = mutableListOf<TextRange>()

    public fun registerRange(pmStart: Int, pmEnd: Int, flatStart: Int, flatEnd: Int) {
        if (pmStart == pmEnd && flatStart == flatEnd) return
        val mapsFlatRangeToSinglePmPosition = pmStart == pmEnd && flatStart != flatEnd
        pmToFlat.add(
            RangeMapping(
                sourceStart = pmStart,
                sourceEnd = pmEnd,
                targetStart = flatStart,
                targetEnd = flatEnd,
                mapsFlatRangeToSinglePmPosition = mapsFlatRangeToSinglePmPosition,
            )
        )
        flatToPm.add(
            RangeMapping(
                sourceStart = flatStart,
                sourceEnd = flatEnd,
                targetStart = pmStart,
                targetEnd = pmEnd,
                mapsFlatRangeToSinglePmPosition = mapsFlatRangeToSinglePmPosition,
            )
        )
    }

    public fun registerBoundary(pmPos: Int, flatPos: Int) {
        pmToFlat.add(RangeMapping(pmPos, pmPos, flatPos, flatPos))
        flatToPm.add(RangeMapping(flatPos, flatPos, pmPos, pmPos))
    }

    /** 注册块分隔符（合成的 "\n"）。pmBefore/pmAfter 为相邻两块的边界位置。 */
    public fun registerBlockSeparator(flatIndex: Int, pmBefore: Int, pmAfter: Int) {
        blockSeparators.add(BlockSeparator(flatIndex, pmBefore, pmAfter))
    }

    /** 记录列表 marker 的扁平区间，供“整段删除 marker = 退出列表”等结构操作识别。 */
    public fun registerListMarkerRange(start: Int, end: Int) {
        if (start != end) flatListMarkerRanges.add(TextRange(start, end))
    }

    public fun build(): PositionCoordinateMap {
        return PositionCoordinateMap(
            pmToFlat.sortedBy { it.sourceStart },
            flatToPm.sortedBy { it.sourceStart },
            blockSeparators.sortedBy { it.flatIndex },
            flatListMarkerRanges.sortedBy { it.min },
        )
    }
}