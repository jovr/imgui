package imgui.internal.api

import gli_.has
import imgui.*
import imgui.ImGui.getNavInputAmount
import imgui.ImGui.io
import imgui.api.g
import imgui.internal.InputReadMode

/** Inputs
 *  FIXME: Eventually we should aim to move e.g. IsActiveIdUsingKey() into IsKeyXXX functions. */
internal interface inputs {

    infix fun isActiveIdUsingNavDir(dir: Dir): Boolean = g.activeIdUsingNavDirMask has (1 shl dir)

    infix fun isActiveIdUsingNavInput(input: NavInput): Boolean = g.activeIdUsingNavInputMask has (1 shl input)
    infix fun isActiveIdUsingKey(key: Key): Boolean {
        assert(key.i < 64)
        return g.activeIdUsingKeyInputMask.and(1L shl key.i) != 0L // TODO Long.has
    }

    /** [Internal] This doesn't test if the button is pressed */
    fun isMouseDragPastThreshold(button: Int, lockThreshold_: Float): Boolean {

        assert(button in io.mouseDown.indices)
        if (!io.mouseDown[button])
            return false
        var lockThreshold = lockThreshold_
        if (lockThreshold < 0f)
            lockThreshold = io.mouseDragThreshold
        return io.mouseDragMaxDistanceSqr[button] >= lockThreshold * lockThreshold
    }

    // the rest of inputs functions are in the NavInput enum

    fun isNavInputPressedAnyOfTwo(n1: NavInput, n2: NavInput, mode: InputReadMode): Boolean =
            getNavInputAmount(n1, mode) + getNavInputAmount(n2, mode) > 0f
}