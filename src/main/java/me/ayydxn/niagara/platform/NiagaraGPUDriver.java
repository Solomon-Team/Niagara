package me.ayydxn.niagara.platform;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import me.ayydxn.luminescence.bitmap.BitmapFormat;
import me.ayydxn.luminescence.bitmap.ULBitmap;
import me.ayydxn.luminescence.gpu.*;
import me.ayydxn.luminescence.platform.ULGPUDriver;
import me.ayydxn.niagara.NiagaraClientMod;
import me.ayydxn.niagara.renderer.shaders.NiagaraShader;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;

public class NiagaraGPUDriver implements ULGPUDriver
{
    private static final float[] IDENTITY_MATRIX = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    private static final int UBO_SIZE = 800;
    private static final int UBO_OFFSET_STATE = 0;
    private static final int UBO_OFFSET_TRANSFORM = 16;
    private static final int UBO_OFFSET_INTEGER4 = 80;
    private static final int UBO_OFFSET_SCALAR4 = 112;
    private static final int UBO_OFFSET_VECTOR = 144;
    private static final int UBO_OFFSET_CLIPDATA = 272;
    private static final int UBO_OFFSET_CLIP = 288;

    private final Map<Integer, Integer> textureIDs = Maps.newHashMap(); // Ultralight ID -> OpenGL Texture
    private final Map<Integer, RenderBufferData> renderBufferIDs = Maps.newHashMap(); // Ultralight ID -> OpenGL Framebuffer
    private final Map<Integer, GeometryData> geometryDataMap = Maps.newHashMap(); // Ultralight ID -> Custom VAO/VBO/IBO Wrapper

    private final AtomicInteger nextTextureID = new AtomicInteger(1);
    private final AtomicInteger nextRenderBufferID = new AtomicInteger(1);
    private final AtomicInteger nextGeometryID = new AtomicInteger(1);
    private final ByteBuffer uboStagingBuffer = BufferUtils.createByteBuffer(UBO_SIZE);
    private final NiagaraShader fillShader;
    private final NiagaraShader pathShader;

    private ULCommand[] pendingCommandsList = new ULCommand[0];
    private int uniformBufferID;

    public NiagaraGPUDriver()
    {
        this.fillShader = new NiagaraShader("UltralightFill");
        this.pathShader = new NiagaraShader("UltralightPath");

        this.uniformBufferID = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, this.uniformBufferID);
        glBufferData(GL_UNIFORM_BUFFER, UBO_SIZE, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        this.bindUniformBlock(this.fillShader.getHandle(), "type_Uniforms", 0);
        this.bindUniformBlock(this.pathShader.getHandle(), "type_Uniforms", 0);

        this.setSamplerUnit(this.fillShader.getHandle(), "SPIRV_Cross_CombinedTexture0Sampler0", 0);
        this.setSamplerUnit(this.fillShader.getHandle(), "SPIRV_Cross_CombinedTexture1Sampler0", 1);
        this.setSamplerUnit(this.fillShader.getHandle(), "SPIRV_Cross_CombinedTexture2Sampler0", 2);
    }

    public void shutdown()
    {
        if (this.uniformBufferID != 0)
        {
            glDeleteBuffers(this.uniformBufferID);
            this.uniformBufferID = 0;
        }

        this.fillShader.destroy();
        this.pathShader.destroy();

        this.textureIDs.forEach((id, glTextureID) -> glDeleteTextures(glTextureID));
        this.textureIDs.clear();

        this.renderBufferIDs.forEach((id, renderBufferData) -> renderBufferData.destroy());
        this.renderBufferIDs.clear();

        this.geometryDataMap.forEach((id, geometryData) -> geometryData.destroy());
        this.geometryDataMap.clear();
    }

    @Override
    public void beginSynchronize()
    {

    }

    @Override
    public void endSynchronize()
    {

    }

    @Override
    public int nextTextureId()
    {
        return this.nextTextureID.getAndIncrement();
    }

    @Override
    public void createTexture(int id, ULBitmap bitmap)
    {
        RenderSystem.assertOnRenderThread();

        int glTextureID = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, glTextureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        if (bitmap.isEmpty())
        {
            // FBO backing texture — null data upload. Use GL_RGBA as external format;
            // GL_BGRA with null ptr is invalid on strict drivers.
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bitmap.getWidth(), bitmap.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        else
        {
            BitmapFormat bitmapFormat = bitmap.getFormat();
            glPixelStorei(GL_UNPACK_ROW_LENGTH, bitmap.getRowBytes() / bitmapFormat.getBytesPerPixel());

            ByteBuffer pixels = bitmap.lockPixels();
            ByteBuffer pixelsCopy = this.copyPixels(pixels);
            bitmap.unlockPixels();

            if (bitmapFormat == BitmapFormat.A8_UNORM)
            {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, bitmap.getWidth(), bitmap.getHeight(), 0, GL_RED, GL_UNSIGNED_BYTE, pixelsCopy);

                // Swizzle RED → alpha so fillGlyph() reads .a correctly.
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_R, GL_ZERO);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_G, GL_ZERO);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, GL_ZERO);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_A, GL_RED);
            }
            else
            {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bitmap.getWidth(), bitmap.getHeight(), 0, GL_BGRA, GL_UNSIGNED_BYTE, pixelsCopy);
            }

            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        }

        glGenerateMipmap(GL_TEXTURE_2D);

        this.textureIDs.put(id, glTextureID);
    }

    @Override
    public void updateTexture(int id, ULBitmap bitmap)
    {
        RenderSystem.assertOnRenderThread();

        Integer glTextureID = this.textureIDs.get(id);
        if (glTextureID == null)
            return;

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, glTextureID);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        if (!bitmap.isEmpty())
        {
            BitmapFormat bitmapFormat = bitmap.getFormat();
            glPixelStorei(GL_UNPACK_ROW_LENGTH, bitmap.getRowBytes() / bitmapFormat.getBytesPerPixel());

            ByteBuffer pixels = bitmap.lockPixels();
            ByteBuffer pixelsCopy = copyPixels(pixels);
            bitmap.unlockPixels();

            // glTexImage2D handles resizes automatically (mutable storage).
            if (bitmapFormat == BitmapFormat.A8_UNORM)
            {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, bitmap.getWidth(), bitmap.getHeight(), 0, GL_RED, GL_UNSIGNED_BYTE, pixelsCopy);
            }
            else
            {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bitmap.getWidth(), bitmap.getHeight(), 0, GL_BGRA, GL_UNSIGNED_BYTE, pixelsCopy);
            }

            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glGenerateMipmap(GL_TEXTURE_2D);
        }
    }

    @Override
    public void destroyTexture(int id)
    {
        RenderSystem.assertOnRenderThread();

        Integer glTextureID = this.textureIDs.remove(id);
        if (glTextureID != null)
            RenderSystem.deleteTexture(glTextureID);
    }

    @Override
    public int nextRenderBufferId()
    {
        return this.nextRenderBufferID.getAndIncrement();
    }

    @Override
    public void createRenderBuffer(int id, ULRenderBuffer renderBuffer)
    {
        RenderSystem.assertOnRenderThread();

        this.renderBufferIDs.put(id, new RenderBufferData(-1, renderBuffer.textureID()));
    }

    @Override
    public void destroyRenderBuffer(int id)
    {
        RenderSystem.assertOnRenderThread();

        RenderBufferData renderBufferData = this.renderBufferIDs.remove(id);
        if (renderBufferData != null && renderBufferData.framebufferID >= 0)
            glDeleteFramebuffers(renderBufferData.framebufferID);
    }

    @Override
    public int nextGeometryId()
    {
        return this.nextGeometryID.getAndIncrement();
    }

    @Override
    public void createGeometry(int id, ULVertexBuffer vertices, ULIndexBuffer indices)
    {
        RenderSystem.assertOnRenderThread();

        int vertexBuffer = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, vertices.getData(), GL_DYNAMIC_DRAW);

        int indexBuffer = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.data(), GL_DYNAMIC_DRAW);

        // Vertex array is created lazily on first draw (getOrCreateVertexArray).
        this.geometryDataMap.put(id, new GeometryData(-1, vertexBuffer, indexBuffer, vertices.getFormat() == ULVertexBuffer.Format.QUAD));
    }

    @Override
    public void updateGeometry(int id, ULVertexBuffer vertices, ULIndexBuffer indices)
    {
        RenderSystem.assertOnRenderThread();

        GeometryData geometryData = this.geometryDataMap.get(id);
        if (geometryData == null)
            return;

        glBindBuffer(GL_ARRAY_BUFFER, geometryData.vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, vertices.getData(), GL_DYNAMIC_DRAW);

        ByteBuffer indexBufferData = indices.data();
        if (indexBufferData != null && indexBufferData.hasRemaining())
        {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, geometryData.indexBuffer);
            glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0, indexBufferData);
        }
    }

    @Override
    public void destroyGeometry(int id)
    {
        RenderSystem.assertOnRenderThread();

        GeometryData geometryData = this.geometryDataMap.remove(id);
        if (geometryData == null)
            return;

        geometryData.destroy();
    }

    @Override
    public void updateCommandList(ULCommand[] commands)
    {
        this.pendingCommandsList = (commands != null) ? commands : new ULCommand[0];
    }

    public void executeCommands()
    {
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDepthFunc(GL_NEVER);

        for (ULCommand command : this.pendingCommandsList)
        {
            if (command == null)
                continue;

            switch (command.type)
            {
                case DRAW_GEOMETRY -> this.drawGeometry(command);
                case CLEAR_RENDER_BUFFER -> this.clearRenderBuffer(command);
            }
        }

        this.pendingCommandsList = new ULCommand[0];

        glDisable(GL_SCISSOR_TEST);
        glDepthMask(true);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glUseProgram(0);
    }

    public Integer getGLTextureID(int ultralightTextureId)
    {
        return this.textureIDs.get(ultralightTextureId);
    }

    private void bindUniformBlock(int program, String name, int bindingPoint)
    {
        int uniformBlockIndex = glGetUniformBlockIndex(program, name);
        if (uniformBlockIndex != GL_INVALID_INDEX)
            glUniformBlockBinding(program, uniformBlockIndex, bindingPoint);
    }

    private void setSamplerUnit(int program, String name, int unit)
    {
        int uniformLocation = glGetUniformLocation(program, name);
        if (uniformLocation >= 0)
        {
            glUseProgram(program);
            glUniform1i(uniformLocation, unit);
            glUseProgram(0);
        }
    }

    private ByteBuffer copyPixels(ByteBuffer sourceBuffer)
    {
        return BufferUtils.createByteBuffer(sourceBuffer.remaining())
                .put(sourceBuffer)
                .flip();
    }

    private void drawGeometry(ULCommand command)
    {
        ULGPUState gpuState = command.gpuState;
        if (gpuState == null)
            return;

        GeometryData geometryData = this.geometryDataMap.get(command.geometryID);
        if (geometryData == null)
            return;

        this.bindRenderBuffer(gpuState.renderBufferId);
        glViewport(0, 0, gpuState.viewportWidth, gpuState.viewportHeight);

        boolean isPath = (gpuState.shaderType == ULShaderType.FILL_PATH);
        glUseProgram(isPath ? this.pathShader.getHandle() : this.fillShader.getHandle());

        this.uploadUniforms(gpuState);

        int vertexArray = this.getOrCreateVertexArray(command.geometryID);
        if (vertexArray < 0)
            return;

        glBindVertexArray(vertexArray);

        this.bindTexture(0, gpuState.texture1Id);
        this.bindTexture(1, gpuState.texture2Id);
        this.bindTexture(2, gpuState.texture3Id);

        // Scissor — flip Y from Ultralight top-left to GL bottom-left origin.
        if (gpuState.enableScissor && gpuState.scissorRect != null && !gpuState.scissorRect.isEmpty())
        {
            glEnable(GL_SCISSOR_TEST);

            int scissorY = gpuState.viewportHeight - gpuState.scissorRect.bottom;

            glScissor(gpuState.scissorRect.left, scissorY, gpuState.scissorRect.right - gpuState.scissorRect.left,
                    gpuState.scissorRect.bottom - gpuState.scissorRect.top);
        }
        else
        {
            glDisable(GL_SCISSOR_TEST);
        }

        if (gpuState.enableBlend)
        {
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glBlendEquation(GL_FUNC_ADD);
        }
        else
        {
            glDisable(GL_BLEND);
        }

        glDrawElements(GL_TRIANGLES, command.indicesCount, GL_UNSIGNED_INT, (long) command.indicesOffset * Integer.BYTES);
        glBindVertexArray(0);
    }

    private void clearRenderBuffer(ULCommand cmd)
    {
        ULGPUState state = cmd.gpuState;
        if (state == null)
            return;

        bindRenderBuffer(state.renderBufferId);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0f, 0f, 0f, 0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private void uploadUniforms(ULGPUState state)
    {
        this.uboStagingBuffer.clear();

        // State: [time, screenWidth, screenHeight, screenScale]
        this.uboStagingBuffer.position(UBO_OFFSET_STATE);
        this.uboStagingBuffer.putFloat(0f);
        this.uboStagingBuffer.putFloat(state.viewportWidth);
        this.uboStagingBuffer.putFloat(state.viewportHeight);
        this.uboStagingBuffer.putFloat(1f);

        // Transform = ortho(viewport) * state.transform
        this.uboStagingBuffer.position(UBO_OFFSET_TRANSFORM);
        float[] transform = (state.transform != null && state.transform.length == 16) ? state.transform : IDENTITY_MATRIX;
        float[] modelViewProjectionMatrix = this.multiplyByOrthographicMatrix(transform, state.viewportWidth, state.viewportHeight);
        for (float value : modelViewProjectionMatrix)
            this.uboStagingBuffer.putFloat(value);

        // Integer4 — not exposed by ULGPUState; zero.
        uboStagingBuffer.position(UBO_OFFSET_INTEGER4);
        for (int i = 0; i < 8; i++)
            uboStagingBuffer.putInt(0);

        // Scalar4
        this.uboStagingBuffer.position(UBO_OFFSET_SCALAR4);
        for (int i = 0; i < 8; i++)
            this.uboStagingBuffer.putFloat((state.uniformScalar != null && i < state.uniformScalar.length) ? state.uniformScalar[i] : 0f);

        // Vector
        this.uboStagingBuffer.position(UBO_OFFSET_VECTOR);
        for (int i = 0; i < 32; i++)
            this.uboStagingBuffer.putFloat((state.uniformVector != null && i < state.uniformVector.length) ? state.uniformVector[i] : 0f);

        // ClipData
        this.uboStagingBuffer.position(UBO_OFFSET_CLIPDATA);
        this.uboStagingBuffer.putInt(state.clipSize);
        this.uboStagingBuffer.putInt(0);
        this.uboStagingBuffer.putInt(0);
        this.uboStagingBuffer.putInt(0);

        // Clip (up to 8 × mat4 = 128 floats)
        this.uboStagingBuffer.position(UBO_OFFSET_CLIP);
        for (int i = 0; i < 128; i++)
        {
            boolean has = (state.clip != null) && (i / 16 < state.clipSize) && (i < state.clip.length);

            this.uboStagingBuffer.putFloat(has ? state.clip[i] : 0f);
        }

        this.uboStagingBuffer.rewind();

        glBindBuffer(GL_UNIFORM_BUFFER, this.uniformBufferID);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, this.uboStagingBuffer);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBufferID);
    }

    private int getOrCreateVertexArray(int geometryId)
    {
        GeometryData geometryData = this.geometryDataMap.get(geometryId);
        if (geometryData == null)
            return -1;

        if (geometryData.vertexArray >= 0)
            return geometryData.vertexArray;

        int vertexArray = glGenVertexArrays();
        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, geometryData.vertexBuffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, geometryData.indexBuffer);

        if (geometryData.isQuad)
        {
            this.setupQuadVertexAttribs();
        }
        else
        {
            this.setupPathVertexAttribs();
        }

        glBindVertexArray(0);

        this.geometryDataMap.put(geometryId, new GeometryData(vertexArray, geometryData.vertexBuffer, geometryData.indexBuffer, geometryData.isQuad));

        return vertexArray;
    }

    private void setupQuadVertexAttribs()
    {
        final int stride = 140;

        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, stride, 8);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 12);
        glVertexAttribPointer(3, 2, GL_FLOAT, false, stride, 20);
        glVertexAttribPointer(4, 4, GL_FLOAT, false, stride, 28);
        glVertexAttribPointer(5, 4, GL_FLOAT, false, stride, 44);
        glVertexAttribPointer(6, 4, GL_FLOAT, false, stride, 60);
        glVertexAttribPointer(7, 4, GL_FLOAT, false, stride, 76);
        glVertexAttribPointer(8, 4, GL_FLOAT, false, stride, 92);
        glVertexAttribPointer(9, 4, GL_FLOAT, false, stride, 108);
        glVertexAttribPointer(10, 4, GL_FLOAT, false, stride, 124);

        for (int i = 0; i <= 10; i++)
            glEnableVertexAttribArray(i);
    }

    private void setupPathVertexAttribs()
    {
        final int stride = 20;

        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, true, stride, 8);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 12);

        for (int i = 0; i <= 2; i++)
            glEnableVertexAttribArray(i);
    }

    private void bindTexture(int unit, int ultralightTextureId)
    {
        glActiveTexture(GL_TEXTURE0 + unit);
        if (ultralightTextureId == 0)
        {
            glBindTexture(GL_TEXTURE_2D, 0);
            return;
        }

        Integer glTextureID = this.textureIDs.get(ultralightTextureId);
        if (glTextureID == null)
            return;

        glBindTexture(GL_TEXTURE_2D, glTextureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private void bindRenderBuffer(int renderBufferId)
    {
        if (renderBufferId == 0)
        {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            return;
        }

        RenderBufferData renderBufferData = this.renderBufferIDs.get(renderBufferId);
        if (renderBufferData == null)
            return;

        if (renderBufferData.framebufferID < 0)
        {
            Integer texGLId = this.textureIDs.get(renderBufferData.textureID);
            if (texGLId == null)
            {
                NiagaraClientMod.LOGGER.error("Niagara GPU: bindRenderBuffer — texture {} not found", renderBufferData.textureID);
                return;
            }

            int glFramebufferID = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, glFramebufferID);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texGLId, 0);
            glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0});

            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE)
                NiagaraClientMod.LOGGER.error("Niagara GPU: Framebuffer {} incomplete — 0x{}", renderBufferId, Integer.toHexString(status));

            this.renderBufferIDs.put(renderBufferId, new RenderBufferData(glFramebufferID, renderBufferData.textureID));
        }
        else
        {
            glBindFramebuffer(GL_FRAMEBUFFER, renderBufferData.framebufferID);
        }
    }

    private float[] multiplyByOrthographicMatrix(float[] targetMatrix, int screenWidth, int screenHeight)
    {
        float pixelWidthScale = 2.0f / screenWidth;
        float pixelHeightScale = -2.0f / screenHeight;

        float[] orthographicMatrix = {
                pixelWidthScale, 0, 0, 0,
                0, pixelHeightScale, 0, 0,
                0, 0, -1, 0,
                -1, 1, 0, 1
        };

        float[] resultMatrix = new float[16];

        for (int column = 0; column < 4; column++)
        {
            for (int row = 0; row < 4; row++)
            {
                float sum = 0f;
                for (int k = 0; k < 4; k++)
                    sum += orthographicMatrix[k * 4 + row] * targetMatrix[column * 4 + k];

                resultMatrix[column * 4 + row] = sum;
            }
        }

        return resultMatrix;
    }

    private record RenderBufferData(int framebufferID, int textureID)
    {
        public void destroy()
        {
            if (this.framebufferID >= 0)
                glDeleteFramebuffers(this.framebufferID);
        }
    }

    private record GeometryData(int vertexArray, int vertexBuffer, int indexBuffer, boolean isQuad)
    {
        public void destroy()
        {
            if (this.vertexArray >= 0)
                GlStateManager._glDeleteVertexArrays(this.vertexArray);

            GlStateManager._glDeleteBuffers(this.vertexBuffer);
            GlStateManager._glDeleteBuffers(this.indexBuffer);
        }
    }
}
