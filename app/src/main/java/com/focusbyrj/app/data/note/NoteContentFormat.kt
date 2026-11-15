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
}
