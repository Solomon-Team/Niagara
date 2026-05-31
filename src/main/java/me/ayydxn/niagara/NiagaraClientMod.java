package me.ayydxn.niagara;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
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
    }
}