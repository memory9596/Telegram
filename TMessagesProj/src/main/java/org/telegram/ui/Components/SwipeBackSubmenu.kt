package org.telegram.ui.Components

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl

class SwipeBackSubmenu {

    fun interface ContentBuilder {
        fun build(builder: Builder): View
    }

    private class Cache(val submenu: LinearLayout, val alignTarget: View)

    class Builder internal constructor(
        private val submenu: LinearLayout,
        private val swipeBack: PopupSwipeBackLayout,
        private val context: Context,
        private val themeDelegate: Theme.ResourcesProvider?
    ) {
        fun addBack(top: Boolean, bottom: Boolean): ActionBarMenuSubItem {
            val item = ActionBarMenuSubItem(context, top, bottom, themeDelegate)
            item.setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back)
            item.setOnClickListener { swipeBack.closeForeground() }
            submenu.addView(
                item,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
            )
            return item
        }

        fun addBack(): ActionBarMenuSubItem = addBack(false, false)

        fun addGap(): View {
            val gap = View(context)
            gap.setBackgroundColor(
                Theme.multAlpha(
                    Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, themeDelegate),
                    0.06f
                )
            )
            submenu.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8))
            return gap
        }

        @JvmOverloads
        fun addItem(
            text: CharSequence,
            icon: Int,
            onClick: View.OnClickListener?,
            top: Boolean = false,
            bottom: Boolean = false
        ): ActionBarMenuSubItem {
            val item = ActionBarMenuSubItem(context, top, bottom, themeDelegate)
            item.setTextAndIcon(text, icon)
            item.setOnClickListener(onClick)
            submenu.addView(
                item,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
            )
            return item
        }

        fun closeForeground() {
            swipeBack.closeForeground()
        }
    }

    companion object {
        @JvmStatic
        fun open(
            popupLayout: ActionBarPopupWindow.ActionBarPopupWindowLayout?,
            anchorCell: View,
            context: Context,
            themeDelegate: Theme.ResourcesProvider?,
            scrimBlur3Factory: BlurredBackgroundDrawableViewFactory,
            contentBuilder: ContentBuilder
        ): Boolean {
            if (popupLayout == null) return false
            val swipeBack = popupLayout.swipeBack ?: return false
            anchorCell.isPressed = false
            anchorCell.jumpDrawablesToCurrentState()
            swipeBack.setForegroundColor(0x01000000)
            val cache = anchorCell.tag as? Cache
            val submenu: LinearLayout
            val alignTarget: View
            val index: Int
            if (cache == null) {
                submenu = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = scrimBlur3Factory.create(this, true)
                        .setColorProvider(
                            BlurredBackgroundProviderImpl.messageMenuBackground(themeDelegate)
                        )
                        .setRadius(AndroidUtilities.dp(12f).toFloat())
                    clipToOutline = true
                    setPadding(0, 0, 0, 0)
                }
                val builder = Builder(submenu, swipeBack, context, themeDelegate)
                alignTarget = contentBuilder.build(builder)
                index = popupLayout.addViewToSwipeBack(submenu)
                if (index < 0) return false
                swipeBack.pinnedScrimForegroundIndex = index
                anchorCell.tag = Cache(submenu, alignTarget)
            } else {
                submenu = cache.submenu
                alignTarget = cache.alignTarget
                index = swipeBack.indexOfChild(submenu)
            }
            val menuWidthPx = popupLayout.measuredWidth - popupLayout.paddingLeft - popupLayout.paddingRight
            (submenu.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.width = menuWidthPx
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.gravity = Gravity.TOP or Gravity.LEFT
                submenu.layoutParams = lp
            }
            submenu.measure(
                View.MeasureSpec.makeMeasureSpec(menuWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    AndroidUtilities.displaySize.y,
                    View.MeasureSpec.AT_MOST
                )
            )
            swipeBack.setNewForegroundHeight(index, submenu.measuredHeight, false)
            var anchorY = 0f
            var v: View? = anchorCell
            while (v != null && v !== swipeBack) {
                anchorY += v.y
                if (v is ScrollView) anchorY -= v.scrollY
                v = v.parent as? ViewGroup
            }
            var targetTop = 0
            for (i in 0 until submenu.childCount) {
                val child = submenu.getChildAt(i)
                if (child === alignTarget) break
                if (child.visibility == View.VISIBLE) {
                    targetTop += child.measuredHeight
                }
            }
            val offset = (anchorY.toInt() - targetTop).coerceAtLeast(0)
            swipeBack.setForegroundOffsetY(index, offset)
            swipeBack.openForeground(index)
            return true
        }
    }
}