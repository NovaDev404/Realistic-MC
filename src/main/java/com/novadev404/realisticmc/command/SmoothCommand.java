package com.novadev404.realisticmc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.novadev404.realisticmc.RealisticMCClient;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;

public class SmoothCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("smooth")
            .executes(context -> {
                RealisticMCClient.smoothTerrainEnabled = !RealisticMCClient.smoothTerrainEnabled;
                
                String status = RealisticMCClient.smoothTerrainEnabled ? "enabled" : "disabled";
                context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("Smooth terrain " + status));
                
                if (context.getSource().getClient().level != null) {
                    context.getSource().getClient().levelRenderer.allChanged();
                }
                
                return 1;
            }));
    }
}
