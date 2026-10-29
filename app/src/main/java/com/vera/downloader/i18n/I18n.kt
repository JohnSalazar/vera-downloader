package com.vera.downloader.i18n

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import org.json.JSONObject
import java.util.Locale

data class AppLanguage(val code: String, val label: String)

object I18n {
    const val SYSTEM = "system"
    const val DEFAULT = "en"

    /** Idiomas mais usados em apps, com nomes nativos. */
    val LANGUAGES = listOf(
        AppLanguage("system", "Sistema / System"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("en", "English"),
        AppLanguage("es", "Español"),
        AppLanguage("fr", "Français"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("it", "Italiano"),
        AppLanguage("ru", "Русский"),
        AppLanguage("ar", "العربية"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("id", "Bahasa Indonesia"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("zh-CN", "简体中文"),
        AppLanguage("zh-TW", "繁體中文"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("th", "ไทย"),
        AppLanguage("pl", "Polski"),
        AppLanguage("nl", "Nederlands"),
        AppLanguage("bn", "বাংলা"),
        AppLanguage("ur", "اردو"),
        AppLanguage("fa", "فارسی"),
        AppLanguage("ms", "Bahasa Melayu"),
        AppLanguage("fil", "Filipino"),
        AppLanguage("uk", "Українська"),
        AppLanguage("ro", "Română"),
        AppLanguage("el", "Ελληνικά"),
        AppLanguage("cs", "Čeština"),
        AppLanguage("sv", "Svenska"),
        AppLanguage("he", "עברית")
    )

    @Volatile
    private var dictionaries: Map<String, Map<String, String>> = emptyMap()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val map = mutableMapOf<String, Map<String, String>>()
            for (language in LANGUAGES) {
                if (language.code == SYSTEM) continue
                val json = runCatching {
                    context.assets.open("i18n/${language.code}.json")
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                }.getOrNull() ?: continue
                val obj = runCatching { JSONObject(json) }.getOrNull() ?: continue
                val values = mutableMapOf<String, String>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    values[key] = obj.optString(key)
                }
                map[language.code] = values
            }
            dictionaries = map
            loaded = true
        }
    }

    fun resolve(setting: String): String {
        if (setting != SYSTEM) {
            return if (setting == "pt") "pt-BR" else setting
        }
        val locale = Locale.getDefault()
        val language = locale.language.lowercase(Locale.ROOT)
        val country = locale.country.uppercase(Locale.ROOT)
        return when {
            language == "pt" -> "pt-BR"
            language == "zh" -> if (country == "TW" || country == "HK") "zh-TW" else "zh-CN"
            language == "he" -> "he"
            language == "in" -> "id"
            language == "iw" -> "he"
            LANGUAGES.any { it.code == language } -> language
            else -> DEFAULT
        }
    }

    fun isRtl(code: String): Boolean = code in setOf("ar", "he", "fa", "ur")

    fun languageLabel(code: String): String =
        LANGUAGES.firstOrNull { it.code == code }?.label ?: code

    fun translate(lang: String, text: String): String {
        if (lang == "pt-BR" || text.isBlank()) return text
        val dictionary = dictionaries[lang] ?: return text

        dictionary[text]?.let { return it }

        Regex("^(\\d+) adicionados à fila, (\\d+) falharam$").find(text)?.let { match ->
            val template = dictionary["__batch_done"] ?: "%d adicionados à fila, %d falharam"
            return runCatching {
                String.format(template, match.groupValues[1], match.groupValues[2])
            }.getOrDefault(text)
        }

        val dynamic = listOf(
            Triple(Regex("^(\\d+) downloads$"), "__downloads", "%d downloads"),
            Triple(Regex("^Vídeo (.+)$"), "__video", "Vídeo %s"),
            Triple(Regex("^Abrir no (.+)$"), "__open_in", "Abrir no %s"),
            Triple(Regex("^via (.+)$"), "__via", "via %s"),
            Triple(Regex("^Versão (.+)$"), "__version", "Versão %s"),
            Triple(Regex("^V(.+) \\(debug\\)$"), "__debug_version", "V%s (debug)"),
            Triple(Regex("^(\\d+) downloads adicionados$"), "__added", "%d downloads adicionados"),
            Triple(Regex("^(\\d+) tentativas adicionadas à fila$"), "__attempts", "%d tentativas adicionadas à fila"),
            Triple(Regex("^Este vídeo não tem (.+)$"), "__no_media", "Este vídeo não tem %s"),
            Triple(Regex("^Atualização (.+)$"), "__update_title", "Atualização %s"),
            Triple(
                Regex("^Você já está na versão mais recente \\((.+)\\)\\.$"),
                "__latest",
                "Você já está na versão mais recente (%s)."
            )
        )
        for ((regex, key, fallback) in dynamic) {
            val match = regex.find(text)
            if (match != null) {
                val template = dictionary[key] ?: fallback
                return runCatching { String.format(template, match.groupValues[1]) }
                    .getOrDefault(text)
            }
        }

        if (text.contains(" • ")) {
            return text.split(" • ").joinToString(" • ") { part ->
                dictionary[part] ?: part
            }
        }

        var result = text
        listOf("sem marca d'água", "com áudio", "somente áudio").forEach { marker ->
            dictionary[marker]?.let { translated ->
                result = result.replace(marker, translated)
            }
        }
        return result
    }

    fun t(context: Context, text: String): String {
        load(context)
        val setting = com.vera.downloader.data.Settings(context).state.value.language
        return translate(resolve(setting), text)
    }

    fun t(lang: String, text: String): String = translate(lang, text)
}

val LocalLang = compositionLocalOf { "pt-BR" }
