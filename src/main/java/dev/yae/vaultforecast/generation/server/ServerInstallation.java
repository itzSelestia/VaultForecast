package dev.yae.vaultforecast.generation.server;

import dev.yae.vaultforecast.generation.GenerationProgressListener;
import dev.yae.vaultforecast.generation.VaultGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public record ServerInstallation(Path directory, Path jar, String levelName) {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    public static final String MINECRAFT_VERSION = "1.21.11";

    public static ServerInstallation prepare(Path generatorRoot, long seed, GenerationProgressListener listener)
            throws VaultGenerationException {
        Path directory = generatorRoot.resolve("server-generator").resolve(MINECRAFT_VERSION);
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new VaultGenerationException("Could not create the generator directory at " + directory, exception);
        }

        Path jar = new ServerJarProvider().obtain(directory, MINECRAFT_VERSION, listener);
        requireAcceptedEula(directory);

        String levelName = "world_%016x".formatted(seed);
        discardIncompleteWorld(directory, levelName);
        writeServerProperties(directory, seed, levelName);

        return new ServerInstallation(directory, jar, levelName);
    }

    private static void requireAcceptedEula(Path directory) throws VaultGenerationException {
        Path eula = directory.resolve("eula.txt");

        if (!Files.exists(eula)) {
            try {
                Files.writeString(eula, """
                        #By changing the setting below to TRUE you are indicating your agreement to the Minecraft EULA.
                        #https://aka.ms/MinecraftEULA
                        eula=false
                        """, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new VaultGenerationException("Could not write " + eula, exception);
            }
        }

        Properties properties = new Properties();
        try (var input = Files.newInputStream(eula)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new VaultGenerationException("Could not read " + eula, exception);
        }

        if (!"true".equalsIgnoreCase(properties.getProperty("eula", "false").trim())) {
            throw new VaultGenerationException(
                    "The Minecraft EULA has not been accepted for the generator server. Read https://aka.ms/MinecraftEULA, "
                            + "then set eula=true in " + eula.toAbsolutePath() + " and run the command again.");
        }
    }

    private static void discardIncompleteWorld(Path directory, String levelName) throws VaultGenerationException {
        Path world = directory.resolve(levelName);
        if (!Files.isDirectory(world) || Files.isRegularFile(world.resolve("level.dat"))) {
            return;
        }

        LOGGER.warn("Discarding the incomplete generator world at {}", world);
        deleteRecursively(world, directory);
    }

    static void deleteRecursively(Path target, Path allowedRoot) throws VaultGenerationException {
        Path resolvedTarget = target.toAbsolutePath().normalize();
        Path resolvedRoot = allowedRoot.toAbsolutePath().normalize();

        if (resolvedTarget.equals(resolvedRoot) || !resolvedTarget.startsWith(resolvedRoot)) {
            throw new VaultGenerationException(
                    "Refusing to delete %s: it is not inside the generator directory %s".formatted(resolvedTarget, resolvedRoot));
        }

        try (Stream<Path> paths = Files.walk(resolvedTarget)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException | UncheckedIOException exception) {
            throw new VaultGenerationException("Could not clean up " + resolvedTarget, exception);
        }
    }

    private static void writeServerProperties(Path directory, long seed, String levelName)
            throws VaultGenerationException {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("level-seed", Long.toString(seed));
        properties.put("level-name", levelName);
        properties.put("level-type", "minecraft:flat");
        properties.put("generate-structures", "false");
        properties.put("online-mode", "false");
        properties.put("enable-query", "false");
        properties.put("enable-rcon", "false");
        properties.put("enable-status", "false");
        properties.put("spawn-animals", "false");
        properties.put("spawn-npcs", "false");
        properties.put("spawn-monsters", "false");
        properties.put("allow-nether", "false");
        properties.put("difficulty", "peaceful");
        properties.put("view-distance", "2");
        properties.put("simulation-distance", "2");
        properties.put("max-players", "0");

        properties.put("max-tick-time", "-1");

        properties.put("server-port", "0");
        properties.put("motd", "VaultForecast sequence generator");

        StringBuilder text = new StringBuilder("#Generated by VaultForecast - disposable, safe to delete\n");
        properties.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));

        Path file = directory.resolve("server.properties");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(text.toString());
        } catch (IOException exception) {
            throw new VaultGenerationException("Could not write " + file, exception);
        }
    }
}
