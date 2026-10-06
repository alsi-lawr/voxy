package me.cortex.voxy.client.core.model.bakery;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.common.util.UnsafeUtil;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureImage;
import static org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL12.GL_PACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING;

public class SoftwareModelTextureBakery {
    //Note: the first bit of metadata is if alpha discard is enabled
    private static final Matrix4f[] VIEWS = new Matrix4f[6];

    private final ReuseVertexConsumer opaqueVC = new ReuseVertexConsumer();
    private final ReuseVertexConsumer translucentVC = new ReuseVertexConsumer(1/*has discard*/);
    private final SoftwareRasterizer rasterizer = new SoftwareRasterizer(ModelFactory.MODEL_TEXTURE_SIZE);

    private final BlockRenderDispatcher fr;
    private FabricModelBakery fabricBakery;
    public SoftwareModelTextureBakery() {
        this.fr = Minecraft.getInstance().getBlockRenderer();
    }

    public void setupTexture() {
        var tex = Minecraft.getInstance().getModelManager().getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        int width = glGetTextureLevelParameteri(tex.getId(), 0, GL_TEXTURE_WIDTH);
        int height = glGetTextureLevelParameteri(tex.getId(), 0, GL_TEXTURE_HEIGHT);

        //Just do it ourselves as doing it with b3d has some issues, (doing it ourselves is also just much much much shorter)
        var texture = new int[width * height];

        glFlush();
        glFinish();
        int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        int imageHeight = glGetInteger(GL_PACK_IMAGE_HEIGHT);
        int skipRows = glGetInteger(GL_PACK_SKIP_ROWS);
        int skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        int alignment = glGetInteger(GL_PACK_ALIGNMENT);
        try {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glPixelStorei(GL_PACK_ROW_LENGTH, width);
            glPixelStorei(GL_PACK_IMAGE_HEIGHT, 0);
            glPixelStorei(GL_PACK_SKIP_ROWS, 0);
            glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_PACK_ALIGNMENT, 4);
            glGetTextureImage(tex.getId(), 0, GL_RGBA, GL_UNSIGNED_BYTE, texture);
        } finally {
            glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_PACK_IMAGE_HEIGHT, imageHeight);
            glPixelStorei(GL_PACK_SKIP_ROWS, skipRows);
            glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels);
            glPixelStorei(GL_PACK_ALIGNMENT, alignment);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
        }
        this.rasterizer.setSamplerTexture(texture, width, height);
    }

    static final class ModelBlockView implements RenderAttachedBlockView {
        private final BlockState state;

        ModelBlockView(BlockState state) { this.state = state; }

        @Override public float getShade(Direction direction, boolean shade) { return 1; }
        @Override public LevelLightEngine getLightEngine() { return null; }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return -1; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public Object getBlockEntityRenderAttachment(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return pos.equals(BlockPos.ZERO) ? state : Blocks.AIR.defaultBlockState(); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 0; }
        @Override public int getMinBuildHeight() { return 0; }
    }

    private void bakeBlockModel(BlockState state) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return;//Dont bake if invisible
        }
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        var fabricModel = (net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel) model;
        if (!fabricModel.isVanillaAdapter()) {
            if (this.fabricBakery == null) this.fabricBakery = new FabricModelBakery(this.opaqueVC, this.translucentVC);
            this.fabricBakery.bake(fabricModel, new ModelBlockView(state), state);
            return;
        }
        var layer = ItemBlockRenderTypes.getChunkRenderType(state);
        var consumer = layer == RenderType.translucent() ? this.translucentVC : this.opaqueVC;
        boolean hasDiscard = layer != RenderType.solid();
        consumer.anyDarkendTex |= layer == RenderType.cutoutMipped();
        var random = new SingleThreadedRandomSource(42L);
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
            random.setSeed(42L);
            for (var quad : model.getQuads(state, direction, random)) {
                consumer.quad(quad, state.is(BlockTags.LEAVES), hasDiscard);
            }
        }
    }


    private void bakeFluidState(BlockState state, int face) {
        var layer = ItemBlockRenderTypes.getRenderLayer(state.getFluidState());
        this.opaqueVC.setDefaultMeta(layer == RenderType.cutout() || layer == RenderType.cutoutMipped() ? 1 : 0);
        this.fr.renderLiquid(BlockPos.ZERO, new BlockAndTintGetter() {
            @Override
            public LevelLightEngine getLightEngine() {
                return null;
            }

            @Override
            public int getBrightness(LightLayer type, BlockPos pos) {
                return 0;
            }

            @Override
            public float getShade(Direction direction, boolean shade) {
                return 1.0f;
            }

            @Override
            public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
                //This is such a stupid and bad hack, we can inject tinting state here since this is called
                // before the quad is added
                //TODO: need to make a quad once tinting thing
                translucentVC.setDefaultMeta(translucentVC.getDefaultMeta()|4);//Tinting
                opaqueVC.setDefaultMeta(opaqueVC.getDefaultMeta()|4);//Tinting
                return -1;
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                if (shouldReturnAirForFluid(pos, face)) {
                    return Blocks.AIR.defaultBlockState();
                }

                //Fixme:
                // This makes it so that the top face of water is always air, if this is commented out
                //  the up block will be a liquid state which makes the sides full
                // if this is uncommented, that issue is fixed but e.g. stacking water layers ontop of eachother
                //  doesnt fill the side of the block

                //if (pos.getY() == 1) {
                //    return Blocks.AIR.getDefaultState();
                //}
                return state;
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                if (shouldReturnAirForFluid(pos, face)) {
                    return Blocks.AIR.defaultBlockState().getFluidState();
                }

                return state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }
        }, layer == RenderType.translucent()
            ? this.translucentVC : this.opaqueVC, state, state.getFluidState());
        this.translucentVC.setDefaultMeta(0);//Reset default meta
        this.opaqueVC.setDefaultMeta(0);//Reset default meta
    }

    private static boolean shouldReturnAirForFluid(BlockPos pos, int face) {
        var fv = Direction.from3DDataValue(face).getNormal();
        int dot = fv.getX()*pos.getX() + fv.getY()*pos.getY() + fv.getZ()*pos.getZ();
        return dot >= 1;
    }

    public void free() {
        this.opaqueVC.free();
        this.translucentVC.free();
    }

    private static final long SINGLE_FACE_OUTPUT_SIZE = (ModelFactory.MODEL_TEXTURE_SIZE * ModelFactory.MODEL_TEXTURE_SIZE)*8;
    //The outputBuffer layout is different from the non software rasterized ModelTextureBakery
    // in this version the values are simply appended (0,0),(1,0),(2,0),(0,1),(1,1),(2,1)

    public int renderToOutput(BlockState state, long outputBuffer) {
        return renderToOutput(state, outputBuffer, false);
    }

    public int renderToOutput(BlockState state, long outputBuffer, boolean rasterAsUV) {
        MemoryUtil.memSet(outputBuffer,0,16*16*8*6);


        boolean isBlock = true;
        if (state.getBlock() instanceof LiquidBlock) {
            isBlock = false;
        }

        //TODO: support block model entities
        //BakedBlockEntityModel bbem = null;
        if (state.hasBlockEntity()) {
            //bbem = BakedBlockEntityModel.bake(state);
        }

        boolean isAnyShaded = false;
        boolean isAnyDarkend = false;
        boolean anyTranslucent = false;
        boolean anyDiscard = false;
        if (isBlock) {
            this.opaqueVC.reset();
            this.translucentVC.reset();
            this.bakeBlockModel(state);
            isAnyShaded |= this.opaqueVC.anyShaded|this.translucentVC.anyShaded;
            isAnyDarkend |= this.opaqueVC.anyDarkendTex|this.translucentVC.anyDarkendTex;
            anyTranslucent |= !this.translucentVC.isEmpty();
            anyDiscard |= this.opaqueVC.anyDiscard;
            if (!(this.opaqueVC.isEmpty()&&this.translucentVC.isEmpty())) {//only render if there... is shit to render
                for (int i = 0; i < VIEWS.length; i++) {
                    this.rasterizer.setFaceCull(i==1||i==2||i==4);
                    this.rasterizer.clear();
                    this.rasterizer.setUVRaster(rasterAsUV);
                    this.rasterizer.setBlending(false);
                    this.rasterizer.raster(VIEWS[i], this.opaqueVC);
                    this.rasterizer.setBlending(!rasterAsUV);
                    this.rasterizer.raster(VIEWS[i], this.translucentVC);
                    UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer+(SINGLE_FACE_OUTPUT_SIZE*i));
                }
            }
        } else {//Is fluid, slow path :(

            if (!(state.getBlock() instanceof LiquidBlock)) throw new IllegalStateException();
            for (int i = 0; i < VIEWS.length; i++) {
                this.opaqueVC.reset();
                this.translucentVC.reset();
                this.bakeFluidState(state, i);
                if (this.opaqueVC.isEmpty()&&this.translucentVC.isEmpty()) continue;
                isAnyShaded |= this.opaqueVC.anyShaded|this.translucentVC.anyShaded;
                isAnyDarkend |= this.opaqueVC.anyDarkendTex|this.translucentVC.anyDarkendTex;
                anyTranslucent |= !this.translucentVC.isEmpty();
                anyDiscard |= this.opaqueVC.anyDiscard;

                this.rasterizer.setFaceCull(i==1||i==2||i==4);

                //The projection matrix
                this.rasterizer.clear();
                this.rasterizer.setUVRaster(rasterAsUV);
                this.rasterizer.setBlending(false);
                this.rasterizer.raster(VIEWS[i], this.opaqueVC);
                this.rasterizer.setBlending(!rasterAsUV);
                this.rasterizer.raster(VIEWS[i], this.translucentVC);
                UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer+(SINGLE_FACE_OUTPUT_SIZE*i));
            }
        }

        return (isAnyShaded?1:0)|(isAnyDarkend?2:0)|(anyTranslucent?4:0)|(anyDiscard?8:0);
    }




    static {
        //the face/direction is the face (e.g. down is the down face)
        addView(0, -90,0, 0, 0);//Direction.DOWN
        addView(1, 90,0, 0, 0b100);//Direction.UP

        addView(2, 0,180, 0, 0b001);//Direction.NORTH
        addView(3, 0,0, 0, 0);//Direction.SOUTH

        addView(4, 0,90, 270, 0b100);//Direction.WEST
        addView(5, 0,270, 270, 0);//Direction.EAST
    }

    private static void addView(int i, float pitch, float yaw, float rotation, int flip) {
        var stack = new PoseStack();
        stack.translate(0.5f,0.5f,0.5f);
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(0,0,1), rotation));
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(1,0,0), pitch));
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(0,1,0), yaw));
        stack.mulPoseMatrix(new Matrix4f().scale(1-2*(flip&1), 1-(flip&2), 1-((flip>>1)&2)));
        stack.translate(-0.5f,-0.5f,-0.5f);
        var mat = new Matrix4f(stack.last().pose());

        mat = new Matrix4f().set(
                        2,0,0,0,
                        0,2,0,0,
                        0,0,-2,0,
                        -1,-1,1,1)
                .mul(mat);
        VIEWS[i] = mat;
    }

    private static Quaternionf makeQuatFromAxisExact(Vector3f vec, float angle) {
        angle = (float) Math.toRadians(angle);
        float hangle = angle / 2.0f;
        float sinAngle = (float) Math.sin(hangle);
        float invVLength = (float) (1/Math.sqrt(vec.lengthSquared()));
        return new Quaternionf(vec.x * invVLength * sinAngle,
                vec.y * invVLength * sinAngle,
                vec.z * invVLength * sinAngle,
                Math.cos(hangle));
    }
}
