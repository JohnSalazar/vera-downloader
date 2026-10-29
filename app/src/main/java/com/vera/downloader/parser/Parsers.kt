package com.vera.downloader.parser

import com.vera.downloader.core.Http
import com.vera.downloader.core.HtmlUtil
import com.vera.downloader.core.LinkDetector
import com.vera.downloader.core.MediaItem
import com.vera.downloader.core.MediaKind
import com.vera.downloader.core.ParseException
import com.vera.downloader.core.ParseResult
import com.vera.downloader.core.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

private const val INSTAGRAM_MOBILE_UA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

interface MediaParser {
    val id: String
    fun supports(url: String): Boolean
    suspend fun parse(url: String): ParseResult
}

class TikTokParser(private val http: Http) : MediaParser {
    override val id = "tikwm"

    override fun supports(url: String): Boolean = LinkDetector.platformOf(url) == Platform.TIKTOK

    override suspend fun parse(url: String): ParseResult {
        val endpoint = "https://tikwm.com/api/?url=${URLEncoder.encode(url, "UTF-8")}&hd=1"
        val raw = requestTikTok(endpoint)
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 0) {
            throw ParseException(root.optString("msg", "O TikTok não retornou dados"))
        }
        val data = root.optJSONObject("data") ?: throw ParseException("Resposta vazia do TikTok")
        val authorJson = data.optJSONObject("author")
        val author = authorJson?.optString("unique_id")?.takeIf { it.isNotBlank() }
            ?: authorJson?.optString("nickname")?.takeIf { it.isNotBlank() }
        val title = data.optString("title").ifBlank { "Vídeo do TikTok" }
        val headers = mapOf(
            "Referer" to "https://www.tiktok.com/",
            "User-Agent" to Http.UA
        )
        val items = mutableListOf<MediaItem>()
        val images = data.optJSONArray("images")
        val isPhoto = images != null && images.length() > 0
        fun audioUrl(value: String): Boolean =
            value.contains("mime_type=audio", ignoreCase = true) ||
                value.contains("audio_mp4", ignoreCase = true)

        val hd = data.optString("hdplay")
        val sd = data.optString("play")
        if (hd.isNotBlank() && !audioUrl(hd)) {
            items += MediaItem(
                MediaKind.VIDEO, hd, "HD", "mp4", "video/mp4",
                data.optLong("hd_size").takeIf { it > 0 }, headers, true
            )
        }
        if (sd.isNotBlank() && sd != hd && !audioUrl(sd)) {
            items += MediaItem(
                MediaKind.VIDEO, sd, if (hd.isBlank()) "HD" else "SD", "mp4", "video/mp4",
                data.optLong("size").takeIf { it > 0 }, headers, true
            )
        }
        val music = data.optString("music")
        val audio = music.ifBlank { if (isPhoto && audioUrl(hd)) hd else "" }
        if (audio.isNotBlank()) {
            val m4a = audioUrl(audio)
            items += MediaItem(
                MediaKind.AUDIO,
                audio,
                "Áudio original",
                if (m4a) "m4a" else "mp3",
                if (m4a) "audio/mp4" else "audio/mpeg",
                null,
                headers,
                true
            )
        }
        if (images != null) {
            for (index in 0 until images.length()) {
                val imageUrl = images.optString(index)
                if (imageUrl.isNotBlank()) {
                    val (extension, mime) = imageType(imageUrl)
                    items += MediaItem(
                        MediaKind.IMAGE, imageUrl, "Imagem ${index + 1}", extension, mime,
                        null, headers, true
                    )
                }
            }
        }
        if (isPhoto && items.any { it.kind == MediaKind.IMAGE }) {
            val imagesList = (0 until (images?.length() ?: 0))
                .mapNotNull { images?.optString(it)?.takeIf { url -> url.isNotBlank() } }
            items.add(
                0,
                MediaItem(
                    MediaKind.VIDEO,
                    audio.ifBlank { imagesList.firstOrNull().orEmpty() },
                    "Slideshow",
                    "mp4",
                    "video/mp4",
                    null,
                    headers,
                    true,
                    slideshowImages = imagesList
                )
            )
        }
        if (items.isEmpty()) throw ParseException("Nenhuma mídia encontrada no TikTok")

        val durationMs = (data.optDouble("duration", 0.0) * 1000.0).toLong().takeIf { it > 0 }
        return ParseResult(
            platform = Platform.TIKTOK,
            sourceUrl = url,
            title = title,
            author = author,
            coverUrl = data.optString("cover").takeIf { it.isNotBlank() },
            durationMs = durationMs,
            items = items,
            provider = id
        )
    }

    private suspend fun requestTikTok(endpoint: String): String {
        var last: Throwable? = null
        for (attempt in 0..1) {
            try {
                return http.get(
                    endpoint,
                    mapOf(
                        "User-Agent" to Http.UA,
                        "Accept" to "application/json"
                    )
                )
            } catch (throwable: Throwable) {
                last = throwable
                if (attempt == 0) kotlinx.coroutines.delay(700)
            }
        }
        throw ParseException("O TikTok demorou para responder. Tente novamente.", last)
    }
}

private fun imageType(url: String): Pair<String, String> = when {
    url.contains(".webp", ignoreCase = true) ||
        url.contains("format=webp", ignoreCase = true) -> "webp" to "image/webp"
    url.contains(".png", ignoreCase = true) -> "png" to "image/png"
    else -> "jpg" to "image/jpeg"
}

class YouTubeParser(private val http: Http) : MediaParser {
    override val id = "youtube"

    override fun supports(url: String): Boolean = LinkDetector.platformOf(url) == Platform.YOUTUBE

    override suspend fun parse(url: String): ParseResult {
        val videoId = extractVideoId(url) ?: throw ParseException("Link do YouTube inválido")
        var lastError: String? = null
        for (client in clients) {
            try {
                val root = requestPlayer(videoId, client)
                val status = root.optJSONObject("playabilityStatus")?.optString("status")
                if (status != "OK") {
                    lastError = root.optJSONObject("playabilityStatus")
                        ?.optString("reason")
                        ?.takeIf { it.isNotBlank() }
                        ?: "YouTube indisponível ($status)"
                    continue
                }
                val result = buildResult(root, url)
                if (result != null) return result
                lastError = "YouTube não retornou formatos utilizáveis"
            } catch (throwable: Throwable) {
                lastError = throwable.message
            }
        }
        throw ParseException(lastError ?: "Não foi possível extrair o vídeo do YouTube")
    }

    private suspend fun requestPlayer(videoId: String, client: YtClient): JSONObject {
        val headers = mutableMapOf(
            "User-Agent" to client.userAgent,
            "X-Youtube-Client-Name" to client.clientNameHeader,
            "X-Youtube-Client-Version" to client.version,
            "Origin" to "https://www.youtube.com"
        )
        val clientJson = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("hl", "en")
            .put("gl", "US")
        client.deviceMake?.let { clientJson.put("deviceMake", it) }
        client.deviceModel?.let { clientJson.put("deviceModel", it) }
        client.osName?.let { clientJson.put("osName", it) }
        client.osVersion?.let { clientJson.put("osVersion", it) }
        client.sdk?.let { clientJson.put("androidSdkVersion", it) }
        val context = JSONObject().put("client", clientJson)
        if (client.useVisitorData) {
            visitorData()?.let { data ->
                clientJson.put("visitorData", data)
                clientJson.put("userAgent", client.userAgent)
                headers["X-Goog-Visitor-Id"] = data
            }
            headers["X-Goog-Api-Format-Version"] = "2"
            headers["Referer"] = "https://www.youtube.com/"
            context.put("user", JSONObject().put("lockedSafetyMode", false))
            context.put("request", JSONObject().put("useSsl", true))
        }
        val body = JSONObject()
            .put("context", context)
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val raw = http.postJson(
            "https://www.youtube.com/youtubei/v1/player?key=$API_KEY",
            body.toString(),
            headers
        )
        return JSONObject(raw)
    }

    private suspend fun visitorData(): String? {
        cachedVisitorData?.let { return it }
        return try {
            val html = http.get(
                "https://www.youtube.com/",
                mapOf(
                    "User-Agent" to DESKTOP_UA,
                    "Accept-Language" to "en-US,en;q=0.9"
                )
            )
            Regex("\"visitorData\":\"([^\"]+)\"")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.also { cachedVisitorData = it }
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildResult(root: JSONObject, url: String): ParseResult? {
        val details = root.optJSONObject("videoDetails") ?: return null
        val streaming = root.optJSONObject("streamingData") ?: return null
        val title = details.optString("title", "Vídeo do YouTube")
        val author = details.optString("author").takeIf { it.isNotBlank() }
        val durationMs = details.optLong("lengthSeconds").takeIf { it > 0 }?.times(1000)
        val thumbs = details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val cover = if (thumbs != null && thumbs.length() > 0) {
            thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
        } else null

        val items = mutableListOf<MediaItem>()
        val muxed = bestMuxed(streaming.optJSONArray("formats"))
        if (muxed != null && streaming.optJSONArray("adaptiveFormats") == null) {
            val videoUrl = muxed.optString("url")
            if (videoUrl.isNotBlank()) {
                val height = muxed.optInt("height", 0)
                items += MediaItem(
                    MediaKind.VIDEO,
                    videoUrl,
                    if (height > 0) "${height}p" else "MP4",
                    "mp4",
                    "video/mp4",
                    muxed.optLong("contentLength").takeIf { it > 0 },
                    emptyMap(),
                    false
                )
                items += MediaItem(
                    MediaKind.AUDIO,
                    videoUrl,
                    "Áudio original",
                    "m4a",
                    "audio/mp4",
                    null,
                    emptyMap(),
                    false,
                    extractAudio = true
                )
            }
        } else {
            val adaptive = streaming.optJSONArray("adaptiveFormats") ?: return null
            val videos = mutableListOf<JSONObject>()
            val audios = mutableListOf<JSONObject>()
            for (index in 0 until adaptive.length()) {
                val format = adaptive.optJSONObject(index) ?: continue
                val mime = format.optString("mimeType")
                if (mime.contains("video/mp4") && mime.contains("avc1")) videos += format
                if (mime.startsWith("audio/mp4")) audios += format
            }
            val selectedAudio = audios.firstOrNull { it.optInt("itag") == 140 }
                ?: audios.maxByOrNull { it.optInt("bitrate") }
            val audioSize = selectedAudio?.optLong("contentLength")?.takeIf { it > 0 } ?: 0L
            // O googlevideo limita downloads grandes sem PO token; escolhemos o melhor
            // vídeo cujo tamanho somado ao áudio caiba com folga (~11 MB).
            val sizeCap = 20L * 1024 * 1024
            val fitting = videos.filter {
                val size = it.optLong("contentLength")
                size > 0 && size + audioSize <= sizeCap
            }
            val knownSizes = videos.filter { it.optLong("contentLength") > 0 }
            val selectedVideo = fitting.maxByOrNull { it.optInt("height", 0) }
                ?: knownSizes.minByOrNull { it.optLong("contentLength") }
                ?: videos.maxByOrNull { it.optInt("height", 0) }
                ?: return null
            val videoUrl = selectedVideo.optString("url")
            if (videoUrl.isBlank()) return null
            val audioUrl = selectedAudio?.optString("url")?.takeIf { it.isNotBlank() }
            val videoSize = selectedVideo.optLong("contentLength").takeIf { it > 0 } ?: 0L
            val height = selectedVideo.optInt("height", 0)
            items += MediaItem(
                MediaKind.VIDEO,
                videoUrl,
                if (height > 0) "${height}p" else "MP4",
                "mp4",
                "video/mp4",
                (videoSize + audioSize).takeIf { it > 0 },
                emptyMap(),
                false,
                muxAudioUrl = audioUrl,
                muxAudioMime = if (audioUrl != null) "audio/mp4" else null
            )
            if (audioUrl != null) {
                items += MediaItem(
                    MediaKind.AUDIO,
                    audioUrl,
                    "M4A",
                    "m4a",
                    "audio/mp4",
                    audioSize.takeIf { it > 0 },
                    emptyMap(),
                    false
                )
            }
        }
        if (items.isEmpty()) return null
        return ParseResult(
            platform = Platform.YOUTUBE,
            sourceUrl = url,
            title = title,
            author = author,
            coverUrl = cover,
            durationMs = durationMs,
            items = items,
            provider = id
        )
    }

    private fun bestMuxed(formats: JSONArray?): JSONObject? {
        if (formats == null) return null
        var best: JSONObject? = null
        var bestHeight = -1
        for (index in 0 until formats.length()) {
            val format = formats.optJSONObject(index) ?: continue
            if (!format.optString("mimeType").startsWith("video/mp4")) continue
            val height = format.optInt("height", 0)
            if (height > bestHeight) {
                bestHeight = height
                best = format
            }
        }
        return best
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
            Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
            Regex("""/(?:shorts|embed|live|v)/([A-Za-z0-9_-]{11})""")
        )
        for (pattern in patterns) {
            pattern.find(url)?.let { return it.groupValues[1] }
        }
        return null
    }

    private data class YtClient(
        val name: String,
        val version: String,
        val userAgent: String,
        val clientNameHeader: String,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val sdk: Int? = null,
        val useVisitorData: Boolean = false
    )

    companion object {
        private const val API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

        @Volatile
        private var cachedVisitorData: String? = null

        private val clients = listOf(
            YtClient(
                name = "VISIONOS",
                version = "1.02.0",
                userAgent = DESKTOP_UA,
                clientNameHeader = "101",
                deviceMake = "Apple",
                deviceModel = "RealityDevice14,1",
                osName = "visionOS",
                osVersion = "1.3.1.21O224",
                useVisitorData = true
            ),
            YtClient(
                name = "IOS",
                version = "20.10.4",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_1 like Mac OS X)",
                clientNameHeader = "5",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iPhone",
                osVersion = "18.3.1.22D72"
            ),
            YtClient(
                name = "ANDROID_VR",
                version = "1.60.19",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; en_US; Quest 3 Build/SQ3A.220605.009.A1) gzip",
                clientNameHeader = "28",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12",
                sdk = 32
            ),
            YtClient(
                name = "WEB",
                version = "2.20240726.00.00",
                userAgent = Http.UA,
                clientNameHeader = "1"
            )
        )
    }
}

class InstagramParser(private val http: Http) : MediaParser {
    override val id = "instagram-embed"

    override fun supports(url: String): Boolean = LinkDetector.platformOf(url) == Platform.INSTAGRAM

    override suspend fun parse(url: String): ParseResult {
        val match = Regex("""instagram\.com/(p|reel|tv)/([A-Za-z0-9_-]+)""").find(url)
            ?: throw ParseException("Link do Instagram inválido")
        val type = match.groupValues[1]
        val code = match.groupValues[2]
        val embed = "https://www.instagram.com/$type/$code/embed/captioned/"
        val html = http.get(
            embed,
            mapOf(
                "User-Agent" to INSTAGRAM_MOBILE_UA,
                "Accept" to "text/html,application/xhtml+xml",
                "Accept-Language" to "en-US,en;q=0.9"
            )
        )
        val cleaned = HtmlUtil.unescape(html)
        val video = embeddedUrls(cleaned, "video_url").firstOrNull()
        val images = embeddedUrls(cleaned, "display_url")
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        val title = HtmlUtil.meta(html, "og:title")
            ?: HtmlUtil.firstMatch(html, "<title>(.*?)</title>")
            ?: "Publicação do Instagram"
        val cover = HtmlUtil.meta(html, "og:image") ?: images.firstOrNull()
        val headers = mapOf(
            "Referer" to "https://www.instagram.com/",
            "User-Agent" to INSTAGRAM_MOBILE_UA
        )

        val items = mutableListOf<MediaItem>()
        if (!video.isNullOrBlank()) {
            items += MediaItem(MediaKind.VIDEO, video, "Original", "mp4", "video/mp4", null, headers, false)
            items += MediaItem(
                MediaKind.AUDIO,
                video,
                "Áudio original",
                "m4a",
                "audio/mp4",
                null,
                headers,
                false,
                extractAudio = true
            )
        }
        images.forEachIndexed { index, imageUrl ->
            if (imageUrl != video) {
                items += MediaItem(
                    MediaKind.IMAGE, imageUrl, "Imagem ${index + 1}", "jpg", "image/jpeg",
                    null, headers, false
                )
            }
        }
        if (items.isEmpty()) {
            val fallback = instagramOembed(url, embed)
            if (fallback != null) return fallback
            throw ParseException("Instagram não retornou mídia (publicação privada ou login necessário)")
        }
        return ParseResult(
            platform = Platform.INSTAGRAM,
            sourceUrl = url,
            title = HtmlUtil.stripTags(title),
            author = null,
            coverUrl = cover,
            durationMs = null,
            items = items,
            provider = id
        )
    }

    private fun embeddedUrls(html: String, key: String): List<String> {
        val source = html.replace("\\\"", "\"")
        val result = mutableListOf<String>()
        var from = 0
        while (true) {
            val marker = "\"$key\":\""
            val start = source.indexOf(marker, from)
            if (start < 0) break
            var index = start + marker.length
            val value = StringBuilder()
            while (index < source.length) {
                val char = source[index]
                if (char == '\\' && index + 1 < source.length && source[index + 1] == '"') {
                    index += 2
                    break
                }
                if (char == '"') {
                    index++
                    break
                }
                value.append(char)
                index++
            }
            val decoded = unescapeJson(value.toString())
            if (decoded.startsWith("http")) result += decoded
            from = index
        }
        return result
    }

    private fun unescapeJson(value: String): String {
        var result = value
        result = Regex("\\\\+u([0-9a-fA-F]{4})").replace(result) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        result = result.replace(Regex("\\\\+/"), "/")
        result = result.replace("\\", "")
        return result
    }

    private suspend fun instagramOembed(url: String, embedUrl: String): ParseResult? {
        return try {
            val endpoint = "https://www.instagram.com/api/v1/oembed/?url=" +
                URLEncoder.encode(url, "UTF-8")
            val raw = http.get(
                endpoint,
                mapOf(
                    "User-Agent" to Http.UA,
                    "Accept" to "application/json",
                    "Referer" to embedUrl
                )
            )
            val json = JSONObject(raw)
            val title = json.optString("title").ifBlank { "Publicação do Instagram" }
            val author = json.optString("author_name").takeIf { it.isNotBlank() }
            val cover = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
            if (title.isBlank() && cover == null) return null
            val imageItems = if (cover != null) {
                listOf(
                    MediaItem(
                        MediaKind.IMAGE,
                        cover,
                        "Imagem",
                        "jpg",
                        "image/jpeg",
                        null,
                        mapOf("Referer" to "https://www.instagram.com/"),
                        false
                    )
                )
            } else {
                emptyList()
            }
            ParseResult(
                platform = Platform.INSTAGRAM,
                sourceUrl = url,
                title = title.take(280),
                author = author,
                coverUrl = cover,
                durationMs = null,
                items = imageItems,
                provider = "instagram-oembed",
                note = if (imageItems.isEmpty()) {
                    "O Instagram não liberou a mídia desta publicação. Abra no Instagram para ver o conteúdo."
                } else {
                    null
                }
            )
        } catch (_: Throwable) {
            null
        }
    }
}

class FacebookParser(private val http: Http) : MediaParser {
    override val id = "facebook-plugin"

    override fun supports(url: String): Boolean = LinkDetector.platformOf(url) == Platform.FACEBOOK

    override suspend fun parse(url: String): ParseResult {
        val mobileHeaders = mapOf(
            "User-Agent" to MOBILE_UA,
            "Accept" to "text/html,application/xhtml+xml",
            "Accept-Language" to "en-US,en;q=0.9"
        )
        val resolved = runCatching { http.getResolved(url, mobileHeaders) }.getOrNull()
        val page = resolved?.second
        val canonical = resolved?.first ?: url
        if (page != null) {
            val video = HtmlUtil.meta(page, "og:video")
                ?: HtmlUtil.meta(page, "og:video:secure_url")
            if (!video.isNullOrBlank()) {
                val headers = mapOf(
                    "Referer" to "https://www.facebook.com/",
                    "User-Agent" to MOBILE_UA
                )
                return ParseResult(
                    platform = Platform.FACEBOOK,
                    sourceUrl = HtmlUtil.meta(page, "og:url") ?: canonical,
                    title = HtmlUtil.meta(page, "og:title")?.let { HtmlUtil.stripTags(it) }
                        ?: "Vídeo do Facebook",
                    author = null,
                    coverUrl = HtmlUtil.meta(page, "og:image"),
                    durationMs = null,
                    items = listOf(
                        MediaItem(
                            MediaKind.VIDEO, video, "Original", "mp4", "video/mp4",
                            null, headers, false
                        ),
                        MediaItem(
                            MediaKind.AUDIO, video, "Áudio original", "m4a", "audio/mp4",
                            null, headers, false, extractAudio = true
                        )
                    ),
                    provider = id
                )
            }
            val image = HtmlUtil.meta(page, "og:image")
            if (!image.isNullOrBlank()) {
                return ParseResult(
                    platform = Platform.FACEBOOK,
                    sourceUrl = HtmlUtil.meta(page, "og:url") ?: canonical,
                    title = HtmlUtil.meta(page, "og:title")?.let { HtmlUtil.stripTags(it) }
                        ?: "Imagem do Facebook",
                    author = null,
                    coverUrl = image,
                    durationMs = null,
                    items = listOf(
                        MediaItem(
                            MediaKind.IMAGE, image, "Imagem", "jpg", "image/jpeg",
                            null,
                            mapOf(
                                "Referer" to "https://www.facebook.com/",
                                "User-Agent" to MOBILE_UA
                            ),
                            false
                        )
                    ),
                    provider = id
                )
            }
        }

        val plugin = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(canonical, "UTF-8") + "&show_text=false&width=560"
        val html = http.get(plugin, mobileHeaders)
        val cleaned = HtmlUtil.unescape(html)
        val hd = HtmlUtil.firstMatch(cleaned, "\"hd_src\":\"(.*?)\"")
            ?: HtmlUtil.firstMatch(cleaned, "\"browser_native_hd_url\":\"(.*?)\"")
        val sd = HtmlUtil.firstMatch(cleaned, "\"sd_src\":\"(.*?)\"")
            ?: HtmlUtil.firstMatch(cleaned, "\"browser_native_sd_url\":\"(.*?)\"")
            ?: HtmlUtil.firstMatch(cleaned, "\"playable_url\":\"(.*?)\"")
        val title = HtmlUtil.meta(html, "og:title") ?: "Vídeo do Facebook"
        val cover = HtmlUtil.meta(html, "og:image")
        val headers = mapOf(
            "Referer" to "https://www.facebook.com/",
            "User-Agent" to MOBILE_UA
        )

        val items = mutableListOf<MediaItem>()
        if (!hd.isNullOrBlank()) {
            items += MediaItem(MediaKind.VIDEO, hd, "HD", "mp4", "video/mp4", null, headers, false)
        }
        if (!sd.isNullOrBlank() && sd != hd) {
            items += MediaItem(
                MediaKind.VIDEO, sd, if (hd.isNullOrBlank()) "HD" else "SD",
                "mp4", "video/mp4", null, headers, false
            )
        }
        val audioSource = hd?.takeIf { it.isNotBlank() } ?: sd
        if (!audioSource.isNullOrBlank()) {
            items += MediaItem(
                MediaKind.AUDIO, audioSource, "Áudio original", "m4a", "audio/mp4",
                null, headers, false, extractAudio = true
            )
        }
        if (items.isEmpty()) {
            val image = cover ?: HtmlUtil.meta(html, "og:image")
            if (!image.isNullOrBlank()) {
                return ParseResult(
                    platform = Platform.FACEBOOK,
                    sourceUrl = canonical,
                    title = HtmlUtil.stripTags(title),
                    author = null,
                    coverUrl = image,
                    durationMs = null,
                    items = listOf(
                        MediaItem(
                            MediaKind.IMAGE, image, "Imagem", "jpg", "image/jpeg",
                            null, headers, false
                        )
                    ),
                    provider = id
                )
            }
            throw ParseException("Facebook não retornou mídia (publicação privada ou login necessário)")
        }
        return ParseResult(
            platform = Platform.FACEBOOK,
            sourceUrl = url,
            title = HtmlUtil.stripTags(title),
            author = null,
            coverUrl = cover,
            durationMs = null,
            items = items,
            provider = id
        )
    }

    companion object {
        private const val MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    }
}

class ParserChain(http: Http) {
    private val tiktok = TikTokParser(http)
    private val youtube = YouTubeParser(http)
    private val instagram = InstagramParser(http)
    private val facebook = FacebookParser(http)

    suspend fun parse(rawText: String): ParseResult {
        val url = LinkDetector.extract(rawText) ?: throw ParseException("Nenhum link encontrado no texto")
        return when (LinkDetector.platformOf(url)) {
            Platform.TIKTOK -> tiktok.parse(url)
            Platform.YOUTUBE -> youtube.parse(url)
            Platform.INSTAGRAM -> instagram.parse(url)
            Platform.FACEBOOK -> facebook.parse(url)
            Platform.UNKNOWN -> throw ParseException("Plataforma ainda não suportada")
        }
    }
}
