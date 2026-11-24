package net.potatom.foodkortniza

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class Category(val id: String, val title: String, val emoji: String)

data class Store(
    val id: String,
    val title: String,
    val entryUrl: String,
    val allowedHosts: List<String>,
    val categoryId: String?,
    val searchKeywords: List<String>,
    val priority: Int,
    val enabled: Boolean,
    val emoji: String,
    val color: String,
    val note: String
)

data class Catalog(
    val schemaVersion: Int,
    val app: String,
    val categories: List<Category>,
    val stores: List<Store>,
    val defaultIds: List<String>,
    val rules: JSONObject
)

data class TabRecord(
    val id: String,
    val storeId: String?,
    val title: String,
    val entryUrl: String,
    val allowedHosts: List<String>,
    val emoji: String,
    val color: String,
    var lastUrl: String,
    val createdAt: Long,
    val isDefault: Boolean,
    var order: Int = 0
) {
    val host: String
        get() = try {
            URI(entryUrl).host ?: ""
        } catch (e: Exception) {
            ""
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("storeId", storeId)
        put("title", title)
        put("entryUrl", entryUrl)
        put("allowedHosts", Jsonx.stringArray(allowedHosts))
        put("emoji", emoji)
        put("color", color)
        put("lastUrl", lastUrl)
        put("createdAt", createdAt)
        put("isDefault", isDefault)
        put("order", order)
    }

    companion object {
        fun fromJson(o: JSONObject): TabRecord = TabRecord(
            id = o.getString("id"),
            storeId = if (o.isNull("storeId")) null else o.optString("storeId"),
            title = o.optString("title"),
            entryUrl = o.optString("entryUrl"),
            allowedHosts = Jsonx.stringList(o.optJSONArray("allowedHosts")),
            emoji = o.optString("emoji", "🔗"),
            color = o.optString("color", "#607D8B"),
            lastUrl = o.optString("lastUrl"),
            createdAt = o.optLong("createdAt", 0L),
            isDefault = o.optBoolean("isDefault", false),
            order = o.optInt("order", 0)
        )
    }
}

class RegistryState(
    val tabs: MutableList<TabRecord> = mutableListOf(),
    val slots: MutableMap<Int, String> = mutableMapOf(),
    val lastUsed: MutableMap<String, Long> = mutableMapOf(),
    var activeTabId: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", 1)
        put("activeTabId", activeTabId)
        put("tabs", JSONArray().also { arr ->
            tabs.forEach { arr.put(it.toJson()) }
        })
        put("slots", JSONObject().also { obj ->
            slots.forEach { (slot, tabId) -> obj.put(slot.toString(), tabId) }
        })
        put("lastUsed", JSONObject().also { obj ->
            lastUsed.forEach { (id, time) -> obj.put(id, time) }
        })
    }

    companion object {
        fun fromJson(json: String): RegistryState {
            val o = JSONObject(json)
            val state = RegistryState()
            o.optJSONArray("tabs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    state.tabs.add(TabRecord.fromJson(arr.getJSONObject(i)))
                }
            }
            o.optJSONObject("slots")?.let { obj ->
                obj.keys().forEach { key ->
                    val slot = key.toIntOrNull() ?: return@forEach
                    state.slots[slot] = obj.optString(key)
                }
            }
            o.optJSONObject("lastUsed")?.let { obj ->
                obj.keys().forEach { key -> state.lastUsed[key] = obj.optLong(key, 0L) }
            }
            state.activeTabId = o.optString("activeTabId").ifBlank { null }
            return state
        }
    }
}

data class Activation(
    val tab: TabRecord,
    val slot: Int,
    val stateJson: String? = null
)

object Jsonx {
    fun stringArray(values: List<String>): JSONArray = JSONArray().also { arr ->
        values.forEach { arr.put(it) }
    }

    fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) result.add(arr.optString(i))
        return result
    }

    fun activation(tab: TabRecord, slot: Int, stateJson: String? = null): JSONObject = JSONObject().apply {
        put("tab", tab.toJson())
        put("slot", slot)
        if (stateJson != null) put("state", stateJson)
    }

    fun activation(json: String): Activation {
        val o = JSONObject(json)
        return Activation(
            tab = TabRecord.fromJson(o.getJSONObject("tab")),
            slot = o.getInt("slot"),
            stateJson = o.optString("state").ifBlank { null }
        )
    }

    fun tab(json: String): TabRecord = TabRecord.fromJson(JSONObject(json))
}

fun hostAllowed(patterns: List<String>, host: String): Boolean {
    val h = host.lowercase()
    return patterns.any { raw ->
        val p = raw.lowercase()
        when {
            p == "*" -> true
            p.startsWith("*.") -> {
                val base = p.substring(2)
                h == base || h.endsWith(".$base")
            }
            else -> h == p
        }
    }
}
