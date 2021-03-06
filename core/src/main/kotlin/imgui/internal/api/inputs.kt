package imgui.internal.api

import gli_.has
import imgui.*
import imgui.ImGui.io
import imgui.api.g

/** Inputs
 *  FIXME: Eventually we should aim to move e.g. IsActiveIdUsingKey() into IsKeyXXX functions. */
internal interface inputs {

    fun setItemUsingMouseWheel() {
        val id = g.currentWindow!!.dc.lastItemId
        if (g.hoveredId == id)
            g.hoveredIdUsingMouseWheel = true
        if (g.activeId == id)
            g.activeIdUsingMouseWheel = true
    }

    infix fun isActiveIdUsingNavDir(dir: Dir): Boolean = g.activeIdUsingNavDirMask has (1 shl dir)

    infix fun isActiveIdUsingNavInput(input: NavInput): Boolean = g.activeIdUsingNavInputMask has (1 shl input)
    infix fun isActiveIdUsingKey(key: Key): Boolean {
        assert(key.i < 64)
        return g.activeIdUsingKeyInputMask.and(1L shl key.i) != 0L // TODO Long.has
    }

    /** Return if a mouse click/drag went past the given threshold. Valid to call during the MouseReleased frame.
     *  [Internal] This doesn't test if the button is pressed */
    fun isMouseDragPastThreshold(button: MouseButton, lockThreshold_: Float): Boolean {

        assert(button.i in io.mouseDown.indices)
        if (!io.mouseDown[button.i])
            return false
        var lockThreshold = lockThreshold_
        if (lockThreshold < 0f)
            lockThreshold = io.mouseDragThreshold
        return io.mouseDragMaxDistanceSqr[button.i] >= lockThreshold * lockThreshold
    }

    // the rest of inputs functions are in the NavInput enum

    /** ~GetMergedKeyModFlags */
    val mergedKeyModFlags: KeyModFlags
        get() {
            var keyModFlags: KeyModFlags = KeyMod.None.i
            if (ImGui.io.keyCtrl) keyModFlags = keyModFlags or KeyMod.Ctrl.i
            if (ImGui.io.keyShift) keyModFlags = keyModFlags or KeyMod.Shift.i
            if (ImGui.io.keyAlt) keyModFlags = keyModFlags or KeyMod.Alt.i
            if (ImGui.io.keySuper) keyModFlags = keyModFlags or KeyMod.Super.i
            return keyModFlags
        }
}