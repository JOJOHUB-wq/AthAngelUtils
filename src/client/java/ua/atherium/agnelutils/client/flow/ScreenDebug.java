package ua.atherium.agnelutils.client.flow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import ua.atherium.agnelutils.AthAgnelUtils;
import ua.atherium.agnelutils.client.ScreenTracker;
import ua.atherium.agnelutils.client.config.ConfigStore;

public final class ScreenDebug {
    private static int lastContainerId = -1;
    private static String lastTitle = "";

    private ScreenDebug() {
    }

    public static void tickScreen(Minecraft client) {
        try {
            if (!ConfigStore.get().debugScreens) {
                return;
            }
            if (ScreenTracker.current() instanceof AbstractContainerScreen<?> cont) {
                var menu = cont.getMenu();
                String title = cont.getTitle() != null ? cont.getTitle().getString() : "?";
                if (menu.containerId == lastContainerId && title.equals(lastTitle)) {
                    return;
                }
                lastContainerId = menu.containerId;
                lastTitle = title;
                StringBuilder sb = new StringBuilder();
                sb.append("=== ").append(LocalDateTime.now()).append(" title='").append(title).append("' id=")
                    .append(menu.containerId).append(" ===\n");
                for (var slot : menu.slots) {
                    ItemStack st = slot.getItem();
                    if (st.isEmpty()) {
                        continue;
                    }
                    String name = st.getHoverName() != null ? st.getHoverName().getString() : "?";
                    String lore = "?";
                    try {
                        Object loreComp = st.get(net.minecraft.core.component.DataComponents.LORE);
                        lore = String.valueOf(loreComp);
                    } catch (Throwable ignored) {
                    }
                    boolean foil = false;
                    try {
                        foil = st.hasFoil();
                    } catch (Throwable ignored) {
                    }
                    sb.append("slot ").append(slot.index).append(" item=").append(st.getItem())
                        .append(" count=").append(st.getCount()).append(" foil=").append(foil)
                        .append(" name='").append(name).append("' lore=").append(lore).append('\n');
                }
                Path out = FabricLoader.getInstance().getConfigDir().resolve("athagnelutils-screens.log");
                Files.writeString(out, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                AthAgnelUtils.LOGGER.info("[AthAgnelUtils][screens]\n{}", sb);
            } else {
                lastContainerId = -1;
                lastTitle = "";
            }
        } catch (Throwable t) {
            AthAgnelUtils.LOGGER.warn("[AthAgnelUtils][screens] failed: {}", t.toString());
        }
    }
}
