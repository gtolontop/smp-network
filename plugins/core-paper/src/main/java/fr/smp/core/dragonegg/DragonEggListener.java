package fr.smp.core.dragonegg;

import fr.smp.core.SMPCore;
import fr.smp.core.utils.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class DragonEggListener implements Listener {

    private final SMPCore plugin;
    private final DragonEggManager manager;

    public DragonEggListener(SMPCore plugin, DragonEggManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // =======================================================================
    //  Block place / break / fall
    // =======================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (manager.isBeaconBeamColumn(event.getBlockPlaced())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Msg.mm("<gray>Le faisceau de l'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient> ne peut pas être obstrué.</gray>"));
            return;
        }
        if (!manager.isTracked(inHand)) {
            // Bloque la pose de DRAGON_EGG vanilla qui n'aurait pas notre tag (anti-dupe).
            if (inHand != null && inHand.getType() == Material.DRAGON_EGG) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Msg.err("Cet œuf n'est pas l'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient>."));
            }
            return;
        }
        event.setCancelled(true);
        if (!manager.canPlaceEgg(event.getPlayer())) {
            return;
        }
        if (!manager.confirmPlacement(event.getPlayer(), event.getBlockPlaced())) {
            return;
        }
        Location placeLocation = event.getBlockPlaced().getLocation();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> manager.onBlockPlace(player, placeLocation.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!manager.isTrackedBlock(block)) {
            if (manager.isAltarBlock(block)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Msg.err("L'autel de l'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient> est protégé."));
            }
            // Si c'est un DRAGON_EGG inconnu, on laisse le vanilla faire.
            return;
        }
        if (!manager.canReclaimPlacedEgg(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        // On force le drop de notre item taggé et on consume le block proprement.
        event.setCancelled(true);
        event.setDropItems(false);
        manager.onBlockBreak(event.getPlayer(), block);
        block.setType(Material.AIR, false);
        ItemStack drop = manager.createEgg();
        block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), drop);
        block.getWorld().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
    }

    /**
     * Interaction sur l'œuf posé :
     *   - Sneak + clic-droit main vide (sur notre œuf tracké) → l'œuf pop dans l'inventaire.
     *   - Sinon : on annule (anti-teleport vanilla, anti-clic accidentel) et on hint le joueur.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (block.getType() != Material.DRAGON_EGG) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        boolean trackedBlock = manager.isTrackedBlock(block);

        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getHand() == EquipmentSlot.HAND
                && player.isSneaking()
                && trackedBlock) {
            event.setCancelled(true);
            if (manager.canReclaimPlacedEgg(player)) {
                harvestPlacedEgg(player, block);
            }
            return;
        }

        event.setCancelled(true);

        if (event.getHand() == EquipmentSlot.HAND && trackedBlock && !player.isSneaking()) {
            long secondsLeft = manager.secondsUntilReclaim();
            if (secondsLeft > 0L) {
                player.sendActionBar(Msg.mm("<gray>Récupération dans <yellow>" + Msg.duration(secondsLeft) + "</yellow>.</gray>"));
            } else {
                player.sendActionBar(Msg.mm("<gray>Sneak + clic-droit pour récupérer l'<gradient:#a78bfa:#67e8f9>Œuf</gradient>.</gray>"));
            }
        }
    }

    private void harvestPlacedEgg(Player player, Block block) {
        manager.onBlockBreak(player, block);
        block.setType(Material.AIR, false);
        ItemStack egg = manager.createEgg();
        var overflow = player.getInventory().addItem(egg);
        if (overflow.isEmpty()) {
            manager.onItemPickup(player);
        } else {
            block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), egg);
        }
        block.getWorld().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
    }

    /** Coup de poing sur l'œuf (vanilla) : également annulé. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.getBlock().getType() != Material.DRAGON_EGG && !manager.isAltarBlock(event.getBlock())) return;
        event.setCancelled(true);
        if (manager.isAltarBlock(event.getBlock())) {
            event.getPlayer().sendActionBar(Msg.mm("<gray>L'autel de l'<gradient:#a78bfa:#67e8f9>Œuf</gradient> est protégé.</gray>"));
        }
    }

    /** L'œuf tombe (gravité) — on suit son bloc d'arrivée. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallingChange(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.FALLING_BLOCK) return;
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        if (manager.isBeaconBeamColumn(event.getBlock())) {
            event.setCancelled(true);
            fb.remove();
            return;
        }
        if (fb.getBlockData().getMaterial() != Material.DRAGON_EGG) return;
        // Quand le FallingBlock (re)devient un bloc, on met à jour la position.
        manager.onBlockFall(event.getBlock(), event.getBlock().getLocation());
    }

    // =======================================================================
    //  Destruction par feu / void / explosion
    // =======================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (manager.isAltarBlock(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (!manager.isTrackedBlock(event.getBlock())) return;
        manager.destroy("brûlé", event.getBlock().getLocation().toCenterLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Block egg = null;
        java.util.Set<org.bukkit.Location> protectedBlocks = new java.util.HashSet<>();
        for (Block b : event.blockList()) {
            if (manager.isAltarBlock(b)) {
                protectedBlocks.add(b.getLocation());
            }
            if (egg == null && manager.isTrackedBlock(b)) {
                egg = b;
            }
        }
        if (egg != null) {
            manager.destroy("pulvérisé par explosion", egg.getLocation().toCenterLocation());
            egg.setType(Material.AIR, false);
        }
        Block destroyedEgg = egg;
        event.blockList().removeIf(b -> protectedBlocks.contains(b.getLocation()) || (destroyedEgg != null && b.getLocation().equals(destroyedEgg.getLocation())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Block egg = null;
        java.util.Set<org.bukkit.Location> protectedBlocks = new java.util.HashSet<>();
        for (Block b : event.blockList()) {
            if (manager.isAltarBlock(b)) {
                protectedBlocks.add(b.getLocation());
            }
            if (egg == null && manager.isTrackedBlock(b)) {
                egg = b;
            }
        }
        if (egg != null) {
            manager.destroy("pulvérisé par explosion", egg.getLocation().toCenterLocation());
            egg.setType(Material.AIR, false);
        }
        Block destroyedEgg = egg;
        event.blockList().removeIf(b -> protectedBlocks.contains(b.getLocation()) || (destroyedEgg != null && b.getLocation().equals(destroyedEgg.getLocation())));
    }

    /** Empêche les pistons de bouger l'œuf — on garde le tracking simple. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (manager.isTrackedBlock(b) || manager.isAltarBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (manager.isTrackedBlock(b) || manager.isAltarBlock(b)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // =======================================================================
    //  Item entity tracking
    // =======================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item it = event.getEntity();
        if (!manager.isTracked(it.getItemStack())) return;
        manager.onItemSpawn(it);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Item it)) return;
        if (!manager.isTracked(it.getItemStack())) return;
        // L'item rentre en feu (lave / soleil / source de feu) → destruction.
        manager.destroy("brûlé", it.getLocation());
        event.setCancelled(true);
        it.remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item it)) return;
        if (!manager.isTracked(it.getItemStack())) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        String reason = switch (cause) {
            case VOID -> "tombé dans le vide";
            case FIRE, FIRE_TICK -> "brûlé";
            case LAVA -> "fondu dans la lave";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "pulvérisé par explosion";
            case CONTACT -> "détruit par un bloc";
            default -> null;
        };
        if (reason == null) return;

        manager.destroy(reason, it.getLocation());
        event.setCancelled(true);
        it.remove();
    }

    /** L'œuf en item ne doit jamais despawn naturellement — il est éternel sauf destruction. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (!manager.isTracked(event.getEntity().getItemStack())) return;
        event.setCancelled(true);
        event.getEntity().setTicksLived(1);
    }

    // =======================================================================
    //  Inventory tracking + storage refusal
    // =======================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!manager.isTracked(event.getItem().getItemStack())) return;
        manager.onItemPickup(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!manager.isTracked(event.getItemDrop().getItemStack())) return;
        manager.onItemDrop(event.getPlayer(), event.getItemDrop());
    }

    /** Refuse de mettre l'œuf dans un coffre / barrel / hopper / ender chest. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryType topType = top.getType();
        // Les inventaires "joueur" et le craft 2x2 sont OK.
        if (topType == InventoryType.CRAFTING || topType == InventoryType.PLAYER) return;

        // Cas 1 : le slot cliqué est sur l'inventaire du haut (= conteneur), avec l'œuf déposé.
        boolean clickInTop = event.getClickedInventory() == top;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (clickInTop && manager.isTracked(cursor)) {
            event.setCancelled(true);
            denyChestStorage(event.getWhoClicked());
            return;
        }
        // Cas 2 : shift-click depuis l'inventaire bas (joueur) vers le top.
        if (!clickInTop && event.isShiftClick() && manager.isTracked(current)) {
            event.setCancelled(true);
            denyChestStorage(event.getWhoClicked());
            return;
        }
        // Cas 3 : double-click qui collecte / hotbar swap qui pousserait l'œuf vers le top.
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int btn = event.getHotbarButton();
            if (btn >= 0 && event.getWhoClicked() instanceof Player p) {
                ItemStack hot = p.getInventory().getItem(btn);
                if (clickInTop && manager.isTracked(hot)) {
                    event.setCancelled(true);
                    denyChestStorage(p);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!manager.isTracked(event.getOldCursor())) return;
        Inventory top = event.getView().getTopInventory();
        InventoryType topType = top.getType();
        if (topType == InventoryType.CRAFTING || topType == InventoryType.PLAYER) return;

        int topSize = top.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                denyChestStorage(event.getWhoClicked());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (manager.isTracked(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (manager.isTracked(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private void denyChestStorage(org.bukkit.entity.HumanEntity who) {
        who.sendMessage(Msg.err("L'<gradient:#a78bfa:#67e8f9>Œuf du Dragon</gradient> refuse d'être stocké."));
        who.getWorld().playSound(who.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.4f);
    }

    // =======================================================================
    //  Quit handling — drop the egg on the ground
    // =======================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!manager.inventoryContainsEgg(player)) return;
        manager.onHolderQuit(player);
    }
}
