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
import android.os.Handler
import android.os.Looper
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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var glassBar: LinearLayout
    private lateinit var playbackFallbackButton: TextView
    private lateinit var networkErrorPanel: LinearLayout
    private lateinit var networkErrorMessage: TextView
    private val pageLoadTimeout = Runnable { showNetworkError("The website took too long to respond. Check your internet connection and try again.") }
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideGlassBarRunnable = Runnable { hideGlassBar() }
    private var revealingGlassBarTouch = false
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
            // Keep Chromium's version but remove Android WebView's embedded-app
            // markers so sites that work in Chrome serve the complete page.
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            isFocusable = true
            isFocusableInTouchMode = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    uiHandler.removeCallbacks(pageLoadTimeout)
                    uiHandler.postDelayed(pageLoadTimeout, 30_000)
                    if (::networkErrorPanel.isInitialized) networkErrorPanel.visibility = View.GONE
                    if (::playbackFallbackButton.isInitialized) playbackFallbackButton.visibility = View.GONE
                }

                override fun onPageFinished(view: WebView, url: String) {
                    uiHandler.removeCallbacks(pageLoadTimeout)
                    val host = Uri.parse(url).host.orEmpty().lowercase()
                    if (host == "zxcstream.com" || host.endsWith(".zxcstream.com")) {
                        addGithubToStayConnectedPopup()
                        hideUnsupportedPwaInstallOption()
                        updatePageChrome(url)
                        if (isMovieDetailsPage(Uri.parse(url))) detectPlaybackUnavailable()
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        uiHandler.removeCallbacks(pageLoadTimeout)
                        showNetworkError(error.description?.toString() ?: "Unable to load the page.")
                    }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                    super.onReceivedHttpError(view, request, response)
                    if (request.isForMainFrame && response.statusCode >= 400) {
                        uiHandler.removeCallbacks(pageLoadTimeout)
                        showNetworkError("The website returned HTTP ${response.statusCode}. Please retry in a moment.")
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme != "https") return true
                    if (!request.isForMainFrame) return false
                    val host = uri.host.orEmpty().lowercase()
                    if (host == "zxcstream.com" || host.endsWith(".zxcstream.com")) {
                        if (isMovieDetailsPage(uri)) hideGlassBar()
                        return false
                    }
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
                    uiHandler.removeCallbacks(hideGlassBarRunnable)
                    glassBar.visibility = View.GONE
                    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                }
                override fun onHideCustomView() { hideCustomVideo() }
            }
            setOnTouchListener { _, event ->
                if (customView != null) return@setOnTouchListener false
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        revealingGlassBarTouch = glassBar.visibility != View.VISIBLE && event.y <= dp(18)
                        if (revealingGlassBarTouch) {
                            showGlassBar(requestFocus = false)
                            true
                        } else {
                            hideGlassBar()
                            false
                        }
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        val consumed = revealingGlassBarTouch
                        revealingGlassBarTouch = false
                        consumed
                    }
                    else -> revealingGlassBarTouch
                }
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
        playbackFallbackButton = TextView(this).apply {
            text = "Playback issue? Open this page in Chrome"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = rounded(Color.argb(238, 32, 32, 38), dp(18), Color.argb(150, 255, 255, 255))
            elevation = dp(8).toFloat()
            visibility = View.GONE
            isFocusable = true
            contentDescription = "Open this title in Chrome if playback fails in the app"
            setOnClickListener { openCurrentPageInBrowser() }
        }
        val fallbackParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        fallbackParams.setMargins(dp(20), 0, dp(20), dp(94))
        root.addView(playbackFallbackButton, fallbackParams)
        networkErrorMessage = TextView(this).apply {
            text = "ZEX couldn't connect. Check your connection and try again."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(18), dp(8), dp(18), dp(12))
        }
        networkErrorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = rounded(Color.argb(245, 28, 28, 34), dp(20), Color.argb(150, 255, 255, 255))
            elevation = dp(12).toFloat()
            visibility = View.GONE
            addView(TextView(this@MainActivity).apply {
                text = "Connection problem"
                textSize = 19f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            })
            addView(networkErrorMessage, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(this@MainActivity).apply {
                text = "TRY AGAIN"
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(22), dp(12), dp(22), dp(12))
                background = rounded(Color.rgb(190, 20, 30), dp(14), Color.TRANSPARENT)
                isFocusable = true
                setOnClickListener {
                    visibility = View.GONE
                    web.reload()
                }
            })
        }
        val errorParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER)
        errorParams.setMargins(dp(28), 0, dp(28), 0)
        root.addView(networkErrorPanel, errorParams)
        setContentView(root)
        setupNotifications()
        web.loadUrl(homeUrl)
    }

    private fun showNetworkError(message: String) {
        if (!::networkErrorPanel.isInitialized) return
        networkErrorMessage.text = message.take(240)
        networkErrorPanel.visibility = View.VISIBLE
        progress.visibility = View.GONE
    }

    private fun updatePageChrome(url: String) {
        val uri = Uri.parse(url)
        if (isMovieDetailsPage(uri)) {
            hideGlassBar()
            return
        }
        showGlassBar(requestFocus = false)
        web.evaluateJavascript(
            "!!document.querySelector('video') || /you're watching|main server|backup i/i.test(document.body.innerText || '')"
        ) { result -> if (result == "true") hideGlassBar() }
    }

    private fun isMovieDetailsPage(uri: Uri): Boolean {
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        val first = segments.firstOrNull()?.lowercase() ?: return false
        if (first in setOf("watch", "player", "detail", "details")) return true
        if (first in setOf("movie", "tv", "series") && segments.size > 1) return true
        val hasItemId = uri.queryParameterNames.any { it.lowercase() in setOf("id", "video", "title_id") }
        return hasItemId && first in setOf("movie", "tv", "series", "watch")
    }

    private fun detectPlaybackUnavailable(retriesRemaining: Int = 6) {
        web.evaluateJavascript(
            "(document.body?.innerText || '').toLowerCase().includes('title unavailable') || " +
                "(document.body?.innerText || '').toLowerCase().includes('currently unavailable for playback')"
        ) { result ->
            if (result == "true") {
                playbackFallbackButton.visibility = View.VISIBLE
            } else if (retriesRemaining > 0) {
                uiHandler.postDelayed({ detectPlaybackUnavailable(retriesRemaining - 1) }, 900)
            }
        }
    }

    private fun openCurrentPageInBrowser() {
        val uri = web.url?.let(Uri::parse) ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.chrome"))
        } catch (_: ActivityNotFoundException) {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) { }
        }
    }

    private fun showGlassBar(requestFocus: Boolean) {
        if (customView != null) return
        uiHandler.removeCallbacks(hideGlassBarRunnable)
        glassBar.visibility = View.VISIBLE
        if (requestFocus) glassBar.getChildAt(0)?.requestFocus()
        uiHandler.postDelayed(hideGlassBarRunnable, 2_500)
    }

    private fun hideGlassBar() {
        uiHandler.removeCallbacks(hideGlassBarRunnable)
        if (!::glassBar.isInitialized || customView != null) return
        if (glassBar.hasFocus()) web.requestFocus()
        glassBar.visibility = View.GONE
    }

    private fun addGithubToStayConnectedPopup() {
        val githubUrl = JSONObject.quote("https://github.com/syedtayyabdev01/zex")
        val discordUrl = JSONObject.quote("https://discord.gg/UHq3x5QjH")
        val telegramUrl = JSONObject.quote("https://t.me/+MJvtkm6__Ag3ZWY0")
        val script = """
            (function() {
              if (window.__zexSocialObserver) return;
              function patchPopup() {
                var dialogs = Array.from(document.querySelectorAll('[role="dialog"]'));
                var dialog = dialogs.find(function(node) {
                  return (node.innerText || '').indexOf('Stay Connected') !== -1;
                });
                if (!dialog) return false;
                var links = Array.from(dialog.querySelectorAll('a[href]'));
                var discord = links.find(function(a) { return /discord\.(com|gg)/i.test(a.href); });
                var telegram = links.find(function(a) { return /(t\.me|telegram\.me)/i.test(a.href); });
                var github = links.find(function(a) { return /facebook\.com/i.test(a.href); });
                if (!discord || !telegram || !github) return false;

                discord.href = $discordUrl;
                discord.target = '_blank';
                discord.rel = 'noopener noreferrer';
                discord.setAttribute('aria-label', 'ZEX Discord server');
                telegram.href = $telegramUrl;
                telegram.target = '_blank';
                telegram.rel = 'noopener noreferrer';
                telegram.setAttribute('aria-label', 'ZEX Telegram channel');

                github.href = $githubUrl;
                github.target = '_blank';
                github.rel = 'noopener noreferrer';
                github.title = 'GitHub';
                github.setAttribute('aria-label', 'ZEX GitHub repository');
                github.style.color = '#f5f5f7';
                var label = github.querySelector('span');
                if (label) label.textContent = 'GitHub';
                var icon = github.querySelector('svg');
                if (icon) {
                  icon.setAttribute('viewBox', '0 0 24 24');
                  icon.innerHTML = '<path fill="currentColor" d="M12 .8a11.2 11.2 0 0 0-3.54 21.83c.56.1.76-.24.76-.54v-1.95c-3.13.68-3.79-1.33-3.79-1.33-.5-1.3-1.25-1.65-1.25-1.65-1.02-.7.08-.69.08-.69 1.12.08 1.72 1.16 1.72 1.16 1 1.72 2.63 1.23 3.27.94.1-.71.39-1.2.71-1.48-2.5-.29-5.13-1.25-5.13-5.57 0-1.23.44-2.24 1.16-3.03-.12-.29-.5-1.44.11-3 0 0 .94-.3 3.07 1.15a10.6 10.6 0 0 1 5.6 0c2.12-1.45 3.07-1.15 3.07-1.15.61 1.56.22 2.71.11 3 .72.79 1.15 1.8 1.15 3.03 0 4.33-2.64 5.28-5.15 5.56.4.35.76 1.04.76 2.1v3.11c0 .3.2.65.77.54A11.2 11.2 0 0 0 12 .8z"/>';
                }
                return true;
              }
              var observer = new MutationObserver(function() {
                if (patchPopup()) observer.disconnect();
              });
              window.__zexSocialObserver = observer;
              observer.observe(document.body, { childList: true });
              if (patchPopup()) observer.disconnect();
              window.setTimeout(function() { observer.disconnect(); }, 30000);
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
    }

    private fun hideUnsupportedPwaInstallOption() {
        val script = """
            (function() {
              if (window.__zexInstallFilterInstalled) return;
              window.__zexInstallFilterInstalled = true;
              function hideInstallAction() {
                Array.from(document.querySelectorAll('button')).forEach(function(button) {
                  if ((button.innerText || '').trim().toLowerCase() !== 'install') return;
                  var menu = button.parentElement;
                  var menuText = (menu && menu.innerText || '').toLowerCase();
                  if (!menu || menuText.indexOf('library') < 0 || menuText.indexOf('settings') < 0) return;
                  button.style.display = 'none';
                  button.setAttribute('aria-hidden', 'true');
                  button.tabIndex = -1;
                });
              }
              document.addEventListener('click', function(event) {
                var button = event.target.closest && event.target.closest('button');
                if (button && (button.innerText || '').trim().toLowerCase() === 'more') {
                  window.setTimeout(hideInstallAction, 0);
                }
              }, true);
              window.setTimeout(hideInstallAction, 0);
            })();
        """.trimIndent()
        web.evaluateJavascript(script, null)
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
            text = "ZEX"; textSize = if (compact) 22f else 24f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(229, 9, 20)); gravity = Gravity.CENTER
            contentDescription = "ZEX home"
            setOnClickListener { web.loadUrl(homeUrl) }
            isFocusable = true; isFocusableInTouchMode = false
        }
        addView(brand, LinearLayout.LayoutParams(dp(if (compact) 68 else 80), -1))
        val divider = View(this@MainActivity).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) }
        addView(divider, LinearLayout.LayoutParams(dp(1), dp(30)).apply { marginStart = dp(8); marginEnd = dp(8) })
        addView(glassAction("⌂", "Home", compact) { web.loadUrl(homeUrl) })
        addView(glassAction("↻", "Reload", compact) { web.reload() })
        addView(glassAction("←", "Back", compact) { if (web.canGoBack()) web.goBack() else toast("Press Back again to exit") })
        if (!compact) addView(TextView(this@MainActivity).apply {
            text = "ANDROID  ·  V0.4"; textSize = 11f; letterSpacing = .12f
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
        hideGlassBar()
        customViewCallback?.onCustomViewHidden(); customViewCallback = null
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onBackPressed() {
        if (customView != null) { hideCustomVideo(); return }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && customView == null) {
            showGlassBar(requestFocus = true)
            return true
        }
        if (keyCode in setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER) && !glassBar.hasFocus()) {
            hideGlassBar()
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            web.evaluateJavascript("document.querySelector('video')?.play()", null); return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            web.evaluateJavascript("document.querySelector('video')?.pause()", null); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { uiHandler.removeCallbacks(hideGlassBarRunnable); uiHandler.removeCallbacks(pageLoadTimeout); web.destroy(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
