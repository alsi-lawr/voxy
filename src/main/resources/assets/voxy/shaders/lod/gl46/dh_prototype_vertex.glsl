#version 460 core
#define PATCHED_SHADER
#define QUAD_BUFFER_BINDING 1
#define MODEL_BUFFER_BINDING 3
#define MODEL_COLOUR_BUFFER_BINDING 4
#define POSITION_SCRATCH_BINDING 5
#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/block_model.glsl>
#import <voxy:lod/gl46/bindings.glsl>
#import <voxy:lod/quad_util.glsl>

void voxyPrototypeInit() {
    Quad raw = quadData[uint(gl_VertexID) >> 2];
    QuadData quad;
    setupQuad(quad, raw, positionBuffer[gl_BaseInstance], false);
    uint corner = uint(gl_VertexID) & 3u;
    vec2 mask = vec2((corner >> 1) & 1u, corner & 1u) * quad.lodScale;
    _vert_position = quad.basePoint + swizzelDataAxis(quad.axis, vec3(quad.quadSizeAddin * mask, 0.0)) - cameraSubPos;
    uint face = extractFace(raw);
    BlockModel model = modelData[extractStateId(raw)];
    _vert_normal = irisNormals[face];
    _vert_tex_light_coord = getLightmapUv(extractLightId(raw));
    dhMaterialId = int(model._pad[6]);
    _vert_color = unpackUnorm4x8(model._pad[face]);
    uint tint = model.colourTint;
    if (modelHasBiomeLUT(model)) tint = colourData[tint + extractBiomeId(raw)];
    if (tint != uint(-1) && faceTintState(model.faceData[face]) != 0u) {
        _vert_color.rgb *= vec3(float((tint >> 16) & 255u), float((tint >> 8) & 255u), float(tint & 255u)) / 255.0;
    }
}
