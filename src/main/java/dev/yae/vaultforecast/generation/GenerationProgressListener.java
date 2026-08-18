package dev.yae.vaultforecast.generation;

import dev.yae.vaultforecast.loot.VaultType;

public interface GenerationProgressListener {
    GenerationProgressListener NONE = new GenerationProgressListener() {
        @Override
        public void onStage(String message) {
        }

        @Override
        public void onProgress(VaultType type, int generated, int total) {
        }
    };

    void onStage(String message);

    void onProgress(VaultType type, int generated, int total);
}
