package dev.yae.vaultforecast.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yae.vaultforecast.VaultForecast;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VaultForecastConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("vaultforecast.json");

    private static VaultForecastConfig instance =
            new VaultForecastConfig();

    private int maxGap = 100;

    public static VaultForecastConfig getInstance() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }

        try {
            String json = Files.readString(
                    PATH,
                    StandardCharsets.UTF_8
            );

            VaultForecastConfig loaded =
                    GSON.fromJson(json, VaultForecastConfig.class);

            if (loaded != null) {
                loaded.maxGap = Math.max(0, loaded.maxGap);
                instance = loaded;
            }
        } catch (Exception exception) {

        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());

            Files.writeString(
                    PATH,
                    GSON.toJson(instance),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {

        }
    }

    public int getMaxGap() {
        return maxGap;
    }

    public void setMaxGap(int maxGap) {
        if (maxGap < 0) {
            throw new IllegalArgumentException(
                    "Max gap cannot be negative"
            );
        }

        this.maxGap = maxGap;
        save();
    }

    private VaultForecastConfig() {
    }
}
