package com.vera.downloader.overlay

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.vera.downloader.core.LinkDetector
import com.vera.downloader.i18n.I18n
import kotlinx.coroutines.launch

/**
 * Activity translúcida usada pela bolha. Por estar em primeiro plano, consegue ler
 * a área de transferência no Android 10+ (a janela de overlay nem sempre consegue).
 */
class OverlayDownloadActivity : ComponentActivity() {
    private lateinit var edit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0))
            setOnClickListener { finish() }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = rounded(Color.WHITE, 20f)
            elevation = dp(10).toFloat()
            setOnClickListener { }
        }
        card.addView(TextView(this).apply {
            text = I18n.t(this@OverlayDownloadActivity, "Baixar sem sair do TikTok")
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = I18n.t(
                this@OverlayDownloadActivity,
                "Copie o link no TikTok e toque em Baixar."
            )
            setTextColor(Color.parseColor("#666666"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(10))
        })
        edit = EditText(this).apply {
            hint = I18n.t(this@OverlayDownloadActivity, "Cole o link do TikTok")
            setTextColor(Color.parseColor("#1A1A1A"))
            setHintTextColor(Color.parseColor("#999999"))
            maxLines = 3
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.parseColor("#F4F4F4"), 12f)
        }
        card.addView(edit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        fun actionButton(label: String, background: Int, foreground: Int, action: () -> Unit) =
            Button(this).apply {
                text = I18n.t(this@OverlayDownloadActivity, label)
                textSize = 14f
                setTextColor(foreground)
                this.background = rounded(background, 12f)
                setOnClickListener { action() }
            }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        actions.addView(
            actionButton("Colar", Color.parseColor("#EEEEEE"), Color.parseColor("#666666")) {
                pasteFromClipboard()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dp(8), 0)
            }
        )
        actions.addView(
            actionButton("Fechar", Color.parseColor("#EEEEEE"), Color.parseColor("#666666")) {
                finish()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(actions)
        card.addView(
            actionButton("Baixar", Color.parseColor("#FE2B54"), Color.WHITE) {
                download()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )
        val width = (resources.displayMetrics.widthPixels - dp(48))
            .coerceAtMost(dp(380))
        root.addView(card, FrameLayout.LayoutParams(
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (edit.text.isNullOrBlank()) {
            clipboardTikTokUrl()?.let { edit.setText(it) }
        }
    }

    override fun onDestroy() {
        runCatching {
            startService(
                Intent(this, TikTokOverlayService::class.java)
                    .setAction(TikTokOverlayService.ACTION_SHOW)
            )
        }
        super.onDestroy()
    }

    private fun pasteFromClipboard() {
        val value = clipboardTikTokUrl()
        if (value.isNullOrBlank()) {
            edit.setText("")
            toast("Copie um link do TikTok primeiro")
        } else {
            edit.setText(value)
            edit.setSelection(edit.text.length)
        }
    }

    private fun clipboardTikTokUrl(): String? = readClipboard()?.let { raw ->
        LinkDetector.extractAll(raw).firstOrNull {
            it.contains("tiktok.com", ignoreCase = true)
        }
    }

    private fun download() {
        val text = edit.text.toString()
        if (text.isBlank()) {
            pasteFromClipboard()
            return
        }
        lifecycleScope.launch {
            val result = OverlayDownloader.enqueue(this@OverlayDownloadActivity, text)
            val message = result.getOrElse { it.message ?: "Falha ao baixar" }
            toast(message)
            if (result.isSuccess) finish()
        }
    }

    private fun readClipboard(): String? = runCatching {
        val manager = getSystemService(ClipboardManager::class.java)
        if (manager.hasPrimaryClip()) {
            manager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        } else {
            null
        }
    }.getOrNull()

    private fun toast(message: String) {
        Toast.makeText(this, I18n.t(this, message), Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(color)
        }
}
