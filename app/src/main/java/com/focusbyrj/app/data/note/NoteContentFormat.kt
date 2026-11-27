/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.note

/** Persisted block delimiters shared by the editor and strict media inventory. */
internal object NoteContentFormat {
    const val BLOCKS_PREFIX = "<!--NOTESNOOK_BLOCKS:"
    const val BLOCKS_SUFFIX = ":BLOCKS_END-->"
    const val ALT_BLOCKS_START = "<!--BLOCKS_START-->"
    const val ALT_BLOCKS_END = "<!--BLOCKS_END-->"

    /** A marker inside a quoted block value is ordinary note text. */
    fun endOfPayload(content: String, from: Int, suffix: String = BLOCKS_SUFFIX): Int {
        var quoted = false
        var escaped = false
        for (index in from until content.length) {
            val char = content[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else if (char == '"') {
                quoted = true
            } else if (content.startsWith(suffix, index) ||
                suffix == BLOCKS_SUFFIX && content.startsWith("-->", index)) {
                return index
            }
        }
        return -1
    }
}
