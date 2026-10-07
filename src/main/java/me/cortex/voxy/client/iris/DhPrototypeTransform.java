package me.cortex.voxy.client.iris;

import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;

import java.util.List;

final class DhPrototypeTransform {
    record Stages(String vertex, String fragment) {}
    static final class Unsupported extends RuntimeException {
        final DhTranslationFailure.Code code;
        Unsupported(DhTranslationFailure.Code code, String detail) { super(detail); this.code = code; }
    }

    private static void validate(String source, boolean vertex) {
        var transformer = new SingleASTTransformer<>();
        transformer.setTransformation((tree, root) -> {
            for (var name : List.of("_vert_init", "_vert_position", "_vert_color", "_vert_normal", "_vert_tex_light_coord", "getVertexPosition", "modelOffset", "mircoOffset", "vPosition")) {
                if (root.identifierIndex.has(name)) throw new Unsupported(DhTranslationFailure.Code.RESERVED_IDENTIFIER, "source uses the pinned Iris DH internal identifier " + name);
            }
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.class)
                    .filter(node -> vertex && (node.storageType == io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType.IN
                            || node.storageType == io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType.ATTRIBUTE))
                    .filter(node -> node.getAncestor(DeclarationExternalDeclaration.class) != null)
                    .filter(node -> {
                        var declaration = node.getAncestor(io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration.class);
                        return declaration == null || declaration.getMembers().stream().anyMatch(member -> root.identifierIndex.getStream(member.getName().getName())
                                .anyMatch(id -> id.getParent() instanceof io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression));
                    })
                    .findFirst().ifPresent(node -> { throw new Unsupported(DhTranslationFailure.Code.UNSUPPORTED_VERTEX_INPUT, "explicit vertex attributes have no packed-quad input binding"); });
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.declaration.InterfaceBlockDeclaration.class).forEach(block -> {
                var qualifiers = block.getTypeQualifier().getParts();
                boolean storage = qualifiers.stream().anyMatch(part -> part instanceof io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier qualifier
                        && (qualifier.storageType == io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType.BUFFER
                        || qualifier.storageType == io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType.UNIFORM));
                if (storage) throw new Unsupported(DhTranslationFailure.Code.UNSUPPORTED_RESOURCE_BINDING, "authored buffer blocks require explicit ownership separate from Voxy's scene/quad/model bindings");
            });
        });
        transformer.transform(source);
    }

    static Stages transform(String name, String vertex, String fragment, it.unimi.dsi.fastutil.objects.Object2ObjectMap<net.irisshaders.iris.helpers.Tri<String, net.irisshaders.iris.gl.texture.TextureType, net.irisshaders.iris.shaderpack.texture.TextureStage>, String> textureMap) {
        validate(vertex, true);
        validate(fragment, false);
        var iris = TransformPatcher.patchDHTerrain(name, vertex, null, null, null, fragment, textureMap);
        return new Stages(lower(iris.get(PatchShaderType.VERTEX)), iris.get(PatchShaderType.FRAGMENT));
    }

    static String lower(String irisVertex) {
        String abi = JcppProcessor.glslPreprocessSource(ShaderLoader.parse("voxy:lod/gl46/dh_prototype_vertex.glsl"), List.of());
        var transformer = new SingleASTTransformer<>();
        transformer.setTransformation((tree, root) -> {
            tree.getVersionStatement().version = Version.GLSL46;
            var identifiers = root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.Identifier.class).map(io.github.douira.glsl_transformer.ast.node.Identifier::getName).toList();
            int suffix = 0;
            String prefix;
            do { prefix = "voxyDh" + suffix++; } while (hasPrefix(identifiers, prefix));
            String namespacedAbi = namespace(abi, prefix);
            var body = tree.getOneFunctionDefinitionBody("_vert_init");
            body.replaceByAndDelete(transformer.parseStatement(root, "{ " + prefix + "voxyPrototypeInit(); }"));
            for (var attribute : List.of("vPosition", "iris_color", "irisExtra")) {
                root.identifierIndex.getStream(attribute).map(id -> id.getAncestor(DeclarationExternalDeclaration.class))
                        .filter(java.util.Objects::nonNull).distinct().toList().forEach(node -> node.detachAndDelete());
            }
            var globals = List.of("_vert_position", "_vert_tex_light_coord", "dhMaterialId", "_vert_color", "_vert_normal", "irisNormals")
                    .stream().flatMap(root.identifierIndex::getStream).map(id -> id.getAncestor(DeclarationExternalDeclaration.class))
                    .filter(java.util.Objects::nonNull).distinct().toList();
            globals.forEach(node -> node.detach());
            var abiTree = transformer.parseTranslationUnit(root, namespacedAbi);
            var declarations = new java.util.ArrayList<io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration>(globals);
            declarations.addAll(abiTree.getChildren());
            tree.injectNodes(ASTInjectionPoint.BEFORE_DECLARATIONS, declarations);
            root.replaceReferenceExpressions(transformer, "modelOffset", "vec3(0.0)");
        });
        return transformer.transform(irisVertex);
    }
    private static boolean hasPrefix(List<String> identifiers, String prefix) {
        return identifiers.stream().anyMatch(name -> name.startsWith(prefix));
    }

    private static String namespace(String abi, String prefix) {
        var transformer = new SingleASTTransformer<>();
        transformer.setTransformation((tree, root) -> {
            var names = new java.util.LinkedHashSet<String>();
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember.class).filter(node -> node.getAncestor(DeclarationExternalDeclaration.class) != null).forEach(node -> names.add(node.getName().getName()));
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.type.specifier.FunctionPrototype.class).forEach(node -> names.add(node.getName().getName()));
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.type.struct.StructSpecifier.class).forEach(node -> { if (node.getName() != null) names.add(node.getName().getName()); });
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.type.struct.StructDeclarator.class).forEach(node -> names.add(node.getName().getName()));
            root.nodeIndex.getStream(io.github.douira.glsl_transformer.ast.node.declaration.InterfaceBlockDeclaration.class).forEach(node -> names.add(node.getBlockName().getName()));
            for (var name : names) root.rename(name, prefix + name);
        });
        return transformer.transform(abi);
    }

}
