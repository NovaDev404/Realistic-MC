package com.novadev404.realisticmc;

import net.fabricmc.api.ClientModInitializer;

public class RealisticMCClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("Realistic MC: Smooth terrain rendering initialized");
    }
}
