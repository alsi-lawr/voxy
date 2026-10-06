uniform mat4 dhProjection;
varying vec4 probeColor;
void main() {
    probeColor = gl_Color;
    gl_Position = dhProjection * gl_ModelViewMatrix * gl_Vertex;
}
