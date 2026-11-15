package com.focusbyrj.app.data

import com.focusbyrj.app.util.AppCategory

data class AppInfo(
    val name: String,
    val packageName: String,
    val category: AppCategory = AppCategory.OTHERS,
)
