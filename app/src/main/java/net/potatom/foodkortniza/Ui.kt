package net.potatom.foodkortniza

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

object Ui {
    /**
     * Edge-to-edge поддерживается вручную: контент сам отступает от системных панелей.
     * На Android 15+ выключить edge-to-edge нельзя, поэтому старый setDecorFitsSystemWindows
     * больше не работает — отступы должны приходить именно из WindowInsets.
     */
    fun setup(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val content = activity.findViewById<View>(android.R.id.content)
        val lightBars = activity.resources.getBoolean(R.bool.system_bars_light)
        WindowInsetsControllerCompat(activity.window, content).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = maxOf(bars.bottom, keyboard.bottom)
            )
            insets
        }
    }

    /** Убирает «съезжание» task при переходах между вкладками и каталогом. */
    fun noTransition(activity: Activity) {
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}
