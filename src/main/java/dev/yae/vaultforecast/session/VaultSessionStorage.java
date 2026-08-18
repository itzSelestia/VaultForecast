package dev.yae.vaultforecast.session;

import dev.yae.vaultforecast.generation.GenerationMethod;
import dev.yae.vaultforecast.generation.VaultGenerationRequest;
import dev.yae.vaultforecast.loot.VaultType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class VaultSessionStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    private static final int FORMAT = 2;

    private static final String KEY_FORMAT = "format";
    private static final String KEY_SESSION = "session";
    private static final String KEY_SEED = "seed";
    private static final String KEY_COUNT = "count";
    private static final String KEY_METHOD = "method";

    private final Path directory;

    public VaultSessionStorage(Path directory) {
        this.directory = directory;
    }

    public Optional<SavedSession> load(String session) {
        Path file = fileFor(session);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());

            if (root.getInt(KEY_FORMAT, 0) != FORMAT) {
                LOGGER.info("Ignoring the saved session at {}: it predates two-type sequences", file);
                return Optional.empty();
            }

            if (!session.equals(root.getString(KEY_SESSION, ""))) {
                LOGGER.warn("Ignoring the saved session at {}: it belongs to another session", file);
                return Optional.empty();
            }

            GenerationMethod method = GenerationMethod.fromId(root.getString(KEY_METHOD, "")).orElse(null);
            int count = root.getInt(KEY_COUNT, 0);
            if (method == null || count < VaultGenerationRequest.MIN_COUNT || count > VaultGenerationRequest.MAX_COUNT) {
                LOGGER.warn("Ignoring the saved session at {}: it has no usable method or count", file);
                return Optional.empty();
            }

            Map<VaultType, Set<Integer>> candidates = new EnumMap<>(VaultType.class);
            for (VaultType type : VaultType.values()) {
                Set<Integer> positions = new LinkedHashSet<>();
                Arrays.stream(root.getIntArray(key(type)).orElse(new int[0])).forEach(positions::add);
                candidates.put(type, positions);
            }

            return Optional.of(new SavedSession(session, root.getLong(KEY_SEED, 0L), count, method, candidates));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read the saved session at {}", file, exception);
            return Optional.empty();
        }
    }

    public void save(SavedSession saved) {
        NbtCompound root = new NbtCompound();
        root.putInt(KEY_FORMAT, FORMAT);
        root.putString(KEY_SESSION, saved.session());
        root.putLong(KEY_SEED, saved.seed());
        root.putInt(KEY_COUNT, saved.count());
        root.putString(KEY_METHOD, saved.method().id());

        for (VaultType type : VaultType.values()) {
            root.putIntArray(key(type), saved.candidates(type).stream().mapToInt(Integer::intValue).toArray());
        }

        Path file = fileFor(saved.session());
        Path temporary = file.resolveSibling(file.getFileName() + ".partial");

        try {
            Files.createDirectories(directory);
            NbtIo.writeCompressed(root, temporary);
            move(temporary, file);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not save the session for {}", saved.session(), exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    public boolean delete(String session) {
        try {
            return Files.deleteIfExists(fileFor(session));
        } catch (IOException exception) {
            LOGGER.warn("Could not delete the saved session for {}", session, exception);
            return false;
        }
    }

    public Path fileFor(String session) {
        return directory.resolve(SessionKey.fileName(session));
    }

    private static String key(VaultType type) {
        return "candidates_" + type.id();
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
