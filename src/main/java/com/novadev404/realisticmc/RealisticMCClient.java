package com.novadev404.realisticmc;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class RealisticMCClient implements ClientModInitializer {
    public static boolean smoothTerrainEnabled = false;
    private static int tickCounter = 0;
    private static final int REQUIRED_TICKS = 100;
    
    @Override
    public void onInitializeClient() {
        System.out.println("Realistic MC: Smooth terrain rendering initialized");
        
        // Register client tick event to detect when player is fully loaded
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!smoothTerrainEnabled) {
                tickCounter++;
                
                // Check if player and level are loaded and stable
                if (tickCounter > REQUIRED_TICKS && 
                    client.player != null && 
                    client.level != null &&
                    client.screen == null) {
                    smoothTerrainEnabled = true;
                    System.out.println("Realistic MC: Smooth terrain enabled after " + tickCounter + " ticks");
                }
            } else {
                // If player disconnects or world unloads, disable smooth terrain
                if (client.player == null || client.level == null) {
                    smoothTerrainEnabled = false;
                    tickCounter = 0;
                    System.out.println("Realistic MC: Smooth terrain disabled (player/level null)");
                }
            }
        });
    }
}
