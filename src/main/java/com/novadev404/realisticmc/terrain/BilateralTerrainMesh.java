package com.novadev404.realisticmc.terrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BilateralTerrainMesh {
    private static final int RADIUS = 2;
    private static final int SUBDIVISIONS = 4;
    private static final float SIGMA_SPACE = 1.65f;
    private static final float SIGMA_HEIGHT = 3.0f;
    private static final float CLIFF_HEIGHT = 4.0f;
    private BilateralTerrainMesh() {
    }

    public static Mesh buildSection(BlockGetter level, SectionPos sectionPos) {
        int originX = sectionPos.minBlockX();
        int originY = sectionPos.minBlockY();
        int originZ = sectionPos.minBlockZ();
        int size = 17;
        int paddedSize = size + RADIUS * 2;
        Sample[][] samples = new Sample[paddedSize][paddedSize];

        for (int gx = 0; gx < paddedSize; gx++) {
            for (int gz = 0; gz < paddedSize; gz++) {
                int x = originX + gx - RADIUS;
                int z = originZ + gz - RADIUS;
                samples[gx][gz] = sampleColumn(level, x, originY, originY + 15, z);
            }
        }

        float[][] smoothHeights = new float[size][size];
        for (int gx = 0; gx < size; gx++) {
            for (int gz = 0; gz < size; gz++) {
                smoothHeights[gx][gz] = bilateralHeight(samples, gx + RADIUS, gz + RADIUS, paddedSize);
            }
        }

        Mesh mesh = new Mesh();
        for (int gx = 0; gx < size - 1; gx++) {
            for (int gz = 0; gz < size - 1; gz++) {
                Sample s00 = samples[gx + RADIUS][gz + RADIUS];
                Sample s10 = samples[gx + 1 + RADIUS][gz + RADIUS];
                Sample s11 = samples[gx + 1 + RADIUS][gz + 1 + RADIUS];
                Sample s01 = samples[gx + RADIUS][gz + 1 + RADIUS];

                if (!s00.renderable || !s10.renderable || !s11.renderable || !s01.renderable) {
                    continue;
                }

                boolean touchesSection = isInsideSection(s00.height, originY) || isInsideSection(s10.height, originY)
                    || isInsideSection(s11.height, originY) || isInsideSection(s01.height, originY);
                if (!touchesSection) {
                    continue;
                }

                for (int sx = 0; sx < SUBDIVISIONS; sx++) {
                    float x0 = gx + sx / (float) SUBDIVISIONS;
                    float x1 = gx + (sx + 1) / (float) SUBDIVISIONS;
                    for (int sz = 0; sz < SUBDIVISIONS; sz++) {
                        float z0 = gz + sz / (float) SUBDIVISIONS;
                        float z1 = gz + (sz + 1) / (float) SUBDIVISIONS;

                        Vec3 v00 = new Vec3(x0, roundedHeightAt(smoothHeights, x0, z0) - originY + 1.015f, z0);
                        Vec3 v10 = new Vec3(x1, roundedHeightAt(smoothHeights, x1, z0) - originY + 1.015f, z0);
                        Vec3 v11 = new Vec3(x1, roundedHeightAt(smoothHeights, x1, z1) - originY + 1.015f, z1);
                        Vec3 v01 = new Vec3(x0, roundedHeightAt(smoothHeights, x0, z1) - originY + 1.015f, z1);
                        Vec3 normal = normal(v00, v10, v11);
                        Sample textureSample = textureSampleForQuad(s00, s10, s11, s01, normal);
                        int color = colorFor(s00, s10, s11, s01, normal);

                        mesh.quads.add(new Quad(v00, v10, v11, v01, normal, color, textureSample.state, textureSample.blockPos));
                    }
                }
                hide(mesh, s00, originY);
                hide(mesh, s10, originY);
                hide(mesh, s11, originY);
                hide(mesh, s01, originY);
            }
        }

        return mesh;
    }

    public static double sampleSmoothedTerrainY(BlockGetter level, double worldX, double worldY, double worldZ) {
        int size = RADIUS * 2 + 1;
        Sample[][] samples = new Sample[size][size];
        int centerX = (int) Math.floor(worldX);
        int centerZ = (int) Math.floor(worldZ);
        int minY = (int) Math.floor(worldY) - 8;
        int maxY = (int) Math.floor(worldY) + 8;

        for (int gx = 0; gx < size; gx++) {
            for (int gz = 0; gz < size; gz++) {
                int x = centerX + gx - RADIUS;
                int z = centerZ + gz - RADIUS;
                samples[gx][gz] = sampleColumn(level, x, minY, maxY, z);
            }
        }

        Sample center = samples[RADIUS][RADIUS];
        if (!center.renderable) {
            return Double.NaN;
        }

        return bilateralHeight(samples, RADIUS, RADIUS, size) + 1.015;
    }

    private static boolean isInsideSection(float y, int originY) {
        return y >= originY && y <= originY + 15;
    }

    private static void hide(Mesh mesh, Sample sample, int originY) {
        if (isInsideSection(sample.height, originY)) {
            mesh.hiddenBlocks.add(sample.blockPos.asLong());
        }
    }

    private static float bilateralHeight(Sample[][] samples, int gx, int gz, int size) {
        Sample center = samples[gx][gz];
        if (!center.renderable) {
            return center.height;
        }

        if (isLocalExtremum(samples, gx, gz, size)) {
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

    private static boolean isLocalExtremum(Sample[][] samples, int gx, int gz, int size) {
        Sample center = samples[gx][gz];
        boolean allLower = true;
        boolean allHigher = true;

        for (int ox = -RADIUS; ox <= RADIUS; ox++) {
            for (int oz = -RADIUS; oz <= RADIUS; oz++) {
                if (ox == 0 && oz == 0) {
                    continue;
                }

                int nx = gx + ox;
                int nz = gz + oz;
                if (nx < 0 || nz < 0 || nx >= size || nz >= size) {
                    continue;
                }

                Sample neighbor = samples[nx][nz];
                if (!neighbor.renderable || center.materialGroup != neighbor.materialGroup) {
                    continue;
                }

                if (neighbor.height >= center.height - 0.001f) {
                    allLower = false;
                }

                if (neighbor.height <= center.height + 0.001f) {
                    allHigher = false;
                }
            }
        }

        return allLower || allHigher;
    }

    private static float roundedHeightAt(float[][] heights, float x, float z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        float tx = smoothStep(x - ix);
        float tz = smoothStep(z - iz);

        float h00 = sampleHeight(heights, ix, iz);
        float h10 = sampleHeight(heights, ix + 1, iz);
        float h01 = sampleHeight(heights, ix, iz + 1);
        float h11 = sampleHeight(heights, ix + 1, iz + 1);

        float hx0 = lerp(h00, h10, tx);
        float hx1 = lerp(h01, h11, tx);
        return lerp(hx0, hx1, tz);
    }

    private static float sampleHeight(float[][] heights, int x, int z) {
        int clampedX = Math.max(0, Math.min(heights.length - 1, x));
        int clampedZ = Math.max(0, Math.min(heights[0].length - 1, z));
        return heights[clampedX][clampedZ];
    }

    private static float smoothStep(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static Sample textureSampleForQuad(Sample s00, Sample s10, Sample s11, Sample s01, Vec3 normal) {
        Sample lowest = s00;
        if (s10.height < lowest.height) lowest = s10;
        if (s11.height < lowest.height) lowest = s11;
        if (s01.height < lowest.height) lowest = s01;

        Sample highest = s00;
        if (s10.height > highest.height) highest = s10;
        if (s11.height > highest.height) highest = s11;
        if (s01.height > highest.height) highest = s01;

        return Math.abs(normal.y) >= 0.55f ? highest : lowest;
    }

    private static float gaussian(float valueSq, float sigma) {
        return (float) Math.exp(-valueSq / (2.0f * sigma * sigma));
    }

    private static Sample sampleColumn(BlockGetter level, int x, int minY, int maxY, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, maxY + 1, z);

        for (int y = maxY + 1; y >= minY - 1; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (isTerrainSurface(state)) {
                return new Sample(x, y, z, true, materialGroup(state), baseColor(state), state, pos.immutable());
            }
        }

        return new Sample(x, minY, z, false, 0, 0, Blocks.AIR.defaultBlockState(), new BlockPos(x, minY, z));
    }

    private static boolean isTerrainSurface(BlockState state) {
        return shouldSmoothBlock(state);
    }

    public static boolean shouldSmoothBlock(BlockState state) {
        return state.isSolidRender()
            && (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.STONE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DEEPSLATE));
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

    public record Mesh(List<Quad> quads, Set<Long> hiddenBlocks) {
        public Mesh() {
            this(new ArrayList<>(), new HashSet<>());
        }
    }

    public record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, int color, BlockState textureState, BlockPos texturePos) {
    }

    private record Sample(int x, float height, int z, boolean renderable, int materialGroup, int color, BlockState state, BlockPos blockPos) {
    }
}
