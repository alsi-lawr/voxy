#version 330 compatibility
varying vec2 texcoord;
varying vec2 lightcoord;
varying vec4 color;
void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lightcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    color = gl_Color;
    gl_Position = ftransform();
}
