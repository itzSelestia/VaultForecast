package dev.yae.vaultforecast.observation;

import dev.yae.vaultforecast.config.VaultForecastConfig;
import dev.yae.vaultforecast.loot.VaultType;
import dev.yae.vaultforecast.loot.model.LootDrop;
import dev.yae.vaultforecast.prediction.LootFinder;
import dev.yae.vaultforecast.session.VaultSessionManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VaultBlock;
import net.minecraft.block.enums.VaultState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class VaultDropObserver {
    private static final Logger LOGGER = LoggerFactory.getLogger("vaultforecast");

    private static final VaultDropObserver INSTANCE = new VaultDropObserver();

    private static final long STACK_RESOLVE_TIMEOUT_TICKS = 10;

    private static final long FINALIZE_GRACE_TICKS = 10;

    private static final double SPAWN_INTEREST_RADIUS = 2.0;

    private final Map<BlockPos, Watch> watching = new HashMap<>();
    private final VaultEjectionCorrelator correlator = new VaultEjectionCorrelator();
    private final List<UnresolvedSpawn> unresolvedSpawns = new ArrayList<>();

    private long tick;

    public static VaultDropObserver getInstance() {
        return INSTANCE;
    }

    public static Optional<VaultType> getVaultType(BlockState state) {
        if (!state.isOf(Blocks.VAULT)) {
            return Optional.empty();
        }

        return Optional.of(state.get(VaultBlock.OMINOUS) ? VaultType.OMINOUS : VaultType.NORMAL);
    }

    public void onBlockInteract(World world, BlockPos pos, ItemStack heldStack) {
        VaultType type = getVaultType(world.getBlockState(pos)).orElse(null);
        if (type == null || !isKeyFor(type, heldStack)) {
            return;
        }

        watching.computeIfAbsent(pos.toImmutable(), ignored -> new Watch(type));
        LOGGER.debug("Watching the {} vault at {}", type.id(), pos);
    }

    private static boolean isKeyFor(VaultType type, ItemStack heldStack) {
        return switch (type) {
            case NORMAL -> heldStack.isOf(Items.TRIAL_KEY);
            case OMINOUS -> heldStack.isOf(Items.OMINOUS_TRIAL_KEY);
        };
    }

    public Text addWatching(World world, BlockPos pos) {
        BlockPos immutablePos = pos.toImmutable();
        VaultType type = getVaultType(world.getBlockState(immutablePos)).orElse(null);

        Text positionText = Text.literal("[" + immutablePos.toShortString() + "]").formatted(Formatting.WHITE);

        if (type == null) {
            return Text.literal("No trial vault found at ").formatted(Formatting.GRAY).append(positionText);
        }

        Watch previousWatch = watching.putIfAbsent(immutablePos, new Watch(type));

        String verb = previousWatch != null ? "Already watching " : "Now watching ";
        return Text.literal(verb + type.displayName() + " vault at ")
                .formatted(Formatting.GRAY)
                .append(positionText);
    }

    public void onVaultTick(World world, BlockPos pos, BlockState state) {
        Watch watch = watching.get(pos);
        if (watch == null || getVaultType(state).filter(watch.type()::equals).isEmpty()) {
            return;
        }

        VaultState vaultState = state.get(VaultBlock.VAULT_STATE);
        boolean ejecting = vaultState == VaultState.UNLOCKING || vaultState == VaultState.EJECTING;

        if (ejecting) {
            watch.markOpening();
        } else if (watch.isOpening()) {
            watch.markFinished(tick);
        }
    }

    public void onWorldEvent(World world, int eventId, BlockPos pos) {
        if (eventId != WorldEvents.VAULT_EJECTS_ITEM || !watching.containsKey(pos)) {
            return;
        }

        correlator.onEjection(world.getRegistryKey(), pos, tick).ifPresent(this::record);
    }

    public void onItemEntitySpawned(World world, ItemEntity entity) {
        Vec3d spawnPosition = entity.getEntityPos();
        if (!isNearWatchedVault(spawnPosition)) {
            return;
        }

        unresolvedSpawns.add(new UnresolvedSpawn(world.getRegistryKey(), entity, spawnPosition, tick));
    }

    private boolean isNearWatchedVault(Vec3d position) {
        return watching.keySet().stream().anyMatch(vault ->
                VaultEjectionCorrelator.spawnPosition(vault).squaredDistanceTo(position)
                        <= SPAWN_INTEREST_RADIUS * SPAWN_INTEREST_RADIUS);
    }

    public void onClientTick() {
        tick++;

        resolveSpawnedStacks();

        for (BlockPos abandoned : correlator.expire(tick)) {
            Watch watch = watching.get(abandoned);
            if (watch != null) {
                watch.markIncomplete();
                LOGGER.warn("A vault ejection at {} produced no item entity within {} ticks (tick {}); "
                                + "the observation is incomplete",
                        abandoned, VaultEjectionCorrelator.EXPIRY_TICKS, tick);
            }
        }

        finalizeFinishedOpenings();
    }

    private void resolveSpawnedStacks() {
        Iterator<UnresolvedSpawn> spawns = unresolvedSpawns.iterator();

        while (spawns.hasNext()) {
            UnresolvedSpawn spawn = spawns.next();
            ItemStack stack = spawn.entity().getStack();

            if (!stack.isEmpty()) {
                spawns.remove();
                correlator.onItemEntity(
                                spawn.world(),
                                spawn.entity().getId(),
                                spawn.position(),
                                stack.copy(),
                                tick)
                        .ifPresent(this::record);
            } else if (tick - spawn.tick() > STACK_RESOLVE_TIMEOUT_TICKS) {
                spawns.remove();
            }
        }
    }

    private void record(VaultEjectionCorrelator.Match match) {
        Watch watch = watching.get(match.vault());
        if (watch == null) {
            return;
        }

        watch.add(match.stack());
    }

    private void finalizeFinishedOpenings() {
        Iterator<Map.Entry<BlockPos, Watch>> entries = watching.entrySet().iterator();
        List<Runnable> completions = new ArrayList<>();

        while (entries.hasNext()) {
            Map.Entry<BlockPos, Watch> entry = entries.next();
            BlockPos pos = entry.getKey();
            Watch watch = entry.getValue();

            if (!watch.isFinished() || tick - watch.finishedAt() < FINALIZE_GRACE_TICKS) {
                continue;
            }

            if (correlator.hasPendingEvents(pos)) {
                continue;
            }

            entries.remove();
            correlator.forget(pos);
            completions.add(() -> complete(pos, watch));
        }

        completions.forEach(Runnable::run);
    }

    private void complete(BlockPos pos, Watch watch) {
        if (watch.isIncomplete() || watch.collected().isEmpty()) {
            LOGGER.warn("Discarding the {} vault observation at {}: {} stack(s) collected, marked incomplete={}",
                    watch.type().id(), pos, watch.collected().size(), watch.isIncomplete());
            message(Text.literal("Missed part of the vault drop at " + pos.toShortString()
                    + " - the observation was discarded").formatted(Formatting.GRAY));
            return;
        }

        LootDrop drop = LootDrop.canonical(watch.collected());
        LOGGER.info("Observed a {} vault drop at {}: {}", watch.type().id(), pos, drop);

        LootFinder finder = VaultSessionManager.getInstance().session().finder(watch.type()).orElse(null);
        if (finder == null) {
            LOGGER.warn("No {} sequence is loaded; the observation was dropped", watch.type().id());
            return;
        }

        Set<Integer> candidates = finder.observe(drop, VaultForecastConfig.getInstance().getMaxGap());
        report(watch.type(), candidates);
    }

    private void report(VaultType type, Set<Integer> candidates) {
        int candidateCount = candidates.size();
        Text prefix = Text.literal(type.displayName() + " vault: ").formatted(Formatting.GRAY);
        Text message;

        if (candidateCount == 0) {
            message = prefix.copy().append(
                    Text.literal("could not synchronize the sequence").formatted(Formatting.GRAY));
        } else if (candidateCount == 1) {
            int currentIndex = candidates.iterator().next();
            message = prefix.copy()
                    .append(Text.literal("synchronized at drop ").formatted(Formatting.GRAY))
                    .append(Text.literal("#" + (currentIndex + 1)).formatted(Formatting.WHITE));
        } else {
            message = prefix.copy()
                    .append(Text.literal("multiple candidates remain: ").formatted(Formatting.GRAY))
                    .append(Text.literal(String.valueOf(candidateCount)).formatted(Formatting.WHITE));
        }

        message(message);
    }

    private static void message(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(text, false);
        }
    }

    public int forget() {
        int watched = watching.size();

        watching.clear();
        correlator.clear();
        unresolvedSpawns.clear();

        return watched;
    }

    private record UnresolvedSpawn(RegistryKey<World> world, ItemEntity entity, Vec3d position, long tick) {
    }

    private static final class Watch {
        private final VaultType type;
        private final List<ItemStack> stacks = new ArrayList<>();

        private boolean opening;
        private boolean finished;
        private boolean incomplete;
        private long finishedAt;

        Watch(VaultType type) {
            this.type = type;
        }

        VaultType type() {
            return type;
        }

        void add(ItemStack stack) {
            stacks.add(stack);
        }

        void markOpening() {
            opening = true;
        }

        void markFinished(long tick) {
            opening = false;
            finished = true;
            finishedAt = tick;
        }

        void markIncomplete() {
            incomplete = true;
        }

        boolean isOpening() {
            return opening;
        }

        boolean isFinished() {
            return finished;
        }

        boolean isIncomplete() {
            return incomplete;
        }

        long finishedAt() {
            return finishedAt;
        }

        List<ItemStack> collected() {
            return List.copyOf(stacks);
        }
    }

    private VaultDropObserver() {
    }
}
