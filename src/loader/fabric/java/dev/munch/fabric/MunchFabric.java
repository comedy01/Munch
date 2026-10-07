package dev.munch.fabric;

import dev.munch.client.MunchClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class MunchFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MunchClient.init(FabricLoader.getInstance().getConfigDir());
    }
}
