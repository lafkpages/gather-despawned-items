package luisafk.gatherdespawneditems;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import luisafk.gatherdespawneditems.config.Config;
import luisafk.gatherdespawneditems.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
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

	private static MinecraftServer server;

	private static DespawnedItemsInventory inventory;

	private int ticksUntilSave;
	private int ticksUntilShuffle;
	private static volatile boolean pendingOptimise;

	private static Config config;

	public void onServerStarted(MinecraftServer server) {
		GatherDespawnedItems.server = server;

		File inventoryFile = getInventoryFile(server);

		try (FileInputStream inventoryFileInputStream = new FileInputStream(inventoryFile);
				DataInputStream inventoryFileDataInput = new DataInputStream(inventoryFileInputStream)) {
			CompoundTag nbt = NbtIo.readCompressed(inventoryFileDataInput, NbtAccounter.unlimitedHeap());

			// ContainerHelper.loadAllItems needs a pre-sized list since it uses slot
			// indices
			int size = DespawnedItemsInventory.MIN_SIZE;
			if (nbt.contains("Items")) {
				size = Math.max(size, nbt.getListOrEmpty("Items").size());
			}

			NonNullList<ItemStack> inventoryItemStacks = NonNullList.withSize(size, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(TagValueInput.create(reporter, server.registryAccess(), nbt),
					inventoryItemStacks);

			inventory = new DespawnedItemsInventory(inventoryItemStacks);

		} catch (Exception e) {
			LOGGER.error("Error while loading inventory: ", e);
			inventory = new DespawnedItemsInventory();
		}
	}

	public static void saveInventory(MinecraftServer server) {
		File inventoryFile = getInventoryFile(server);

		inventory.optimise();

		TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
		ContainerHelper.saveAllItems(output, inventory.getList());

		CompoundTag nbt = output.buildResult();

		try (FileOutputStream inventoryFileOutputStream = new FileOutputStream(inventoryFile);
				DataOutputStream inventoryFileDataOutput = new DataOutputStream(inventoryFileOutputStream)) {
			inventoryFile.createNewFile();
			NbtIo.writeCompressed(nbt, inventoryFileDataOutput);
		} catch (Exception e) {
			LOGGER.error("Error while saving inventory: ", e);
		}
	}

	public void onServerStopping(MinecraftServer server) {
		saveInventory(server);
	}

	public void onEndTick(MinecraftServer server) {
		if (--ticksUntilSave <= 0) {
			saveInventory(server);
			ticksUntilSave = config.autosaveSeconds * 20;
		}

		if (config.autoShuffleSeconds > 0 && --ticksUntilShuffle <= 0) {
			inventory.shuffle();
			ticksUntilShuffle = config.autoShuffleSeconds * 20;
		}

		if (pendingOptimise) {
			pendingOptimise = false;
			inventory.optimise();
		}
	}

	@Override
	public void onInitialize() {
		config = ConfigManager.load();

		// Offset initial ticks to stagger operations and avoid all running on the same
		// tick
		ticksUntilSave = config.autosaveSeconds * 20 - 10;
		ticksUntilShuffle = config.autoShuffleSeconds * 20 - 5;

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
					.register(Commands.literal("despawneditems:optimise")
							.requires(source -> source.permissions()
									.hasPermission(new Permission.HasCommandLevel(
											PermissionLevel.byId(config.optimiseCommandPermissionLevel))))
							.executes(context -> {
								boolean didOptimise = inventory.optimise();

								context.getSource().sendSuccess(
										() -> Component.literal(didOptimise ? "Optimised despawned items inventory."
												: "Despawned items inventory is already optimised."),
										didOptimise);

								return 1;
							}));

			dispatcher.register(Commands.literal("despawneditems:shuffle")
					.requires(source -> source.permissions().hasPermission(
							new Permission.HasCommandLevel(PermissionLevel.byId(config.shuffleCommandPermissionLevel))))
					.executes(context -> {
						inventory.shuffle();
						return 1;
					}));
		});

		LOGGER.info("Gather Despawned Items initialized!");
	}

	public static void openDespawnedItemsInventory(Player player) {
		try {
			player.openMenu(
					new SimpleMenuProvider(
							(i, playerInventory, playerEntity) -> new ReadOnlyChestMenu(config.screenHandlerType(),
									i, playerInventory, inventory, config.inventoryRows,
									GatherDespawnedItems::onItemTakenFromInventory),
							Component.nullToEmpty(config.inventoryName)));
		} catch (Exception e) {
			LOGGER.error("Error opening despawned items inventory: ", e);
			player.displayClientMessage(Component.literal("Error opening inventory: " + e.getMessage()), false);
		}
	}

	private static void onItemTakenFromInventory(Player player, ItemStack itemStack) {
		if (config.broadcastTakenItems) {
			server.getPlayerList().broadcastSystemMessage(
					Component.empty()
							.append(player.getDisplayName())
							.append(Component.literal(" retrieved "))
							.append(itemStack.getDisplayName())
							.append(Component.literal(" from despawned items")),
					false);
		}

		pendingOptimise = true;
	}

	public static void gatherDespawnedItem(ItemStack itemStack) {
		String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();

		if (config.isItemExcluded(itemId)) {
			return;
		}

		inventory.addItem(itemStack);

		if (config.broadcastItemDespawns) {
			server.getPlayerList().broadcastSystemMessage(
					Component.literal("Despawned item gathered: ").append(itemStack.getDisplayName()), false);
		}
	}

	private static File getInventoryFile(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("despawned-items.sav").toFile();
	}
}