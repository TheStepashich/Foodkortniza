package net.potatom.foodkortniza

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CatalogRepository(context: Context) {
    val catalog: Catalog

    init {
        val root = JSONObject(CatalogSource.load(context))

        val categories = mutableListOf<Category>()
        root.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                categories.add(
                    Category(
                        id = o.getString("id"),
                        title = o.optString("title"),
                        emoji = o.optString("emoji")
                    )
                )
            }
        }

        val stores = mutableListOf<Store>()
        root.optJSONArray("stores")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                stores.add(
                    Store(
                        id = o.getString("id"),
                        title = o.optString("title"),
                        entryUrl = o.optString("entryUrl"),
                        allowedHosts = Jsonx.stringList(o.optJSONArray("allowedHosts")),
                        categoryId = o.optString("categoryId").ifBlank { null },
                        searchKeywords = Jsonx.stringList(o.optJSONArray("searchKeywords")),
                        priority = o.optInt("priority", 100),
                        enabled = o.optBoolean("enabled", true),
                        emoji = o.optString("emoji", "🍽"),
                        color = o.optString("color", "#607D8B"),
                        note = o.optString("note")
                    )
                )
            }
        }

        val defaultIds = mutableListOf<String>()
        root.optJSONArray("defaultIds")?.let { arr: JSONArray ->
            for (i in 0 until arr.length()) defaultIds.add(arr.optString(i))
        }

        catalog = Catalog(
            schemaVersion = root.optInt("schemaVersion", 1),
            app = root.optString("app"),
            categories = categories,
            stores = stores,
            defaultIds = defaultIds,
            rules = root.optJSONObject("rules") ?: JSONObject()
        )
    }

    fun enabledStores(): List<Store> = catalog.stores.filter { it.enabled }.sortedBy { it.priority }

    fun find(id: String?): Store? = catalog.stores.firstOrNull { it.id == id }
}
