package dev.munch.showcase;

import net.fabricmc.api.ClientModInitializer;

public final class MunchShowcaseFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Showcase.start();
    }
}
