package me.ayydxn.niagara.core;

import me.ayydxn.luminescence.config.FontHinting;
import me.ayydxn.luminescence.config.ULConfig;
import me.ayydxn.luminescence.platform.ULPlatform;
import me.ayydxn.luminescence.platform.impl.StandardULFileSystem;
import me.ayydxn.luminescence.platform.impl.StandardULFontLoader;
import me.ayydxn.luminescence.renderer.ULRenderer;
import me.ayydxn.niagara.NiagaraClientMod;
import me.ayydxn.niagara.platform.NiagaraLogger;
import me.ayydxn.niagara.utils.NiagaraPaths;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Monitor;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFWVidMode;

import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;

public class NiagaraEngine
{
    private static NiagaraEngine INSTANCE;

    private final ULRenderer renderer;

    private NiagaraEngine()
    {
        ULPlatform.setFileSystem(new StandardULFileSystem());
        ULPlatform.setFontLoader(new StandardULFontLoader());
        ULPlatform.setLogger(new NiagaraLogger());

        try (ULConfig config = new ULConfig())
        {
            Monitor monitor = MinecraftClient.getInstance().getWindow().getMonitor();
            if (monitor == null)
                monitor = new Monitor(glfwGetPrimaryMonitor());

            GLFWVidMode videoMode = glfwGetVideoMode(monitor.getHandle());

            config.setResourcePathPrefix(NiagaraPaths.NIAGARA_RESOURCES_DIRECTORY + "/");
            config.setCachePath(NiagaraPaths.NIAGARA_CACHE_DIRECTORY.toString());
            config.setFontHinting(FontHinting.SMOOTH);

            if (videoMode != null)
            {
                double delay = 1.0d / videoMode.refreshRate();

                config.setScrollTimerDelay(delay);
                config.setAnimationTimerDelay(delay);
            }

            this.renderer = new ULRenderer(config);
        }
    }

    public static void initialize()
    {
        if (INSTANCE != null)
        {
            NiagaraClientMod.LOGGER.warn("Cannot initialize Niagara's backend Ultralight engine more than once!");
            return;
        }

        INSTANCE = new NiagaraEngine();
    }

    public void shutdown()
    {
        if (INSTANCE == null)
            return;

        this.renderer.destroy();

        INSTANCE = null;
    }

    public void update()
    {
        this.renderer.update();
    }

    public void render()
    {
        this.renderer.refreshDisplay(0);
        this.renderer.render();
    }

    public static NiagaraEngine getInstance()
    {
        if (INSTANCE == null)
            throw new IllegalStateException("Tried to access Niagara's backend Ultralight engine before it was available!");

        return INSTANCE;
    }
}
