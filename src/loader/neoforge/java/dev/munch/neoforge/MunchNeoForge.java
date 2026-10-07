package dev.munch.neoforge;

import dev.munch.client.MunchClient;
import dev.munch.client.gui.MunchSettingsScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MunchClient.MOD_ID, dist = Dist.CLIENT)
public final class MunchNeoForge {
    public MunchNeoForge(IEventBus modBus, ModContainer container) {
        MunchClient.init(FMLPaths.CONFIGDIR.get());

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new MunchSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
