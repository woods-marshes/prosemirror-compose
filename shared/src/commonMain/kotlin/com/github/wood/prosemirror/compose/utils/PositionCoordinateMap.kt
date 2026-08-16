package com.github.wood.prosemirror.compose.utils

import kotlin.math.abs

/**
 * 扁平文本中的块分隔符（合成的 "\n"）记录。
 * pmBefore = 前一块的结束位置，pmAfter = 下一块的起始位置。
 * 用于把"删除合成换行"映射为"合并相邻块"的结构操作。
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
    internal val blockSeparators: List<BlockSeparator> = emptyList()
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
        // 线性扫描取首个包含该位置的映射。
        // 列表 marker 的零宽 pm 区间与文本区间在 flat 侧重叠，
        // 必须按注册顺序取第一个（marker 先于文本注册，边界先于内容）。
        for (range in flatToPmRanges) {
            if (flatPos >= range.sourceStart && flatPos <= range.sourceEnd) {
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
    val targetEnd: Int
)

public class PositionCoordinateMapBuilder {
    private val pmToFlat = mutableListOf<RangeMapping>()
    private val flatToPm = mutableListOf<RangeMapping>()
    internal val blockSeparators = mutableListOf<BlockSeparator>()

    public fun registerRange(pmStart: Int, pmEnd: Int, flatStart: Int, flatEnd: Int) {
        if (pmStart == pmEnd && flatStart == flatEnd) return
        pmToFlat.add(RangeMapping(pmStart, pmEnd, flatStart, flatEnd))
        flatToPm.add(RangeMapping(flatStart, flatEnd, pmStart, pmEnd))
    }

    public fun registerBoundary(pmPos: Int, flatPos: Int) {
        pmToFlat.add(RangeMapping(pmPos, pmPos, flatPos, flatPos))
        flatToPm.add(RangeMapping(flatPos, flatPos, pmPos, pmPos))
    }

    /** 注册块分隔符（合成的 "\n"）。pmBefore/pmAfter 为相邻两块的边界位置。 */
    public fun registerBlockSeparator(flatIndex: Int, pmBefore: Int, pmAfter: Int) {
        blockSeparators.add(BlockSeparator(flatIndex, pmBefore, pmAfter))
    }

    public fun build(): PositionCoordinateMap {
        return PositionCoordinateMap(
            pmToFlat.sortedBy { it.sourceStart },
            flatToPm.sortedBy { it.sourceStart },
            blockSeparators.sortedBy { it.flatIndex }
        )
    }
}