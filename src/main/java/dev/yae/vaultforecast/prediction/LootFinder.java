package dev.yae.vaultforecast.prediction;

import dev.yae.vaultforecast.loot.model.LootDrop;
import java.util.*;

public class LootFinder {
    private final List<LootDrop> sequence;

    private Set<Integer> candidates = new HashSet<>();

    public LootFinder(List<LootDrop> sequence) {
        this.sequence = sequence;
    }

    public Set<Integer> observe(LootDrop observedDrop, int maxGap) {
        if (candidates.isEmpty()) {
            candidates = findMatches(
                    observedDrop,
                    0,
                    sequence.size() - 1
            );

            return Set.copyOf(candidates);
        }

        Set<Integer> newCandidates = new HashSet<>();

        for (int candidate : candidates) {
            int fromIndex = candidate + 1;
            int toIndex = candidate + maxGap + 1;

            newCandidates.addAll(
                    findMatches(
                            observedDrop,
                            fromIndex,
                            toIndex
                    )
            );
        }

        if (newCandidates.isEmpty()) {
            newCandidates = findMatches(
                    observedDrop,
                    0,
                    sequence.size() - 1
            );
        }

        candidates = newCandidates;

        return Set.copyOf(candidates);
    }

    private Set<Integer> findMatches(
            LootDrop drop,
            int fromIndex,
            int toIndex
    ) {
        Set<Integer> matches = new HashSet<>();

        int from = Math.max(0, fromIndex);
        int to = Math.min(toIndex, sequence.size() - 1);

        for (int index = from; index <= to; index++) {
            if (sequence.get(index).equals(drop)) {
                matches.add(index);
            }
        }

        return matches;
    }

    public Set<Integer> getCandidates() {
        return Set.copyOf(candidates);
    }

    public void setCandidates(Set<Integer> candidates) {
        this.candidates = new HashSet<>(candidates);
    }

    public List<LootDrop> getFutureDrops(int limit) {
        if (candidates.size() != 1 || limit <= 0) {
            return List.of();
        }

        int currentIndex = candidates.iterator().next();
        int fromIndex = currentIndex + 1;

        if (fromIndex < 0 || fromIndex >= sequence.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + limit, sequence.size());

        return List.copyOf(sequence.subList(fromIndex, toIndex));
    }

}
