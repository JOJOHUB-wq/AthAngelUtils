package ua.atherium.agnelutils;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AthAgnelUtils implements ModInitializer {
    public static final String MOD_ID = "athagnelutils";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[AthAgnelUtils] main init (author: AtheriumDev). Client logic in AthAgnelUtilsClient.");
    }
}
