package dev.yae.vaultforecast.observation;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VaultEjectionCorrelator {
    private static final double SPAWN_Y_OFFSET = 1.2 - 0.125;

    private static final double MATCH_RADIUS = 1.5;
    private static final double MATCH_RADIUS_SQUARED = MATCH_RADIUS * MATCH_RADIUS;

    static final long MATCH_WINDOW_TICKS = 20;
    static final long EXPIRY_TICKS = 40;

    private static final int MAX_PENDING = 256;

    public record Match(BlockPos vault, ItemStack stack) {
    }

    private record PendingEvent(RegistryKey<World> world, BlockPos vault, long tick) {
    }

    private record PendingEntity(RegistryKey<World> world, int entityId, Vec3d position, ItemStack stack, long tick) {
    }

    private final List<PendingEvent> events = new ArrayList<>();
    private final List<PendingEntity> entities = new ArrayList<>();
    private final Map<Integer, Long> processedEntities = new LinkedHashMap<>();

    public Optional<Match> onEjection(RegistryKey<World> world, BlockPos vault, long tick) {
        BlockPos immutableVault = vault.toImmutable();

        Optional<PendingEntity> entity = entities.stream()
                .filter(candidate -> candidate.world().equals(world))
                .filter(candidate -> withinWindow(candidate.tick(), tick))
                .filter(candidate -> withinRadius(candidate.position(), immutableVault))
                .min(Comparator
                        .comparingDouble((PendingEntity candidate) -> distanceSquared(candidate.position(), immutableVault))
                        .thenComparingLong(PendingEntity::tick));

        if (entity.isPresent()) {
            entities.remove(entity.get());
            return Optional.of(new Match(immutableVault, entity.get().stack()));
        }

        events.add(new PendingEvent(world, immutableVault, tick));
        trim(events);
        return Optional.empty();
    }

    public Optional<Match> onItemEntity(
            RegistryKey<World> world,
            int entityId,
            Vec3d position,
            ItemStack stack,
            long tick
    ) {
        if (processedEntities.containsKey(entityId)) {
            return Optional.empty();
        }
        processedEntities.put(entityId, tick);
        trimProcessed();

        Optional<PendingEvent> event = events.stream()
                .filter(candidate -> candidate.world().equals(world))
                .filter(candidate -> withinWindow(candidate.tick(), tick))
                .filter(candidate -> withinRadius(position, candidate.vault()))
                .min(Comparator
                        .comparingDouble((PendingEvent candidate) -> distanceSquared(position, candidate.vault()))
                        .thenComparingLong(PendingEvent::tick));

        if (event.isPresent()) {
            events.remove(event.get());
            return Optional.of(new Match(event.get().vault(), stack));
        }

        entities.add(new PendingEntity(world, entityId, position, stack, tick));
        trim(entities);
        return Optional.empty();
    }

    public List<BlockPos> expire(long tick) {
        List<BlockPos> abandoned = new ArrayList<>();

        Iterator<PendingEvent> pendingEvents = events.iterator();
        while (pendingEvents.hasNext()) {
            PendingEvent event = pendingEvents.next();
            if (tick - event.tick() > EXPIRY_TICKS) {
                abandoned.add(event.vault());
                pendingEvents.remove();
            }
        }

        entities.removeIf(entity -> tick - entity.tick() > EXPIRY_TICKS);
        processedEntities.entrySet().removeIf(entry -> tick - entry.getValue() > EXPIRY_TICKS * 2);

        return abandoned;
    }

    public boolean hasPendingEvents(BlockPos vault) {
        return events.stream().anyMatch(event -> event.vault().equals(vault));
    }

    public void forget(BlockPos vault) {
        events.removeIf(event -> event.vault().equals(vault));
    }

    public void clear() {
        events.clear();
        entities.clear();
        processedEntities.clear();
    }

    public int pendingEventCount() {
        return events.size();
    }

    public int pendingEntityCount() {
        return entities.size();
    }

    private static boolean withinWindow(long first, long second) {
        return Math.abs(first - second) <= MATCH_WINDOW_TICKS;
    }

    private static boolean withinRadius(Vec3d position, BlockPos vault) {
        return distanceSquared(position, vault) <= MATCH_RADIUS_SQUARED;
    }

    private static double distanceSquared(Vec3d position, BlockPos vault) {
        return spawnPosition(vault).squaredDistanceTo(position);
    }

    public static Vec3d spawnPosition(BlockPos vault) {
        return new Vec3d(vault.getX() + 0.5, vault.getY() + SPAWN_Y_OFFSET, vault.getZ() + 0.5);
    }

    private static void trim(List<?> pending) {
        while (pending.size() > MAX_PENDING) {
            pending.removeFirst();
        }
    }

    private void trimProcessed() {
        while (processedEntities.size() > MAX_PENDING) {
            processedEntities.remove(processedEntities.keySet().iterator().next());
        }
    }
}
