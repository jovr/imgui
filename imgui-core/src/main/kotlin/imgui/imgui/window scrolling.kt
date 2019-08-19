package imgui.imgui

import imgui.ImGui

/** Windows Scrolling */
interface imgui_windowScrolling {

    /** Scrolling amount [0..GetScrollMaxX()] */
    var scrollX: Float
        /** ~GetScrollX */
        get() = g.currentWindow!!.scroll.x
        /**  ~ SetScrollX */
        set(value) = with(ImGui.currentWindow) { scrollTarget.x = value; scrollTargetCenterRatio.x = 0f }

    /** scrolling amount [0..GetScrollMaxY()] */
    var scrollY: Float
        /** GetScrollY */
        get() = g.currentWindow!!.scroll.y
        /**  ~SetScrollY */
        set(value) = with(ImGui.currentWindow) {
            scrollTarget.y = value
            scrollTargetCenterRatio.y = 0f
        }

    /** get maximum scrolling amount ~~ ContentSize.X - WindowSize.X
     *  ~GetScrollMaxX */
    val scrollMaxX: Float
        get() = ImGui.currentWindow.scrollMax.x

    /** get maximum scrolling amount ~~ ContentSize.Y - WindowSize.Y
     *  ~GetScrollMaxY */
    val scrollMaxY: Float
        get() = ImGui.currentWindow.scrollMax.y

    /** center_x_ratio: 0.0f left of last item, 0.5f horizontal center of last item, 1.0f right of last item.
     *
     *  adjust scrolling amount to make current cursor position visible. center_x_ratio=0.0: left, 0.5: center, 1.0: right. When using to make a "default/current item" visible, consider using SetItemDefaultFocus() instead. */
    fun setScrollHereX(centerXratio: Float) {
        val window = g.currentWindow!!
        var targetX = window.dc.lastItemRect.min.x - window.pos.x // Left of last item, in window space
        val lastItemWidth = window.dc.lastItemRect.width
        targetX += lastItemWidth * centerXratio + g.style.itemSpacing.x * (centerXratio - 0.5f) * 2f // Precisely aim before, in the middle or after the last item.
        setScrollFromPosX(targetX, centerXratio)
    }

    /** adjust scrolling amount to make current cursor position visible.
     *  centerYRatio = 0.0: top, 0.5: center, 1.0: bottom.
     *   When using to make a "default/current item" visible, consider using setItemDefaultFocus() instead.*/
    fun setScrollHereY(centerYRatio: Float = 0.5f) = with(ImGui.currentWindow) {
        var targetY = dc.cursorPosPrevLine.y - pos.y  // Top of last item, in window space
        // Precisely aim above, in the middle or below the last line.
        targetY += (dc.prevLineSize.y * centerYRatio) + ImGui.style.itemSpacing.y * (centerYRatio - 0.5f) * 2f
        setScrollFromPosY(targetY, centerYRatio)
    }

    fun setScrollFromPosX(localX: Float, centerXratio: Float) = g.currentWindow!!.setScrollFromPosX(localX, centerXratio)

    fun setScrollFromPosY(localY: Float, centerYratio: Float) = g.currentWindow!!.setScrollFromPosY(localY, centerYratio)
}