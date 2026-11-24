package net.potatom.foodkortniza.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import net.potatom.foodkortniza.AppApi
import net.potatom.foodkortniza.R
import net.potatom.foodkortniza.RegistryState
import net.potatom.foodkortniza.TabOrder
import net.potatom.foodkortniza.TabRecord
import kotlin.math.abs

/** Нижняя панель: прокручиваемые кнопки вкладок и закреплённый «+». */
class BottomBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
    }
    private val tabsContainer = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    private val plusButton = TextView(context).apply {
        text = "+"
        textSize = 26f
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.brand))
        isClickable = true
        isFocusable = true
        layoutParams = LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT)
        contentDescription = "Добавить вкладку"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    // После долгого нажатия нужно осознанное движение, иначе меню удаления
    // съедается микросдвигом пальца и вкладка уходит в перетаскивание.
    private val dragThreshold = touchSlop * 2

    private var pendingView: View? = null
    private var draggingView: View? = null
    private var dropTarget: View? = null
    private var downRawX = 0f
    private var pressStartX = 0f
    private var longPressFired = false
    private var dragMoved = false

    private var currentActiveId: String? = null
    private var currentOnSelect: ((TabRecord) -> Unit)? = null
    private var currentOnLongPress: ((TabRecord) -> Unit)? = null
    private var currentOnAdd: (() -> Unit)? = null

    private val longPressRunnable = Runnable {
        longPressFired = true
        val view = pendingView ?: return@Runnable
        draggingView = view
        view.alpha = 0.85f
        view.elevation = dp(8).toFloat()
        scroller.requestDisallowInterceptTouchEvent(true)
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.bar_bg))
        addView(scroller)
        addView(plusButton)
        scroller.addView(tabsContainer)
        plusButton.setOnClickListener { currentOnAdd?.invoke() }
    }

    fun render(
        activeId: String?,
        onSelect: (TabRecord) -> Unit,
        onLongPress: (TabRecord) -> Unit,
        onAdd: () -> Unit,
        stateJson: String? = null
    ) {
        currentActiveId = activeId
        currentOnSelect = onSelect
        currentOnLongPress = onLongPress
        currentOnAdd = onAdd
        resetDrag()
        tabsContainer.removeAllViews()

        val json = stateJson ?: AppApi.getState(context)
        val tabs = try {
            json?.let { RegistryState.fromJson(it) }?.tabs ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        var activeIndex = -1
        tabs.forEachIndexed { index, tab ->
            if (tab.id == activeId) activeIndex = index
            tabsContainer.addView(createTabButton(tab, tab.id == activeId))
        }

        if (activeIndex >= 0) {
            post {
                val child = tabsContainer.getChildAt(activeIndex) ?: return@post
                val target = child.left + child.width / 2 - scroller.width / 2
                scroller.smoothScrollTo(target.coerceAtLeast(0), 0)
            }
        }
    }

    private fun createTabButton(tab: TabRecord, active: Boolean): ViewGroup {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            tag = tab.id
            layoutParams = LayoutParams(dp(96), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(2), dp(4), dp(2), dp(4))
            }
            background = GradientDrawable().apply {
                setColor(
                    if (active) ContextCompat.getColor(context, R.color.tab_active) else Color.TRANSPARENT
                )
                cornerRadius = dp(8).toFloat()
            }
            contentDescription = "${tab.title}, ${tab.host.ifBlank { "своя ссылка" }}"
            isClickable = true
            isFocusable = true
            setOnTouchListener { view, event -> handleTabTouch(view, event, tab) }
        }

        val iconSlot = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(dp(26), dp(26))
        }
        val emoji = TextView(context).apply {
            text = tab.emoji
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val image = ImageView(context).apply {
            visibility = ImageView.GONE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        iconSlot.addView(emoji)
        iconSlot.addView(image)

        val title = TextView(context).apply {
            text = tab.title
            textSize = 10f
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutParams = ViewGroup.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        container.addView(iconSlot)
        container.addView(title)

        FaviconLoader.load(tab.host) { bitmap ->
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
                image.visibility = ImageView.VISIBLE
                emoji.visibility = ImageView.GONE
            }
        }
        return container
    }

    /**
     * Короткое нажатие = переключение, долгое без движения = карточка домена,
     * долгое с движением = перетаскивание с сохранением нового порядка.
     */
    private fun handleTabTouch(view: View, event: MotionEvent, tab: TabRecord): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingView = view
                downRawX = event.rawX
                pressStartX = view.translationX
                longPressFired = false
                dragMoved = false
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, 450L)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                if (!longPressFired) {
                    if (abs(dx) > touchSlop) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                    return false
                }
                if (!dragMoved && abs(dx) <= dragThreshold) return true
                dragMoved = true
                view.translationX = pressStartX + dx
                updateDropTarget(event.rawX)
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                when {
                    longPressFired && dragMoved -> finishDrag(event.rawX)
                    longPressFired -> {
                        resetDrag()
                        currentOnLongPress?.invoke(tab)
                    }
                    abs(event.rawX - downRawX) <= touchSlop -> currentOnSelect?.invoke(tab)
                }
                pendingView = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                resetDrag()
                pendingView = null
                return true
            }
        }
        return false
    }

    private fun updateDropTarget(rawX: Float) {
        val found = viewAt(rawX)
        if (dropTarget === found) return
        dropTarget?.scaleX = 1f
        dropTarget?.scaleY = 1f
        dropTarget = found
        dropTarget?.scaleX = 1.08f
        dropTarget?.scaleY = 1.08f
    }

    private fun viewAt(rawX: Float): View? {
        val location = IntArray(2)
        for (i in 0 until tabsContainer.childCount) {
            val child = tabsContainer.getChildAt(i) ?: continue
            if (child === draggingView) continue
            child.getLocationOnScreen(location)
            if (TabOrder.containsX(rawX, location[0], child.width)) return child
        }
        return null
    }

    private fun finishDrag(rawX: Float) {
        val fromIndex = draggingView?.let { tabsContainer.indexOfChild(it) } ?: -1
        val targetView = viewAt(rawX)
        val targetIndex = targetView?.let { tabsContainer.indexOfChild(it) } ?: -1

        if (fromIndex >= 0 && targetIndex >= 0 && targetIndex != fromIndex) {
            val ids = (0 until tabsContainer.childCount)
                .mapNotNull { tabsContainer.getChildAt(it)?.tag as? String }
                .toMutableList()
            if (fromIndex in ids.indices && targetIndex in ids.indices) {
                AppApi.reorder(context, TabOrder.move(ids, fromIndex, targetIndex))
            }
        }

        resetDrag()
        val select = currentOnSelect
        val longPress = currentOnLongPress
        val add = currentOnAdd
        if (select != null && longPress != null && add != null) {
            render(currentActiveId, select, longPress, add)
        }
    }

    private fun resetDrag() {
        draggingView?.translationX = 0f
        draggingView?.alpha = 1f
        draggingView?.elevation = 0f
        dropTarget?.scaleX = 1f
        dropTarget?.scaleY = 1f
        draggingView = null
        dropTarget = null
        scroller.requestDisallowInterceptTouchEvent(false)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
