package com.vera.downloader.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.vera.downloader.MainActivity
import com.vera.downloader.VeraApp
import com.vera.downloader.data.Graph
import com.vera.downloader.i18n.I18n
import kotlin.math.abs

/**
 * Bolha flutuante. Toque abre a Activity de colar link; arraste para o X para desativar.
 */
class TikTokOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var removeTarget: View? = null
    private var removeParams: WindowManager.LayoutParams? = null
    private var overRemove = false

    override fun onCreate() {
        super.onCreate()
        if (!Graph.settings.state.value.overlayEnabled) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)
        startForeground(NOTIFICATION_ID, notification())
        if (!Settings.canDrawOverlays(this)) {
            toast("Autorize a sobreposição para usar a bolha")
            stopSelf()
            return
        }
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW) {
            if (Graph.settings.state.value.overlayEnabled && Settings.canDrawOverlays(this)) {
                addBubble()
            }
            return START_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeBubble()
        hideRemoveTarget()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, VeraApp.CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(tr("Vera ativo"))
            .setContentText(tr("Toque na bolha para baixar sem sair do TikTok"))
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun addBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return
        val size = dp(58)
        val view = FrameLayout(this).apply {
            background = oval(Color.parseColor("#FE2B54"))
            addView(
                ImageView(this@TikTokOverlayService).apply {
                    setImageResource(android.R.drawable.stat_sys_download)
                    setColorFilter(Color.WHITE)
                    layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
                }
            )
        }
        val layoutParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(12)
            y = resources.displayMetrics.heightPixels / 2
        }
        params = layoutParams
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        dragged = true
                        layoutParams.x = startX + dx
                        layoutParams.y = startY + dy
                        windowManager.updateViewLayout(view, layoutParams)
                        if (layoutParams.y > resources.displayMetrics.heightPixels * 0.55) {
                            showRemoveTarget()
                            overRemove = isOverRemove(layoutParams, size)
                            setTargetHover(overRemove)
                        } else {
                            hideRemoveTarget()
                            overRemove = false
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (overRemove) {
                        Graph.settings.setOverlayEnabled(false)
                        toast("Bolha desativada")
                        stopSelf()
                    } else {
                        hideRemoveTarget()
                        if (!dragged) openDownloader()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideRemoveTarget()
                    true
                }
                else -> false
            }
        }
        windowManager.addView(view, layoutParams)
        bubble = view
    }

    private fun openDownloader() {
        removeBubble()
        startActivity(
            Intent(this, OverlayDownloadActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun showRemoveTarget() {
        if (removeTarget != null) return
        val width = dp(220)
        val height = dp(44)
        val container = FrameLayout(this).apply {
            background = rounded(Color.parseColor("#D32F2F"), 22f)
        }
        container.addView(TextView(this).apply {
            text = "✕  " + tr("REMOVER")
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        })
        val layoutParams = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(12)
        }
        windowManager.addView(container, layoutParams)
        removeTarget = container
        removeParams = layoutParams
    }
    private fun hideRemoveTarget() {
        removeTarget?.let { runCatching { windowManager.removeView(it) } }
        removeTarget = null
        removeParams = null
        overRemove = false
    }

    private fun setTargetHover(hover: Boolean) {
        removeTarget?.background = rounded(
            Color.parseColor(if (hover) "#B71C1C" else "#D32F2F"), 22f
        )
        removeTarget?.scaleX = if (hover) 1.12f else 1f
        removeTarget?.scaleY = if (hover) 1.12f else 1f
    }

    private fun isOverRemove(layoutParams: WindowManager.LayoutParams, size: Int): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        return layoutParams.y + size > screenHeight - dp(150)
    }
    private fun removeBubble() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        params = null
    }

    private fun tr(message: String): String = I18n.t(this, message)

    private fun toast(message: String) {
        Toast.makeText(this, tr(message), Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        setColor(color)
    }

    companion object {
        const val ACTION_SHOW = "com.vera.downloader.overlay.SHOW"
        private const val NOTIFICATION_ID = 4202
    }
}
