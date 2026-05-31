#version 410 core

in  vec2 v_UV;
out vec4 o_Color;

uniform sampler2D u_Texture;
uniform vec4 u_UVRect;

void main() {
    vec2 uv = mix(u_UVRect.xy, u_UVRect.zw, v_UV);

    o_Color = texture(u_Texture, uv);
}