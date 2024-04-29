package main.java.me.dniym.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import main.java.me.dniym.IllegalStack;
import main.java.me.dniym.checks.BadPotionCheck;
import main.java.me.dniym.checks.IllegalEnchantCheck;
import main.java.me.dniym.checks.RemoveItemTypesCheck;
import main.java.me.dniym.enums.Msg;
import main.java.me.dniym.enums.Protections;
import main.java.me.dniym.timers.fTimer;
import main.java.me.dniym.utils.Scheduler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.type.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.awt.*;
import java.util.HashMap;
import java.util.UUID;

public class pLisbListener {

    private static final Logger LOGGER = LogManager.getLogger("IllegalStack/" + pLisbListener.class.getSimpleName());

    Plugin plugin;
    int debug = 0;
    HashMap<UUID, Long> messageDelay = new HashMap<>();


    public pLisbListener(IllegalStack illegalStack) {
        plugin = illegalStack;

        //ProtocolLibrary.getProtocolManager().addPacketListener(new BookCrashExploitCheck(plugin));
        if (Protections.BlockBadItemsFromCreativeTab.isEnabled()) {
            ProtocolLibrary.getProtocolManager().addPacketListener(
                    new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Client.SET_CREATIVE_SLOT).optionSync()) {
                        @Override
                        public void onPacketReceiving(PacketEvent event) {
                            try {
                                ItemStack stack = event.getPacket().getItemModifier().readSafely(0);
                                if (stack != null) {
                                    boolean isIllegal = false;

                                    if (stack.getType().toString().toUpperCase().contains("SHULKER_BOX")) {
                                        ItemStack[] shulkerContents = getShulkerContents(stack);
                                        for (ItemStack item : shulkerContents) {
                                            if (item != null) {
                                                boolean illegalEnchants = IllegalEnchantCheck.isIllegallyEnchanted(item, null, true);
                                                boolean illegalTypes = isUnobtainableVanillaItem(item);
                                                boolean illegalEgg =
                                                        (item.getType().toString().toUpperCase().contains("SPAWN_EGG") &&
                                                                item.hasItemMeta());
                                                boolean illegalContainer =
                                                        (isBlockContainer(item.getType()) && item.hasItemMeta());
                                                if (illegalEnchants || illegalTypes || illegalEgg || illegalContainer) isIllegal = true;
                                            }
                                        }

                                    } else if (stack.hasItemMeta() && stack.getType().toString().toUpperCase().contains("SPAWN_EGG")) {
                                        isIllegal = true;
                                    } else {
                                        boolean illegalEnchants = IllegalEnchantCheck.isIllegallyEnchanted(stack, null, true);
                                        boolean illegalTypes = isUnobtainableVanillaItem(stack);
                                        boolean illegalEgg = stack.hasItemMeta() && stack.getType().toString().toUpperCase().contains("SPAWN_EGG");
                                        boolean illegalContainer = stack.hasItemMeta() && isBlockContainer(stack.getType());
                                        if (illegalEnchants || illegalTypes || illegalEgg || illegalContainer) isIllegal = true;
                                    }

                                    if (isIllegal) {
                                        stack.setType(Material.AIR);
                                        Scheduler.runTaskLater(plugin, event.getPlayer()::updateInventory, 5L, event.getPlayer());
                                        event.setCancelled(true);
                                        Msg.StaffMsgCreativeBlock.getValue(event.getPlayer().getName());
                                    }
                                }
                            } catch (IndexOutOfBoundsException ex) {
                                LOGGER.error("An error receiving a SET_CREATIVE_SLOT packet has occurred, you are probably using paper and have BlockBadItemsFromCreativeTab turned on. This setting is needed very rarely, and ONLY if you have regular non-op players with access to /gmc.");
                            }
                        }
                    });
        }

        if (Protections.DisableChestsOnMobs.isEnabled()) {
    
        	
       		ProtocolLibrary.getProtocolManager().addPacketListener(
       				new PacketAdapter(PacketAdapter.params(plugin, PacketType.Play.Client.USE_ENTITY).optionAsync()) {
       					
       					/*
       					 * Must use optionAsync here... if optionSync is used it breaks player damage, eg no crits, no sweeping edge...
       					 * 
       					 * new PacketAdapter(PacketAdapter.params().plugin(plugin).optionSync().types(PacketType.Play.Client.USE_ENTITY)) {
       					 * 
       					 */
       					
       					

                        @Override
                        public void onPacketReceiving(PacketEvent event) {

                            if (event.getPacket().getIntegers().read(0) <= 0) {
                                return;
                            }

                            if (IllegalStack.hasChestedAnimals() && Protections.DisableChestsOnMobs.isEnabled()) {
                                    Entity entity;
                                    try {
                                        entity = event
                                                .getPacket()
                                                .getEntityModifier(event.getPlayer().getWorld())
                                                .read(0);
                                    } catch (RuntimeException ex) {
                                   //     LOGGER.error("Async Packet - Couldn't get an entity from id: ", ex);
                                        return;
                                    }
                                
                                    Scheduler.runTaskLater(this.plugin, () -> {
                                        if (entity instanceof Horse && ((Horse) entity).isTamed()) {
                                            ItemStack is = event.getPlayer().getInventory().getItemInHand();
                                            if (!fListener.is18() && (is == null || is.getType() != Material.CHEST)) {
                                                is = event.getPlayer().getInventory().getItemInOffHand();
                                            }
                                            if (is == null || is.getType() != Material.CHEST) {
                                                return;
                                            }
                                            exploitMessage(event.getPlayer(), entity);
                                            event.setCancelled(true);
                                            fTimer.getPunish().put(event.getPlayer(), entity);
                                            return;
                                        }

                                        if (entity instanceof ChestedHorse && ((ChestedHorse) entity).isTamed()) {
                                            ItemStack is = event.getPlayer().getInventory().getItemInMainHand();
                                            if (is == null || is.getType() != Material.CHEST) {
                                                is = event.getPlayer().getInventory().getItemInOffHand();
                                            }
                                            if (is == null || is.getType() != Material.CHEST) {
                                                return;
                                            }
                                            exploitMessage(event.getPlayer(), entity);
                                            event.setCancelled(true);

                                            ((ChestedHorse) entity).setCarryingChest(true);
                                            ((ChestedHorse) entity).setCarryingChest(false);
                                            fTimer.getPunish().put(event.getPlayer(), entity);
                                        }
                                    }, 1, entity);
                                    

                            }

                        }
                    });
        }
    }

    private ItemStack[] getShulkerContents(ItemStack shulkerBox) {
        if (shulkerBox.getType().toString().toUpperCase().contains("SHULKER_BOX")) {
            ItemMeta boxMeta = shulkerBox.getItemMeta();
            if (boxMeta instanceof BlockStateMeta) {
                BlockStateMeta blockStateMeta = (BlockStateMeta) boxMeta;
                if (blockStateMeta.getBlockState() instanceof ShulkerBox) {
                    ShulkerBox shulkerBoxState = (ShulkerBox) blockStateMeta.getBlockState();
                    ItemStack[] contents = shulkerBoxState.getInventory().getContents();
                    for (int i = 0; i < contents.length; i++) {
                        if (contents[i] != null) {
                            contents[i] = contents[i].clone(); // Clone the ItemStack to avoid modifying the original
                            ItemMeta contentMeta = contents[i].getItemMeta();
                            if (contentMeta != null) {
                                contents[i].setItemMeta(contentMeta); // Set the metadata back to the cloned ItemStack
                            }
                        }
                    }
                    return contents;
                }
            }
        }
        return new ItemStack[0];
    }

    private boolean isUnobtainableVanillaItem(ItemStack stack) {
        Material material = stack.getType();
        return material == Material.BARRIER ||
                material == Material.BEDROCK ||
                material == Material.COMMAND_BLOCK ||
                material == Material.CHAIN_COMMAND_BLOCK ||
                material == Material.REPEATING_COMMAND_BLOCK ||
                material == Material.STRUCTURE_BLOCK ||
                material == Material.STRUCTURE_VOID ||
                material == Material.JIGSAW ||
                material == Material.LIGHT ||
                material == Material.COMMAND_BLOCK_MINECART ||
                material == Material.SPAWNER ||
                material == Material.DEBUG_STICK ||
                material == Material.KNOWLEDGE_BOOK ||
                material == Material.PETRIFIED_OAK_SLAB ||
                material == Material.CHISELED_BOOKSHELF;
    }

    private boolean isBlockContainer(Material material) {
        return material == Material.CHEST ||
                material == Material.TRAPPED_CHEST ||
                material == Material.DROPPER ||
                material == Material.FURNACE ||
                material == Material.BLAST_FURNACE ||
                material == Material.SMOKER ||
                material == Material.DISPENSER ||
                material == Material.HOPPER ||
                material == Material.CHEST_MINECART ||
                material == Material.HOPPER_MINECART;
    }

    private void exploitMessage(Player p, Entity ent) {
        if (!messageDelay.containsKey(p.getUniqueId())) {
            messageDelay.put(p.getUniqueId(), 0L);
        }

        if (System.currentTimeMillis() > messageDelay.get(p.getUniqueId())) {
            p.sendMessage(Msg.PlayerDisabledHorseChestMsg.getValue());
            fListener.getLog().append2(Msg.ChestPrevented.getValue(p, ent));
            messageDelay.put(p.getUniqueId(), System.currentTimeMillis() + 2000L);
        }
    }

}
