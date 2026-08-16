package com.github.wood.prosemirror.compose.schema

import com.atlassian.prosemirror.model.Schema
import com.atlassian.prosemirror.model.SchemaSpec

/**
 * 构建默认 [Schema]。
 * Schema 不可变，构造后由所有 [com.github.wood.prosemirror.compose.model.ProseMirrorState] 实例共享。
 */
internal fun createDefaultSchema(): Schema =
    Schema(SchemaSpec(nodes = DefaultNodeSpecs, marks = DefaultMarkSpecs))

/**
 * 默认 schema 单例。节点：doc/paragraph/heading/bullet_list/ordered_list/list_item/image/token/hard_break/text；
 * marks：strong/em/underline/strike/code/link/textStyle。
 */
public val DefaultProseMirrorSchema: Schema by lazy { createDefaultSchema() }
