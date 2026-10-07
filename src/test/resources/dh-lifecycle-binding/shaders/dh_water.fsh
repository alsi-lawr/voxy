#version 330 compatibility
varying vec4 probeColor;
uniform sampler2D voxyUnboundSampler;
/* DRAWBUFFERS:0 */
void main() { gl_FragData[0] = texture2D(voxyUnboundSampler, vec2(0.5)) + probeColor; }
