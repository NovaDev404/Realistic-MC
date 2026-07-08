package com.novadev404.realisticmc;

import com.novadev404.realisticmc.terrain.SmoothTerrainRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class RealisticMCClient implements ClientModInitializer {
    public static boolean smoothTerrainEnabled = true;

    private final SmoothTerrainRenderer smoothTerrainRenderer = new SmoothTerrainRenderer();
    
    @Override
    public void onInitializeClient() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (smoothTerrainEnabled) {
                smoothTerrainRenderer.render(context);
            } else {
                smoothTerrainRenderer.clear();
            }
        });

        System.out.println("Realistic MC: bilateral smooth terrain renderer initialized");
    }
}
