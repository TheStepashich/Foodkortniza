package net.potatom.foodkortniza

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import net.potatom.foodkortniza.ui.BottomBarView
import net.potatom.foodkortniza.ui.TabCloser

class CatalogActivity : AppCompatActivity() {
    private lateinit var search: EditText
    private lateinit var categoryContainer: LinearLayout
    private lateinit var storesContainer: LinearLayout
    private lateinit var customTitle: EditText
    private lateinit var customUrl: EditText
    private lateinit var bottomBar: BottomBarView
    private lateinit var catalog: Catalog

    private var selectedCategory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setup(this)
        Ui.noTransition(this)
        setContentView(R.layout.activity_catalog)

        search = findViewById(R.id.search)
        categoryContainer = findViewById(R.id.categoryContainer)
        storesContainer = findViewById(R.id.storesContainer)
        customTitle = findViewById(R.id.customTitle)
        customUrl = findViewById(R.id.customUrl)
        bottomBar = findViewById(R.id.bottomBar)
        catalog = CatalogRepository(this).catalog
        CatalogSource.refresh(this) { updated ->
            if (updated && !isFinishing) {
                catalog = CatalogRepository(this).catalog
                selectedCategory = null
                buildCategories()
                renderStores()
            }
        }

        buildCategories()
        renderStores()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = renderStores()
        })

        findViewById<Button>(R.id.addCustom).setOnClickListener { addCustomTab() }
        refresh()
    }

    private fun buildCategories() {
        categoryContainer.removeAllViews()
        categoryContainer.addView(categoryChip("Все", null))
        catalog.categories.forEach { category ->
            categoryContainer.addView(categoryChip("${category.emoji} ${category.title}", category.id))
        }
    }

    private fun categoryChip(label: String, id: String?): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setTextColor(ContextCompat.getColor(this@CatalogActivity, R.color.text_primary))
        background = GradientDrawable().apply {
            setColor(
                ContextCompat.getColor(
                    this@CatalogActivity,
                    if (id == selectedCategory) R.color.tab_active else R.color.field_bg
                )
            )
            cornerRadius = dp(16).toFloat()
        }
        layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, dp(8), 0) }
        setOnClickListener {
            selectedCategory = id
            buildCategories()
            renderStores()
        }
    }

    private fun renderStores() {
        val query = search.text.toString().trim().lowercase()
        val stores = catalog.stores
            .filter { it.enabled }
            .filter { selectedCategory == null || it.categoryId == selectedCategory }
            .filter { store ->
                query.isEmpty() ||
                    store.title.lowercase().contains(query) ||
                    store.searchKeywords.any { it.lowercase().contains(query) }
            }
            .sortedBy { it.priority }

        storesContainer.removeAllViews()
        stores.forEach { storesContainer.addView(storeItem(it)) }

        if (stores.isEmpty()) {
            storesContainer.addView(TextView(this).apply {
                text = "Ничего не найдено"
                setPadding(dp(12), dp(20), dp(12), dp(20))
            })
        }
    }

    private fun storeItem(store: Store): ViewGroup = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isClickable = true
        isFocusable = true
        setOnClickListener { addStore(store) }
        background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@CatalogActivity, R.color.card_bg))
            cornerRadius = dp(8).toFloat()
            setStroke(1, ContextCompat.getColor(this@CatalogActivity, R.color.bar_divider))
        }
        layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(8)) }

        addView(TextView(context).apply {
            text = store.emoji
            textSize = 28f
            setPadding(0, 0, dp(12), 0)
        })

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(context).apply {
            text = store.title
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@CatalogActivity, R.color.text_primary))
        })
        texts.addView(TextView(context).apply {
            text = store.note
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@CatalogActivity, R.color.text_secondary))
            maxLines = 2
        })
        addView(texts)
    }

    private fun addStore(store: Store) {
        val existing = AppApi.findTab(this, store.id)
        val id = existing ?: AppApi.addFromStore(this, store.id)?.let { Jsonx.tab(it).id }
        if (id != null) activateAndOpen(id) else toast(this, "Не удалось добавить магазин")
    }

    private fun addCustomTab() {
        val result = UrlValidator.normalize(customUrl.text.toString())
        if (result.error != null || result.url == null || result.host == null) {
            toast(this, result.error ?: "Некорректный адрес")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Добавить свою ссылку?")
            .setMessage(
                "Полный домен: ${result.host}\n\n" +
                    "Сайт не проверялся приложением. Приложение не хранит пароли и данные карт — " +
                    "вводите их только на сайте."
            )
            .setPositiveButton("Добавить") { _, _ ->
                val title = customTitle.text.toString().trim().ifBlank { result.host }
                val existing = AppApi.findTab(this, null, result.url)
                if (existing != null) {
                    activateAndOpen(existing)
                    return@setPositiveButton
                }
                val json = AppApi.addCustom(this, title, result.url, result.host)
                val id = try {
                    json?.let { Jsonx.tab(it).id }
                } catch (e: Exception) {
                    null
                }
                if (id != null) activateAndOpen(id) else toast(this, "Не удалось добавить ссылку")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun activateAndOpen(tabId: String) {
        val json = AppApi.activate(this, tabId)
        if (json == null) {
            toast(this, "Не удалось открыть вкладку")
            return
        }
        try {
            AppApi.openTab(this, Jsonx.activation(json))
            finish()
            Ui.noTransition(this)
        } catch (e: Exception) {
            toast(this, "Не удалось открыть вкладку")
        }
    }

    fun refresh() {
        bottomBar.render(
            activeId = null,
            onSelect = { tab -> activateAndOpen(tab.id) },
            onLongPress = { tab -> showTabCard(tab) },
            onAdd = { customUrl.requestFocus() }
        )
    }

    private fun showTabCard(tab: TabRecord) {
        AlertDialog.Builder(this)
            .setTitle(tab.title)
            .setMessage("Домен: ${tab.host.ifBlank { "своя ссылка" }}\n\nЗакрыть вкладку и стереть её данные?")
            .setPositiveButton("Закрыть") { _, _ -> TabCloser.confirmAndClose(this, tab) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
