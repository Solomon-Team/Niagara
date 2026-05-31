package me.ayydxn.niagara.renderer;

import me.ayydxn.luminescence.gpu.ULRenderTarget;
import me.ayydxn.luminescence.view.ULView;
import me.ayydxn.niagara.platform.NiagaraGPUDriver;
import me.ayydxn.niagara.renderer.shaders.NiagaraShader;
import net.minecraft.client.gui.DrawContext;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;

public class NiagaraGPURenderer
{
    private final ULView view;
    private final NiagaraGPUDriver gpuDriver;

    private NiagaraShader blitShader;
    private boolean isInitialized = false;
    private int locUVRect = -1;
    private int quadVertexArray = 0;
    private int quadVertexBuffer = 0;
    private int quadIndexBuffer = 0;

    public NiagaraGPURenderer(ULView view, NiagaraGPUDriver gpuDriver)
    {
        this.view = view;
        this.gpuDriver = gpuDriver;
    }

    /**
     * Executes the GPU driver's pending command list then blits the view's render target.
     * Must be called on the render thread.
     */
    public void draw(DrawContext context)
    {
        this.ensureInitialized();

        this.gpuDriver.executeCommands();

        ULRenderTarget renderTarget = this.view.getRenderTarget();
        if (renderTarget == null || renderTarget.isEmpty)
            return;

        Integer glTextureID = this.gpuDriver.getGLTextureID(renderTarget.textureID);
        if (glTextureID == null)
            return;

        // uvCoords origin is top-left; quad has v=0 at bottom — swap v0/v1.
        float u0 = renderTarget.uvCoords.left;
        float v0 = renderTarget.uvCoords.bottom; // Y-flip
        float u1 = renderTarget.uvCoords.right;
        float v1 = renderTarget.uvCoords.top;    // Y-flip

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDisable(GL_SCISSOR_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glBlendEquation(GL_FUNC_ADD);

        this.blitShader.bind();

        glUniform4f(locUVRect, u0, v0, u1, v1);
        glBindTextureUnit(0, glTextureID);
        glBindVertexArray(quadVertexArray);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
        glUseProgram(0);
    }

    /**
     * Releases blit shader and quad GL resources.
     */
    public void destroy()
    {
        this.blitShader.destroy();

        if (this.quadVertexArray != 0)
        {
            glDeleteVertexArrays(this.quadVertexArray);
            this.quadVertexArray = 0;
        }

        if (this.quadVertexBuffer != 0)
        {
            glDeleteBuffers(this.quadVertexBuffer);
            this.quadVertexBuffer = 0;

        }
        if (this.quadIndexBuffer != 0)
        {
            glDeleteBuffers(this.quadIndexBuffer);
            this.quadIndexBuffer = 0;
        }
    }

    private void ensureInitialized()
    {
        if (this.isInitialized)
            return;

        // Blit shader
        this.blitShader = new NiagaraShader("NiagaraBlit");
        this.blitShader.bind();

        glUniform1i(glGetUniformLocation(this.blitShader.getHandle(), "u_Texture"), 0);
        this.locUVRect = glGetUniformLocation(this.blitShader.getHandle(), "u_UVRect");

        glUseProgram(0);

        // Fullscreen quad (NDC -1..1, UV 0..1)
        float[] fullscreenQuadVertices = {-1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, -1f, -1f, 0f, 0f};
        int[] fullscreenQuadIndices = {0, 1, 2, 2, 3, 0};

        this.quadVertexArray = glGenVertexArrays();
        this.quadVertexBuffer = glGenBuffers();
        this.quadIndexBuffer = glGenBuffers();

        glBindVertexArray(this.quadVertexArray);

        glBindBuffer(GL_ARRAY_BUFFER, this.quadVertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, fullscreenQuadVertices, GL_STATIC_DRAW);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.quadIndexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, fullscreenQuadIndices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);

        this.isInitialized = true;
    }
}
