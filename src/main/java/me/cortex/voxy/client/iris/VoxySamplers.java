package me.cortex.voxy.client.iris;

import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

import java.util.function.IntSupplier;

public class VoxySamplers {
    static SamplerHolder waterDepthSamplers(SamplerHolder samplers, IntSupplier completedOpaqueDepth) {
        return new SamplerHolder() {
            private IntSupplier depth(IntSupplier texture, String[] names) {
                for (String name : names) {
                    if (name.equals("dhDepthTex") || name.equals("dhDepthTex0") || name.equals("dhDepthTex1")) return completedOpaqueDepth;
                }
                return texture;
            }

            @Override
            public void addExternalSampler(int unit, String... names) { samplers.addExternalSampler(unit, names); }

            @Override
            public boolean hasSampler(String name) { return samplers.hasSampler(name); }

            @Override
            public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
                return samplers.addDefaultSampler(type, texture, notifier, sampler, names);
            }

            @Override
            public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
                return samplers.addDynamicSampler(type, depth(texture, names), sampler, names);
            }

            @Override
            public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
                return samplers.addDynamicSampler(type, depth(texture, names), notifier, sampler, names);
            }
        };
    }

    static SamplerHolder trackedSamplers(SamplerHolder samplers, java.util.Set<String> bound) {
        return new SamplerHolder() {
            private void record(String... names) {
                for (String name : names) if (samplers.hasSampler(name)) bound.add(name);
            }
            public boolean hasSampler(String name) { return samplers.hasSampler(name); }
            public void addExternalSampler(int unit, String... names) {
                samplers.addExternalSampler(unit, names);
                record(names);
            }
            public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
                boolean added = samplers.addDefaultSampler(type, texture, notifier, sampler, names);
                if (added) record(names);
                return added;
            }
            public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
                boolean added = samplers.addDynamicSampler(type, texture, sampler, names);
                if (added) record(names);
                return added;
            }
            public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
                boolean added = samplers.addDynamicSampler(type, texture, notifier, sampler, names);
                if (added) record(names);
                return added;
            }
        };
    }

    public static int prototypeDepth(IrisRenderingPipeline pipeline, boolean opaque) {
        var data = ((IGetIrisVoxyPipelineData)pipeline).voxy$getPipelineData();
        if (data == null || data.thePipeline == null) return 0;
        var depth = opaque ? data.thePipeline.fb.getDepthTex() : data.thePipeline.fbTranslucent.getDepthTex();
        return depth == null ? 0 : depth.id;
    }

    public static void addSamplers(IrisRenderingPipeline pipeline, SamplerHolder samplers) {
        var patchData = ((IGetVoxyPatchData)pipeline).voxy$getPatchData();
        if (patchData != null) {
            String[] opaqueNames = new String[]{"vxDepthTexOpaque"};
            String[] translucentNames = new String[]{"vxDepthTexTrans"};
            /*
            if (IrisShaderPatch.IMPERSONATE_DISTANT_HORIZONS) {
                opaqueNames = new String[]{"vxDepthTexOpaque", "dhDepthTex1"};
                translucentNames = new String[]{"vxDepthTexTrans", "dhDepthTex", "dhDepthTex0"};
            }*/

            //TODO replace ()->0 with the actual depth texture id
            samplers.addDynamicSampler(TextureType.TEXTURE_2D, () -> {
                var pipeData = ((IGetIrisVoxyPipelineData)pipeline).voxy$getPipelineData();
                if (pipeData == null) {
                    return 0;
                }
                if (pipeData.thePipeline == null) {
                    return 0;
                }

                //In theory the first frame could be null
                var dt = pipeData.thePipeline.fb.getDepthTex();
                if (dt == null) {
                    return 0;
                }
                return dt.id;
            }, ((IGetIrisVoxyPipelineData) pipeline).voxy$getDepthSampler(), opaqueNames);

            samplers.addDynamicSampler(TextureType.TEXTURE_2D, () -> {
                var pipeData = ((IGetIrisVoxyPipelineData)pipeline).voxy$getPipelineData();
                if (pipeData == null) {
                    return 0;
                }
                if (pipeData.thePipeline == null) {
                    return 0;
                }
                //In theory the first frame could be null
                var dt = pipeData.thePipeline.fbTranslucent.getDepthTex();
                if (dt == null) {
                    return 0;
                }
                return dt.id;
            }, ((IGetIrisVoxyPipelineData) pipeline).voxy$getDepthSampler(), translucentNames);
        }
    }
}
