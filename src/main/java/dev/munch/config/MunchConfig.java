package dev.munch.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class MunchConfig {
    public static final String FILE_NAME = "munch.json";

    private static final Logger LOGGER = LogManager.getLogger("munch");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean enabled = true;
    private boolean eating = true;
    private boolean drinking = true;
    private boolean others = true;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean eating() {
        return eating;
    }

    public void setEating(boolean value) {
        eating = value;
    }

    public boolean drinking() {
        return drinking;
    }

    public void setDrinking(boolean value) {
        drinking = value;
    }

    public boolean others() {
        return others;
    }

    public void setOthers(boolean value) {
        others = value;
    }

    public void resetToDefaults() {
        MunchConfig fresh = new MunchConfig();
        enabled = fresh.enabled;
        eating = fresh.eating;
        drinking = fresh.drinking;
        others = fresh.others;
    }

    public static MunchConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            MunchConfig fresh = new MunchConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MunchConfig loaded = GSON.fromJson(reader, MunchConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            MunchConfig fresh = new MunchConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.write(temp, (GSON.toJson(this) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
