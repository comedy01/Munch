package dev.munch.selftest.forge;

import dev.munch.selftest.MunchSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("munch_selftest")
public final class MunchSelfTestForge {
    public MunchSelfTestForge() {
        MunchSelfTest test = new MunchSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
