package luisafk.gatherdespawneditems.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import luisafk.gatherdespawneditems.GatherDespawnedItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

@Mixin(Entity.class)
public class EntityMixin {
	@Inject(at = @At("HEAD"), method = "discard")
	private void gatherBeforeDespawn(CallbackInfo info) {

		if (((Entity) (Object) this) instanceof ItemEntity) {
			// Prevent ItemEntities from being discarded if this is due to despawning
			ItemEntity itemEntity = (ItemEntity) (Object) this;
			if (itemEntity.getAge() >= ItemEntityAccessor.getLifetime()) {
				GatherDespawnedItems.addItemToInventory(itemEntity.getItem());
			}
		}
	}
}
