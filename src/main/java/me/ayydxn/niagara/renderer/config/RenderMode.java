package me.ayydxn.niagara.renderer.config;

import me.ayydxn.luminescence.platform.ULGPUDriver;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * Controls how a Niagara view composites into Minecraft's render pipeline.
 * <p>
 * <ul>
 *     <li>GPU uses a {@link ULGPUDriver} implementation against Minecraft's OpenGL context. This is the default.</li>
 *     <li>CPU uploads pixel data to a {@link NativeImageBackedTexture} and draws via {@link DrawContext}.</li>
 * </ul>
 *
 * @author Ayydxn
 */
public enum RenderMode
{
    /**
     * Hardware-accelerated rendering via ULGPUDriver. Default.
     */
    GPU,

    /**
     * Software rendering via NativeImageBackedTexture + DrawContext.
     */
    CPU
}
