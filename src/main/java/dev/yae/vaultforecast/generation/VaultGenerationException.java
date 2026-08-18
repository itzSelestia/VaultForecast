package dev.yae.vaultforecast.generation;

public class VaultGenerationException extends Exception {
    public VaultGenerationException(String message) {
        super(message);
    }

    public VaultGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
