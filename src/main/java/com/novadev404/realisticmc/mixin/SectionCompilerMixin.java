package com.novadev404.realisticmc.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.novadev404.realisticmc.RealisticMCClient;
import com.novadev404.realisticmc.terrain.BilateralTerrainMesh;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @Shadow
    @Final
    private boolean ambientOcclusion;

    @Shadow
    @Final
    private boolean cutoutLeaves;

    @Shadow
    @Final
    private BlockStateModelSet blockModelSet;

    @Shadow
    @Final
    private FluidStateModelSet fluidModelSet;

    @Shadow
    @Final
    private BlockColors blockColors;

    @Shadow
    @Final
    private BlockEntityRenderDispatcher blockEntityRenderer;

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void realisticmc$compileWithSmoothing(
        final SectionPos sectionPos,
        final RenderSectionRegion region,
        final VertexSorting vertexSorting,
        final SectionBufferBuilderPack builders,
        final CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        if (!RealisticMCClient.smoothTerrainRuntimeReady) {
            return;
        }

        cir.setReturnValue(this.realisticmc$compileReplacement(sectionPos, region, vertexSorting, builders));
    }

    private SectionCompiler.Results realisticmc$compileReplacement(
        final SectionPos sectionPos,
        final RenderSectionRegion region,
        final VertexSorting vertexSorting,
        final SectionBufferBuilderPack builders
    ) {
        SectionCompiler.Results results = new SectionCompiler.Results();
        BlockPos minPos = sectionPos.origin();
        BlockPos maxPos = minPos.offset(15, 15, 15);
        VisGraph visGraph = new VisGraph();
        BlockModelLighter.enableCaching();
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true, this.blockColors);
        FluidRenderer fluidRenderer = new FluidRenderer(this.fluidModelSet);
        Map<ChunkSectionLayer, BufferBuilder> startedLayers = new EnumMap<>(ChunkSectionLayer.class);
        BilateralTerrainMesh.Mesh smoothMesh = RealisticMCClient.smoothTerrainRuntimeReady ? BilateralTerrainMesh.buildSection(region, sectionPos) : new BilateralTerrainMesh.Mesh();

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, quad.materialInfo().layer());
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };
        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };
        FluidRenderer.Output fluidOutput = layer -> this.getOrBeginLayer(startedLayers, builders, layer);

        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockState blockState = region.getBlockState(pos);
            if (!blockState.isAir()) {
                try {
                    if (blockState.isSolidRender()) {
                        visGraph.setOpaque(pos);
                    }

                    if (blockState.hasBlockEntity()) {
                        BlockEntity blockEntity = region.getBlockEntity(pos);
                        if (blockEntity != null) {
                            this.handleBlockEntity(results, blockEntity);
                        }
                    }

                    FluidState fluidState = blockState.getFluidState();
                    if (!fluidState.isEmpty()) {
                        fluidRenderer.tesselate(region, pos, fluidOutput, blockState, fluidState);
                    }

                    if (blockState.getRenderShape() == RenderShape.MODEL && !smoothMesh.hiddenBlocks().contains(pos.asLong())) {
                        blockRenderer.tesselateBlock(
                            ModelBlockRenderer.forceOpaque(this.cutoutLeaves, blockState) ? opaqueQuadOutput : quadOutput,
                            SectionPos.sectionRelative(pos.getX()),
                            SectionPos.sectionRelative(pos.getY()),
                            SectionPos.sectionRelative(pos.getZ()),
                            region,
                            pos,
                            blockState,
                            this.blockModelSet.get(blockState),
                            blockState.getSeed(pos)
                        );
                    }
                } catch (Throwable t) {
                    CrashReport report = CrashReport.forThrowable(t, "Tesselating block in world");
                    CrashReportCategory category = report.addCategory("Block being tesselated");
                    CrashReportCategory.populateBlockDetails(category, region, pos, blockState);
                    throw new ReportedException(report);
                }
            }
        }

        if (!smoothMesh.quads().isEmpty()) {
            emitSmoothTerrain(smoothMesh, region, startedLayers, builders);
        }

        for (Entry<ChunkSectionLayer, BufferBuilder> entry : startedLayers.entrySet()) {
            ChunkSectionLayer layer = entry.getKey();
            MeshData mesh = entry.getValue().build();
            if (mesh != null) {
                if (layer == ChunkSectionLayer.TRANSLUCENT) {
                    results.transparencyState = mesh.sortQuads(builders.buffer(layer), vertexSorting);
                }

                results.renderedLayers.put(layer, mesh);
            }
        }

        BlockModelLighter.clearCache();
        results.visibilitySet = visGraph.resolve();
        return results;
    }

    private void emitSmoothTerrain(
        BilateralTerrainMesh.Mesh mesh,
        RenderSectionRegion region,
        Map<ChunkSectionLayer, BufferBuilder> startedLayers,
        SectionBufferBuilderPack builders
    ) {
        BufferBuilder builder = this.getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
        for (BilateralTerrainMesh.Quad quad : mesh.quads()) {
            Vec3 a = quad.a();
            Vec3 b = quad.b();
            Vec3 c = quad.c();
            Vec3 d = quad.d();
            Vec3 normal = quad.normal();

            TextureAtlasSprite sprite = spriteFor(quad.textureState(), quad.texturePos(), normal);
            int color = tintColor(region, quad.textureState(), quad.texturePos(), quad.color());

            // Keep vertex winding consistent with normal to avoid front-face culling artifacts.
            Vec3 windingNormal = b.subtract(a).cross(c.subtract(a));
            if (windingNormal.dot(normal) < 0.0) {
                Vec3 tmp = b;
                b = d;
                d = tmp;
            }

            float uBlocks = (float) Math.max(0.001, a.distanceTo(b));
            float vBlocks = (float) Math.max(0.001, a.distanceTo(d));
            emitTiledQuad(builder, sprite, color, normal, a, b, c, d, uBlocks, vBlocks);
        }
    }

    private static void emitTiledQuad(
        BufferBuilder builder,
        TextureAtlasSprite sprite,
        int color,
        Vec3 normal,
        Vec3 a,
        Vec3 b,
        Vec3 c,
        Vec3 d,
        float uBlocks,
        float vBlocks
    ) {
        int uTiles = Math.max(1, (int) Math.ceil(uBlocks));
        int vTiles = Math.max(1, (int) Math.ceil(vBlocks));

        for (int iu = 0; iu < uTiles; iu++) {
            float u0 = iu;
            float u1 = Math.min(uBlocks, iu + 1.0f);
            float s0 = u0 / uBlocks;
            float s1 = u1 / uBlocks;
            float uLocalMax = u1 - u0;

            for (int iv = 0; iv < vTiles; iv++) {
                float v0 = iv;
                float v1 = Math.min(vBlocks, iv + 1.0f);
                float t0 = v0 / vBlocks;
                float t1 = v1 / vBlocks;
                float vLocalMax = v1 - v0;

                Vec3 p00 = bilerp(a, b, c, d, s0, t0);
                Vec3 p10 = bilerp(a, b, c, d, s1, t0);
                Vec3 p11 = bilerp(a, b, c, d, s1, t1);
                Vec3 p01 = bilerp(a, b, c, d, s0, t1);

                putVertex(builder, p00, normal, sprite, color, 0.0f, 0.0f);
                putVertex(builder, p10, normal, sprite, color, uLocalMax, 0.0f);
                putVertex(builder, p11, normal, sprite, color, uLocalMax, vLocalMax);
                putVertex(builder, p01, normal, sprite, color, 0.0f, vLocalMax);
            }
        }
    }

    private static Vec3 bilerp(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float s, float t) {
        Vec3 ab = a.scale(1.0 - s).add(b.scale(s));
        Vec3 dc = d.scale(1.0 - s).add(c.scale(s));
        return ab.scale(1.0 - t).add(dc.scale(t));
    }

    private TextureAtlasSprite spriteFor(BlockState state, BlockPos pos, Vec3 normal) {
        RandomSource random = RandomSource.create(state.getSeed(pos));
        List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new ArrayList<>();
        this.blockModelSet.get(state).collectParts(random, parts);

        Direction preferred = dominantDirection(normal);
        for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(preferred);
            if (!quads.isEmpty()) {
                return quads.getFirst().materialInfo().sprite();
            }
        }

        for (Direction direction : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            if (direction == preferred) {
                continue;
            }

            for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
                List<BakedQuad> quads = part.getQuads(direction);
                if (!quads.isEmpty()) {
                    return quads.getFirst().materialInfo().sprite();
                }
            }
        }

        for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(null);
            if (!quads.isEmpty()) {
                return quads.getFirst().materialInfo().sprite();
            }
        }

        return parts.isEmpty() ? this.blockModelSet.missingModel().particleMaterial().sprite() : parts.getFirst().particleMaterial().sprite();
    }

    private static Direction dominantDirection(Vec3 normal) {
        double ax = Math.abs(normal.x);
        double ay = Math.abs(normal.y);
        double az = Math.abs(normal.z);
        if (ay >= ax && ay >= az) {
            return normal.y >= 0.0 ? Direction.UP : Direction.DOWN;
        }

        if (ax >= az) {
            return normal.x >= 0.0 ? Direction.EAST : Direction.WEST;
        }

        return normal.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private int tintColor(RenderSectionRegion region, BlockState state, BlockPos pos, int fallbackColor) {
        BlockTintSource tintSource = this.blockColors.getTintSource(state, 0);
        if (tintSource == null) {
            return 0xFFFFFFFF;
        }

        int tint = tintSource.colorInWorld(state, region, pos);
        int r = tint >> 16 & 255;
        int g = tint >> 8 & 255;
        int b = tint & 255;
        int a = fallbackColor & 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static void putVertex(BufferBuilder builder, Vec3 pos, Vec3 normal, TextureAtlasSprite sprite, int color, float u, float v) {
        builder.addVertex((float) pos.x, (float) pos.y, (float) pos.z)
            .setColor(color)
            .setUv(sprite.getU(u), sprite.getV(v))
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightCoordsUtil.FULL_BRIGHT)
            .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private BufferBuilder getOrBeginLayer(
        final Map<ChunkSectionLayer, BufferBuilder> startedLayers, final SectionBufferBuilderPack buffers, final ChunkSectionLayer layer
    ) {
        BufferBuilder builder = startedLayers.get(layer);
        if (builder == null) {
            ByteBufferBuilder buffer = buffers.buffer(layer);
            builder = new BufferBuilder(buffer, VertexFormat.Mode.QUADS, layer.vertexFormat());
            startedLayers.put(layer, builder);
        }

        return builder;
    }

    private <E extends BlockEntity> void handleBlockEntity(final SectionCompiler.Results results, final E blockEntity) {
        BlockEntityRenderer<E, ?> renderer = this.blockEntityRenderer.getRenderer(blockEntity);
        if (renderer != null && !renderer.shouldRenderOffScreen()) {
            results.blockEntities.add(blockEntity);
        }
    }
}
