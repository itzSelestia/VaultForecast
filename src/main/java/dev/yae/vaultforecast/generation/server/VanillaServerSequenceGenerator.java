package dev.yae.vaultforecast.generation.server;

import dev.yae.vaultforecast.generation.GenerationProgressListener;
import dev.yae.vaultforecast.generation.VaultGenerationException;
import dev.yae.vaultforecast.generation.VaultGenerationRequest;
import dev.yae.vaultforecast.generation.VaultGenerationResult;
import dev.yae.vaultforecast.generation.VaultSequenceGenerator;
import dev.yae.vaultforecast.loot.VaultType;
import dev.yae.vaultforecast.loot.model.LootDrop;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VanillaServerSequenceGenerator implements VaultSequenceGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    private static final String CHEST_POS = "0 300 0";

    private static final int BATCH_SIZE = 200;

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration BATCH_TIMEOUT = Duration.ofMinutes(2);

    private final Path generatorRoot;
    private final RegistryWrapper.WrapperLookup registries;

    public VanillaServerSequenceGenerator(Path generatorRoot, RegistryWrapper.WrapperLookup registries) {
        this.generatorRoot = generatorRoot;
        this.registries = registries;
    }

    @Override
    public VaultGenerationResult generate(VaultGenerationRequest request, GenerationProgressListener progressListener)
            throws VaultGenerationException {
        int count = request.count();
        long seed = request.seed();

        ChestItemsConverter converter = new ChestItemsConverter(registries);
        ServerSequenceCache cache = new ServerSequenceCache(
                generatorRoot.resolve("cache"), ServerInstallation.MINECRAFT_VERSION);

        Optional<Map<VaultType, List<NbtList>>> cached = cache.load(seed, count);
        if (cached.isPresent()) {
            progressListener.onStage("Reusing the cached vanilla server sequences.");
            return convert(request, cached.get(), converter);
        }

        ServerInstallation installation = ServerInstallation.prepare(generatorRoot, seed, progressListener);
        Map<VaultType, List<NbtList>> raw = run(installation, count, converter, progressListener);

        for (VaultType type : VaultType.values()) {
            int produced = raw.getOrDefault(type, List.of()).size();
            if (produced != count) {
                throw new VaultGenerationException(
                        "The vanilla server produced %d %s drops instead of the %d requested."
                                .formatted(produced, type.id(), count));
            }
        }

        cache.store(seed, raw);
        return convert(request, raw, converter);
    }

    private Map<VaultType, List<NbtList>> run(
            ServerInstallation installation,
            int count,
            ChestItemsConverter converter,
            GenerationProgressListener progressListener
    ) throws VaultGenerationException {
        progressListener.onStage("Starting the vanilla %s server...".formatted(ServerInstallation.MINECRAFT_VERSION));

        try (VanillaServerProcess server = VanillaServerProcess.start(installation)) {
            try {
                server.awaitStartup(STARTUP_TIMEOUT);
                progressListener.onStage("Vanilla server started.");

                prepareChest(server);

                Map<VaultType, List<NbtList>> drops = new EnumMap<>(VaultType.class);
                for (VaultType type : VaultType.values()) {
                    progressListener.onStage("Generating %d %s drops...".formatted(count, type.displayName()));
                    drops.put(type, collect(server, type, count, converter, progressListener));
                }

                return drops;
            } catch (VaultGenerationException exception) {
                LOGGER.error("Vanilla server generation failed. Last console output:\n{}",
                        String.join(System.lineSeparator(), server.recentOutput()));
                throw exception;
            }
        }
    }

    private static List<NbtList> collect(
            VanillaServerProcess server,
            VaultType type,
            int count,
            ChestItemsConverter converter,
            GenerationProgressListener progressListener
    ) throws VaultGenerationException {
        List<NbtList> drops = new ArrayList<>(count);

        while (drops.size() < count) {
            int batch = Math.min(BATCH_SIZE, count - drops.size());
            requestDrops(server, type, batch);

            for (String response : server.awaitBlockData(batch, BATCH_TIMEOUT)) {
                drops.add(converter.parseItems(response, drops.size()));
            }

            progressListener.onProgress(type, drops.size(), count);
        }

        return drops;
    }

    private static void prepareChest(VanillaServerProcess server) throws VaultGenerationException {
        server.send("forceload add 0 0");

        for (VaultType type : VaultType.values()) {
            server.send("random reset " + type.lootTableId());
        }

        server.send("setblock " + CHEST_POS + " minecraft:air");
        server.send("setblock " + CHEST_POS + " minecraft:chest");
        server.flush();
    }

    private static void requestDrops(VanillaServerProcess server, VaultType type, int batch)
            throws VaultGenerationException {
        for (int index = 0; index < batch; index++) {
            server.send("data remove block " + CHEST_POS + " Items");
            server.send("loot insert " + CHEST_POS + " loot " + type.lootTableId());
            server.send("data get block " + CHEST_POS + " Items");
        }
        server.flush();
    }

    private static VaultGenerationResult convert(
            VaultGenerationRequest request,
            Map<VaultType, List<NbtList>> raw,
            ChestItemsConverter converter
    ) throws VaultGenerationException {
        Map<VaultType, List<LootDrop>> drops = new EnumMap<>(VaultType.class);

        for (VaultType type : VaultType.values()) {
            List<NbtList> sequence = raw.getOrDefault(type, List.of());
            List<LootDrop> converted = new ArrayList<>(sequence.size());

            for (int index = 0; index < sequence.size(); index++) {
                converted.add(converter.toDrop(sequence.get(index), index));
            }

            drops.put(type, converted);
        }

        return new VaultGenerationResult(request.seed(), request.count(), request.method(), drops);
    }
}
