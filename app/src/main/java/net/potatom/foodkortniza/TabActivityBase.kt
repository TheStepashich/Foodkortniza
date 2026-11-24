package net.potatom.foodkortniza

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import net.potatom.foodkortniza.ui.BottomBarView

/**
 * Один процесс на все вкладки. WebView каждой вкладки живёт в памяти,
 * поэтому переключение мгновенное и без перезагрузки страницы.
 */
abstract class TabActivityBase : AppCompatActivity() {
    var tabId: String? = null
        private set
    protected var tab: TabRecord? = null
        private set

    private lateinit var webContainer: FrameLayout
    private lateinit var bottomBar: BottomBarView
    private lateinit var interstitial: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var webView: WebView

    private val webViews = LinkedHashMap<String, WebView>()
    private val approvedHosts = mutableMapOf<String, MutableSet<String>>()
    private var state: RegistryState = RegistryState()

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.any { it }
        val origin = pendingGeoOrigin
        val callback = pendingGeoCallback
        pendingGeoOrigin = null
        pendingGeoCallback = null
        if (origin != null && callback != null) {
            if (granted) {
                callback.invoke(origin, true, true)
            } else {
                callback.invoke(origin, false, false)
                toast(this, "Сайт не получил геопозицию")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setup(this)
        Ui.noTransition(this)
        setContentView(R.layout.activity_tab)

        webContainer = findViewById(R.id.webContainer)
        bottomBar = findViewById(R.id.bottomBar)
        interstitial = findViewById(R.id.interstitial)
        progress = findViewById(R.id.progress)

        readIntent()
        applyIntent()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (interstitial.visibility == View.VISIBLE) {
                    interstitial.visibility = View.GONE
                } else if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    AppApi.startActivityNoAnim(this@TabActivityBase, Intent(this@TabActivityBase, CatalogActivity::class.java))
                    Ui.noTransition(this@TabActivityBase)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent()
        applyIntent()
    }

    private fun readIntent() {
        tabId = intent?.getStringExtra(AppApi.EXTRA_TAB_ID)
    }

    private fun applyIntent() {
        val id = tabId
        if (id == null) {
            finish()
            return
        }
        val tabJson = intent?.getStringExtra(AppApi.EXTRA_TAB_JSON)
        val stateJson = intent?.getStringExtra(AppApi.EXTRA_STATE_JSON)
        val current = when {
            tabJson != null -> runCatching { Jsonx.tab(tabJson) }.getOrNull()
            else -> AppApi.getTab(this, id)?.let { runCatching { Jsonx.tab(it) }.getOrNull() }
        }
        if (current == null) {
            toast(this, "Вкладка больше не существует")
            finish()
            return
        }
        switchTo(current, stateJson)
    }

    /** Переключает вкладку на месте: WebView не пересоздаётся и не перезагружается. */
    private fun switchTo(next: TabRecord, stateJson: String? = null) {
        if (stateJson != null) {
            runCatching { RegistryState.fromJson(stateJson) }.getOrNull()?.let { state = it }
        }
        if (::webView.isInitialized) {
            tabId?.let { old -> AppApi.saveUrl(this, old, webView.url ?: "") }
        }
        tabId = next.id
        tab = next

        val view = webViews.getOrPut(next.id) { createWebView(next.id, next) }
        webView = view
        (view.parent as? ViewGroup)?.removeView(view)
        webContainer.removeAllViews()
        webContainer.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        interstitial.visibility = View.GONE
        progress.visibility = View.GONE
        renderBottomBar()
    }

    private fun selectTab(selected: TabRecord) {
        if (selected.id == tabId) {
            if (::webView.isInitialized) webView.scrollY = 0
            return
        }
        val json = AppApi.activate(this, selected.id)
        if (json == null) {
            toast(this, "Не удалось переключиться")
            return
        }
        val activation = runCatching { Jsonx.activation(json) }.getOrNull()
        if (activation == null) {
            toast(this, "Не удалось переключиться")
            return
        }
        switchTo(activation.tab, activation.stateJson)
    }

    /** Вызывается из TabCloser после удаления вкладки. */
    fun onTabClosed(closedId: String, stateJson: String) {
        webViews.remove(closedId)?.destroy()
        approvedHosts.remove(closedId)
        runCatching { RegistryState.fromJson(stateJson) }.getOrNull()?.let { state = it }
        if (closedId == tabId) {
            val next = state.tabs.firstOrNull()
            if (next != null) {
                switchTo(next, stateJson)
            } else {
                AppApi.startActivityNoAnim(this, Intent(this, CatalogActivity::class.java))
                Ui.noTransition(this)
                finish()
            }
        } else {
            renderBottomBar()
        }
    }

    private fun renderBottomBar() {
        bottomBar.render(
            activeId = tabId,
            onSelect = { selected -> selectTab(selected) },
            onLongPress = { selected ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(selected.title)
                    .setMessage("Домен: ${selected.host.ifBlank { "своя ссылка" }}")
                    .setPositiveButton("Закрыть вкладку") { _, _ ->
                        net.potatom.foodkortniza.ui.TabCloser.confirmAndClose(this, selected)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            },
            onAdd = {
                AppApi.startActivityNoAnim(this, Intent(this, CatalogActivity::class.java))
            },
            stateJson = state.toJson().toString()
        )
    }

    private fun createWebView(id: String, record: TabRecord): WebView = WebView(applicationContext).apply {
        tag = id
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = false
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(true)
            @Suppress("DEPRECATION")
            setSavePassword(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val ownerId = view?.tag as? String ?: return false
                val current = tabFor(ownerId) ?: return false
                val uri = request?.url ?: return false
                if (uri.scheme?.lowercase() != "https") return true

                val host = uri.host?.lowercase() ?: return true
                if (hostAllowed(current.allowedHosts, host) || approvedHosts.getOrPut(ownerId) { mutableSetOf() }.contains(host)) {
                    return false
                }
                showInterstitial(ownerId, host, uri.toString())
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val ownerId = view?.tag as? String ?: return
                if (url != null) AppApi.saveUrl(this@TabActivityBase, ownerId, url)
                if (ownerId == tabId) this@TabActivityBase.progress.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    toast(this@TabActivityBase, "Не удалось загрузить сайт. Проверьте соединение.")
                }
            }
        }

        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if ((view?.tag as? String) != tabId) return
                this@TabActivityBase.progress.progress = newProgress
                this@TabActivityBase.progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            /** Сайт (например, Burger King) просит геопозицию — спрашиваем пользователя. */
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                if (origin == null || callback == null) return

                fun deny() {
                    pendingGeoOrigin = null
                    pendingGeoCallback = null
                    callback.invoke(origin, false, false)
                }

                pendingGeoOrigin = origin
                pendingGeoCallback = callback

                AlertDialog.Builder(this@TabActivityBase)
                    .setTitle("Геопозиция")
                    .setMessage("Сайт $origin хочет узнать ваше местоположение, чтобы показать ближайшие точки. Разрешить?")
                    .setPositiveButton("Разрешить") { _, _ ->
                        if (hasLocationPermission()) {
                            pendingGeoOrigin = null
                            pendingGeoCallback = null
                            callback.invoke(origin, true, true)
                        } else {
                            requestLocation.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                    .setNegativeButton("Не разрешать") { _, _ -> deny() }
                    .setOnCancelListener { deny() }
                    .show()
            }
        }

        setDownloadListener { _, _, _, _, _ ->
            toast(this@TabActivityBase, "Скачивание файлов в приложении не поддерживается")
        }

        loadUrl(record.lastUrl.ifBlank { record.entryUrl })
    }

    private fun tabFor(id: String): TabRecord? =
        if (id == tabId) tab else state.tabs.firstOrNull { it.id == id }

    private fun showInterstitial(ownerId: String, host: String, url: String) {
        interstitial.removeAllViews()
        val card = layoutInflater.inflate(R.layout.view_interstitial, null)
        card.findViewById<TextView>(R.id.domain).text = host
        card.findViewById<Button>(R.id.continueBtn).setOnClickListener {
            approvedHosts.getOrPut(ownerId) { mutableSetOf() }.add(host)
            interstitial.visibility = View.GONE
            webViews[ownerId]?.loadUrl(url)
        }
        card.findViewById<Button>(R.id.backBtn).setOnClickListener {
            interstitial.visibility = View.GONE
        }
        interstitial.addView(
            card,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        interstitial.visibility = View.VISIBLE
    }

    override fun onPause() {
        webViews.forEach { (id, view) -> AppApi.saveUrl(this, id, view.url ?: "") }
        super.onPause()
    }

    override fun onDestroy() {
        webViews.values.forEach { it.destroy() }
        webViews.clear()
        super.onDestroy()
    }
}
