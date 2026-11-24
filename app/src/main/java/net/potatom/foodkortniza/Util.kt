package net.potatom.foodkortniza

import android.content.Context
import android.widget.Toast
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

object UrlValidator {
    data class Result(val url: String?, val host: String?, val error: String?)

    fun normalize(input: String): Result {
        var value = input.trim()
        if (value.isEmpty()) return Result(null, null, "Введите адрес сайта")

        if (!value.contains("://")) value = "https://$value"

        val uri = try {
            URI(value)
        } catch (e: URISyntaxException) {
            return Result(null, null, "Не удалось разобрать адрес")
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return Result(null, null, "Разрешены только https-адреса")
        }

        val host = uri.host?.lowercase()
        if (host.isNullOrBlank()) return Result(null, null, "Не указан домен")
        if (uri.userInfo != null) return Result(null, null, "Адрес не должен содержать логин и пароль")
        if (host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal")
        ) {
            return Result(null, null, "Локальные домены не разрешены")
        }
        if (isLocalIp(host)) {
            return Result(null, null, "Локальные IP-адреса не разрешены")
        }

        val portPart = if (uri.port == -1) "" else ":${uri.port}"
        val path = if (uri.rawPath.isNullOrBlank()) "/" else uri.rawPath
        val query = if (uri.rawQuery.isNullOrBlank()) "" else "?${uri.rawQuery}"
        val normalized = "https://$host$portPart$path$query"
        return Result(normalized, host, null)
    }

    private fun isLocalIp(host: String): Boolean {
        if (!Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host)) return false
        return try {
            val address = InetAddress.getByName(host)
            address.isLoopbackAddress || address.isSiteLocalAddress ||
                address.isLinkLocalAddress || address.isAnyLocalAddress ||
                address.isMulticastAddress
        } catch (e: Exception) {
            true
        }
    }
}

fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
