#version 330 compatibility
uniform sampler2D colortex0;
uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
uniform sampler2D shadowcolor0;
uniform sampler2D dhDepthTex0;
uniform sampler2D dhDepthTex1;
varying vec2 texcoord;
void main() {
    if (texcoord.x < 0.25) gl_FragColor = texture2D(shadowcolor0, vec2(texcoord.x*4.0,texcoord.y));
    else if (texcoord.x < 0.5) gl_FragColor = vec4(vec3(texture2D(shadowtex0,vec2((texcoord.x-.25)*4.0,texcoord.y)).r),1.0);
    else if (texcoord.x < 0.75) gl_FragColor = vec4(vec3(texture2D(shadowtex1,vec2((texcoord.x-.5)*4.0,texcoord.y)).r),1.0);
    else {
        vec2 uv=vec2((texcoord.x-.75)*4.0,texcoord.y);
        float a=texture2D(dhDepthTex0,uv).r,b=texture2D(dhDepthTex1,uv).r;
        gl_FragColor=vec4(vec3(a),1.0);
        if(a<b-0.00001) gl_FragColor=vec4(0.0,0.0,1.0,1.0);
    }
}
