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
        return com.focusbyrj.app.util.AppCatalog.filter(installedApps, category, searchQuery)
    }
}
