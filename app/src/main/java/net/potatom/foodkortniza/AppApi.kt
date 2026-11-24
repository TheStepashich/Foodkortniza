package net.potatom.foodkortniza

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.util.UUID

object AppApi {
    const val EXTRA_TAB_ID = "tabId"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_TAB_JSON = "tabJson"
    const val EXTRA_STATE_JSON = "stateJson"

    private fun registryUri(context: Context): Uri =
        Uri.parse("content://${context.packageName}.registry")

    private fun call(context: Context, method: String, arg: String? = null, extras: Bundle? = null): Bundle? =
        try {
            context.contentResolver.call(registryUri(context), method, arg, extras)
        } catch (e: Exception) {
            null
        }

    fun getState(context: Context): String? = call(context, "get_state")?.getString("json")

    fun getTab(context: Context, tabId: String): String? = call(context, "get_tab", tabId)?.getString("json")

    fun activate(context: Context, tabId: String): String? = call(context, "activate", tabId)?.getString("json")

    fun registerPid(context: Context, tabId: String, slot: Int, pid: Int) {
        call(context, "register_pid", tabId, Bundle().apply {
            putInt("slot", slot)
            putInt("pid", pid)
        })
    }

    fun saveUrl(context: Context, tabId: String, url: String) {
        call(context, "save_url", tabId, Bundle().apply { putString("url", url) })
    }

    fun findTab(context: Context, storeId: String?, url: String? = null): String? =
        call(context, "find_tab", storeId, Bundle().apply { putString("url", url) })?.getString("id")

    fun addFromStore(context: Context, storeId: String): String? =
        call(context, "add_store", storeId)?.getString("json")

    fun addCustom(context: Context, title: String, url: String, host: String): String? =
        call(context, "add_custom", null, Bundle().apply {
            putString("title", title)
            putString("url", url)
            putString("host", host)
        })?.getString("json")

    fun reorder(context: Context, ids: List<String>) {
        call(context, "reorder", null, Bundle().apply { putString("ids", ids.joinToString(",")) })
    }

    fun closeTab(context: Context, tabId: String): String? = call(context, "close_tab", tabId)?.getString("json")

    fun openTab(context: Context, activation: Activation) {
        val intent = Intent(context, TabActivity::class.java).apply {
            putExtra(EXTRA_TAB_ID, activation.tab.id)
            putExtra(EXTRA_TAB_JSON, activation.tab.toJson().toString())
            putExtra(EXTRA_STATE_JSON, activation.stateJson)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivityNoAnim(context, intent)
    }

    /** Запуск без анимации: переходы между каталогом и вкладками должны быть мгновенными. */
    fun startActivityNoAnim(context: Context, intent: Intent) {
        val options = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (context is Activity) {
            context.startActivity(intent, options)
            @Suppress("DEPRECATION")
            context.overridePendingTransition(0, 0)
        } else {
            context.startActivity(intent, options)
        }
    }

    fun newTabId(): String = UUID.randomUUID().toString()
}
