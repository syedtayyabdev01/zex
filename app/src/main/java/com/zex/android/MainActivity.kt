package com.zex.android

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var glassBar: LinearLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val homeUrl = "https://zxcstream.com/"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 15, 18)
        window.navigationBarColor = Color.rgb(15, 15, 18)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(15, 15, 18)) }
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(15, 15, 18))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            isFocusable = true
            isFocusableInTouchMode = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme != "https") return true
                    if (!request.isForMainFrame) return false
                    val host = uri.host.orEmpty().lowercase()
                    if (host == "zxcstream.com" || host.endsWith(".zxcstream.com")) return false
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) { }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@MainActivity.progress.setProgress(newProgress)
                    this@MainActivity.progress.setVisibility(if (newProgress in 1..99) View.VISIBLE else View.GONE)
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (customView != null) { callback.onCustomViewHidden(); return }
                    customView = view
                    customViewCallback = callback
                    root.addView(view, FrameLayout.LayoutParams(-1, -1))
                    glassBar.visibility = View.GONE
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                }
                override fun onHideCustomView() { hideCustomVideo() }
            }
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 9, 20))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        }
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP))
        glassBar = createGlassBar()
        val margin = dp(24)
        val barParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(62), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        barParams.setMargins(margin, dp(20), margin, 0)
        root.addView(glassBar, barParams)
        setContentView(root)
        setupNotifications()
        web.loadUrl(homeUrl)
    }

    private fun createGlassBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val compact = resources.configuration.screenWidthDp < 600
        setPadding(dp(if (compact) 10 else 14), dp(6), dp(if (compact) 10 else 14), dp(6))
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(142, 158, 159, 166), Color.argb(125, 45, 45, 52), Color.argb(148, 20, 20, 25))
        ).apply { cornerRadius = dp(32).toFloat(); setStroke(dp(1), Color.argb(150, 255, 255, 255)) }
        elevation = dp(5).toFloat()
        val brand = TextView(this@MainActivity).apply {
            text = "ZEX"; textSize = 24f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(229, 9, 20)); gravity = Gravity.CENTER
            contentDescription = "ZEX home"
            setOnClickListener { web.loadUrl(homeUrl) }
            isFocusable = true; isFocusableInTouchMode = false
        }
        addView(brand, LinearLayout.LayoutParams(dp(if (compact) 54 else 70), -1))
        val divider = View(this@MainActivity).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) }
        addView(divider, LinearLayout.LayoutParams(dp(1), dp(30)).apply { marginStart = dp(8); marginEnd = dp(8) })
        addView(glassAction("⌂", "Home", compact) { web.loadUrl(homeUrl) })
        addView(glassAction("↻", "Reload", compact) { web.reload() })
        addView(glassAction("←", "Back", compact) { if (web.canGoBack()) web.goBack() else toast("Press Back again to exit") })
        if (!compact) addView(TextView(this@MainActivity).apply {
            text = "ANDROID  ·  V0.2"; textSize = 11f; letterSpacing = .12f
            setTextColor(Color.rgb(225, 225, 230)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -1).apply { marginStart = dp(12); marginEnd = dp(8) })
    }

    private fun glassAction(symbol: String, label: String, compact: Boolean, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        minimumWidth = dp(if (compact) 42 else 74)
        setPadding(dp(if (compact) 8 else 10), 0, dp(if (compact) 8 else 10), 0)
        contentDescription = label; isFocusable = true; isFocusableInTouchMode = false
        background = rounded(Color.TRANSPARENT, dp(20), Color.TRANSPARENT)
        addView(TextView(this@MainActivity).apply {
            text = symbol; textSize = 21f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-2, -1))
        if (!compact) addView(TextView(this@MainActivity).apply {
            text = label; textSize = 13f; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-2, -1).apply { marginStart = dp(7) })
        setOnClickListener { action() }
        setOnFocusChangeListener { view, focused ->
            view.background = rounded(if (focused) Color.argb(72, 255, 255, 255) else Color.TRANSPARENT,
                dp(20), if (focused) Color.argb(210, 255, 255, 255) else Color.TRANSPARENT)
            view.scaleX = if (focused) 1.04f else 1f; view.scaleY = if (focused) 1.04f else 1f
        }
    }

    private fun setupNotifications() {
        ZexNotifications.createChannel(this)
        val reminder = PeriodicWorkRequestBuilder<DailyNotificationWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "zex_daily_reminder_and_update_check", ExistingPeriodicWorkPolicy.KEEP, reminder
        )
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val preferences = getSharedPreferences("zex_notifications", MODE_PRIVATE)
            if (!preferences.getBoolean("permission_requested", false)) {
                preferences.edit().putBoolean("permission_requested", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1204)
            }
        }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius = radius.toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun hideCustomVideo() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        glassBar.visibility = View.VISIBLE
        customViewCallback?.onCustomViewHidden(); customViewCallback = null
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onBackPressed() {
        if (customView != null) { hideCustomVideo(); return }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            web.evaluateJavascript("document.querySelector('video')?.play()", null); return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            web.evaluateJavascript("document.querySelector('video')?.pause()", null); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
