package com.novadev404.realisticmc.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void realisticmc$skipUpdateWithoutPlayer(Camera camera, CallbackInfo ci) {
        // During world join/teardown there is a brief window where level exists before player is attached.
        if (this.minecraft.player == null) {
            ci.cancel();
        }
    }
}
