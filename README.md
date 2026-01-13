# Gather Despawned Items

Ever thrown away flint, seeds, or other "common" items only to need them later? This mod saves despawning items to a virtual inventory so nothing goes to waste. Perfect for players who hate losing non-renewable resources.

## How it works

Gathers items that despawn into a globally available inventory that can be opened with the `/despawneditems` command. Items can't be placed in this inventory, only taken out.

![Despawned Items inventory GUI full of recovered items](https://cdn.modrinth.com/data/DwKYoqTO/images/dea3e0f0b123b45aca4f48c266e2884eceff1511_350.webp)

## Configuration

The config file is located at `config/gatherdespawneditems.json` on the server.

| Option                           | Default                    | Description                                                                      |
| -------------------------------- | -------------------------- | -------------------------------------------------------------------------------- |
| `autosaveSeconds`                | `60` (every minute)        | How often the despawned items inventory is saved to disk                         |
| `autoShuffleSeconds`             | `300` (every five minutes) | How often items are automatically shuffled/compacted to fill gaps                |
| `inventoryRows`                  | `6`                        | Number of rows in the inventory (1-6)                                            |
| `inventoryName`                  | `"Despawned Items"`        | Display name of the inventory                                                    |
| `excludedItems`                  | `["minecraft:egg", ...]`   | Items that won't be gathered when they despawn (e.g., trivially frameable items) |
| `broadcastItemDespawns`          | `true`                     | Announce in chat when items are gathered                                         |
| `broadcastTakenItems`            | `true`                     | Announce in chat when players take items from the inventory                      |
| `optimiseCommandPermissionLevel` | `1`                        | Permission level required for `/despawneditems:optimise`                         |
| `shuffleCommandPermissionLevel`  | `1`                        | Permission level required for `/despawneditems:shuffle`                          |
