#version 330 compatibility
varying vec4 probeColor;
/* DRAWBUFFERS:1 */
void main() { gl_FragData[0] = probeColor; }
