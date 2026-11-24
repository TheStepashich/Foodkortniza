package net.potatom.foodkortniza

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Каталог магазинов: встроенный assets/stores.json как надёжный fallback,
 * а при наличии сети — свежая копия по BuildConfig.STORES_URL с кэшем на диске.
 */
object CatalogSource {
    private const val CACHE_FILE = "stores_remote.json"
    private const val MAX_CACHE_AGE_MS = 6L * 60 * 60 * 1000
    private const val TIMEOUT_MS = 6000

    /** Синхронно: свежий кэш → встроенный каталог. */
    fun load(context: Context): String {
        val cache = File(context.filesDir, CACHE_FILE)
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() <= MAX_CACHE_AGE_MS) {
            val text = runCatching { cache.readText() }.getOrNull()
            if (text != null && isValid(text)) return text
        }
        return context.assets.open("stores.json").bufferedReader().use { it.readText() }
    }

    /** Асинхронно обновляет кэш; onUpdated получает true, если каталог реально обновился. */
    fun refresh(context: Context, onUpdated: (Boolean) -> Unit = {}) {
        Thread {
            val updated = runCatching { download() }.getOrNull()?.let { text ->
                if (isValid(text)) {
                    runCatching {
                        val cache = File(context.filesDir, CACHE_FILE)
                        val temp = File(context.filesDir, "$CACHE_FILE.tmp")
                        temp.writeText(text)
                        if (cache.exists()) cache.delete()
                        temp.renameTo(cache)
                    }.isSuccess
                } else {
                    false
                }
            } ?: false

            Handler(Looper.getMainLooper()).post { onUpdated(updated) }
        }.start()
    }

    private fun download(): String {
        val url = URL(BuildConfig.STORES_URL)
        require(url.protocol == "https") { "Поддерживается только https" }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun isValid(text: String): Boolean = runCatching {
        val root = JSONObject(text)
        root.optJSONArray("stores") != null && root.optJSONArray("stores")?.length() != 0
    }.getOrDefault(false)
}
