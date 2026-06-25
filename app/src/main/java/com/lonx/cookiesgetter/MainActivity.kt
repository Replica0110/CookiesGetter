package com.lonx.cookiesgetter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.lonx.cookiesgetter.databinding.ActivityMainBinding
import com.lonx.cookiesgetter.databinding.ItemCookieBinding

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding

    private var cookieGroups = emptyList<CookieDomainGroup>()
    private var selectedDomainIndex = 0

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleLoginResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        setupListeners()
        updateCookieGroups(emptyList())
    }

    private fun setupListeners() {
        binding.openLoginButton.setOnClickListener {
            val normalizedUrl = binding.urlEdit.text.toString().toWebUrl()
            binding.urlEdit.setText(normalizedUrl)
            binding.statusText.text = "正在登录页面中等待完成"

            loginLauncher.launch(
                Intent(this, WebViewActivity::class.java)
                    .putExtra(EXTRA_LOGIN_URL, normalizedUrl)
            )
        }

        binding.clearCacheButton.setOnClickListener {
            clearWebViewCache()
        }

        binding.copyButton.setOnClickListener {
            val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(
                ClipData.newPlainText("cookies", cookieGroups.toClipboardText())
            )
            binding.statusText.text = "已复制到剪贴板"
        }
    }

    private fun handleLoginResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            return
        }

        val domains = data?.getStringArrayListExtra(EXTRA_COOKIE_DOMAINS).orEmpty()
        val headers = data?.getStringArrayListExtra(EXTRA_COOKIE_HEADERS).orEmpty()

        val groups = domains.zip(headers)
            .map { (domain, header) ->
                CookieDomainGroup(
                    domain = domain,
                    cookies = parseCookies(header)
                )
            }
            .filter { it.cookies.isNotEmpty() }

        val status = data?.getStringExtra(EXTRA_STATUS_TEXT).orEmpty()

        updateCookieGroups(groups)

        binding.statusText.text = status.ifBlank {
            if (groups.isEmpty()) {
                "没有读取到 Cookie"
            } else {
                "已读取全部可访问 Cookie"
            }
        }
    }

    private fun updateCookieGroups(groups: List<CookieDomainGroup>) {
        cookieGroups = groups
        selectedDomainIndex = selectedDomainIndex.coerceIn(
            0,
            (groups.size - 1).coerceAtLeast(0)
        )

        binding.copyButton.isEnabled = groups.isNotEmpty()
        binding.copyButton.alpha = if (binding.copyButton.isEnabled) 1f else 0.55f

        renderTabs()
        renderCookieItems()
    }

    private fun renderTabs() {
        binding.domainTabContainer.removeAllViews()
        binding.domainTabScroll.visibility =
            if (cookieGroups.isEmpty()) View.GONE else View.VISIBLE

        cookieGroups.forEachIndexed { index, group ->
            val tab = android.widget.TextView(this).apply {
                text = group.domain
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                gravity = Gravity.CENTER
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(14), 0, dp(14), 0)

                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (index == selectedDomainIndex) {
                            R.color.primary_button_text
                        } else {
                            R.color.text_primary
                        }
                    )
                )

                setBackgroundResource(
                    if (index == selectedDomainIndex) {
                        R.drawable.bg_tab_selected
                    } else {
                        R.drawable.bg_tab_normal
                    }
                )

                setOnClickListener {
                    selectedDomainIndex = index
                    renderTabs()
                    renderCookieItems()
                }
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply {
                marginEnd = dp(8)
            }

            binding.domainTabContainer.addView(tab, params)
        }
    }

    private fun renderCookieItems() {
        binding.cookieList.removeAllViews()

        if (cookieGroups.isEmpty()) {
            binding.emptyCookieText.visibility = View.VISIBLE

            // emptyCookieText 原本属于 activity_main.xml，
            // removeAllViews() 后可以重新添加。
            binding.cookieList.addView(binding.emptyCookieText)
            return
        }

        binding.emptyCookieText.visibility = View.GONE

        val selectedGroup = cookieGroups[selectedDomainIndex]

        selectedGroup.cookies.forEachIndexed { index, cookie ->
            val itemBinding = ItemCookieBinding.inflate(
                LayoutInflater.from(this),
                binding.cookieList,
                false
            )

            itemBinding.cookieKey.text = cookie.key
            itemBinding.cookieValue.text = cookie.value
            itemBinding.copyCookieButton.setOnClickListener {
                copyText(cookie.value)
                binding.statusText.text = "已复制 ${cookie.key}"
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    topMargin = dp(8)
                }
            }

            binding.cookieList.addView(itemBinding.root, params)
        }
    }

    private fun copyText(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText("cookie", text)
        )
    }

    private fun clearWebViewCache() {
        binding.statusText.text = "正在清理 Cookie 和缓存"

        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            runOnUiThread {
                updateCookieGroups(emptyList())
                binding.statusText.text = "Cookie 和缓存已清理"
            }
        }

        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword()

        WebView(this).apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
    }

    private fun setupSystemBars() {
        val background = ContextCompat.getColor(this, R.color.page_background)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding.rootScroll.setBackgroundColor(background)
        window.decorView.setBackgroundColor(background)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun parseCookies(header: String): List<CookieItem> {
        return header.split(";")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .map { cookie ->
                CookieItem(
                    key = cookie.substringBefore("=").trim(),
                    value = cookie.substringAfter("=").trim()
                )
            }
            .filter { it.key.isNotEmpty() }
            .distinctBy { it.key }
            .toList()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

data class CookieDomainGroup(
    val domain: String,
    val cookies: List<CookieItem>
)

data class CookieItem(
    val key: String,
    val value: String
)

private fun List<CookieDomainGroup>.toClipboardText(): String {
    return joinToString(separator = "\n\n") { group ->
        val header = group.cookies.joinToString(separator = "; ") { cookie ->
            "${cookie.key}=${cookie.value}"
        }
        "[${group.domain}]\n$header"
    }
}

fun String.toWebUrl(): String {
    val trimmed = trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.isBlank() -> "https://music.apple.com/"
        else -> "https://$trimmed"
    }
}