package com.wooldrum.chattermod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;  // ← use FabricClientCommandSource
import net.minecraft.text.Text;

public class ChatterMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // register a simple /chatter command
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("chatter")
                    .executes(context -> {
                        reply(context.getSource(), "Hello from ChatterMod!");
                        return 1;
                    })
            );
        });
    }

    // send a feedback message to the client
    private static void reply(FabricClientCommandSource src, String message) {
        src.sendFeedback(Text.literal(message));
    }
}
