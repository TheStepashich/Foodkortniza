package net.potatom.foodkortniza.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object FaviconLoader {
    private val cache = LruCache<String, Bitmap>(64)
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(host: String, callback: (Bitmap?) -> Unit) {
        if (host.isBlank()) {
            callback(null)
            return
        }
        cache.get(host)?.let {
            callback(it)
            return
        }
        executor.execute {
            val bitmap = try {
                val connection = URL("https://$host/favicon.ico").openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.instanceFollowRedirects = true
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            if (bitmap != null) cache.put(host, bitmap)
            mainHandler.post { callback(bitmap) }
        }
    }
}
