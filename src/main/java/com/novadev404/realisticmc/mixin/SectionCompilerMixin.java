package com.novadev404.realisticmc.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.novadev404.realisticmc.RealisticMCClient;
import com.novadev404.realisticmc.terrain.SmoothTerrainMeshGenerator;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
    
    @Shadow
    private BlockGetter level;
    
    @Shadow
    private SectionPos sectionPos;
    
    // Inject at the beginning of compile() to add smooth terrain generation
    @Inject(method = "compile", at = @At("HEAD"))
    private void realisticmc$beforeCompile(SectionBufferBuilderPack buffers, CallbackInfo ci) {
        // Generate smooth terrain before vanilla block rendering
        // Add null checks to prevent crashes during initialization
        if (level == null || sectionPos == null) {
            return;
        }
        
        // Only generate smooth terrain if enabled by the client tick event
        if (!RealisticMCClient.smoothTerrainEnabled) {
            return;
        }
        
        SmoothTerrainMeshGenerator generator = new SmoothTerrainMeshGenerator(level, sectionPos, true);
        
        // Get the ByteBufferBuilder for the solid render layer and wrap it in BufferBuilder
        try {
            var byteBuffer = buffers.buffer(ChunkSectionLayer.SOLID);
            var bufferBuilder = new BufferBuilder(
                byteBuffer,
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
            );
            generator.generateSmoothTerrain(bufferBuilder);
        } catch (Exception e) {
            // If buffer access fails, fall back to no-vertex output
            generator.generateSmoothTerrain();
        }
    }
}
