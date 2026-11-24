package net.potatom.foodkortniza

import android.app.Activity
import androidx.core.view.WindowCompat

object Ui {
    /**
     * Возвращает обычное поведение системных панелей: контент не залезает под часы/сеть
     * и под нижнюю панель навигации. На Android 15+ дополнительно используется
     * windowOptOutEdgeToEdgeEnforcement в теме.
     */
    fun setup(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    }

    /** Убирает «съезжание» task при переходах между вкладками и каталогом. */
    fun noTransition(activity: Activity) {
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}
