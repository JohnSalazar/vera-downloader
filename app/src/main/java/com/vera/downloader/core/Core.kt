package com.vera.downloader.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

enum class Platform(val label: String) {
    TIKTOK("TikTok"),
    YOUTUBE("YouTube"),
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    UNKNOWN("Link")
}

enum class MediaKind { VIDEO, AUDIO, IMAGE }

data class MediaItem(
    val kind: MediaKind,
    val url: String,
    val quality: String? = null,
    val extension: String,
    val mime: String,
    val sizeBytes: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val watermarkFree: Boolean = false,
    val muxAudioUrl: String? = null,
    val muxAudioMime: String? = null,
    val slideshowImages: List<String> = emptyList(),
    val extractAudio: Boolean = false
)

data class ParseResult(
    val platform: Platform,
    val sourceUrl: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long? = null,
    val items: List<MediaItem>,
    val provider: String,
    val note: String? = null
)

class ParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object LinkDetector {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? {
        val match = urlRegex.find(text) ?: return null
        return match.value.trimEnd('.', ',', ')', ']', '}', '"')
    }

    fun extractAll(text: String): List<String> = urlRegex.findAll(text)
        .map { it.value.trimEnd('.', ',', ')', ']', '}', '"') }
        .distinct()
        .toList()

    fun platformOf(url: String): Platform {
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return Platform.UNKNOWN
        return when {
            host == "vm.tiktok.com" || host == "vt.tiktok.com" || host.endsWith("tiktok.com") -> Platform.TIKTOK
            host == "youtu.be" || host.endsWith("youtube.com") -> Platform.YOUTUBE
            host.endsWith("instagram.com") -> Platform.INSTAGRAM
            host == "fb.watch" || host.endsWith("facebook.com") -> Platform.FACEBOOK
            else -> Platform.UNKNOWN
        }
    }
}

object HtmlUtil {
    fun unescape(raw: String): String {
        var value = raw
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("\\u003D", "=")
            .replace("\\u003f", "?")
            .replace("\\u003F", "?")
            .replace("\\u0025", "%")
        value = value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        value = value.replace(Regex("&#x([0-9a-fA-F]+);")) {
            runCatching { String(Character.toChars(it.groupValues[1].toInt(16))) }
                .getOrDefault(it.value)
        }
        value = value.replace(Regex("&#(\\d+);")) {
            runCatching { String(Character.toChars(it.groupValues[1].toInt())) }
                .getOrDefault(it.value)
        }
        return value
    }

    fun stripTags(value: String): String = value.replace(Regex("<[^>]*>"), "").trim()

    fun meta(html: String, key: String): String? {
        val patterns = listOf(
            Regex("<meta[^>]+(?:property|name)=\"$key\"[^>]+content=\"([^\"]*)\"", RegexOption.IGNORE_CASE),
            Regex("<meta[^>]+content=\"([^\"]*)\"[^>]+(?:property|name)=\"$key\"", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(html)?.let { return unescape(it.groupValues[1]) }
        }
        return null
    }

    fun firstMatch(html: String, pattern: String): String? =
        Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.getOrNull(1)
}

class Http {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val okHttp: OkHttpClient get() = client

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        request(
            Request.Builder()
                .url(url)
                .get()
                .apply { headers.forEach { (key, value) -> header(key, value) } }
                .build()
        )

    suspend fun getResolved(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ParseException("HTTP ${response.code} (${response.request.url.host})")
            }
            response.request.url.toString() to body
        }
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String =
        request(
            Request.Builder()
                .url(url)
                .post(json.toRequestBody(jsonMedia))
                .apply { headers.forEach { (key, value) -> header(key, value) } }
                .build()
        )

    private suspend fun request(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ParseException("HTTP ${response.code} (${request.url.host})")
            }
            body
        }
    }

    companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    }
}
