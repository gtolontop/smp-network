package fr.smp.logger.model;

/**
 * Compact 1-byte action enum stored in events.action.
 * Order MUST be append-only. NEVER renumber existing values — partition tables
 * older than the change would decode incorrectly.
 */
public enum Action {
    BLOCK_PLACE(0),
    BLOCK_BREAK(1),
    BLOCK_EXPLODE(2),
    BLOCK_BURN(3),
    BLOCK_FADE(4),
    LEAF_DECAY(5),
    LIQUID_FLOW(6),
    PISTON_EXTEND(7),
    PISTON_RETRACT(8),
    STRUCTURE_GROW(9),
    BLOCK_FORM(10),
    ENTITY_CHANGE_BLOCK(11),
    SIGN_EDIT(12),
    BANNER_PATTERN(13),
    JUKEBOX_INSERT(14),
    JUKEBOX_EJECT(15),
    LECTERN_INSERT(16),
    LECTERN_TAKE(17),
    ITEMFRAME_PLACE(18),
    ITEMFRAME_TAKE(19),
    ITEMFRAME_ROTATE(20),
    ARMOR_STAND_EQUIP(21),
    ARMOR_STAND_TAKE(22),
    ANVIL_RENAME(23),
    CONTAINER_OPEN(24),
    CONTAINER_CLOSE(25),
    CONTAINER_INSERT(26),
    CONTAINER_TAKE(27),
    INVENTORY_TRANSFER(28),
    ENTITY_KILL(29),
    PLAYER_DEATH(30),
    MOB_SPAWN(31),
    ENTITY_TAME(32),
    ENTITY_BREED(33),
    ENTITY_LEASH(34),
    ENTITY_UNLEASH(35),
    ENTITY_DAMAGE(36),
    PROJECTILE_HIT(37),
    CHAT(38),
    COMMAND(39),
    PRIVATE_MSG(40),
    SESSION_JOIN(41),
    SESSION_QUIT(42),
    SESSION_KICK(43),
    WORLD_CHANGE(44),
    TELEPORT(45),
    GAMEMODE_CHANGE(46),
    CHUNK_TRANSITION(47),
    DROP_ITEM(48),
    PICKUP_ITEM(49),
    TRADE_DROP_PICKUP(50),
    TRADE_CHEST_HANDOFF(51),
    TRADE_VILLAGER(52),
    TRADE_ITEMFRAME(53),
    TRADE_DIRECT(54),
    RARE_BREAK(55),
    RARE_PLACE(56),
    RARE_PICKUP(57),
    RARE_DROP(58),
    SPAWNER_BREAK(59),
    SPAWNER_PLACE(60),
    SPAWNER_TYPE_CHANGE(61),
    BUCKET_FILL(62),
    BUCKET_EMPTY(63),
    EXPERIENCE_PICKUP(64),
    BED_ENTER(65),
    BED_LEAVE(66),
    PORTAL_USE(67),
    BREAK_PROGRESS(68),
    INTERACT(69),
    ENCHANT(70),
    GRINDSTONE_USE(71),
    SMITHING_USE(72),
    ITEM_DAMAGE(73),
    ITEM_BREAK(74),
    EFFECT_APPLY(75);

    private final int id;
    Action(int id) { this.id = id; }
    public int id() { return id; }

    private static final Action[] BY_ID;
    static {
        int max = 0;
        for (Action a : values()) max = Math.max(max, a.id);
        BY_ID = new Action[max + 1];
        for (Action a : values()) BY_ID[a.id] = a;
    }
    public static Action of(int id) {
        if (id < 0 || id >= BY_ID.length) return null;
        return BY_ID[id];
    }
}
