package luisafk.gatherdespawneditems;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public class GatherDespawnedItems implements ModInitializer, ServerStarted, ServerStopping, EndTick {
	public static final String MOD_ID = "gather-despawned-items";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ProblemReporter reporter = new ProblemReporter.ScopedCollector(LOGGER);

	private static DespawnedItemsInventory inventory;

	private static final int SAVE_INTERVAL_TICKS = 20 * 60; // Save every minute
	private int ticksUntilSave = SAVE_INTERVAL_TICKS - 10;

	public void onServerStarted(MinecraftServer server) {
		File inventoryFile = getInventoryFile(server);

		try (FileInputStream inventoryFileInputStream = new FileInputStream(inventoryFile);
				DataInputStream inventoryFileDataInput = new DataInputStream(inventoryFileInputStream)) {
			CompoundTag nbt = NbtIo.readCompressed(inventoryFileDataInput, NbtAccounter.unlimitedHeap());

			// ContainerHelper.loadAllItems needs a pre-sized list since it uses slot
			// indices
			// We need to determine the max slot index from the saved items
			int size = DespawnedItemsInventory.MIN_SIZE;
			if (nbt.contains("Items")) {
				var itemsList = nbt.getListOrEmpty("Items");
				for (int i = 0; i < itemsList.size(); i++) {
					var itemTag = itemsList.getCompoundOrEmpty(i);
					int slot = itemTag.getByteOr("Slot", (byte) 0) & 255;
					size = Math.max(size, slot + 1);
				}
			}

			NonNullList<ItemStack> inventoryItemStacks = NonNullList.withSize(size, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(TagValueInput.create(reporter, server.registryAccess(), nbt),
					inventoryItemStacks);

			inventory = new DespawnedItemsInventory(inventoryItemStacks);
			inventory.optimise();
		} catch (Exception e) {
			LOGGER.error("Error while loading inventory: ", e);
			inventory = new DespawnedItemsInventory();
		}
	}

	public static void saveInventory(MinecraftServer server) {
		File inventoryFile = getInventoryFile(server);

		TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
		ContainerHelper.saveAllItems(output, inventory.getList());

		CompoundTag nbt = output.buildResult();

		try (FileOutputStream inventoryFileOutputStream = new FileOutputStream(inventoryFile);
				DataOutputStream inventoryFileDataOutput = new DataOutputStream(inventoryFileOutputStream)) {
			inventoryFile.createNewFile();
			NbtIo.writeCompressed(nbt, inventoryFileDataOutput);
		} catch (Exception e) {
			LOGGER.error("Error while saving inventory: " + e);
		}
	}

	public void onServerStopping(MinecraftServer server) {
		saveInventory(server);
	}

	public void onEndTick(MinecraftServer server) {
		if (--ticksUntilSave <= 0) {
			inventory.optimise();
			saveInventory(server);
			ticksUntilSave = SAVE_INTERVAL_TICKS;
		}
	}

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(this);
		ServerLifecycleEvents.SERVER_STOPPING.register(this);
		ServerTickEvents.END_SERVER_TICK.register(this);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("despawneditems").executes(context -> {
				Player player = context.getSource().getPlayerOrException();
				openDespawnedItemsInventory(player);
				return 1;
			}));

			dispatcher
					.register(Commands.literal("despawneditems:optimise").requires(source -> source.hasPermission(1))
							.executes(context -> {
								boolean didOptimise = inventory.optimise();

								context.getSource().sendSuccess(
										() -> Component.literal(didOptimise ? "Optimised despawned items inventory."
												: "Despawned items inventory is already optimised."),
										didOptimise);

								return 1;
							}));
		});

		LOGGER.info("Gather Despawned Items initialized!");
	}

	public static void openDespawnedItemsInventory(Player player) {
		try {
			player.openMenu(
					new SimpleMenuProvider((i, playerInventory, playerEntity) -> new ChestMenu(MenuType.GENERIC_9x6,
							i, playerInventory, inventory, 6), Component.nullToEmpty("Despawned Items")));
		} catch (Exception e) {
			LOGGER.error("Error opening despawned items inventory: ", e);
			player.displayClientMessage(Component.literal("Error opening inventory: " + e.getMessage()), false);
		}
	}

	public static void addItemToInventory(ItemStack itemStack) {
		boolean success = inventory.addItem(itemStack);

		if (!success) {
			// Broadcast chat message
			// TODO
			LOGGER.info("Despawned Items inventory full, could not gather item: " + itemStack);
		}
	}

	private static File getInventoryFile(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("despawned-items.sav").toFile();
	}
}