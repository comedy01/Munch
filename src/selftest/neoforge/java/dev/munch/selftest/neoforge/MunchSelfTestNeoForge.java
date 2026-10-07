package dev.munch.selftest.neoforge;

import dev.munch.selftest.MunchSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "munch_selftest", dist = Dist.CLIENT)
public final class MunchSelfTestNeoForge {
    public MunchSelfTestNeoForge() {
        MunchSelfTest test = new MunchSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
