#version 330 compatibility
varying vec4 lodColor;
void main() { lodColor = gl_Color; gl_Position = ftransform(); }
