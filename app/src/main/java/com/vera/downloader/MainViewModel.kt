package com.vera.downloader

import android.app.Application
import android.app.WallpaperManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.vera.downloader.core.Feedback
import com.vera.downloader.core.LinkDetector
import com.vera.downloader.core.MediaItem
import com.vera.downloader.core.MediaKind
import com.vera.downloader.core.ParseResult
import com.vera.downloader.data.DownloadRecord
import com.vera.downloader.data.Graph
import com.vera.downloader.data.SettingsState
import com.vera.downloader.download.Downloads
import com.vera.downloader.download.EnqueueResult
import com.vera.downloader.download.EnqueueState
import com.vera.downloader.overlay.TikTokOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Preview : Screen
    data object History : Screen
    data object Settings : Screen
    data object Vip : Screen
    data object HowTo : Screen
    data object Feedback : Screen
}

data class UiState(
    val screen: Screen = Screen.Home,
    val input: String = "",
    val loading: Boolean = false,
    val result: ParseResult? = null,
    val error: String? = null,
    val history: List<DownloadRecord> = emptyList(),
    val settings: SettingsState = SettingsState(),
    val toast: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            Graph.settings.state.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            Graph.history.state.collect { history ->
                _state.update { it.copy(history = history) }
            }
        }
    }

    fun onInput(value: String) = _state.update { it.copy(input = value) }

    fun paste(value: String) {
        _state.update { it.copy(input = value.trim()) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearToast() = _state.update { it.copy(toast = null) }

    fun go(screen: Screen) = _state.update { it.copy(screen = screen, error = null) }

    fun back() {
        if (_state.value.screen != Screen.Home) {
            _state.update { it.copy(screen = Screen.Home) }
        }
    }

    fun parse(text: String? = null, allowAutoDownload: Boolean = false) {
        val value = (text ?: _state.value.input).trim()
        if (value.isEmpty()) return
        val urls = LinkDetector.extractAll(value)
        if (urls.size > 1) {
            batchDownload(urls)
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = Graph.parsers.parse(value)
                _state.update { it.copy(loading = false, result = result, screen = Screen.Preview) }
                if (allowAutoDownload && _state.value.settings.autoDownload) {
                    val autoItem = result.items.firstOrNull {
                        it.kind == MediaKind.VIDEO && it.slideshowImages.isEmpty()
                    } ?: result.items.firstOrNull { it.kind == MediaKind.IMAGE }
                        ?: result.items.firstOrNull()
                    if (autoItem != null) {
                        val enqueued = Downloads.enqueue(getApplication(), result, autoItem)
                        _state.update {
                            it.copy(toast = enqueueToast(enqueued, automatic = true))
                        }
                    }
                }
            } catch (throwable: Throwable) {
                val raw = throwable.message ?: "Não foi possível analisar o link"
                val message = if (raw.contains("timeout", ignoreCase = true) ||
                    raw.contains("timed out", ignoreCase = true)
                ) {
                    "A plataforma demorou para responder. Tente novamente."
                } else {
                    raw
                }
                _state.update {
                    it.copy(loading = false, error = message)
                }
            }
        }
    }

    private fun batchDownload(urls: List<String>) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            var queued = 0
            var skipped = 0
            var failed = 0
            for (url in urls) {
                try {
                    val result = Graph.parsers.parse(url)
                    val item = result.items.firstOrNull { it.kind == MediaKind.VIDEO }
                        ?: result.items.firstOrNull()
                    if (item != null) {
                        when (Downloads.enqueue(getApplication(), result, item).state) {
                            EnqueueState.NEW -> queued++
                            else -> skipped++
                        }
                    } else {
                        failed++
                    }
                } catch (_: Throwable) {
                    failed++
                }
            }
            _state.update {
                it.copy(
                    loading = false,
                    screen = Screen.History,
                    toast = "$queued adicionados, $skipped já existentes, $failed falharam"
                )
            }
        }
    }

    fun onShared(text: String) {
        val first = LinkDetector.extract(text) ?: return
        _state.update { it.copy(input = text.trim(), screen = Screen.Home) }
        parse(if (LinkDetector.extractAll(text).size > 1) text else first, allowAutoDownload = true)
    }

    fun download(item: MediaItem) {
        val result = _state.value.result ?: return
        val enqueued = Downloads.enqueue(getApplication(), result, item)
        _state.update { it.copy(toast = enqueueToast(enqueued)) }
    }

    fun downloadItems(items: List<MediaItem>) {
        val result = _state.value.result ?: return
        if (items.isEmpty()) return
        val results = items.map { Downloads.enqueue(getApplication(), result, it) }
        val added = results.count { it.state == EnqueueState.NEW }
        val existing = results.size - added
        _state.update {
            it.copy(
                toast = when {
                    added > 0 && existing > 0 -> "$added adicionados, $existing já existentes"
                    added > 0 -> "$added downloads adicionados"
                    else -> "Tudo já baixado ou na fila"
                }
            )
        }
    }

    fun downloadFirst(kind: MediaKind) {
        val result = _state.value.result ?: return
        val item = result.items.firstOrNull { it.kind == kind }
        if (item == null) {
            _state.update { it.copy(toast = "Este vídeo não tem ${if (kind == MediaKind.AUDIO) "áudio" else "essa opção"}") }
            return
        }
        download(item)
    }

    fun downloadAll() {
        val result = _state.value.result ?: return
        if (result.items.isEmpty()) return
        val results = result.items.map { Downloads.enqueue(getApplication(), result, it) }
        val added = results.count { it.state == EnqueueState.NEW }
        val existing = results.size - added
        _state.update {
            it.copy(
                toast = when {
                    added > 0 && existing > 0 -> "$added adicionados, $existing já existentes"
                    added > 0 -> "$added downloads adicionados"
                    else -> "Tudo já baixado ou na fila"
                }
            )
        }
    }

    fun retryAllErrors() {
        val urls = _state.value.history
            .filter { it.status == "ERROR" }
            .map { it.sourceUrl }
            .distinct()
        if (urls.isEmpty()) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            var queued = 0
            for (url in urls) {
                try {
                    val result = Graph.parsers.parse(url)
                    val item = result.items.firstOrNull { it.kind == MediaKind.VIDEO }
                        ?: result.items.firstOrNull()
                    if (item != null) {
                        if (Downloads.enqueue(getApplication(), result, item).state == EnqueueState.NEW) {
                            queued++
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            _state.update {
                it.copy(loading = false, toast = "$queued tentativas adicionadas à fila")
            }
        }
    }

    private fun enqueueToast(result: EnqueueResult, automatic: Boolean = false): String =
        when (result.state) {
            EnqueueState.DONE -> "Este item já foi baixado"
            EnqueueState.QUEUED -> "Este download já está na fila"
            EnqueueState.NEW -> if (automatic) {
                "Download automático iniciado"
            } else {
                "Download adicionado à fila"
            }
        }

    fun showToast(message: String) {
        _state.update { it.copy(toast = message) }
    }

    fun openTikTok() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun shareApp() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Conheça o Vera Downloader: baixe vídeos do TikTok, YouTube, Instagram e Facebook."
            )
        }
        val chooser = Intent.createChooser(send, "Recomendar o Vera")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(chooser) }
    }

    fun downloadById(record: DownloadRecord) {
        if (!record.isDone || record.localUri == null) return
        open(record)
    }

    fun open(record: DownloadRecord) {
        val uri = record.localUri?.let { Uri.parse(it) } ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, record.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
            .onFailure { _state.update { state -> state.copy(toast = "Nenhum app para abrir este arquivo") } }
    }

    fun share(record: DownloadRecord) {
        val uri = record.localUri?.let { Uri.parse(it) } ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType(record.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "Compartilhar")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(chooser) }
            .onFailure { _state.update { state -> state.copy(toast = "Não foi possível compartilhar") } }
    }

    fun delete(record: DownloadRecord) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (record.isRunning) Downloads.cancel(app, record.id)
            val uri = record.localUri?.let { Uri.parse(it) }
            if (uri != null) {
                runCatching { app.contentResolver.delete(uri, null, null) }
            }
            Graph.history.delete(record.id)
            _state.update { it.copy(toast = "Download removido") }
        }
    }

    fun clearFinished() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            Graph.history.state.value.filter { it.isDone || it.status == "ERROR" }.forEach { record ->
                record.localUri?.let { runCatching { app.contentResolver.delete(Uri.parse(it), null, null) } }
            }
            Graph.history.clearFinished()
        }
    }

    fun setWallpaper(record: DownloadRecord) {
        if (record.kind != MediaKind.IMAGE.name) return
        val uri = record.localUri?.let { Uri.parse(it) } ?: return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        WallpaperManager.getInstance(app).setStream(stream)
                    }
                }
            }
            _state.update {
                it.copy(
                    toast = if (result.isSuccess) "Papel de parede definido"
                    else "Não foi possível definir o papel de parede"
                )
            }
        }
    }

    fun setRingtone(record: DownloadRecord) {
        if (record.kind != MediaKind.AUDIO.name) return
        val uri = record.localUri?.let { Uri.parse(it) } ?: return
        val app = getApplication<Application>()
        if (!Settings.System.canWrite(app)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(intent) }
            _state.update { it.copy(toast = "Autorize alterar configurações do sistema") }
            return
        }
        runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE, uri)
        }
        _state.update { it.copy(toast = "Toque definido") }
    }

    fun openInBrowser() {
        val url = _state.value.result?.sourceUrl ?: _state.value.input
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun retry(record: DownloadRecord) {
        _state.update {
            it.copy(input = record.sourceUrl, loading = true, error = null, screen = Screen.History)
        }
        viewModelScope.launch {
            try {
                val result = Graph.parsers.parse(record.sourceUrl)
                val item = result.items.firstOrNull { it.kind == MediaKind.VIDEO }
                    ?: result.items.firstOrNull()
                if (item == null) error("Nenhuma mídia disponível")
                val enqueued = Downloads.enqueue(getApplication(), result, item)
                _state.update {
                    it.copy(loading = false, toast = enqueueToast(enqueued))
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        toast = throwable.message ?: "Falha ao tentar novamente"
                    )
                }
            }
        }
    }

    fun sendFeedback(category: String, message: String, contact: String) {
        val body = buildString {
            appendLine("Tipo: $category")
            appendLine()
            appendLine(message)
            if (contact.isNotBlank()) {
                appendLine()
                appendLine("Contato: $contact")
            }
            appendLine()
            appendLine("Versão: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE}")
        }
        val uri = Uri.parse(
            "mailto:${Uri.encode(Feedback.recipient())}" +
                "?subject=${Uri.encode("[Vera] $category")}" +
                "&body=${Uri.encode(body)}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val app = getApplication<Application>()
        if (intent.resolveActivity(app.packageManager) == null) {
            _state.update { it.copy(toast = "Nenhum app de e-mail encontrado") }
            return
        }
        runCatching { app.startActivity(intent) }
            .onFailure {
                _state.update { state -> state.copy(toast = "Não foi possível abrir o e-mail") }
            }
    }

    fun openSource(record: DownloadRecord) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(record.sourceUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun openAuthor(author: String) {
        val record = _state.value.history.firstOrNull { it.author == author } ?: return
        val url = if (record.platform.equals("TIKTOK", ignoreCase = true)) {
            "https://www.tiktok.com/@$author"
        } else {
            record.sourceUrl
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun setAutoDownload(value: Boolean) = Graph.settings.setAutoDownload(value)
    fun setWifiOnly(value: Boolean) = Graph.settings.setWifiOnly(value)
    fun setHdPreferred(value: Boolean) = Graph.settings.setHdPreferred(value)
    fun setTheme(value: String) = Graph.settings.setTheme(value)
    fun setLanguage(value: String) = Graph.settings.setLanguage(value)

    fun setOverlayEnabled(value: Boolean) {
        Graph.settings.setOverlayEnabled(value)
        val app = getApplication<Application>()
        val intent = Intent(app, TikTokOverlayService::class.java)
        if (value) {
            runCatching { ContextCompat.startForegroundService(app, intent) }
        } else {
            app.stopService(intent)
        }
    }

    fun subscribeDemo() {
        Graph.settings.setPremium(true)
        _state.update { it.copy(toast = "VIP ativado no modo demonstração") }
    }

    fun cancelDemo() {
        Graph.settings.setPremium(false)
        _state.update { it.copy(toast = "Assinatura de demonstração cancelada") }
    }
}
