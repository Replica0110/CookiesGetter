package com.lonx.cookiesgetter

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.lonx.cookiesgetter.databinding.ActivityWebviewBinding

class WebViewActivity : ComponentActivity() {
    private lateinit var binding: ActivityWebviewBinding

    private var loginUrl = "https://music.apple.com/"
    private var currentUrl = loginUrl

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()

        loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL).orEmpty().toWebUrl()
        currentUrl = loginUrl

        setupBackHandling()
        setupActions()
        setupWebView()

        binding.currentUrlText.text = loginUrl
        binding.loginTitle.text = "加载中..."
        binding.loginWebview.loadUrl(loginUrl)
    }

    private fun setupActions() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.finishButton.setOnClickListener {
            finishWithCookies()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.loginWebview, true)

        binding.loginWebview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        binding.loginWebview.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                binding.loginTitle.text = title
                    ?.takeIf { it.isNotBlank() }
                    ?: currentUrl.toDomainLabel()
            }
        }

        binding.loginWebview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = false

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentUrl = url
                binding.currentUrlText.text = url
                binding.loadingProgress.visibility = View.VISIBLE
                binding.loginTitle.text = "加载中..."
            }

            override fun onPageFinished(view: WebView, url: String) {
                currentUrl = url
                binding.currentUrlText.text = url
                binding.loadingProgress.visibility = View.GONE

                binding.loginTitle.text = view.title
                    ?.takeIf { it.isNotBlank() }
                    ?: url.toDomainLabel()
            }
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.loginWebview.canGoBack()) {
                        binding.loginWebview.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        binding.loginWebview.stopLoading()
        binding.loginWebview.destroy()
        super.onDestroy()
    }

    private fun finishWithCookies() {
        CookieManager.getInstance().flush()

        val cookieResult = readAllCookies()

        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_COOKIE_TEXT, cookieResult.value)
                .putStringArrayListExtra(EXTRA_COOKIE_DOMAINS, ArrayList(cookieResult.domains))
                .putStringArrayListExtra(EXTRA_COOKIE_HEADERS, ArrayList(cookieResult.headers))
                .putExtra(EXTRA_STATUS_TEXT, cookieResult.message)
        )

        finish()
    }

    private fun readAllCookies(): CookieReadResult {
        val cookieManager = CookieManager.getInstance()

        val cookieEntries = listOf(currentUrl, loginUrl)
            .distinct()
            .mapNotNull { url ->
                cookieManager.getCookie(url)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { header -> url.toDomainLabel() to header }
            }
            .distinctBy { it.first }

        if (cookieEntries.isEmpty()) {
            return CookieReadResult(
                value = "",
                domains = emptyList(),
                headers = emptyList(),
                message = "没有读取到 Cookie"
            )
        }

        return CookieReadResult(
            value = cookieEntries.joinToString(separator = "\n\n") { (domain, header) ->
                "[$domain]\n$header"
            },
            domains = cookieEntries.map { it.first },
            headers = cookieEntries.map { it.second },
            message = "已读取全部可访问 Cookie"
        )
    }

    private fun setupSystemBars() {
        val background = ContextCompat.getColor(this, R.color.card_background)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding.loginRoot.setBackgroundColor(background)
        window.decorView.setBackgroundColor(background)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun String.toDomainLabel(): String {
        return runCatching { this.toUri().host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: this
    }

    private data class CookieReadResult(
        val value: String,
        val domains: List<String>,
        val headers: List<String>,
        val message: String
    )
}