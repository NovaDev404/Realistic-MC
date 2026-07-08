package com.novadev404.realisticmc.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Surface Nets algorithm for smooth terrain mesh generation.
 * Converts voxel data (blocks) into smooth isosurface meshes.
 */
public class SurfaceNetsAlgorithm {
    
    private final BlockGetter level;
    private final int chunkX;
    private final int chunkY;
    private final int chunkZ;
    private static final float ISO_LEVEL = 0.5f;
    private static final int CHUNK_SIZE = 16;
    
    public SurfaceNetsAlgorithm(BlockGetter level, int chunkX, int chunkY, int chunkZ) {
        this.level = level;
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;
    }
    
    public SurfaceMeshData generateMesh() {
        SurfaceMeshData meshData = new SurfaceMeshData();
        
        // Generate vertices for cells containing the surface
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    if (cellContainsSurface(x, y, z)) {
                        float[] vertexPos = calculateInterpolatedVertexPosition(x, y, z);
                        int vertexIndex = meshData.addVertex(
                            chunkX * CHUNK_SIZE + vertexPos[0],
                            chunkY * CHUNK_SIZE + vertexPos[1],
                            chunkZ * CHUNK_SIZE + vertexPos[2]
                        );
                    }
                }
            }
        }
        
        // Generate faces by connecting neighboring vertices
        generateFaces(meshData);
        
        return meshData;
    }
    
    private float getDensity(int x, int y, int z) {
        BlockPos pos = new BlockPos(chunkX * CHUNK_SIZE + x, chunkY * CHUNK_SIZE + y, chunkZ * CHUNK_SIZE + z);
        BlockState state = level.getBlockState(pos);
        return state.isAir() ? 0.0f : 1.0f;
    }
    
    private float[] getCorners(int x, int y, int z) {
        return new float[]{
            getDensity(x, y, z),
            getDensity(x + 1, y, z),
            getDensity(x + 1, y, z + 1),
            getDensity(x, y, z + 1),
            getDensity(x, y + 1, z),
            getDensity(x + 1, y + 1, z),
            getDensity(x + 1, y + 1, z + 1),
            getDensity(x, y + 1, z + 1)
        };
    }
    
    private boolean cellContainsSurface(int x, int y, int z) {
        float[] corners = getCorners(x, y, z);
        boolean hasAbove = false;
        boolean hasBelow = false;
        for (float corner : corners) {
            if (corner >= ISO_LEVEL) hasAbove = true;
            if (corner < ISO_LEVEL) hasBelow = true;
        }
        return hasAbove && hasBelow;
    }
    
    private float[] calculateInterpolatedVertexPosition(int x, int y, int z) {
        float[] sum = new float[]{0.0f, 0.0f, 0.0f};
        int[] count = new int[]{0};
        
        // Check each of the 12 edges of the cube for surface crossing
        addEdgeIntersection(x, y, z, getDensity(x, y, z), getDensity(x + 1, y, z), 0, 0, 0, 1, 0, 0, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x, y, z + 1), getDensity(x + 1, y, z + 1), 0, 0, 1, 1, 0, 0, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x, y + 1, z), getDensity(x + 1, y + 1, z), 0, 1, 0, 1, 0, 0, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x, y + 1, z + 1), getDensity(x + 1, y + 1, z + 1), 0, 1, 1, 1, 0, 0, sum, count);
        
        addEdgeIntersection(x, y, z, getDensity(x, y, z), getDensity(x, y + 1, z), 0, 0, 0, 0, 1, 0, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x + 1, y, z), getDensity(x + 1, y + 1, z), 1, 0, 0, 0, 1, 0, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x + 1, y, z + 1), getDensity(x + 1, y + 1, z + 1), 1, 0, 1, 0, 1, 0, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x, y, z + 1), getDensity(x, y + 1, z + 1), 0, 0, 1, 0, 1, 0, sum, count);
        
        addEdgeIntersection(x, y, z, getDensity(x, y, z), getDensity(x, y, z + 1), 0, 0, 0, 0, 0, 1, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x + 1, y, z), getDensity(x + 1, y, z + 1), 1, 0, 0, 0, 0, 1, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x, y + 1, z), getDensity(x, y + 1, z + 1), 0, 1, 0, 0, 0, 1, sum, count);
        addEdgeIntersection(x, y, z, getDensity(x + 1, y + 1, z), getDensity(x + 1, y + 1, z + 1), 1, 1, 0, 0, 0, 1, sum, count);
        
        if (count[0] > 0) {
            return new float[]{sum[0] / count[0], sum[1] / count[0], sum[2] / count[0]};
        } else {
            return new float[]{x + 0.5f, y + 0.5f, z + 0.5f};
        }
    }
    
    private void addEdgeIntersection(int x, int y, int z, float c1, float c2, 
                                    int dx1, int dy1, int dz1, int dx2, int dy2, int dz2,
                                    float[] sum, int[] count) {
        if ((c1 < ISO_LEVEL && c2 >= ISO_LEVEL) || (c1 >= ISO_LEVEL && c2 < ISO_LEVEL)) {
            float t = interpolate(c1, c2, ISO_LEVEL);
            float ix = x + dx1 + t * (dx2 - dx1);
            float iy = y + dy1 + t * (dy2 - dy1);
            float iz = z + dz1 + t * (dz2 - dz1);
            sum[0] += ix;
            sum[1] += iy;
            sum[2] += iz;
            count[0]++;
        }
    }
    
    private float interpolate(float a, float b, float x) {
        return (x - a) / (b - a);
    }
    
    private void generateFaces(SurfaceMeshData meshData) {
        // Generate faces by connecting neighboring vertices that share edges
        // This is a simplified approach - proper Surface Nets uses adjacency detection
        
        // For each cell that contains a surface, generate faces connecting to neighbors
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    if (cellContainsSurface(x, y, z)) {
                        // Check each of the 6 faces of the cube
                        // Generate quads for faces where the surface passes through
                        
                        // +X face
                        if (shouldGenerateFace(x, y, z, 1, 0, 0)) {
                            generateQuadFace(meshData, x, y, z, 1, 0, 0);
                        }
                        // -X face
                        if (shouldGenerateFace(x, y, z, -1, 0, 0)) {
                            generateQuadFace(meshData, x, y, z, -1, 0, 0);
                        }
                        // +Y face
                        if (shouldGenerateFace(x, y, z, 0, 1, 0)) {
                            generateQuadFace(meshData, x, y, z, 0, 1, 0);
                        }
                        // -Y face
                        if (shouldGenerateFace(x, y, z, 0, -1, 0)) {
                            generateQuadFace(meshData, x, y, z, 0, -1, 0);
                        }
                        // +Z face
                        if (shouldGenerateFace(x, y, z, 0, 0, 1)) {
                            generateQuadFace(meshData, x, y, z, 0, 0, 1);
                        }
                        // -Z face
                        if (shouldGenerateFace(x, y, z, 0, 0, -1)) {
                            generateQuadFace(meshData, x, y, z, 0, 0, -1);
                        }
                    }
                }
            }
        }
    }
    
    private boolean shouldGenerateFace(int x, int y, int z, int dx, int dy, int dz) {
        // Check if the neighbor in this direction has different density
        float currentDensity = getDensity(x, y, z);
        float neighborDensity = getDensity(x + dx, y + dy, z + dz);
        
        // Generate face if there's a surface crossing between these cells
        return (currentDensity >= ISO_LEVEL && neighborDensity < ISO_LEVEL) ||
               (currentDensity < ISO_LEVEL && neighborDensity >= ISO_LEVEL);
    }
    
    private void generateQuadFace(SurfaceMeshData meshData, int x, int y, int z, int dx, int dy, int dz) {
        // Generate a quad face for the given direction
        // This is a simplified implementation - proper implementation would use interpolated vertices
        
        // Calculate the 4 corners of the face
        float[] corners = new float[12]; // 4 vertices * 3 coordinates
        
        if (dx != 0) {
            // X-facing face
            corners[0] = x + dx; corners[1] = y; corners[2] = z;
            corners[3] = x + dx; corners[4] = y; corners[5] = z + 1;
            corners[6] = x + dx; corners[7] = y + 1; corners[8] = z + 1;
            corners[9] = x + dx; corners[10] = y + 1; corners[11] = z;
        } else if (dy != 0) {
            // Y-facing face
            corners[0] = x; corners[1] = y + dy; corners[2] = z;
            corners[3] = x + 1; corners[4] = y + dy; corners[5] = z;
            corners[6] = x + 1; corners[7] = y + dy; corners[8] = z + 1;
            corners[9] = x; corners[10] = y + dy; corners[11] = z + 1;
        } else {
            // Z-facing face
            corners[0] = x; corners[1] = y; corners[2] = z + dz;
            corners[3] = x + 1; corners[4] = y; corners[5] = z + dz;
            corners[6] = x + 1; corners[7] = y + 1; corners[8] = z + dz;
            corners[9] = x; corners[10] = y + 1; corners[11] = z + dz;
        }
        
        // Add vertices and create face
        int v0 = meshData.addVertex(chunkX * CHUNK_SIZE + corners[0], chunkY * CHUNK_SIZE + corners[1], chunkZ * CHUNK_SIZE + corners[2]);
        int v1 = meshData.addVertex(chunkX * CHUNK_SIZE + corners[3], chunkY * CHUNK_SIZE + corners[4], chunkZ * CHUNK_SIZE + corners[5]);
        int v2 = meshData.addVertex(chunkX * CHUNK_SIZE + corners[6], chunkY * CHUNK_SIZE + corners[7], chunkZ * CHUNK_SIZE + corners[8]);
        int v3 = meshData.addVertex(chunkX * CHUNK_SIZE + corners[9], chunkY * CHUNK_SIZE + corners[10], chunkZ * CHUNK_SIZE + corners[11]);
        
        meshData.addFace(v0, v1, v2, v3);
    }
    
    public static class SurfaceMeshData {
        private final java.util.List<float[]> vertices = new java.util.ArrayList<>();
        private final java.util.List<int[]> faces = new java.util.ArrayList<>();
        
        public int addVertex(float x, float y, float z) {
            vertices.add(new float[]{x, y, z});
            return vertices.size() - 1;
        }
        
        public void addFace(int v1, int v2, int v3, int v4) {
            faces.add(new int[]{v1, v2, v3, v4});
        }
        
        public java.util.List<float[]> getVertices() {
            return vertices;
        }
        
        public java.util.List<int[]> getFaces() {
            return faces;
        }
    }
}
