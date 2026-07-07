package com.novadev404.realisticmc.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    
    // Modify the smoothTerrainEnabled parameter in SectionCompiler constructor call
    @ModifyArg(
        method = "setLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;<init>(ZZLnet/minecraft/client/renderer/block/BlockStateModelSet;Lnet/minecraft/client/renderer/block/BlockStateModelSet;Lnet/minecraft/client/color/BlockColors;Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;Z)V"
        ),
        index = 6
    )
    private boolean realisticmc$enableSmoothTerrain(boolean original) {
        return true; // Enable smooth terrain
    }
}
