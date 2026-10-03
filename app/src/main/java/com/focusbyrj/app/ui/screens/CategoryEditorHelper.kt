/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens

object CategoryEditorHelper {
    /**
     * Filters installed apps by category and search query, deduplicating by packageName
     * to prevent LazyColumn duplicate key collisions.
     */
    fun filterAndDeduplicateApps(
        installedApps: List<InstalledApp>,
        category: AppCategory,
        searchQuery: String
    ): List<InstalledApp> {
        var list = if (category == AppCategory.ALL) {
            installedApps.filter { it.packageName.isNotBlank() }
        } else {
            installedApps.filter { it.packageName.isNotBlank() && it.category == category }
        }
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isNotBlank()) {
            list = list.filter { 
                it.appName.contains(trimmedQuery, ignoreCase = true) || 
                it.packageName.contains(trimmedQuery, ignoreCase = true)
            }
        }
        return list.distinctBy { it.packageName }
    }
}
