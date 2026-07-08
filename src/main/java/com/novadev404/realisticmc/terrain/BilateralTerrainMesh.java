package com.novadev404.realisticmc.terrain;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BilateralTerrainMesh {
    private static final int RADIUS = 2;
    private static final float SIGMA_SPACE = 1.65f;
    private static final float SIGMA_HEIGHT = 3.0f;
    private static final float CLIFF_HEIGHT = 4.0f;
    private static final int SEARCH_ABOVE = 12;
    private static final int SEARCH_BELOW = 32;

    private BilateralTerrainMesh() {
    }

    public static Mesh build(BlockGetter level, int centerX, int centerY, int centerZ, int radiusBlocks, int step) {
        int minX = centerX - radiusBlocks;
        int minZ = centerZ - radiusBlocks;
        int size = (radiusBlocks * 2) / step + 1;
        Sample[][] samples = new Sample[size][size];

        for (int gx = 0; gx < size; gx++) {
            for (int gz = 0; gz < size; gz++) {
                int x = minX + gx * step;
                int z = minZ + gz * step;
                samples[gx][gz] = sampleColumn(level, x, centerY, z);
            }
        }

        float[][] smoothHeights = new float[size][size];
        for (int gx = 0; gx < size; gx++) {
            for (int gz = 0; gz < size; gz++) {
                smoothHeights[gx][gz] = bilateralHeight(samples, gx, gz, size);
            }
        }

        Mesh mesh = new Mesh();
        for (int gx = 0; gx < size - 1; gx++) {
            for (int gz = 0; gz < size - 1; gz++) {
                Sample s00 = samples[gx][gz];
                Sample s10 = samples[gx + 1][gz];
                Sample s11 = samples[gx + 1][gz + 1];
                Sample s01 = samples[gx][gz + 1];

                if (!s00.renderable || !s10.renderable || !s11.renderable || !s01.renderable) {
                    continue;
                }

                Vec3 v00 = new Vec3(s00.x, smoothHeights[gx][gz] + 1.015f, s00.z);
                Vec3 v10 = new Vec3(s10.x, smoothHeights[gx + 1][gz] + 1.015f, s10.z);
                Vec3 v11 = new Vec3(s11.x, smoothHeights[gx + 1][gz + 1] + 1.015f, s11.z);
                Vec3 v01 = new Vec3(s01.x, smoothHeights[gx][gz + 1] + 1.015f, s01.z);
                Vec3 normal = normal(v00, v10, v11);
                int color = colorFor(s00, s10, s11, s01, normal);

                mesh.quads.add(new Quad(v00, v10, v11, v01, normal, color));
            }
        }

        return mesh;
    }

    private static float bilateralHeight(Sample[][] samples, int gx, int gz, int size) {
        Sample center = samples[gx][gz];
        if (!center.renderable) {
            return center.height;
        }

        float weightedSum = 0.0f;
        float totalWeight = 0.0f;

        for (int ox = -RADIUS; ox <= RADIUS; ox++) {
            for (int oz = -RADIUS; oz <= RADIUS; oz++) {
                int nx = gx + ox;
                int nz = gz + oz;
                if (nx < 0 || nz < 0 || nx >= size || nz >= size) {
                    continue;
                }

                Sample neighbor = samples[nx][nz];
                if (!neighbor.renderable || center.materialGroup != neighbor.materialGroup) {
                    continue;
                }

                float heightDelta = Math.abs(center.height - neighbor.height);
                if (heightDelta >= CLIFF_HEIGHT) {
                    continue;
                }

                float distanceSq = ox * ox + oz * oz;
                float spatial = gaussian(distanceSq, SIGMA_SPACE);
                float range = gaussian(heightDelta * heightDelta, SIGMA_HEIGHT);
                float weight = spatial * range;
                weightedSum += neighbor.height * weight;
                totalWeight += weight;
            }
        }

        return totalWeight <= 0.0001f ? center.height : weightedSum / totalWeight;
    }

    private static float gaussian(float valueSq, float sigma) {
        return (float) Math.exp(-valueSq / (2.0f * sigma * sigma));
    }

    private static Sample sampleColumn(BlockGetter level, int x, int centerY, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, centerY + SEARCH_ABOVE, z);
        int bottom = centerY - SEARCH_BELOW;

        for (int y = centerY + SEARCH_ABOVE; y >= bottom; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (isTerrainSurface(state)) {
                return new Sample(x, y, z, true, materialGroup(state), baseColor(state));
            }
        }

        return new Sample(x, centerY, z, false, 0, 0);
    }

    private static boolean isTerrainSurface(BlockState state) {
        return state.isSolidRender() && !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER);
    }

    private static int materialGroup(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
            return 1;
        }
        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)) {
            return 2;
        }
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return 3;
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DEEPSLATE)) {
            return 4;
        }
        return 5;
    }

    private static int baseColor(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK)) {
            return rgb(94, 142, 63, 210);
        }
        if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
            return rgb(115, 83, 53, 210);
        }
        if (state.is(Blocks.SAND)) {
            return rgb(216, 202, 139, 205);
        }
        if (state.is(Blocks.RED_SAND)) {
            return rgb(190, 103, 47, 205);
        }
        if (state.is(Blocks.GRAVEL)) {
            return rgb(126, 122, 116, 205);
        }
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return rgb(232, 240, 244, 210);
        }
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
            return rgb(138, 183, 220, 180);
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DEEPSLATE)) {
            return rgb(126, 126, 122, 210);
        }
        return rgb(120, 138, 92, 195);
    }

    private static int colorFor(Sample a, Sample b, Sample c, Sample d, Vec3 normal) {
        int ar = (a.color >> 24) & 255;
        int ag = (a.color >> 16) & 255;
        int ab = (a.color >> 8) & 255;
        int aa = a.color & 255;
        int br = (b.color >> 24) & 255;
        int bg = (b.color >> 16) & 255;
        int bb = (b.color >> 8) & 255;
        int ba = b.color & 255;
        int cr = (c.color >> 24) & 255;
        int cg = (c.color >> 16) & 255;
        int cb = (c.color >> 8) & 255;
        int ca = c.color & 255;
        int dr = (d.color >> 24) & 255;
        int dg = (d.color >> 16) & 255;
        int db = (d.color >> 8) & 255;
        int da = d.color & 255;
        float shade = 0.62f + 0.38f * Math.max(0.0f, (float) normal.y);
        return rgb(
            (int) (((ar + br + cr + dr) / 4.0f) * shade),
            (int) (((ag + bg + cg + dg) / 4.0f) * shade),
            (int) (((ab + bb + cb + db) / 4.0f) * shade),
            (aa + ba + ca + da) / 4
        );
    }

    private static int rgb(int r, int g, int b, int a) {
        return (clamp(r) << 24) | (clamp(g) << 16) | (clamp(b) << 8) | clamp(a);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static Vec3 normal(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 ab = b.subtract(a);
        Vec3 ac = c.subtract(a);
        Vec3 normal = ab.cross(ac).normalize();
        return normal.y < 0.0 ? normal.scale(-1.0) : normal;
    }

    public record Mesh(List<Quad> quads) {
        public Mesh() {
            this(new ArrayList<>());
        }
    }

    public record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int color) {
    }

    private record Sample(int x, float height, int z, boolean renderable, int materialGroup, int color) {
    }
}
