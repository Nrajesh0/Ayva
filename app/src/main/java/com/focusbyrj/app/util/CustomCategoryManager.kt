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
    
    private val _categories = MutableStateFlow<List<CustomCategory>>(emptyList())
    val categories: StateFlow<List<CustomCategory>> = _categories.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CATEGORIES, "[]") ?: "[]"
        _categories.value = parseCategories(jsonString)
    }

    fun getCategories(context: Context): List<CustomCategory> {
        if (_categories.value.isEmpty()) {
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

    private fun parseCategories(jsonString: String): List<CustomCategory> {
        val list = mutableListOf<CustomCategory>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val pkgsArray = obj.getJSONArray("packages")
                val pkgs = mutableSetOf<String>()
                for (j in 0 until pkgsArray.length()) {
                    pkgs.add(pkgsArray.getString(j))
                }
                list.add(CustomCategory(id, name, pkgs))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
