package com.novadev404.realisticmc;

import com.novadev404.realisticmc.terrain.BilateralTerrainMesh;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class RealisticMCClient implements ClientModInitializer {
    public static boolean smoothTerrainEnabled = true;
    public static boolean smoothTerrainRuntimeReady = false;
    private static final double SMOOTH_GROUND_MAX_DELTA = 1.25;

    private boolean rebuiltForSmoothTerrain = false;
    
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean ready = smoothTerrainEnabled
                && client.level != null
                && client.player != null
                && client.gameMode != null
                && client.levelRenderer != null;
            smoothTerrainRuntimeReady = ready;

            if (ready && !rebuiltForSmoothTerrain) {
                rebuiltForSmoothTerrain = true;
                client.levelRenderer.allChanged();
            } else if (!ready) {
                rebuiltForSmoothTerrain = false;
            }

            applySmoothGroundWalking(client);
        });

        System.out.println("Realistic MC: bilateral chunk terrain mesher initialized");
    }

    private static void applySmoothGroundWalking(Minecraft client) {
        if (!smoothTerrainRuntimeReady || client.level == null || client.player == null) {
            return;
        }

        LocalPlayer player = client.player;
        if (!player.onGround() || player.isSpectator() || player.getAbilities().flying || player.isInWater() || player.isInLava()) {
            return;
        }

        if (client.options.keyJump.isDown()) {
            return;
        }

        double targetY = BilateralTerrainMesh.sampleSmoothedTerrainY(client.level, player.getX(), player.getY(), player.getZ());
        if (Double.isNaN(targetY)) {
            return;
        }

        double delta = targetY - player.getY();
        if (Math.abs(delta) < 0.02 || Math.abs(delta) > SMOOTH_GROUND_MAX_DELTA) {
            return;
        }

        player.setPos(player.getX(), player.getY() + delta * 0.35, player.getZ());
    }
}
