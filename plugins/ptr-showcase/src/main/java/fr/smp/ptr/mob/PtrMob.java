package fr.smp.ptr.mob;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface PtrMob {
    String id();
    String displayName();
    Entity spawn(Location at, Player by);
}
