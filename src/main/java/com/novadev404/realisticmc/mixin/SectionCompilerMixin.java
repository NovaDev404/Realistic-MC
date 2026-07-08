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
    
    // Re-enabled - using LevelRenderEvents approach instead
    // This Mixin is kept as backup but the main approach is now LevelRenderEvents
    
    @Inject(method = "compile", at = @At("RETURN"))
    private void realisticmc$afterCompile(SectionBufferBuilderPack buffers, CallbackInfo ci) {
        // Only use this if LevelRenderEvents approach doesn't work
        if (!RealisticMCClient.smoothTerrainEnabled) {
            return;
        }
        
        if (level == null || sectionPos == null) {
            return;
        }
        
        SmoothTerrainMeshGenerator generator = new SmoothTerrainMeshGenerator(level, sectionPos, true);
        
        try {
            var byteBuffer = buffers.buffer(ChunkSectionLayer.SOLID);
            var bufferBuilder = new BufferBuilder(
                byteBuffer,
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
            );
            generator.generateSmoothTerrain(bufferBuilder);
        } catch (Exception e) {
            generator.generateSmoothTerrain();
        }
    }
}
