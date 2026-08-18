package dev.yae.vaultforecast.generation.server;

import java.util.Optional;

public final class ServerConsoleParser {
    private static final String BLOCK_DATA_MARKER = " has the following block data: ";

    private static final String STARTUP_DONE = "Done (";
    private static final String STARTUP_HELP = "For help, type";

    public static boolean isStartupComplete(String line) {
        return line.contains(STARTUP_DONE) && line.contains(STARTUP_HELP);
    }

    public static Optional<String> readBlockData(String line) {
        int marker = line.indexOf(BLOCK_DATA_MARKER);
        if (marker < 0) {
            return Optional.empty();
        }

        String payload = line.substring(marker + BLOCK_DATA_MARKER.length()).trim();
        return payload.isEmpty() ? Optional.empty() : Optional.of(payload);
    }

    private ServerConsoleParser() {
    }
}
