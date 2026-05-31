package me.ayydxn.niagara.platform;

import me.ayydxn.luminescence.platform.ULLogger;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

public class NiagaraLogger implements ULLogger
{
    private final Logger logger = (Logger) LogManager.getLogger("Niagara - Ultralight");

    @Override
    public void logMessage(Level logLevel, String message)
    {
        switch (logLevel)
        {
            case INFO ->
            {
                if (FabricLoader.getInstance().isDevelopmentEnvironment())
                    this.logger.info(message);
            }

            case WARNING -> this.logger.warn(message);
            case ERROR -> this.logger.error(message);
        }
    }
}
