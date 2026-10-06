package me.cortex.voxy.client.iris;

import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import java.util.List;

final class DhPrototypeTransform {
    record Stages(String vertex, String fragment) {}

    static Stages transform(ProgramSource source, IrisRenderingPipeline pipeline) {
        return transform(source, pipeline.getTextureMap());
    }

    static Stages transform(ProgramSource source, it.unimi.dsi.fastutil.objects.Object2ObjectMap<net.irisshaders.iris.helpers.Tri<String, net.irisshaders.iris.gl.texture.TextureType, net.irisshaders.iris.shaderpack.texture.TextureStage>, String> textureMap) {
        if (source.getGeometrySource().isPresent() || source.getTessControlSource().isPresent() || source.getTessEvalSource().isPresent()) {
            throw new IllegalStateException("VOXY-003 probe cannot submit geometry/tessellation stages: " + source.getName());
        }
        return transform(source.getName(), source.getVertexSource().orElseThrow(), source.getFragmentSource().orElseThrow(), textureMap);
    }

    static Stages transform(String name, String vertex, String fragment, it.unimi.dsi.fastutil.objects.Object2ObjectMap<net.irisshaders.iris.helpers.Tri<String, net.irisshaders.iris.gl.texture.TextureType, net.irisshaders.iris.shaderpack.texture.TextureStage>, String> textureMap) {
        var iris = TransformPatcher.patchDHTerrain(name, vertex, null, null, null, fragment, textureMap);
        return new Stages(lower(iris.get(PatchShaderType.VERTEX)), iris.get(PatchShaderType.FRAGMENT));
    }

    static String lower(String irisVertex) {
        String abi = namespace(JcppProcessor.glslPreprocessSource(ShaderLoader.parse("voxy:lod/gl46/dh_prototype_vertex.glsl"), List.of()));
        var transformer = new SingleASTTransformer<>();
        transformer.setTransformation((tree, root) -> {
            tree.getVersionStatement().version = Version.GLSL46;
            var body = tree.getOneFunctionDefinitionBody("_vert_init");
            body.replaceByAndDelete(transformer.parseStatement(root, "{ voxyPrototypeInit(); }"));
            for (var attribute : List.of("vPosition", "iris_color", "irisExtra")) {
                root.identifierIndex.getStream(attribute).map(id -> id.getAncestor(DeclarationExternalDeclaration.class))
                        .filter(java.util.Objects::nonNull).distinct().toList().forEach(node -> node.detachAndDelete());
            }
            var globals = List.of("_vert_position", "_vert_tex_light_coord", "dhMaterialId", "_vert_color", "_vert_normal", "irisNormals")
                    .stream().flatMap(root.identifierIndex::getStream).map(id -> id.getAncestor(DeclarationExternalDeclaration.class))
                    .filter(java.util.Objects::nonNull).distinct().toList();
            globals.forEach(node -> node.detach());
            var abiTree = transformer.parseTranslationUnit(root, abi);
            var declarations = new java.util.ArrayList<io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration>(globals);
            declarations.addAll(abiTree.getChildren());
            tree.injectNodes(ASTInjectionPoint.BEFORE_DECLARATIONS, declarations);
            root.replaceReferenceExpressions(transformer, "modelOffset", "vec3(0.0)");
        });
        return transformer.transform(irisVertex);
    }
    private static String namespace(String abi) {
        var transformer = new SingleASTTransformer<>();
        transformer.setTransformation((tree, root) -> {
            var names = new java.util.LinkedHashSet<String>();
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember.class).filter(node -> node.getAncestor(DeclarationExternalDeclaration.class) != null).forEach(node -> names.add(node.getName().getName()));
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.type.specifier.FunctionPrototype.class).forEach(node -> names.add(node.getName().getName()));
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.type.struct.StructSpecifier.class).forEach(node -> { if (node.getName() != null) names.add(node.getName().getName()); });
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.type.struct.StructDeclarator.class).forEach(node -> names.add(node.getName().getName()));
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.declaration.InterfaceBlockDeclaration.class).forEach(node -> names.add(node.getBlockName().getName()));
            for (var name : names) if (!name.startsWith("voxyPrototype")) root.rename(name, "voxyPrototype_" + name);
        });
        return transformer.transform(abi);
    }

}
