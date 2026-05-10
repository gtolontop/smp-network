package fr.smp.ptr.mob;

import fr.smp.ptr.PtrShowcase;
import fr.smp.ptr.mob.impl.GardienMine;
import fr.smp.ptr.mob.impl.RoiPillards;
import fr.smp.ptr.mob.impl.Anomalie;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MobRegistry {

    private final PtrShowcase plugin;
    private final Map<String, PtrMob> mobs = new LinkedHashMap<>();
    private final Set<Entity> spawned = new HashSet<>();

    public MobRegistry(PtrShowcase plugin) { this.plugin = plugin; }

    public void registerAll() {
        register(new GardienMine(plugin));
        register(new RoiPillards(plugin));
        register(new Anomalie(plugin));
    }

    public void register(PtrMob m) { mobs.put(m.id(), m); }
    public PtrMob get(String id) { return mobs.get(id); }
    public Map<String, PtrMob> all() { return mobs; }
    public int size() { return mobs.size(); }

    public void track(Entity e) { spawned.add(e); }

    public boolean spawn(String id, Location at, Player by) {
        PtrMob m = mobs.get(id);
        if (m == null) return false;
        Entity e = m.spawn(at, by);
        if (e != null) spawned.add(e);
        return e != null;
    }

    public void cleanupAll() {
        for (Entity e : spawned) if (e.isValid()) e.remove();
        spawned.clear();
    }
}
