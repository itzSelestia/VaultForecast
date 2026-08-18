package dev.yae.vaultforecast.client;

import dev.yae.vaultforecast.command.VaultForecastCommand;
import dev.yae.vaultforecast.observation.VaultDropObserver;
import dev.yae.vaultforecast.session.VaultSessionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.entity.ItemEntity;

public class VaultForecastClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> VaultForecastCommand.register(dispatcher)
        );

        VaultSessionManager.getInstance().register();
        registerObserver();
    }

    private static void registerObserver() {
        VaultDropObserver observer = VaultDropObserver.getInstance();

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item) {
                observer.onItemEntitySpawned(world, item);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> observer.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> observer.forget());
    }
}
