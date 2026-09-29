package com.focusbyrj.app.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomCategory(val id: String, val name: String, val packages: Set<String>)

object CustomCategoryManager {
    private const val PREFS_NAME = "custom_categories_prefs"
    private const val KEY_CATEGORIES = "categories"
    
    private var isInitialized = false
    private val _categories = MutableStateFlow<List<CustomCategory>>(emptyList())
    val categories: StateFlow<List<CustomCategory>> = _categories.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CATEGORIES, "[]") ?: "[]"
        _categories.value = parseCategories(jsonString)
        isInitialized = true
    }

    fun getCategories(context: Context): List<CustomCategory> {
        if (!isInitialized) {
            init(context)
        }
        return _categories.value
    }

    fun saveCategory(context: Context, id: String?, name: String, packages: Set<String>) {
        val currentList = _categories.value.toMutableList()
        val finalId = id ?: UUID.randomUUID().toString()
        val index = currentList.indexOfFirst { it.id == finalId }
        val newCategory = CustomCategory(finalId, name, packages)
        if (index != -1) {
            currentList[index] = newCategory
        } else {
            currentList.add(newCategory)
        }
        _categories.value = currentList
        persist(context, currentList)
    }

    fun deleteCategory(context: Context, id: String) {
        val currentList = _categories.value.filter { it.id != id }
        _categories.value = currentList
        persist(context, currentList)
    }

    private fun persist(context: Context, list: List<CustomCategory>) {
        val jsonArray = JSONArray()
        list.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            val pkgsArray = JSONArray()
            cat.packages.forEach { pkgsArray.put(it) }
            obj.put("packages", pkgsArray)
            jsonArray.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATEGORIES, jsonArray.toString())
            .apply()
    }

    internal fun parseCategories(jsonString: String): List<CustomCategory> {
        val list = mutableListOf<CustomCategory>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Unnamed"
                    val pkgsArray = obj.optJSONArray("packages")
                    val pkgs = mutableSetOf<String>()
                    if (pkgsArray != null) {
                        for (j in 0 until pkgsArray.length()) {
                            val pkg = pkgsArray.optString(j)
                            if (!pkg.isNullOrBlank()) {
                                pkgs.add(pkg)
                            }
                        }
                    }
                    list.add(CustomCategory(id, name, pkgs))
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.distinctBy { it.id }
    }
}
