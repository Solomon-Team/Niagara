package me.ayydxn.niagara;

import me.ayydxn.niagara.core.NiagaraEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

@Environment(EnvType.CLIENT)
public class NiagaraClientMod implements ClientModInitializer
{
    public static final Logger LOGGER = (Logger) LogManager.getLogger("Niagara");
    public static final String MOD_ID = "niagara";

    @Override
    public void onInitializeClient()
    {
        LOGGER.info("Initializing Niagara... (Version: {})", FabricLoader.getInstance().getModContainer(MOD_ID)
                .orElseThrow().getMetadata().getVersion().getFriendlyString());

        // (Ayydxn) Defer the initialization as we need the game's window object, and it won't be available otherwise.
        MinecraftClient.getInstance().execute(NiagaraEngine::initialize);

        this.registerEvents();
    }

    private void registerEvents()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            NiagaraEngine.getInstance().update();
            NiagaraEngine.getInstance().render();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> NiagaraEngine.getInstance().shutdown());
    }
}