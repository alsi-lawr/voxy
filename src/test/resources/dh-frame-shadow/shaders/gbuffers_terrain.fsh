#version 330 compatibility
uniform sampler2D gtexture;
uniform sampler2D lightmap;
varying vec2 texcoord;
varying vec2 lightcoord;
varying vec4 color;
/* DRAWBUFFERS:0 */
void main() {
    vec4 sampleColor = texture2D(gtexture, texcoord) * color;
    if (sampleColor.a < 0.1) discard;
    gl_FragData[0] = sampleColor * texture2D(lightmap, lightcoord);
}
