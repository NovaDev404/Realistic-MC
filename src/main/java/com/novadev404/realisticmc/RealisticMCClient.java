package com.novadev404.realisticmc;

import net.fabricmc.api.ClientModInitializer;

public class RealisticMCClient implements ClientModInitializer {
    // Set this to true to enable smooth terrain
    // WARNING: May cause crash during world loading - enable after joining world
    public static boolean smoothTerrainEnabled = false;
    
    @Override
    public void onInitializeClient() {
        System.out.println("Realistic MC: Smooth terrain rendering initialized");
        System.out.println("Realistic MC: Set smoothTerrainEnabled to true in code to enable");
        System.out.println("Realistic MC: Recommended: enable after joining a world to avoid crashes");
    }
}
