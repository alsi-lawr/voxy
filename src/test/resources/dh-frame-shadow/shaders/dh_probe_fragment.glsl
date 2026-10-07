uniform int renderStage;
varying vec4 probeColor;
/* DRAWBUFFERS:0 */
void main() { gl_FragData[0] = vec4(float(renderStage)/24.0, probeColor.gba); }
