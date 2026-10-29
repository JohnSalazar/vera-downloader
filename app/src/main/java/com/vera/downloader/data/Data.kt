package com.vera.downloader.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.vera.downloader.VeraApp
import com.vera.downloader.core.Http
import com.vera.downloader.parser.ParserChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

object Status {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val ERROR = "ERROR"
}

data class DownloadRecord(
    val id: Long,
    val sourceUrl: String,
    val platform: String,
    val title: String,
    val author: String?,
    val thumb: String?,
    val kind: String,
    val quality: String?,
    val mime: String,
    val fileName: String,
    val localUri: String?,
    val status: String,
    val bytes: Long,
    val total: Long,
    val error: String?,
    val createdAt: Long
) {
    val progress: Float
        get() = if (total > 0) (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val isDone: Boolean get() = status == Status.DONE
    val isRunning: Boolean get() = status == Status.RUNNING || status == Status.QUEUED
}

class HistoryDb(context: Context) : SQLiteOpenHelper(context, "vera.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_url TEXT NOT NULL,
                platform TEXT NOT NULL,
                title TEXT NOT NULL,
                author TEXT,
                thumb TEXT,
                kind TEXT NOT NULL,
                quality TEXT,
                mime TEXT NOT NULL,
                file_name TEXT NOT NULL,
                local_uri TEXT,
                status TEXT NOT NULL,
                bytes INTEGER NOT NULL DEFAULT 0,
                total INTEGER NOT NULL DEFAULT 0,
                error TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(
        sourceUrl: String,
        platform: String,
        title: String,
        author: String?,
        thumb: String?,
        kind: String,
        quality: String?,
        mime: String,
        fileName: String,
        total: Long
    ): Long {
        val values = ContentValues().apply {
            put("source_url", sourceUrl)
            put("platform", platform)
            put("title", title)
            put("author", author)
            put("thumb", thumb)
            put("kind", kind)
            put("quality", quality)
            put("mime", mime)
            put("file_name", fileName)
            put("status", Status.QUEUED)
            put("bytes", 0)
            put("total", total)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow("downloads", null, values)
    }

    fun all(): List<DownloadRecord> {
        val result = mutableListOf<DownloadRecord>()
        readableDatabase.query("downloads", null, null, null, null, null, "created_at DESC")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    result += DownloadRecord(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        sourceUrl = cursor.getString(cursor.getColumnIndexOrThrow("source_url")),
                        platform = cursor.getString(cursor.getColumnIndexOrThrow("platform")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                        thumb = cursor.getString(cursor.getColumnIndexOrThrow("thumb")),
                        kind = cursor.getString(cursor.getColumnIndexOrThrow("kind")),
                        quality = cursor.getString(cursor.getColumnIndexOrThrow("quality")),
                        mime = cursor.getString(cursor.getColumnIndexOrThrow("mime")),
                        fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name")),
                        localUri = cursor.getString(cursor.getColumnIndexOrThrow("local_uri")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        bytes = cursor.getLong(cursor.getColumnIndexOrThrow("bytes")),
                        total = cursor.getLong(cursor.getColumnIndexOrThrow("total")),
                        error = cursor.getString(cursor.getColumnIndexOrThrow("error")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                    )
                }
            }
        return result
    }

    fun updateProgress(id: Long, bytes: Long, total: Long) {
        val values = ContentValues().apply {
            put("status", Status.RUNNING)
            put("bytes", bytes)
            if (total > 0) put("total", total)
        }
        writableDatabase.update("downloads", values, "id = ?", arrayOf(id.toString()))
    }

    fun markDone(id: Long, uri: String) {
        val values = ContentValues().apply {
            put("status", Status.DONE)
            put("local_uri", uri)
            putNull("error")
            put("bytes", 1)
            put("total", 1)
        }
        writableDatabase.update("downloads", values, "id = ?", arrayOf(id.toString()))
    }

    fun markError(id: Long, message: String) {
        val values = ContentValues().apply {
            put("status", Status.ERROR)
            put("error", message)
        }
        writableDatabase.update("downloads", values, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long): String? {
        val uri = readableDatabase.query(
            "downloads", arrayOf("local_uri"), "id = ?", arrayOf(id.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        writableDatabase.delete("downloads", "id = ?", arrayOf(id.toString()))
        return uri
    }

    fun clearFinished() {
        writableDatabase.delete("downloads", "status IN (?, ?)", arrayOf(Status.DONE, Status.ERROR))
    }

    fun findLatest(sourceUrl: String, kind: String, quality: String?): DownloadRecord? {
        readableDatabase.query(
            "downloads",
            null,
            "source_url = ? AND kind = ? AND IFNULL(quality, '') = ?",
            arrayOf(sourceUrl, kind, quality ?: ""),
            null,
            null,
            "id DESC",
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return DownloadRecord(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                sourceUrl = cursor.getString(cursor.getColumnIndexOrThrow("source_url")),
                platform = cursor.getString(cursor.getColumnIndexOrThrow("platform")),
                title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                author = cursor.getString(cursor.getColumnIndexOrThrow("author")),
                thumb = cursor.getString(cursor.getColumnIndexOrThrow("thumb")),
                kind = cursor.getString(cursor.getColumnIndexOrThrow("kind")),
                quality = cursor.getString(cursor.getColumnIndexOrThrow("quality")),
                mime = cursor.getString(cursor.getColumnIndexOrThrow("mime")),
                fileName = cursor.getString(cursor.getColumnIndexOrThrow("file_name")),
                localUri = cursor.getString(cursor.getColumnIndexOrThrow("local_uri")),
                status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                bytes = cursor.getLong(cursor.getColumnIndexOrThrow("bytes")),
                total = cursor.getLong(cursor.getColumnIndexOrThrow("total")),
                error = cursor.getString(cursor.getColumnIndexOrThrow("error")),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
            )
        }
    }

    fun resetForRetry(id: Long) {
        val values = ContentValues().apply {
            put("status", Status.QUEUED)
            put("bytes", 0)
            put("total", 0)
            putNull("error")
            putNull("local_uri")
        }
        writableDatabase.update("downloads", values, "id = ?", arrayOf(id.toString()))
    }
}

class HistoryRepository(private val db: HistoryDb) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val state: StateFlow<List<DownloadRecord>> = _state.asStateFlow()
    private val lastRefresh = AtomicLong(0)

    init {
        refresh()
    }

    fun refresh() {
        scope.launch { _state.value = db.all() }
    }

    fun insert(
        sourceUrl: String,
        platform: String,
        title: String,
        author: String?,
        thumb: String?,
        kind: String,
        quality: String?,
        mime: String,
        fileName: String,
        total: Long
    ): Long {
        val id = db.insert(
            sourceUrl, platform, title, author, thumb, kind, quality, mime, fileName, total
        )
        refresh()
        return id
    }

    fun progress(id: Long, bytes: Long, total: Long) {
        scope.launch {
            db.updateProgress(id, bytes, total)
            val now = System.currentTimeMillis()
            if (now - lastRefresh.get() > 400) {
                lastRefresh.set(now)
                _state.value = db.all()
            }
        }
    }

    fun done(id: Long, uri: String) {
        scope.launch {
            db.markDone(id, uri)
            _state.value = db.all()
        }
    }

    fun error(id: Long, message: String) {
        scope.launch {
            db.markError(id, message)
            _state.value = db.all()
        }
    }

    fun delete(id: Long): String? {
        val uri = db.delete(id)
        refresh()
        return uri
    }

    fun clearFinished() {
        db.clearFinished()
        refresh()
    }

    fun findLatest(sourceUrl: String, kind: String, quality: String?): DownloadRecord? =
        db.findLatest(sourceUrl, kind, quality)

    fun requeue(id: Long) {
        db.resetForRetry(id)
        refresh()
    }
}

data class SettingsState(
    val autoDownload: Boolean = false,
    val wifiOnly: Boolean = false,
    val hdPreferred: Boolean = true,
    val theme: String = "system",
    val language: String = "system",
    val overlayEnabled: Boolean = false,
    val premium: Boolean = false
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("vera_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun read(): SettingsState = SettingsState(
        autoDownload = prefs.getBoolean("auto_download", false),
        wifiOnly = prefs.getBoolean("wifi_only", false),
        hdPreferred = prefs.getBoolean("hd_preferred", true),
        theme = prefs.getString("theme", "system") ?: "system",
        language = prefs.getString("language", "system") ?: "system",
        overlayEnabled = prefs.getBoolean("overlay_enabled", false),
        premium = prefs.getBoolean("premium", false)
    )

    fun setAutoDownload(value: Boolean) = update { it.copy(autoDownload = value) }
    fun setWifiOnly(value: Boolean) = update { it.copy(wifiOnly = value) }
    fun setHdPreferred(value: Boolean) = update { it.copy(hdPreferred = value) }
    fun setTheme(value: String) = update { it.copy(theme = value) }
    fun setLanguage(value: String) = update { it.copy(language = value) }
    fun setOverlayEnabled(value: Boolean) = update { it.copy(overlayEnabled = value) }
    fun setPremium(value: Boolean) = update { it.copy(premium = value) }

    private fun update(block: (SettingsState) -> SettingsState) {
        val next = block(_state.value)
        prefs.edit()
            .putBoolean("auto_download", next.autoDownload)
            .putBoolean("wifi_only", next.wifiOnly)
            .putBoolean("hd_preferred", next.hdPreferred)
            .putString("theme", next.theme)
            .putString("language", next.language)
            .putBoolean("overlay_enabled", next.overlayEnabled)
            .putBoolean("premium", next.premium)
            .apply()
        _state.value = next
    }
}

object Graph {
    lateinit var app: VeraApp
        private set

    val http: Http by lazy { Http() }
    val settings: Settings by lazy { Settings(app) }
    val history: HistoryRepository by lazy { HistoryRepository(HistoryDb(app)) }
    val parsers: ParserChain by lazy { ParserChain(http) }

    fun init(application: VeraApp) {
        app = application
    }
}
