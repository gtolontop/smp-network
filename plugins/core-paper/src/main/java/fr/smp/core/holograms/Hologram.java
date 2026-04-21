package fr.smp.core.holograms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hologramme = entité vanilla TextDisplay qui rend plusieurs lignes dans un
 * seul composant (chaque <newline> est un saut de ligne côté client).
 * Aucun packet NMS, aucun entity-tracking custom : l'entité vit dans le monde
 * comme une mob standard et est gérée par le moteur vanilla.
 */
public final class Hologram {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final long id;
    private final String name;
    private Location location;
    private List<String> lines;
    private TextDisplay entity;

    public Hologram(long id, String name, Location location, List<String> lines) {
        this.id = id;
        this.name = name;
        this.location = location.clone();
        this.lines = new ArrayList<>(lines);
    }

    public long id() { return id; }
    public String name() { return name; }
    public Location location() { return location.clone(); }
    public List<String> lines() { return new ArrayList<>(lines); }
    public TextDisplay entity() { return entity; }

    public void setLines(List<String> lines) {
        this.lines = new ArrayList<>(lines);
        applyTextTo(entity);
    }

    public void setLocation(Location loc) {
        this.location = loc.clone();
        if (entity != null && !entity.isDead()) entity.teleport(loc);
    }

    public void spawn() {
        if (location.getWorld() == null) return;
        if (entity != null && !entity.isDead()) return;
        entity = location.getWorld().spawn(location, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(true);
            td.setBackgroundColor(Color.fromARGB(0x40000000));
            td.setSeeThrough(false);
            td.setDefaultBackground(false);
            td.setShadowed(false);
            td.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new org.joml.AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f),
                    new org.joml.AxisAngle4f()));
            applyTextTo(td);
        });
    }

    public void despawn() {
        if (entity != null && !entity.isDead()) entity.remove();
        entity = null;
    }

    private void applyTextTo(TextDisplay td) {
        if (td == null) return;
        Component joined = null;
        for (String line : lines) {
            Component part = MM.deserialize(line);
            if (joined == null) joined = part;
            else joined = joined.append(Component.newline()).append(part);
        }
        if (joined == null) joined = Component.empty();
        td.text(joined);
    }

    public static List<String> defaultLines() {
        return new ArrayList<>(Arrays.asList("<yellow>Hologramme</yellow>", "<gray>Ligne 2</gray>"));
    }
}
