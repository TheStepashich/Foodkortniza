package net.potatom.foodkortniza

import android.content.Context
import android.webkit.CookieManager
import java.io.File
import java.io.RandomAccessFile

/**
 * Реестр вкладок. Работает только в основном процессе — внутри RegistryProvider.
 * На диске лежит один JSON-файл; все процессы обращаются к нему через ContentProvider.
 */
class TabRegistry(private val context: Context) {
    private val file = File(context.filesDir, "tabs.json")
    private val tempFile = File(context.filesDir, "tabs.json.tmp")
    private val lockFile = File(context.filesDir, "tabs.lock")
    private val pids = mutableMapOf<Int, Int>()
    private var state: RegistryState = RegistryState()

    init {
        load()
        if (state.tabs.isEmpty()) seedDefaults()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            state = RegistryState.fromJson(file.readText())
        } catch (e: Exception) {
            state = RegistryState()
        }
        // Идентификаторы процессов не переживают перезапуск.
        pids.clear()
    }

    private fun seedDefaults() {
        val catalog = CatalogRepository(context).catalog
        catalog.defaultIds.mapNotNull { id -> catalog.stores.firstOrNull { it.id == id } }
            .forEachIndexed { index, store ->
                state.tabs.add(tabFromStore(store, isDefault = true, order = index))
            }
        state.activeTabId = state.tabs.firstOrNull()?.id
        persist()
    }

    private fun tabFromStore(store: Store, isDefault: Boolean, order: Int): TabRecord = TabRecord(
        id = AppApi.newTabId(),
        storeId = store.id,
        title = store.title,
        entryUrl = store.entryUrl,
        allowedHosts = store.allowedHosts,
        emoji = store.emoji,
        color = store.color,
        lastUrl = store.entryUrl,
        createdAt = System.currentTimeMillis(),
        isDefault = isDefault,
        order = order
    )

    @Synchronized
    fun snapshotJson(): String = state.toJson().toString()

    @Synchronized
    fun tabJson(tabId: String): String? = state.tabs.firstOrNull { it.id == tabId }?.toJson()?.toString()

    @Synchronized
    fun findTab(storeId: String?, url: String?): String? {
        storeId?.let { id -> state.tabs.firstOrNull { it.storeId == id }?.let { return it.id } }
        url?.let { value ->
            state.tabs.firstOrNull { it.entryUrl == value || it.lastUrl == value }?.let { return it.id }
        }
        return null
    }

    @Synchronized
    fun addFromStore(storeId: String): String? {
        val store = CatalogRepository(context).find(storeId) ?: return null
        val existing = state.tabs.firstOrNull { it.storeId == store.id }
        if (existing != null) return existing.toJson().toString()
        val tab = tabFromStore(store, isDefault = false, order = state.tabs.size)
        state.tabs.add(tab)
        persist()
        return tab.toJson().toString()
    }

    @Synchronized
    fun addCustom(title: String, url: String, host: String): String {
        val tab = TabRecord(
            id = AppApi.newTabId(),
            storeId = null,
            title = title.ifBlank { host },
            entryUrl = url,
            allowedHosts = listOf(host, "*.$host"),
            emoji = "🔗",
            color = "#607D8B",
            lastUrl = url,
            createdAt = System.currentTimeMillis(),
            isDefault = false,
            order = state.tabs.size
        )
        state.tabs.add(tab)
        persist()
        return tab.toJson().toString()
    }

    /**
     * Возвращает вкладку и слот. Слоты 0..3 назначаются по LRU: при пятой активной вкладке
     * процесс самой давней убивается, а её данные остаются на диске.
     */
    @Synchronized
    fun activate(tabId: String): Pair<TabRecord, Int> {
        val tab = state.tabs.first { it.id == tabId }
        val now = System.currentTimeMillis()

        val assignedSlot = state.slots.entries.firstOrNull { it.value == tabId }?.key
        val slot = assignedSlot ?: run {
            val free = (0 until MAX_ACTIVE).firstOrNull { !state.slots.containsKey(it) }
            if (free != null) {
                free
            } else {
                val victimId = state.tabs
                    .filter { state.slots.containsValue(it.id) }
                    .minByOrNull { state.lastUsed[it.id] ?: 0L }
                    ?.id
                if (victimId == null) {
                    0
                } else {
                    val victimSlot = state.slots.entries.first { it.value == victimId }.key
                    state.slots.remove(victimSlot)
                    victimSlot
                }
            }
        }

        state.slots[slot] = tabId
        state.lastUsed[tabId] = now
        state.activeTabId = tabId
        persist()
        return tab to slot
    }

    /** Переставляет вкладки в панели согласно присланному порядку id. */
    @Synchronized
    fun reorder(orderedIds: List<String>) {
        val byId = state.tabs.associateBy { it.id }
        val reordered = mutableListOf<TabRecord>()
        orderedIds.forEach { id -> byId[id]?.let { reordered.add(it) } }
        state.tabs.forEach { tab -> if (tab.id !in orderedIds) reordered.add(tab) }
        reordered.forEachIndexed { index, tab -> tab.order = index }
        state.tabs.clear()
        state.tabs.addAll(reordered)
        persist()
    }

    @Synchronized
    fun registerPid(tabId: String, slot: Int, pid: Int) {
        pids[slot] = pid
    }

    @Synchronized
    fun saveUrl(tabId: String, url: String) {
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.lastUrl == url) return
        tab.lastUrl = url
        state.lastUsed[tabId] = System.currentTimeMillis()
        persist()
    }

    /** Удаляет вкладку и физически стирает данные её WebView. */
    @Synchronized
    fun closeTab(tabId: String, callerPid: Int): String {
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return snapshotJson()
        val slot = state.slots.entries.firstOrNull { it.value == tabId }?.key

        state.tabs.remove(tab)
        state.lastUsed.remove(tabId)
        if (slot != null) state.slots.remove(slot)
        if (state.activeTabId == tabId) {
            state.activeTabId = state.tabs.maxByOrNull { state.lastUsed[it.id] ?: 0L }?.id
        }
        persist()

        clearTabCookies(tab)
        if (slot != null) pids.remove(slot)

        return snapshotJson()
    }

    /** Куки теперь общие для всех вкладок, поэтому чистим только домены закрытой. */
    private fun clearTabCookies(tab: TabRecord) {
        val manager = CookieManager.getInstance()
        tab.allowedHosts
            .filter { it != "*" && !it.startsWith("*.") }
            .forEach { host -> manager.setCookie("https://$host", null) }
        runCatching { manager.flush() }
    }

    private fun persist() {
        val lock = RandomAccessFile(lockFile, "rw")
        val channelLock = try {
            lock.channel.lock()
        } catch (e: Exception) {
            null
        }
        try {
            tempFile.writeText(state.toJson().toString())
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
        } finally {
            try {
                channelLock?.release()
            } catch (e: Exception) {
                // ignore
            }
            lock.close()
        }
    }

    companion object {
        const val MAX_ACTIVE = 4
    }
}
