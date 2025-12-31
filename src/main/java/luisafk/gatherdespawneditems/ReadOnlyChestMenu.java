package luisafk.gatherdespawneditems;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A chest menu that only allows players to take items out, not put them back
 * in.
 */
public class ReadOnlyChestMenu extends AbstractContainerMenu {
    private final Container container;
    private final int containerRows;

    public ReadOnlyChestMenu(MenuType<?> menuType, int syncId, Inventory playerInventory, Container container,
            int rows) {
        super(menuType, syncId);
        checkContainerSize(container, rows * 9);
        this.container = container;
        this.containerRows = rows;
        container.startOpen(playerInventory.player);

        // Add container slots with read-only behavior
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new ReadOnlySlot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // Add player inventory slots (these remain normal)
        int playerInvY = 18 + rows * 18 + 13;
        this.addStandardInventorySlots(playerInventory, 8, playerInvY);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            int containerSlots = this.containerRows * 9;

            if (slotIndex < containerSlots) {
                // Moving from container to player inventory - ALLOWED
                if (!this.moveItemStackTo(slotStack, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory to container - BLOCKED
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    public Container getContainer() {
        return this.container;
    }

    public int getRowCount() {
        return this.containerRows;
    }

    /**
     * A slot that only allows taking items, not placing them.
     */
    private static class ReadOnlySlot extends Slot {
        public ReadOnlySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false; // Never allow placing items
        }
    }
}
