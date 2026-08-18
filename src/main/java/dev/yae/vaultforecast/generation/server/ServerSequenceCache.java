package dev.yae.vaultforecast.generation.server;

import dev.yae.vaultforecast.loot.VaultType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ServerSequenceCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    private static final int FORMAT = 2;
    private static final String METHOD = "server";

    private static final String KEY_FORMAT = "format";
    private static final String KEY_VERSION = "minecraft_version";
    private static final String KEY_SEED = "seed";
    private static final String KEY_METHOD = "method";
    private static final String KEY_COUNT = "count";

    private final Path directory;
    private final String minecraftVersion;

    public ServerSequenceCache(Path directory, String minecraftVersion) {
        this.directory = directory;
        this.minecraftVersion = minecraftVersion;
    }

    public Optional<Map<VaultType, List<NbtList>>> load(long seed, int count) {
        Path file = fileFor(seed);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());

            if (root.getInt(KEY_FORMAT, 0) != FORMAT) {
                LOGGER.info("Ignoring the sequence cache at {}: it predates two-type generation", file);
                return Optional.empty();
            }

            boolean matches = minecraftVersion.equals(root.getString(KEY_VERSION, ""))
                    && METHOD.equals(root.getString(KEY_METHOD, ""))
                    && root.getLong(KEY_SEED, 0L) == seed;

            if (!matches) {
                return Optional.empty();
            }

            Map<VaultType, List<NbtList>> result = new EnumMap<>(VaultType.class);
            for (VaultType type : VaultType.values()) {
                List<NbtList> sequence = readSequence(root, type, count);
                if (sequence == null) {
                    return Optional.empty();
                }
                result.put(type, sequence);
            }

            return Optional.of(result);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Ignoring the unreadable sequence cache at {}", file, exception);
            return Optional.empty();
        }
    }

    private static List<NbtList> readSequence(NbtCompound root, VaultType type, int count) {
        NbtList drops = root.getList(key(type)).orElse(null);
        if (drops == null || drops.size() < count) {
            return null;
        }

        List<NbtList> sequence = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            NbtList items = drops.getList(index).orElse(null);
            if (items == null) {
                return null;
            }
            sequence.add(items);
        }

        return sequence;
    }

    public void store(long seed, Map<VaultType, List<NbtList>> drops) {
        int count = -1;
        for (VaultType type : VaultType.values()) {
            List<NbtList> sequence = drops.get(type);
            if (sequence == null) {
                LOGGER.warn("Refusing to cache an incomplete run for seed {}: no {} drops", seed, type.id());
                return;
            }
            if (count >= 0 && sequence.size() != count) {
                LOGGER.warn("Refusing to cache an uneven run for seed {}", seed);
                return;
            }
            count = sequence.size();
        }

        NbtCompound root = new NbtCompound();
        root.putInt(KEY_FORMAT, FORMAT);
        root.putString(KEY_VERSION, minecraftVersion);
        root.putString(KEY_METHOD, METHOD);
        root.putLong(KEY_SEED, seed);
        root.putInt(KEY_COUNT, count);

        drops.forEach((type, sequence) -> {
            NbtList serialised = new NbtList();
            sequence.forEach(serialised::add);
            root.put(key(type), serialised);
        });

        Path file = fileFor(seed);
        Path temporary = file.resolveSibling(file.getFileName() + ".partial");

        try {
            Files.createDirectories(directory);

            if (Files.isRegularFile(file) && storedCount(file) >= count) {
                return;
            }

            NbtIo.writeCompressed(root, temporary);
            move(temporary, file);
            LOGGER.info("Cached {} drops per vault type for seed {}", count, seed);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not cache the vanilla server sequences for seed {}", seed, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private int storedCount(Path file) {
        try {
            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            return root.getInt(KEY_FORMAT, 0) == FORMAT ? root.getInt(KEY_COUNT, 0) : 0;
        } catch (IOException | RuntimeException exception) {
            return 0;
        }
    }

    private static String key(VaultType type) {
        return "drops_" + type.id();
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path fileFor(long seed) {
        return directory.resolve("%s_%s_%016x.nbt".formatted(minecraftVersion, METHOD, seed));
    }
}
