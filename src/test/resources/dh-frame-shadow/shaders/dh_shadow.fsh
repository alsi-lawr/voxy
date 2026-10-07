#version 330 compatibility
uniform int renderStage;
varying vec4 lodColor;
/* DRAWBUFFERS:01 */
void main() { gl_FragData[0] = vec4(0.0,1.0,0.0,1.0); gl_FragData[1] = vec4(float(renderStage)/24.0,lodColor.gba); }
