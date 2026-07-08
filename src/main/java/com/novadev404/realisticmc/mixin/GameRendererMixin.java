package com.novadev404.realisticmc.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Redirect(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;update(Lnet/minecraft/client/Camera;)V")
    )
    private void realisticmc$guardLevelRendererUpdate(LevelRenderer levelRenderer, Camera camera) {
        if (this.minecraft.player != null) {
            levelRenderer.update(camera);
        }
    }
}
