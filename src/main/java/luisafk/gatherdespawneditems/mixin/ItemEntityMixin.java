package luisafk.gatherdespawneditems.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import luisafk.gatherdespawneditems.GatherDespawnedItems;
import net.minecraft.world.entity.item.ItemEntity;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
    @Shadow
    private int pickupDelay;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))
    private void onItemDespawn(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;

        // This prevents a duplication glitch with the /give command, which uses
        // ItemEntity.makeFakeItem() to create items with age 5999 (one tick away from
        // despawning) and a pickupDelay of 32767 (from setNeverPickUp())
        if (this.pickupDelay == 32767) {
            return;
        }

        GatherDespawnedItems.addItemToInventory(self.getItem());
    }
}
