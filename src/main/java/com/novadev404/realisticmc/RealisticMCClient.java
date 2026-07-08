package com.novadev404.realisticmc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class RealisticMCClient implements ClientModInitializer {
    public static boolean smoothTerrainEnabled = true;
    public static boolean smoothTerrainRuntimeReady = false;

    private boolean rebuiltForSmoothTerrain = false;
    
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean ready = smoothTerrainEnabled && client.level != null && client.player != null;
            smoothTerrainRuntimeReady = ready;

            if (ready && !rebuiltForSmoothTerrain) {
                rebuiltForSmoothTerrain = true;
                client.levelRenderer.allChanged();
            } else if (!ready) {
                rebuiltForSmoothTerrain = false;
            }
        });

        System.out.println("Realistic MC: bilateral chunk terrain mesher initialized");
    }
}
