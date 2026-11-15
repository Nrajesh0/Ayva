/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.data.note

/** Shared recent-note cache; vault lock and restore paths must invalidate decrypted entries. */
internal object LatestNotesCache {
    private const val MAX_SIZE = 200

    val entries: MutableMap<Long, NoteEntity> =
        java.util.Collections.synchronizedMap(
            object : java.util.LinkedHashMap<Long, NoteEntity>(MAX_SIZE, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<Long, NoteEntity>?
                ): Boolean = size > MAX_SIZE
            }
        )

    fun clear() = entries.clear()
}
