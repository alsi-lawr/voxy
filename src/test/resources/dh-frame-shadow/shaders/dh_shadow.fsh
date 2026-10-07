#version 330 compatibility
varying vec4 lodColor;
/* DRAWBUFFERS:01 */
void main() { gl_FragData[0] = vec4(0.0,1.0,0.0,1.0); gl_FragData[1] = lodColor; }
