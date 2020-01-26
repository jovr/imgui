module com.github.kotlin_graphics.imgui_gl {

    requires java.desktop;

    requires kotlin.stdlib;

    requires com.github.kotlin_graphics.imgui_core;
    requires com.github.kotlin_graphics.imgui_glfw;
    requires com.github.kotlin_graphics.uno_core;
    requires com.github.kotlin_graphics.glm;
    requires com.github.kotlin_graphics.gln;
    requires com.github.kotlin_graphics.kool;

    requires org.lwjgl.opengl;
    requires org.lwjgl.glfw;

    exports imgui.impl.gl;
}