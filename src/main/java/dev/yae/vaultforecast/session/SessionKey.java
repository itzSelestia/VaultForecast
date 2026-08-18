package dev.yae.vaultforecast.session;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;

import java.util.Locale;
import java.util.Optional;

public final class SessionKey {
    public static Optional<String> current(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server != null) {
            String folder = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName().toString();
            return Optional.of("singleplayer/" + folder);
        }

        ServerInfo entry = client.getCurrentServerEntry();
        if (entry != null) {
            return Optional.of("multiplayer/" + entry.address.toLowerCase(Locale.ROOT));
        }

        return Optional.empty();
    }

    public static String fileName(String key) {
        StringBuilder safe = new StringBuilder(key.length());
        for (char character : key.toCharArray()) {
            safe.append(isSafe(character) ? character : '_');
        }

        return "%s-%08x.nbt".formatted(trim(safe.toString()), key.hashCode());
    }

    private static boolean isSafe(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '.' || character == '-';
    }

    private static String trim(String text) {
        return text.length() <= 64 ? text : text.substring(0, 64);
    }

    private SessionKey() {
    }
}
