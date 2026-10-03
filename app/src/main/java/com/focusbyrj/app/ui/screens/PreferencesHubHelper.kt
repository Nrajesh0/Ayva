/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.data.TaskDao

object PreferencesHubHelper {
    /**
     * Resolves TaskDao safely without blind ClassCastException on non-FocusApplication contexts.
     */
    fun getTaskDao(context: Context): TaskDao? {
        return (context.applicationContext as? FocusApplication)?.database?.taskDao()
    }
}
