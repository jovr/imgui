package imgui.internal.api

import glm_.glm
import glm_.vec2.Vec2
import imgui.*
import imgui.ImGui.begin
import imgui.ImGui.contentRegionAvail
import imgui.ImGui.endPopup
import imgui.ImGui.findWindowByName
import imgui.ImGui.focusTopMostWindowUnderOne
import imgui.ImGui.focusWindow
import imgui.ImGui.io
import imgui.ImGui.isMousePosValid
import imgui.ImGui.navInitWindow
import imgui.ImGui.setActiveId
import imgui.ImGui.setNextWindowBgAlpha
import imgui.ImGui.setNextWindowPos
import imgui.ImGui.setNextWindowSize
import imgui.ImGui.style
import imgui.api.g
import imgui.internal.*
import imgui.internal.classes.Window
import imgui.internal.classes.PopupData
import imgui.internal.classes.Rect
import imgui.static.navCalcPreferredRefPos
import imgui.static.navRestoreLastChildNavWindow
import uno.kotlin.getValue
import uno.kotlin.setValue
import kotlin.reflect.KMutableProperty0

/** Popups, Modals, Tooltips */
internal interface PopupsModalsTooltips {

    fun beginChildEx(name: String, id: ID, sizeArg: Vec2, border: Boolean, flags_: WindowFlags): Boolean {

        val parentWindow = g.currentWindow!!
        var flags = WindowFlag.NoTitleBar or WindowFlag.NoResize or WindowFlag.NoSavedSettings or WindowFlag._ChildWindow
        flags = flags or (parentWindow.flags and WindowFlag.NoMove.i)  // Inherit the NoMove flag

        // Size
        val contentAvail = contentRegionAvail
        val size = floor(sizeArg)
        val autoFitAxes = (if (size.x == 0f) 1 shl Axis.X else 0x00) or (if (size.y == 0f) 1 shl Axis.Y else 0x00)
        if (size.x <= 0f)   // Arbitrary minimum child size (0.0f causing too much issues)
            size.x = glm.max(contentAvail.x + size.x, 4f)
        if (size.y <= 0f)
            size.y = glm.max(contentAvail.y + size.y, 4f)
        setNextWindowSize(size)

        // Build up name. If you need to append to a same child from multiple location in the ID stack, use BeginChild(ImGuiID id) with a stable value.
        val title = when {
            name.isNotEmpty() -> "${parentWindow.name}/$name".format(style.locale)
            else -> "${parentWindow.name}/%08X".format(style.locale, id)
        }
        val backupBorderSize = style.childBorderSize
        if (!border) style.childBorderSize = 0f
        flags = flags or flags_
        val ret = begin(title, null, flags)
        style.childBorderSize = backupBorderSize

        val childWindow = g.currentWindow!!.apply {
            childId = id
            autoFitChildAxes = autoFitAxes
        }

        // Set the cursor to handle case where the user called SetNextWindowPos()+BeginChild() manually.
        // While this is not really documented/defined, it seems that the expected thing to do.
        if (childWindow.beginCount == 1)
            parentWindow.dc.cursorPos put childWindow.pos

        // Process navigation-in immediately so NavInit can run on first frame
        if (g.navActivateId == id && flags hasnt WindowFlag._NavFlattened && (childWindow.dc.navLayerActiveMask != 0 || childWindow.dc.navHasScroll)) {
            focusWindow(childWindow)
            navInitWindow(childWindow, false)
            setActiveId(id + 1, childWindow) // Steal ActiveId with a dummy id so that key-press won't activate child item
            g.activeIdSource = InputSource.Nav
        }

        return ret
    }

    /** Mark popup as open (toggle toward open state).
     *  Popups are closed when user click outside, or activate a pressable item, or CloseCurrentPopup() is called within
     *  a BeginPopup()/EndPopup() block.
     *  Popup identifiers are relative to the current ID-stack (so OpenPopup and BeginPopup needs to be at the same
     *  level).
     *  One open popup per level of the popup hierarchy (NB: when assigning we reset the Window member of ImGuiPopupRef
     *  to NULL)    */
    fun openPopupEx(id: ID) {

        val parentWindow = g.currentWindow!!
        val currentStackSize = g.beginPopupStack.size
        // Tagged as new ref as Window will be set back to NULL if we write this into OpenPopupStack.
        val openPopupPos = navCalcPreferredRefPos()
        val popupRef = PopupData(popupId = id, window = null, sourceWindow = g.navWindow, openFrameCount = g.frameCount,
                openParentId = parentWindow.idStack.last(), openPopupPos = openPopupPos,
                openMousePos = if (isMousePosValid(io.mousePos)) Vec2(io.mousePos) else Vec2(openPopupPos))
//        println("" + g.openPopupStack.size +", "+currentStackSize)
        if (g.openPopupStack.size < currentStackSize + 1)
            g.openPopupStack += popupRef
        else {
            /*  Gently handle the user mistakenly calling OpenPopup() every frame. It is a programming mistake!
                However, if we were to run the regular code path, the ui would become completely unusable because
                the popup will always be in hidden-while-calculating-size state _while_ claiming focus.
                Which would be a very confusing situation for the programmer. Instead, we silently allow the popup
                to proceed, it will keep reappearing and the programming error will be more obvious to understand.  */
            if (g.openPopupStack[currentStackSize].popupId == id && g.openPopupStack[currentStackSize].openFrameCount == g.frameCount - 1)
                g.openPopupStack[currentStackSize].openFrameCount = popupRef.openFrameCount
            else {
                // Close child popups if any
                if (g.openPopupStack.size > currentStackSize + 1) // ~resize
                    for (i in currentStackSize + 1 until g.openPopupStack.size)
                        g.openPopupStack.pop()
                else if (g.openPopupStack.size < currentStackSize + 1)
                    TODO()
                g.openPopupStack[currentStackSize] = popupRef
            }
            /*  When reopening a popup we first refocus its parent, otherwise if its parent is itself a popup
                it would get closed by closePopupsOverWindow().  This is equivalent to what ClosePopupToLevel() does. */
            //if (g.openPopupStack[currentStackSize].popupId == id) sourceWindow.focus()
        }
    }

    fun closePopupToLevel(remaining: Int, restoreFocusToWindowUnderPopup: Boolean) {

        assert(remaining >= 0 && remaining < g.openPopupStack.size)
        var focusWindow = g.openPopupStack[remaining].sourceWindow
        val popupWindow = g.openPopupStack[remaining].window
        for (i in remaining until g.openPopupStack.size) // resize(remaining)
            g.openPopupStack.pop()

        if (restoreFocusToWindowUnderPopup)
            if (focusWindow?.wasActive == false && popupWindow != null)
                focusTopMostWindowUnderOne(popupWindow)   // Fallback
            else {
                if (g.navLayer == NavLayer.Menu && focusWindow != null)
                    focusWindow = navRestoreLastChildNavWindow(focusWindow)
                focusWindow(focusWindow)
            }
    }

    fun closePopupsOverWindow(refWindow: Window?, restoreFocusToWindowUnderPopup: Boolean) {

        if (g.openPopupStack.empty())
            return

        /*  When popups are stacked, clicking on a lower level popups puts focus back to it and close popups above it.
            Don't close our own child popup windows */
        var popupCountToKeep = 0
        if (refWindow != null)
        // Find the highest popup which is a descendant of the reference window (generally reference window = NavWindow)
            while (popupCountToKeep < g.openPopupStack.size) {
                val popup = g.openPopupStack[popupCountToKeep]
                if (popup.window == null) {
                    popupCountToKeep++
                    continue
                }
                assert(popup.window!!.flags has WindowFlag._Popup)
                if (popup.window!!.flags has WindowFlag._ChildWindow) {
                    popupCountToKeep++
                    continue
                }
                // Trim the stack when popups are not direct descendant of the reference window (the reference window is often the NavWindow)
                var popupOrDescendentIsRefWindow = false
                var m = popupCountToKeep
                while (m < g.openPopupStack.size && !popupOrDescendentIsRefWindow) {
                    g.openPopupStack[m].window?.let { popupWindow ->
                        if (popupWindow.rootWindow === refWindow.rootWindow)
                            popupOrDescendentIsRefWindow = true
                    }
                    m++
                }
                if (!popupOrDescendentIsRefWindow) break
                popupCountToKeep++
            }

        if (popupCountToKeep < g.openPopupStack.size) { // This test is not required but it allows to set a convenient breakpoint on the statement below
            //IMGUI_DEBUG_LOG("ClosePopupsOverWindow(%s) -> ClosePopupToLevel(%d)\n", ref_window->Name, popup_count_to_keep);
            closePopupToLevel(popupCountToKeep, restoreFocusToWindowUnderPopup)
        }
    }

    /** return true if the popup is open at the current begin-ed level of the popup stack.
     *  Test for id within current popup stack level (currently begin-ed into); this doesn't scan the whole popup stack! */
    fun isPopupOpen(id: ID) = g.openPopupStack.size > g.beginPopupStack.size && g.openPopupStack[g.beginPopupStack.size].popupId == id

    fun beginPopupEx(id: ID, extraFlags: WindowFlags): Boolean {

        if (!isPopupOpen(id)) {
            g.nextWindowData.clearFlags() // We behave like Begin() and need to consume those values
            return false
        }

        val name = when {
            extraFlags has WindowFlag._ChildMenu -> "##Menu_%02d".format(style.locale, g.beginPopupStack.size)    // Recycle windows based on depth
            else -> "##Popup_%08x".format(style.locale, id)     // Not recycling, so we can close/open during the same frame
        }
        val isOpen = begin(name, null, extraFlags or WindowFlag._Popup)
        if (!isOpen) // NB: Begin can return false when the popup is completely clipped (e.g. zero size display)
            endPopup()

        return isOpen
    }

    /** Not exposed publicly as BeginTooltip() because bool parameters are evil. Let's see if other needs arise first.
     *  @param extraFlags WindowFlag   */
    fun beginTooltipEx(extraFlags: WindowFlags, tooltipFlags_: TooltipFlags) {
        var tooltipFlags = tooltipFlags_
        if (g.dragDropWithinSourceOrTarget)        {
            // The default tooltip position is a little offset to give space to see the context menu (it's also clamped within the current viewport/monitor)
            // In the context of a dragging tooltip we try to reduce that offset and we enforce following the cursor.
            // Whatever we do we want to call SetNextWindowPos() to enforce a tooltip position and disable clipping the tooltip without our display area, like regular tooltip do.
            //ImVec2 tooltip_pos = g.IO.MousePos - g.ActiveIdClickOffset - g.Style.WindowPadding;
            val tooltipPos = io.mousePos + Vec2(16 * style.mouseCursorScale, 8 * style.mouseCursorScale)
            setNextWindowPos(tooltipPos)
            setNextWindowBgAlpha(style.colors[Col.PopupBg].w * 0.6f)
            //PushStyleVar(ImGuiStyleVar_Alpha, g.Style.Alpha * 0.60f); // This would be nice but e.g ColorButton with checkboard has issue with transparent colors :(
            tooltipFlags = tooltipFlags or TooltipFlag.OverridePreviousTooltip
        }

        var windowName = "##Tooltip_%02d".format(style.locale, g.tooltipOverrideCount)
        if (tooltipFlags has TooltipFlag.OverridePreviousTooltip)
            findWindowByName(windowName)?.let {
                if (it.active) {
                    // Hide previous tooltip from being displayed. We can't easily "reset" the content of a window so we create a new one.
                    it.hidden = true
                    it.hiddenFramesCanSkipItems = 1
                    windowName = "##Tooltip_%02d".format(++g.tooltipOverrideCount)
                }
            }
        val flags = WindowFlag._Tooltip or WindowFlag.NoMouseInputs or WindowFlag.NoTitleBar or WindowFlag.NoMove or WindowFlag.NoResize or WindowFlag.NoSavedSettings or WindowFlag.AlwaysAutoResize
        begin(windowName, null, flags or extraFlags)
    }

    /** ~GetTopMostPopupModal */
    val topMostPopupModal: Window?
        get() {
            for (n in g.openPopupStack.size - 1 downTo 0)
                g.openPopupStack[n].window?.let { if (it.flags has WindowFlag._Modal) return it }
            return null
        }

    fun findBestWindowPosForPopup(window: Window): Vec2 {

        val rOuter = window.getAllowedExtentRect()
        if (window.flags has WindowFlag._ChildMenu) {
            /*  Child menus typically request _any_ position within the parent menu item,
                and then we move the new menu outside the parent bounds.
                This is how we end up with child menus appearing (most-commonly) on the right of the parent menu. */
            assert(g.currentWindow === window)
            val parentWindow = g.currentWindowStack[g.currentWindowStack.size - 2]
            // We want some overlap to convey the relative depth of each menu (currently the amount of overlap is hard-coded to style.ItemSpacing.x).
            val horizontalOverlap = style.itemInnerSpacing.x
            val rAvoid = parentWindow.run {
                when {
                    dc.menuBarAppending -> Rect(-Float.MAX_VALUE, pos.y + titleBarHeight, Float.MAX_VALUE, pos.y + titleBarHeight + menuBarHeight)
                    else -> Rect(pos.x + horizontalOverlap, -Float.MAX_VALUE, pos.x + size.x - horizontalOverlap - scrollbarSizes.x, Float.MAX_VALUE)
                }
            }
            return findBestWindowPosForPopupEx(Vec2(window.pos), window.size, window::autoPosLastDirection, rOuter, rAvoid)
        }
        if (window.flags has WindowFlag._Popup) {
            val rAvoid = Rect(window.pos.x - 1, window.pos.y - 1, window.pos.x + 1, window.pos.y + 1)
            return findBestWindowPosForPopupEx(Vec2(window.pos), window.size, window::autoPosLastDirection, rOuter, rAvoid)
        }
        if (window.flags has WindowFlag._Tooltip) {
            // Position tooltip (always follows mouse)
            val sc = style.mouseCursorScale
            val refPos = navCalcPreferredRefPos()
            val rAvoid = when {
                !g.navDisableHighlight && g.navDisableMouseHover && !(io.configFlags has ConfigFlag.NavEnableSetMousePos) ->
                    Rect(refPos.x - 16, refPos.y - 8, refPos.x + 16, refPos.y + 8)
                else -> Rect(refPos.x - 16, refPos.y - 8, refPos.x + 24 * sc, refPos.y + 24 * sc) // FIXME: Hard-coded based on mouse cursor shape expectation. Exact dimension not very important.
            }
            val pos = findBestWindowPosForPopupEx(refPos, window.size, window::autoPosLastDirection, rOuter, rAvoid)
            if (window.autoPosLastDirection == Dir.None)
            // If there's not enough room, for tooltip we prefer avoiding the cursor at all cost even if it means that part of the tooltip won't be visible.
                pos(refPos + 2)
            return pos
        }
        assert(false)
        return Vec2(window.pos)
    }

    /** rAvoid = the rectangle to avoid (e.g. for tooltip it is a rectangle around the mouse cursor which we want to avoid. for popups it's a small point around the cursor.)
     *  rOuter = the visible area rectangle, minus safe area padding. If our popup size won't fit because of safe area padding we ignore it. */
    fun findBestWindowPosForPopupEx(refPos: Vec2, size: Vec2, lastDirPtr: KMutableProperty0<Dir>, rOuter: Rect, rAvoid: Rect,
                                    policy: PopupPositionPolicy = PopupPositionPolicy.Default): Vec2 {

        var lastDir by lastDirPtr
        val basePosClamped = glm.clamp(refPos, rOuter.min, rOuter.max - size)
        //GImGui->OverlayDrawList.AddRect(r_avoid.Min, r_avoid.Max, IM_COL32(255,0,0,255));
        //GImGui->OverlayDrawList.AddRect(rOuter.Min, rOuter.Max, IM_COL32(0,255,0,255));

        // Combo Box policy (we want a connecting edge)
        if (policy == PopupPositionPolicy.ComboBox) {
            val dirPreferedOrder = arrayOf(Dir.Down, Dir.Right, Dir.Left, Dir.Up)
            for (n in (if (lastDir != Dir.None) -1 else 0) until Dir.COUNT) {
                val dir = if (n == -1) lastDir else dirPreferedOrder[n]
                if (n != -1 && dir == lastDir) continue // Already tried this direction?
                val pos = Vec2()
                if (dir == Dir.Down) pos.put(rAvoid.min.x, rAvoid.max.y)          // Below, Toward Right (default)
                if (dir == Dir.Right) pos.put(rAvoid.min.x, rAvoid.min.y - size.y) // Above, Toward Right
                if (dir == Dir.Left) pos.put(rAvoid.max.x - size.x, rAvoid.max.y) // Below, Toward Left
                if (dir == Dir.Up) pos.put(rAvoid.max.x - size.x, rAvoid.min.y - size.y) // Above, Toward Left
                if (Rect(pos, pos + size) !in rOuter) continue
                lastDir = dir
                return pos
            }
        }

        // Default popup policy
        val dirPreferedOrder = arrayOf(Dir.Right, Dir.Down, Dir.Up, Dir.Left)
        for (n in (if (lastDir != Dir.None) -1 else 0) until Dir.COUNT) {
            val dir = if (n == -1) lastDir else dirPreferedOrder[n]
            if (n != -1 && dir == lastDir) continue  // Already tried this direction?
            val availW = (if (dir == Dir.Left) rAvoid.min.x else rOuter.max.x) - if (dir == Dir.Right) rAvoid.max.x else rOuter.min.x
            val availH = (if (dir == Dir.Up) rAvoid.min.y else rOuter.max.y) - if (dir == Dir.Down) rAvoid.max.y else rOuter.min.y
            if (availW < size.x || availH < size.y) continue
            val pos = Vec2(
                    if (dir == Dir.Left) rAvoid.min.x - size.x else if (dir == Dir.Right) rAvoid.max.x else basePosClamped.x,
                    if (dir == Dir.Up) rAvoid.min.y - size.y else if (dir == Dir.Down) rAvoid.max.y else basePosClamped.y)
            lastDir = dir
            return pos
        }
        // Fallback, try to keep within display
        lastDir = Dir.None
        return Vec2(refPos).apply {
            x = kotlin.math.max(kotlin.math.min(x + size.x, rOuter.max.x) - size.x, rOuter.min.x)
            y = kotlin.math.max(kotlin.math.min(y + size.y, rOuter.max.y) - size.y, rOuter.min.y)
        }
    }
}