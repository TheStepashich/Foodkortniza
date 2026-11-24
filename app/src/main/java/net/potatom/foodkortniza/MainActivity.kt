package net.potatom.foodkortniza

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Тонкий роутер: ничего не показывает, сразу открывает последнюю активную вкладку. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.setup(this)
        Ui.noTransition(this)
        CatalogSource.refresh(this)
        setContentView(R.layout.activity_main)

        val state = try {
            AppApi.getState(this)?.let { RegistryState.fromJson(it) }
        } catch (e: Exception) {
            null
        }

        val targetId = state?.activeTabId?.takeIf { id -> state.tabs.any { it.id == id } }
            ?: state?.tabs?.maxByOrNull { state.lastUsed[it.id] ?: 0L }?.id

        val openCatalog = {
            AppApi.startActivityNoAnim(this, Intent(this, CatalogActivity::class.java))
        }
        if (targetId == null) {
            openCatalog()
        } else {
            val json = AppApi.activate(this, targetId)
            if (json != null) {
                try {
                    AppApi.openTab(this, Jsonx.activation(json))
                } catch (e: Exception) {
                    openCatalog()
                }
            } else {
                openCatalog()
            }
        }
        finish()
        Ui.noTransition(this)
    }
}
