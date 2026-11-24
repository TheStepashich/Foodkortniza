package net.potatom.foodkortniza

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/**
 * Единственный владелец реестра вкладок. Живёт в основном процессе; вкладки
 * обращаются к нему через ContentResolver.call().
 */
class RegistryProvider : ContentProvider() {
    private lateinit var registry: TabRegistry

    override fun onCreate(): Boolean {
        registry = TabRegistry(context!!)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            "get_state" -> bundle("json", registry.snapshotJson())

            "get_tab" -> bundle("json", arg?.let { registry.tabJson(it) })

            "activate" -> {
                if (arg == null) return null
                val (tab, slot) = registry.activate(arg)
                bundle("json", Jsonx.activation(tab, slot, registry.snapshotJson()).toString())
            }

            "register_pid" -> {
                if (arg != null && extras != null) {
                    registry.registerPid(arg, extras.getInt("slot"), extras.getInt("pid"))
                }
                Bundle()
            }

            "save_url" -> {
                if (arg != null) registry.saveUrl(arg, extras?.getString("url") ?: "")
                Bundle()
            }

            "find_tab" -> bundle(
                "id", registry.findTab(arg, extras?.getString("url"))
            )

            "add_store" -> {
                val json = arg?.let { registry.addFromStore(it) }
                bundle("json", json)
            }

            "add_custom" -> {
                val json = if (extras == null) {
                    null
                } else {
                    registry.addCustom(
                        title = extras.getString("title") ?: "",
                        url = extras.getString("url") ?: "",
                        host = extras.getString("host") ?: ""
                    )
                }
                bundle("json", json)
            }

            "reorder" -> {
                val ids = extras?.getString("ids")
                if (!ids.isNullOrBlank()) registry.reorder(ids.split(','))
                Bundle()
            }

            "close_tab" -> {
                if (arg == null) return null
                bundle("json", registry.closeTab(arg, Binder.getCallingPid()))
            }

            else -> super.call(method, arg, extras)
        }
    }

    private fun bundle(key: String, value: String?): Bundle = Bundle().apply {
        putString(key, value)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
