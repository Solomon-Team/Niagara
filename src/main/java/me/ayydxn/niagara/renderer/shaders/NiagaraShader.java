package me.ayydxn.niagara.renderer.shaders;

import com.mojang.blaze3d.platform.GlStateManager;
import me.ayydxn.niagara.NiagaraClientMod;
import net.minecraft.client.gl.ShaderStage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.lwjgl.opengl.GL20.*;

public class NiagaraShader
{
    private final String name;

    private int programID;

    public NiagaraShader(String name)
    {
        NiagaraClientMod.LOGGER.info("Loading shader '{}'...", name);

        this.name = name;

        String vertexShaderSource = this.loadSource(name, ShaderStage.Type.VERTEX);
        String fragmentShaderSource = this.loadSource(name, ShaderStage.Type.FRAGMENT);

        int vertexShader = this.compile(vertexShaderSource, ShaderStage.Type.VERTEX);
        int fragmentShader = this.compile(fragmentShaderSource, ShaderStage.Type.FRAGMENT);

        this.programID = this.createProgram(vertexShader, fragmentShader);
    }

    public void bind()
    {
        glUseProgram(this.programID);
    }

    public void destroy()
    {
        if (this.programID != 0)
        {
            GlStateManager.glDeleteProgram(this.programID);
            this.programID = 0;
        }
    }

    /**
     * Returns the raw OpenGL shader program ID.
     *
     * @return The raw OpenGL shader program ID.
     */
    public int getHandle()
    {
        return this.programID;
    }

    private String loadSource(String name, ShaderStage.Type type)
    {
        String shaderFilepath = "/assets/niagara/shaders/" + name + type.getFileExtension();

        try (InputStream inputStream = NiagaraShader.class.getResourceAsStream(shaderFilepath))
        {
            if (inputStream == null)
                throw new FileNotFoundException("Failed to load shader file '" + shaderFilepath + "'");

            return new String(inputStream.readAllBytes());
        }
        catch (IOException exception)
        {
            throw new RuntimeException(exception);
        }
    }

    private int compile(String source, ShaderStage.Type type)
    {
        int shaderID = GlStateManager.glCreateShader(this.getGLShaderType(type));

        GlStateManager.glShaderSource(shaderID, Collections.singletonList(source));
        GlStateManager.glCompileShader(shaderID);

        if (GlStateManager.glGetShaderi(shaderID, GL_COMPILE_STATUS) == GL_FALSE)
            throw new RuntimeException(String.format("Failed to compile %s shader for shader '%s': %s", type.getName(), this.name, glGetShaderInfoLog(shaderID)));

        return shaderID;
    }

    private int createProgram(int vertexShaderHandle, int fragmentShaderHandle)
    {
        int program = GlStateManager.glCreateProgram();

        GlStateManager.glAttachShader(program, vertexShaderHandle);
        GlStateManager.glAttachShader(program, fragmentShaderHandle);
        GlStateManager.glLinkProgram(program);

        if (GlStateManager.glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException(String.format("Failed to link program for shader '%s': %s", this.name, glGetProgramInfoLog(program)));

        return program;
    }

    private int getGLShaderType(ShaderStage.Type type)
    {
        return switch (type)
        {
            case VERTEX -> GL_VERTEX_SHADER;
            case FRAGMENT -> GL_FRAGMENT_SHADER;
        };
    }
}
