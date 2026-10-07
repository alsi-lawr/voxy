#version 330 compatibility
const int shadowMapResolution = 1024;
const float shadowDistance = 256.0;
void main() { gl_Position = ftransform(); }
