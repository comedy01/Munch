package dev.munch.client.gui;

import dev.munch.client.MunchClient;
import dev.munch.client.Texts;
import dev.munch.config.MunchConfig;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class MunchSettingsScreen extends MunchOptionsScreen {
    private static final int WIDTH = 150;

    public MunchSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Texts.translatable("munch.options.title"));
    }

    @Override
    protected void addOptions() {
        MunchConfig config = MunchClient.config();

        addRow(
                toggle("munch.options.enabled", config::enabled, config::setEnabled),
                toggle("munch.options.others", config::others, config::setOthers));
        addRow(
                toggle("munch.options.eating", config::eating, config::setEating),
                toggle("munch.options.drinking", config::drinking, config::setDrinking));
        addRow(resetButton(config), null);
    }

    @Override
    public void removed() {
        super.removed();
        MunchClient.saveConfig();
    }

    private AbstractWidget resetButton(MunchConfig config) {
        return tooltip(button(Texts.translatable("munch.options.reset"), WIDTH, button -> {
            config.resetToDefaults();
            ScreenOpener.open(minecraft, new MunchSettingsScreen(lastScreen, options));
        }), Texts.translatable("munch.options.reset.tooltip"));
    }

    private AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return tooltip(button(toggleLabel(key, getter.getAsBoolean()), WIDTH, button -> {
            setter.accept(!getter.getAsBoolean());
            button.setMessage(toggleLabel(key, getter.getAsBoolean()));
        }), Texts.translatable(key + ".tooltip"));
    }

    private static Component toggleLabel(String key, boolean on) {
        Component state = Texts.translatable(on ? "options.on" : "options.off");
        return Texts.translatable("options.generic_value", Texts.translatable(key), state);
    }
}
