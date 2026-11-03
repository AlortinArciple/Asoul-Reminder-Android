package com.alortinarciple.asoulreminder

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.alortinarciple.asoulreminder.data.BilibiliLogin

/**
 * 内置 WebView 登录 B 站：抓取请求头里的 Cookie（含 HttpOnly 的 SESSDATA），
 * 由用户确认后交给 Repository 合并写入 SecretStore。
 */
class BilibiliLoginActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var statusView: TextView

    @Volatile
    private var capturedCookie: String? = null
    private var prompting = false
    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        configureWebView()
        registerBackHandler()
        webView.loadUrl(BilibiliLogin.LOGIN_URL)
    }

    private fun buildLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
        }
        val title = TextView(this).apply {
            text = "登录 B 站"
            textSize = 19f
            setTextColor(Color.parseColor("#161616"))
            setPadding(dp(20), dp(18), dp(20), dp(4))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val hint = TextView(this).apply {
            text = "登录完成后会自动检测凭证；出现滑块或短信验证时按页面提示完成即可。只有点击「保存」才会写入本机。"
            textSize = 13f
            setTextColor(Color.parseColor("#6F6B5E"))
            setPadding(dp(20), 0, dp(20), dp(8))
        }
        statusView = TextView(this).apply {
            text = "正在打开 B 站登录页…"
            textSize = 13f
            setTextColor(Color.parseColor("#6F6B5E"))
            setPadding(dp(20), 0, dp(20), dp(6))
        }
        webView = WebView(this)
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(8), dp(20), dp(12))
        }
        val cancel = Button(this).apply {
            text = "取消"
            setOnClickListener { finish() }
        }
        val done = Button(this).apply {
            text = "完成登录"
            setOnClickListener { checkNow(showFailure = true) }
        }
        val spacer = android.view.View(this)
        bottom.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(spacer, LinearLayout.LayoutParams(dp(12), 1))
        bottom.addView(done, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(title)
        root.addView(hint)
        root.addView(statusView)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottom)
        return root
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val header = request.requestHeaders["Cookie"] ?: request.requestHeaders["cookie"]
                if (!header.isNullOrBlank() && BilibiliLogin.hasLoginCookie(header)) {
                    capturedCookie = BilibiliLogin.merge(capturedCookie.orEmpty(), header)
                    if (!prompting) {
                        runOnUiThread { checkNow(showFailure = false) }
                    }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url?.toString().orEmpty()
                if (url.startsWith("bilibili://") || url.startsWith("bilibili:")) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                statusView.text = "正在加载…"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (!prompting) {
                    statusView.text = if (BilibiliLogin.hasLoginCookie(capturedCookie.orEmpty())) {
                        "已检测到登录凭证，请确认是否保存。"
                    } else {
                        "请在页面完成登录；已登录的账号可能会直接跳过登录页。"
                    }
                    checkNow(showFailure = false)
                }
            }
        }
    }

    private fun checkNow(showFailure: Boolean) {
        if (prompting) return
        val cookie = BilibiliLogin.merge(
            capturedCookie.orEmpty(),
            CookieManager.getInstance().getCookie(BilibiliLogin.HOME_URL).orEmpty(),
        )
        if (!BilibiliLogin.hasLoginCookie(cookie)) {
            if (showFailure) {
                Toast.makeText(this, "还没有检测到登录凭证，请先在页面上完成登录。", Toast.LENGTH_SHORT).show()
            }
            return
        }
        capturedCookie = cookie
        statusView.text = "已检测到登录凭证，请确认是否保存。"
        promptSave(cookie)
    }

    private fun promptSave(rawCookie: String) {
        prompting = true
        AlertDialog.Builder(this)
            .setTitle("保存登录凭证")
            .setMessage(
                "检测到 B 站登录凭证（SESSDATA / DedeUserID）。\n\n" +
                    "是否保存到本机？Cookie 等同登录凭证，只会用系统密钥库加密保存在本机，不会上传。",
            )
            .setCancelable(false)
            .setPositiveButton("保存") { _, _ -> save(rawCookie) }
            .setNegativeButton("取消") { _, _ ->
                prompting = false
                statusView.text = "未保存，可以重新登录后再试。"
            }
            .show()
    }

    private fun save(rawCookie: String) {
        val repository = (application as AsoulApplication).container.repository
        if (!BilibiliLogin.hasLoginCookie(BilibiliLogin.loginCookies(rawCookie))) {
            prompting = false
            Toast.makeText(this, "登录凭证不完整，未保存。", Toast.LENGTH_SHORT).show()
            return
        }
        repository.onCookieUpdated(rawCookie)
        Toast.makeText(this, "B 站登录凭证已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBack()
    }

    private fun handleBack() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return
        }
        finish()
    }

    private fun registerBackHandler() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback { handleBack() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback,
        )
        backCallback = callback
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            backCallback = null
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
