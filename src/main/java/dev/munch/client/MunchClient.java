package dev.munch.client;

import dev.munch.config.MunchConfig;

import java.nio.file.Path;

public final class MunchClient {
    public static final String MOD_ID = "munch";

    private static MunchConfig config = new MunchConfig();
    private static Path configPath;

    private MunchClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(MunchConfig.FILE_NAME);
        config = MunchConfig.load(configPath);
    }

    public static MunchConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }
}
