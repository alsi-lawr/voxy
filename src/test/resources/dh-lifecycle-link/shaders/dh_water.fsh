#version 330 compatibility
varying vec3 probeColor;
/* DRAWBUFFERS:0 */
void main() { gl_FragData[0] = vec4(probeColor, 1.0); }
