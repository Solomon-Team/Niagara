package me.ayydxn.niagara.renderer;

import me.ayydxn.luminescence.bitmap.ULBitmap;
import me.ayydxn.luminescence.geometry.ULIntRect;
import me.ayydxn.luminescence.renderer.ULRenderer;
import me.ayydxn.luminescence.surface.ULBitmapSurface;
import me.ayydxn.luminescence.surface.ULSurface;
import me.ayydxn.luminescence.view.ULView;
import me.ayydxn.niagara.NiagaraClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Composites a CPU-rendered {@link ULView} into Minecraft's render pipeline using a {@link NativeImageBackedTexture} registered with Minecraft's {@link TextureManager}.
 * <p>
 * Only the dirty region reported by {@link ULSurface#getDirtyBounds()} is uploaded each frame for efficiency.
 *
 * @author Ayydxn
 */
public class NiagaraCPURenderer
{
    private final ULView view;
    private final Identifier textureID;

    private NativeImageBackedTexture texture;
    private NativeImage image;
    private int registeredWidth;
    private int registeredHeight;

    public NiagaraCPURenderer(ULView view)
    {
        this.view = view;
        this.textureID = Identifier.of(NiagaraClientMod.MOD_ID, "view/" + UUID.randomUUID());
    }

    /**
     * Uploads any dirty pixels from Ultralight's bitmap surface to the GPU texture.
     * <p>
     * Must be called on the render thread after {@link ULRenderer#render()}.
     */
    public void uploadIfDirty()
    {
        ULSurface surface = view.getSurface();
        if (surface == null)
            return;

        ULBitmapSurface bitmapSurface = ULBitmapSurface.fromSurface(surface);
        ULIntRect dirtyBounds = bitmapSurface.getDirtyBounds();

        // Empty dirty rect means nothing changed this frame.
        if (dirtyBounds.isEmpty())
            return;

        int viewWidth = this.view.getWidth();
        int viewHeight = this.view.getHeight();

        this.ensureTextureAllocated(viewWidth, viewHeight);

        try (ULBitmap.LockedPixels lockedPixels = bitmapSurface.getBitmap().acquirePixelLock())
        {
            ByteBuffer pixels = lockedPixels.pixels();

            // Ultralight outputs BGRA8; NativeImage expects RGBA8 — swizzle while copying.
            for (int y = dirtyBounds.top; y < dirtyBounds.bottom; y++)
            {
                for (int x = dirtyBounds.left; x < dirtyBounds.right; x++)
                {
                    int offset = (y * viewWidth + x) * 4;

                    int blue = Byte.toUnsignedInt(pixels.get(offset));
                    int green = Byte.toUnsignedInt(pixels.get(offset + 1));
                    int red = Byte.toUnsignedInt(pixels.get(offset + 2));
                    int alpha = Byte.toUnsignedInt(pixels.get(offset + 3));

                    // NativeImage.setColor expects ABGR packed int (Minecraft's format)
                    this.image.setColor(x, y, (alpha << 24) | (blue << 16) | (green << 8) | red);
                }
            }
        }

        this.texture.upload();

        bitmapSurface.clearDirtyBounds();
    }

    /**
     * Releases the GPU texture and unregisters it from Minecraft's TextureManager.
     * <p>
     * Call when the view is being destroyed.
     */
    public void destroy()
    {
        if (this.texture != null)
        {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(this.textureID);

            this.texture = null;
            this.image = null;
        }
    }

    /**
     * Draws the composited view texture at (0, 0) filling the full view dimensions.
     *
     * @param context The current draw context
     */
    public void draw(DrawContext context)
    {
        if (this.texture == null)
            return;

        context.drawTexture(this.textureID, 0, 0, 0, 0, this.registeredWidth, this.registeredHeight, this.registeredWidth, this.registeredHeight);
    }

    private void ensureTextureAllocated(int width, int height)
    {
        if (this.texture != null && this.registeredWidth == width && this.registeredHeight == height)
            return;

        // Destroy old texture if dimensions changed.
        if (this.texture != null)
            MinecraftClient.getInstance().getTextureManager().destroyTexture(this.textureID);

        this.image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        this.texture = new NativeImageBackedTexture(image);

        MinecraftClient.getInstance().getTextureManager().registerTexture(this.textureID, this.texture);

        this.registeredWidth = width;
        this.registeredHeight = height;
    }
}
