package dev.munch.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.munch.client.gui.MunchSettingsScreen;
import net.minecraft.client.Minecraft;

public final class MunchModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new MunchSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
