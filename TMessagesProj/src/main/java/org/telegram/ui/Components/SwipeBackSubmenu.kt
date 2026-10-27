package org.telegram.ui.Components

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
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
            val submenu = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = scrimBlur3Factory.create(this, true)
                    .setColorProvider(
                        BlurredBackgroundProviderImpl.messageMenuBackground(themeDelegate)
                    )
                    .setRadius(AndroidUtilities.dp(12f).toFloat())
                clipToOutline = true
            }
            val alignTarget = contentBuilder.build(Builder(submenu, swipeBack, context, themeDelegate))
            val scrollView = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(
                    submenu,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }
            val index = popupLayout.addViewToSwipeBack(scrollView)
            if (index < 0) return false
            val menuWidthPx = (popupLayout.measuredWidth - popupLayout.paddingLeft - popupLayout.paddingRight).coerceAtLeast(0)
            (scrollView.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.width = menuWidthPx
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.gravity = Gravity.TOP or Gravity.LEFT
                scrollView.layoutParams = lp
            }
            submenu.measure(
                MeasureSpec.makeMeasureSpec(menuWidthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(
                    AndroidUtilities.displaySize.y,
                    MeasureSpec.AT_MOST
                )
            )
            val overlapBelow = reserveSubmenuRoom(popupLayout, swipeBack, submenu)
            swipeBack.pinnedScrimForegroundIndex = index
            swipeBack.setOnForegroundClosedListener {
                if (scrollView.parent === swipeBack) {
                    swipeBack.removeView(scrollView)
                }
                if (swipeBack.pinnedScrimForegroundIndex == index) {
                    swipeBack.pinnedScrimForegroundIndex = -1
                }
                if (overlapBelow > 0) {
                    (popupLayout.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                        params.bottomMargin += overlapBelow
                        popupLayout.layoutParams = params
                    }
                    swipeBack.invalidate()
                }
            }
            swipeBack.setForegroundOffsetY(index, computeOffsetY(swipeBack, anchorCell, submenu, alignTarget))
            swipeBack.openForeground(index)
            return true
        }

        @JvmStatic
        fun reserveRoomAbove(container: ChatScrimPopupContainerLayout, popupY: Int): Int {
            val reserve = (popupY - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(8f)).coerceAtLeast(0)
            container.setPadding(
                container.paddingLeft,
                container.paddingTop + reserve,
                container.paddingRight,
                container.paddingBottom
            )
            return popupY - reserve
        }

        private fun reserveSubmenuRoom(
            popupLayout: ActionBarPopupWindow.ActionBarPopupWindowLayout,
            swipeBack: PopupSwipeBackLayout,
            submenu: LinearLayout
        ): Int {
            val container = popupLayout.parent as? ChatScrimPopupContainerLayout ?: return 0
            val frame = Rect().also(container::getWindowVisibleDisplayFrame)
            val location = IntArray(2).also(container::getLocationOnScreen)
            val availableHeight = container.getAvailableHeight().takeIf { it > 0 } ?: container.measuredHeight
            val limit = minOf(
                availableHeight,
                frame.bottom - location[1] - AndroidUtilities.dp(8f)
            ).coerceAtLeast(0)
            container.setCurrentMaxHeight(limit)
            if (container.getBottomView() != null) return 0

            val missing = submenu.measuredHeight - swipeBack.measuredHeight - (limit - container.measuredHeight)
            if (missing <= 0) return 0
            val reactions = container.getReactionsLayout()?.takeIf { it.parent === container }
            val reactionsBelow = reactions != null && container.isReactionsBelow()
            val popupParams = popupLayout.layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
            val overlapBelow = if (reactionsBelow) {
                missing.coerceIn(0, (container.measuredHeight - popupLayout.bottom).coerceAtLeast(0))
            } else {
                0
            }
            val overlapAbove = if (reactions != null && !reactionsBelow) {
                (missing - overlapBelow).coerceIn(0, (popupLayout.top - container.paddingTop).coerceAtLeast(0))
            } else {
                0
            }
            val fromPadding = (missing - overlapBelow - overlapAbove).coerceIn(0, container.paddingTop)
            if (overlapBelow + overlapAbove + fromPadding == 0) return 0

            popupParams.topMargin -= overlapAbove
            popupParams.bottomMargin -= overlapBelow
            popupLayout.layoutParams = popupParams
            if (overlapAbove + fromPadding > 0) {
                val menu = swipeBack.getChildAt(0)
                (menu?.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                    params.topMargin += overlapAbove + fromPadding
                    menu.layoutParams = params
                }
            }
            if (fromPadding > 0) {
                container.setPadding(
                    container.paddingLeft,
                    container.paddingTop - fromPadding,
                    container.paddingRight,
                    container.paddingBottom
                )
                if (reactions != null && !reactionsBelow) {
                    reactions.translationY += fromPadding
                }
            }
            return overlapBelow
        }

        private fun computeOffsetY(
            swipeBack: PopupSwipeBackLayout,
            anchorCell: View,
            submenu: LinearLayout,
            alignTarget: View
        ): Int {
            var anchorY = 0f
            var v: View? = anchorCell
            while (v != null && v !== swipeBack) {
                anchorY += v.y
                if (v is ScrollView) anchorY -= v.scrollY
                v = v.parent as? ViewGroup
            }
            val slack = (swipeBack.measuredHeight - submenu.measuredHeight).coerceAtLeast(0)
            val menuTop = (swipeBack.getChildAt(0)?.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
            var alignTop = 0
            for (i in 0 until submenu.childCount) {
                val child = submenu.getChildAt(i)
                if (child === alignTarget) break
                if (child.visibility == View.VISIBLE) {
                    alignTop += child.measuredHeight
                }
            }
            return (anchorY.toInt() - alignTop).coerceIn(minOf(menuTop, slack), slack)
        }
    }
}
