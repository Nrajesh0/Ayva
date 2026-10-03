/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.drill

import com.focusbyrj.app.data.drill.DrillQuestionResult
import org.json.JSONArray

object SolutionsViewHelper {
    /**
     * Parses questions safely from a JSONArray, skipping null or corrupt elements
     * without throwing JSONException or dropping the remaining valid questions.
     */
    fun parseQuestionsSafely(qArr: JSONArray?, defaultAvgSec: Int = 20): List<DrillQuestionResult> {
        if (qArr == null || qArr.length() == 0) return emptyList()
        val list = mutableListOf<DrillQuestionResult>()
        for (i in 0 until qArr.length()) {
            val obj = qArr.optJSONObject(i) ?: continue
            try {
                list.add(DrillQuestionResult.fromJson(obj, defaultAvgSec, i))
            } catch (_: Exception) {}
        }
        return list
    }

    /**
     * Clamps the question selection index within valid bounds.
     */
    fun safeQuestionIndex(index: Int, totalCount: Int): Int {
        if (totalCount <= 0) return 0
        return index.coerceIn(0, totalCount - 1)
    }
}
