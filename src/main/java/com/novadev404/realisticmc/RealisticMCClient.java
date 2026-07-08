package com.novadev404.realisticmc;

import net.fabricmc.api.ClientModInitializer;

public class RealisticMCClient implements ClientModInitializer {
    public static boolean smoothTerrainEnabled = false;
    
    @Override
    public void onInitializeClient() {
        System.out.println("Realistic MC: Smooth terrain rendering initialized");
        System.out.println("Realistic MC: Smooth terrain is DISABLED by default to prevent crashes during world loading");
        System.out.println("Realistic MC: To enable, set smoothTerrainEnabled to true in code or use a future command");
    }
}
