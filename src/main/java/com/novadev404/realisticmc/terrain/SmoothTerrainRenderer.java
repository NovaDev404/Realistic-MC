package com.novadev404.realisticmc.terrain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

public class SmoothTerrainRenderer {
    private static final int MESH_RADIUS = 48;
    private static final int GRID_STEP = 2;
    private static final int MAX_CACHE_ENTRIES = 12;

    private final Map<Long, BilateralTerrainMesh.Mesh> meshCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BilateralTerrainMesh.Mesh> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    public void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Player player = minecraft.player;
        int centerX = snap(player.getBlockX());
        int centerY = player.getBlockY();
        int centerZ = snap(player.getBlockZ());
        long key = cacheKey(centerX, centerY >> 4, centerZ);
        BilateralTerrainMesh.Mesh mesh = meshCache.computeIfAbsent(key, ignored -> BilateralTerrainMesh.build(minecraft.level, centerX, centerY, centerZ, MESH_RADIUS, GRID_STEP));
        if (mesh.quads().isEmpty()) {
            return;
        }

        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> emit(mesh, pose, buffer));
        poseStack.popPose();
    }

    public void clear() {
        meshCache.clear();
    }

    private static void emit(BilateralTerrainMesh.Mesh mesh, PoseStack.Pose pose, VertexConsumer buffer) {
        for (BilateralTerrainMesh.Quad quad : mesh.quads()) {
            vertex(buffer, pose, quad.a(), quad.color());
            vertex(buffer, pose, quad.b(), quad.color());
            vertex(buffer, pose, quad.c(), quad.color());
            vertex(buffer, pose, quad.d(), quad.color());
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 pos, int color) {
        int r = (color >> 24) & 255;
        int g = (color >> 16) & 255;
        int b = (color >> 8) & 255;
        int a = color & 255;
        buffer.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z).setColor(r, g, b, a);
    }

    private static int snap(int value) {
        return Math.floorDiv(value, 16) * 16 + 8;
    }

    private static long cacheKey(int x, int ySection, int z) {
        long lx = (long) (x >> 4) & 0x3FFFFFFL;
        long ly = (long) ySection & 0xFFFL;
        long lz = (long) (z >> 4) & 0x3FFFFFFL;
        return (lx << 38) | (lz << 12) | ly;
    }
}
