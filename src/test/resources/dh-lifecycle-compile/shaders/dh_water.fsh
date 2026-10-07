#version 330 compatibility
varying vec4 probeColor;
/* DRAWBUFFERS:0 */
void main() { gl_FragData[0] = voxyMissingCompileSymbol + probeColor; }
