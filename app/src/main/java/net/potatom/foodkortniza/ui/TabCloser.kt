package net.potatom.foodkortniza.ui

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import net.potatom.foodkortniza.AppApi
import net.potatom.foodkortniza.CatalogActivity
import net.potatom.foodkortniza.TabActivityBase
import net.potatom.foodkortniza.TabRecord
import net.potatom.foodkortniza.Ui

object TabCloser {
    fun confirmAndClose(activity: Activity, tab: TabRecord) {
        AlertDialog.Builder(activity)
            .setTitle("Закрыть вкладку «${tab.title}»?")
            .setMessage("Кнопка вкладки исчезнет с панели. Её можно будет добавить заново.")
            .setPositiveButton("Продолжить") { _, _ -> warnAndClose(activity, tab) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun warnAndClose(activity: Activity, tab: TabRecord) {
        AlertDialog.Builder(activity)
            .setTitle("Удалить данные вкладки?")
            .setMessage(
                "Куки и входы для доменов этой вкладки будут удалены безвозвратно. " +
                    "Отменить это действие нельзя."
            )
            .setPositiveButton("Удалить и закрыть") { _, _ -> performClose(activity, tab) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performClose(activity: Activity, tab: TabRecord) {
        val json = AppApi.closeTab(activity, tab.id) ?: return
        val own = activity as? TabActivityBase
        if (own != null) {
            own.onTabClosed(tab.id, json)
        } else {
            (activity as? CatalogActivity)?.refresh()
        }
        if (activity is CatalogActivity) {
            Ui.noTransition(activity)
        }
    }
}
