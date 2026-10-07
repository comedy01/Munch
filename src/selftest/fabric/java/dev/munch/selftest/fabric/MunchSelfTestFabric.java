package dev.munch.selftest.fabric;

import dev.munch.selftest.MunchSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class MunchSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MunchSelfTest test = new MunchSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
