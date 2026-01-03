package luisafk.gatherdespawneditems.config;

import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

public class Config {
    public int autosaveSeconds = 60;
    public int autoShuffleSeconds = 300;
    public int inventoryRows = 6;
    public String inventoryName = "Despawned Items";

    public boolean broadcastItemDespawns = true;
    public boolean broadcastTakenItems = true;

    public int optimiseCommandPermissionLevel = 1;
    public int shuffleCommandPermissionLevel = 1;

    public MenuType<ChestMenu> screenHandlerType() {
        return switch (inventoryRows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> MenuType.GENERIC_9x3;
        };
    }
}