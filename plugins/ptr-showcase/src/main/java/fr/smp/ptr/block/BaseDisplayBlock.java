package fr.smp.ptr.block;

import fr.smp.ptr.PtrShowcase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public abstract class BaseDisplayBlock implements PtrBlock {

    protected final PtrShowcase plugin;

    protected BaseDisplayBlock(PtrShowcase plugin) { this.plugin = plugin; }

    @Override public abstract String id();
    @Override public abstract String displayName();
    protected abstract Material baseDisplayMaterial();
    protected int customModelData() { return 0; }
    protected float scale() { return 1.0f; }
    protected boolean glow() { return false; }
    protected boolean withLabel() { return true; }

    @Override
    public ItemStack createItem() {
        ItemStack stack = new ItemStack(baseDisplayMaterial());
        stack.editMeta(meta -> {
            meta.displayName(Component.text(displayName(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            int cmd = customModelData();
            if (cmd > 0) meta.setCustomModelData(cmd);
            meta.getPersistentDataContainer().set(plugin.key("ptr_block_item"),
                    PersistentDataType.STRING, id());
        });
        return stack;
    }

    @Override
    public void place(Location at, Player by) {
        Location center = at.getBlock().getLocation().add(0.5, 0.0, 0.5);
        var world = center.getWorld();

        // Place a solid NOTE_BLOCK under the display so the block is mineable
        at.getBlock().setType(Material.NOTE_BLOCK, false);

        // Item display rendering the model
        var key = plugin.key("ptr_block_id");
        ItemDisplay disp = world.spawn(center, ItemDisplay.class, e -> {
            int cmd = customModelData();
            ItemStack stack = new ItemStack(baseDisplayMaterial());
            if (cmd > 0) {
                stack.editMeta(m -> {
                    try {
                        var comp = m.getCustomModelDataComponent();
                        comp.setFloats(java.util.List.of((float) cmd));
                        m.setCustomModelDataComponent(comp);
                    } catch (Throwable t) {
                        m.setCustomModelData(cmd);
                    }
                });
            }
            e.setItemStack(stack);
            e.setBillboard(Display.Billboard.FIXED);
            float s = scale();
            e.setTransformation(new Transformation(
                    new Vector3f(0, 0.05f, 0),
                    new Quaternionf(),
                    new Vector3f(s, s, s),
                    new Quaternionf(new AxisAngle4f((float) Math.toRadians(by != null ? -by.getYaw() : 0), 0, 1, 0))));
            e.setGlowing(glow());
            e.getPersistentDataContainer().set(key, PersistentDataType.STRING, id());
        });

        // Interaction entity sized to a block
        Interaction interact = world.spawn(center, Interaction.class, e -> {
            e.setInteractionHeight(1.0f);
            e.setInteractionWidth(1.0f);
            e.setResponsive(true);
            e.getPersistentDataContainer().set(key, PersistentDataType.STRING, id());
        });

        if (withLabel()) {
            world.spawn(center.clone().add(0, 1.3, 0), TextDisplay.class, e -> {
                e.text(Component.text(displayName(), NamedTextColor.AQUA));
                e.setBillboard(Display.Billboard.CENTER);
                e.setSeeThrough(false);
                e.setShadowed(true);
                e.getPersistentDataContainer().set(key, PersistentDataType.STRING, id());
            });
        }
    }
}
