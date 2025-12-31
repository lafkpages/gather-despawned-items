package luisafk.gatherdespawneditems;

import java.util.Iterator;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class DespawnedItemsInventory implements Container {

    public static final int MIN_SIZE = 9 * 6; // Minimum size for chest GUI

    private final NonNullList<ItemStack> stacks;

    public DespawnedItemsInventory() {
        this.stacks = NonNullList.create();

        // Fill with empty stacks for chest GUI
        for (int i = 0; i < MIN_SIZE; i++) {
            stacks.add(ItemStack.EMPTY);
        }
    }

    public DespawnedItemsInventory(NonNullList<ItemStack> itemStacks) {
        // Copy into a growable list
        this.stacks = NonNullList.create();
        this.stacks.addAll(itemStacks);

        // Ensure minimum size for chest GUI
        while (stacks.size() < MIN_SIZE) {
            stacks.add(ItemStack.EMPTY);
        }
    }

    public NonNullList<ItemStack> getList() {
        return stacks;
    }

    public int getContainerSize() {
        return stacks.size();
    }

    public boolean isEmpty() {
        Iterator<ItemStack> stacksIterator = this.stacks.iterator();

        ItemStack currentItemStack;
        do {
            if (!stacksIterator.hasNext()) {
                return true;
            }

            currentItemStack = (ItemStack) stacksIterator.next();
        } while (currentItemStack.isEmpty());

        return false;
    }

    public ItemStack getItem(int i) {
        return i >= stacks.size() ? ItemStack.EMPTY : stacks.get(i);
    }

    public ItemStack removeItem(int i, int j) {
        ItemStack itemStack = ContainerHelper.removeItem(this.stacks, i, j);
        return itemStack;
    }

    public ItemStack removeItemNoUpdate(int i) {
        return ContainerHelper.takeItem(this.stacks, i);
    }

    public void setItem(int i, ItemStack itemStack) {
        this.stacks.set(i, itemStack);
    }

    public void setChanged() {
    }

    public boolean stillValid(Player playerEntity) {
        return true;
    }

    public void clearContent() {
        stacks.clear();
    }

    public boolean addItem(ItemStack itemStack) {
        itemStack = itemStack.copy();

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack currentStack = stacks.get(i);

            if (currentStack.isEmpty()) {
                stacks.set(i, itemStack);
                return true;
            } else if (ItemStack.isSameItemSameComponents(itemStack, currentStack)) {
                int maxStackSize = Math.min(currentStack.getMaxStackSize(), this.getMaxStackSize());

                // Skip if this stack is already full
                if (currentStack.getCount() >= maxStackSize) {
                    continue;
                }

                int combinedCount = currentStack.getCount() + itemStack.getCount();

                if (combinedCount <= maxStackSize) {
                    currentStack.setCount(combinedCount);
                    return true;
                } else {
                    currentStack.setCount(maxStackSize);

                    // Continue trying to add the remaining items
                    int remainingCount = combinedCount - maxStackSize;
                    itemStack.setCount(remainingCount);

                    // Don't return here - continue the loop to find more slots
                }
            }
        }

        // No empty slot found, append new stacks to the end (infinite growth)
        while (itemStack.getCount() > 0) {
            int maxStackSize = Math.min(itemStack.getMaxStackSize(), this.getMaxStackSize());
            int toAdd = Math.min(itemStack.getCount(), maxStackSize);

            ItemStack newStack = itemStack.copy();
            newStack.setCount(toAdd);
            stacks.add(newStack);

            itemStack.setCount(itemStack.getCount() - toAdd);
        }

        return true;
    }

    /**
     * Optimise the inventory by merging stacks where possible, and moving items to
     * lower indices.
     */
    public boolean optimise() {
        // Build a new compacted list
        NonNullList<ItemStack> compacted = NonNullList.create();

        boolean didOptimise = false;

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                didOptimise = true;
                continue;
            }

            ItemStack remaining = stack.copy();

            // Try to merge with existing stacks in compacted list
            for (ItemStack target : compacted) {
                if (remaining.isEmpty()) {
                    break;
                }

                if (ItemStack.isSameItemSameComponents(remaining, target)) {
                    int maxStackSize = target.getMaxStackSize();
                    int spaceAvailable = maxStackSize - target.getCount();

                    if (spaceAvailable > 0) {
                        int toTransfer = Math.min(spaceAvailable, remaining.getCount());
                        target.setCount(target.getCount() + toTransfer);
                        remaining.setCount(remaining.getCount() - toTransfer);
                        didOptimise = true;
                    }
                }
            }

            // Add remaining as new stack(s)
            while (!remaining.isEmpty()) {
                int maxStackSize = remaining.getMaxStackSize();
                int toAdd = Math.min(remaining.getCount(), maxStackSize);

                ItemStack newStack = remaining.copy();
                newStack.setCount(toAdd);
                compacted.add(newStack);

                remaining.setCount(remaining.getCount() - toAdd);
            }
        }

        // Replace contents
        if (didOptimise) {
            stacks.clear();
            stacks.addAll(compacted);

            // Ensure minimum size for chest GUI
            while (stacks.size() < MIN_SIZE) {
                stacks.add(ItemStack.EMPTY);
            }
        }

        return didOptimise;
    }
}
