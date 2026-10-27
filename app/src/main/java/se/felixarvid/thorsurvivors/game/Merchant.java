package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.EquipDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Salvager: a surviving technician who trades scavenged gear for run gold. Visits the two Ashfall stages between the
 * boss minutes (4:30, 9:30 ... 24:30), stays {@link #STAY} seconds, and sells three things (a weapon or passive card, a piece of
 * equipment, supplies) rolled once per visit. Walking within {@link #TALK_R} units asks the Engine for the shop phase
 * ({@code Engine.PH_MERCHANT}); the Sim itself never pauses. Everything random comes from a Random derived from the run seed,
 * never {@link Sim#rng}, so seeded runs stay identical. All strings are made when the stock is rolled, none while drawing.
 */
public final class Merchant {
    /** Arrival times in seconds (4:30, 9:30, 14:30, 19:30, 24:30), how long it stays, when the packing-up toast comes before it leaves. */
    public static final float[] ARRIVE = {270f, 570f, 870f, 1170f, 1470f};
    public static final float STAY = 75f, PACK_WARN = 15f;
    /** Spawn distance from the hero, the walk-up radius that opens the shop and the extra distance to leave before it can open again. */
    public static final float DIST_MIN = 350f, DIST_MAX = 450f, TALK_R = 45f, HYST = 20f;
    /** The three cards of every visit, and the Fuse Kit as a 4th card (STOCK in all) that is shown only while a fusion pair is ready. */
    public static final int SLOTS = 3, STOCK = 4, SLOT_OFFER = 0, SLOT_GEAR = 1, SLOT_SUPPLY = 2, SLOT_FUSE = 3;
    public static final int SUP_MEDKIT = 0, SUP_VACUUM = 1, SUP_OROLOGION = 2;
    public static final int BUY_OK = 0, BUY_SOLD = 1, BUY_POOR = 2;
    /**
     * Base prices in run gold of the slots on the first visit (card, gear, supply, Fuse Kit); each later visit costs more: the factor is
     * 1 + PRICE_STEP x visit + PRICE_STEP2 x visit^2 (see {@link #priceOf}). v1.4: dearer and steeper, because the gold in the pocket grows faster
     * than linearly over the run (a strong build kills more and more) - the prices follow it so that one item per visit is typical.
     */
    static final int[] BASE_PRICE = {100, 140, 55, 350};
    static final float PRICE_STEP = 0.5f, PRICE_STEP2 = 0.45f;
    private static final long SALT = 0x5A17A6E5C0DEL;
    private static final int AMBER = 0xFFFFB347, GOOD = 0xFF5CF08E;

    private final Sim s;
    /** False on the legacy stages and for headless soaks that must not be disturbed. */
    public boolean enabled;
    /** True while the Salvager stands on the map. */
    public boolean present;
    public float x, y;
    /** Visits started so far (the stock of the current visit belongs to visit {@code visits - 1}). */
    public int visits;
    /** Run time when it leaves. */
    public double leaveAt;
    private int next;
    private long seed;
    private boolean warned, armed = true, wants;
    private Random vr = new Random();

    // the stock of the current visit
    public Offer offer;
    public EquipDef gearDef;
    public int gearRar, supply;
    public final boolean[] sold = new boolean[STOCK];
    public final int[] price = new int[STOCK];
    public final String[] name = new String[STOCK], desc = new String[STOCK], tag = new String[STOCK],
            priceText = new String[STOCK];
    public final int[] tagColor = new int[STOCK];
    /** The Fuse Kit: shown (the 4th card) while a pair is ready, and the fusion it would make. */
    public boolean kit;
    public se.felixarvid.thorsurvivors.data.WeaponDef kitDef;

    /** Number of cards on the counter: 3, or 4 with the Fuse Kit. */
    public int cards() {
        return kit ? STOCK : SLOTS;
    }

    Merchant(Sim s) {
        this.s = s;
    }

    void init(long runSeed, boolean enabled) {
        this.seed = runSeed ^ SALT;
        this.enabled = enabled;
    }

    /** True while the hero stands in the walk-up radius and the shop may open (the Engine checks nothing is pending first). */
    public boolean wantsOpen() {
        return present && wants;
    }

    /** The Engine opened the shop: it stays closed until the hero has left the radius and come back. */
    void opened() {
        wants = false;
        armed = false;
        refreshOffer();
        refreshKit();
    }

    /** Seconds until it leaves (0 when it is not here). */
    public float timeLeft() {
        return present ? (float) Math.max(0.0, leaveAt - s.time) : 0f;
    }

    void update() {
        if (!enabled) return;
        double t = s.time;
        if (!present) {
            while (next < ARRIVE.length && t >= ARRIVE[next] + STAY) { // a jump in time past a whole visit skips it
                next++;
                visits++;
            }
            if (next < ARRIVE.length && t >= ARRIVE[next]) arrive();
            return;
        }
        if (t >= leaveAt) {
            present = false;
            wants = false;
            return;
        }
        if (!warned && t >= leaveAt - PACK_WARN) {
            warned = true;
            s.toast("The Salvager is packing up", AMBER);
        }
        float dx = s.px - x, dy = s.py - y, d2 = dx * dx + dy * dy;
        if (!armed && d2 > (TALK_R + HYST) * (TALK_R + HYST)) armed = true;
        wants = armed && d2 < TALK_R * TALK_R;
    }

    /**
     * An event's reward (the Beacon held): a Salvager for {@code stay} seconds off the schedule. Already here: it only stays
     * longer. Not on the stages without a Salvager (and not in the soaks that switched it off).
     */
    void summon(float stay) {
        if (!enabled) return;
        if (present) {
            leaveAt = Math.max(leaveAt, s.time + stay);
            return;
        }
        int visit = Math.max(visits, ARRIVE.length);
        vr = new Random(seed + 0x5EED + visit * 0x9E3779B97F4A7C15L);
        if (!place()) return;
        visits = visit + 1;
        present = true;
        warned = false;
        armed = true;
        wants = false;
        leaveAt = s.time + stay;
        roll(visit);
        s.sound.emit(SoundEvents.CHEST_OPEN);
        s.toast("A SALVAGER HAS ARRIVED", AMBER);
    }

    private void arrive() {
        int visit = next++;
        vr = new Random(seed + visit * 0x9E3779B97F4A7C15L);
        visits = visit + 1;
        if (!place()) return; // nowhere to stand (cannot happen on the Ashfall maps): this visit is skipped
        present = true;
        warned = false;
        armed = true;
        wants = false;
        leaveAt = ARRIVE[visit] + STAY;
        if (s.time > ARRIVE[visit] + 1.0) leaveAt = s.time + STAY; // late arrival (tests that jump the clock) still stays the full time
        roll(visit);
        s.sound.emit(SoundEvents.CHEST_OPEN);
        s.toast("A SALVAGER HAS ARRIVED", AMBER);
    }

    /** A free spot 350 to 450 units from the hero; same blocked check as the finds. */
    private boolean place() {
        for (int attempt = 0; attempt < 200; attempt++) {
            float a = vr.nextFloat() * 6.2831855f, r = DIST_MIN + vr.nextFloat() * (DIST_MAX - DIST_MIN);
            if (free(s.px + (float) Math.cos(a) * r, s.py + (float) Math.sin(a) * r)) return true;
        }
        for (int i = 0; i < 72; i++) { // a very crowded area: sweep the circle at 400 units
            float a = i * 6.2831855f / 72f;
            if (free(s.px + (float) Math.cos(a) * 400f, s.py + (float) Math.sin(a) * 400f)) return true;
        }
        return false;
    }

    private boolean free(float cx, float cy) {
        if (s.map.blocked(cx, cy) || s.map.blocked(cx + 22f, cy) || s.map.blocked(cx - 22f, cy)
                || s.map.blocked(cx, cy + 22f) || s.map.blocked(cx, cy - 22f)) return false;
        x = cx;
        y = cy;
        return true;
    }

    // ------------------------------------------------------------------ stock

    /** Price of a slot on a visit: base x (1 + 0.5 x visit + 0.45 x visit^2), rounded to 5. */
    public static int priceOf(int slot, int visit) {
        return Math.round(BASE_PRICE[slot] * (1f + PRICE_STEP * visit + PRICE_STEP2 * visit * visit) / 5f) * 5;
    }

    /** The Fuse Kit appears only while a pair is ready (checked when the shop opens); once bought it stays on the counter as SOLD. */
    private void refreshKit() {
        if (kit && sold[SLOT_FUSE]) return;
        se.felixarvid.thorsurvivors.data.WeaponDef f = s.data.fusions.length == 0 ? null : s.inv.readyFusion(s.data.fusions, s.data.weapons);
        kit = f != null;
        kitDef = f;
        if (f == null) return;
        int visit = Math.max(0, visits - 1);
        sold[SLOT_FUSE] = false;
        price[SLOT_FUSE] = priceOf(SLOT_FUSE, visit);
        priceText[SLOT_FUSE] = Integer.toString(price[SLOT_FUSE]);
        name[SLOT_FUSE] = "Fuse Kit";
        desc[SLOT_FUSE] = "Fuse " + s.inv.weapons[s.inv.fuseA].def.name + " + " + s.inv.weapons[s.inv.fuseB].def.name
                + " into " + f.name + ". Frees a weapon slot.";
        tag[SLOT_FUSE] = "FUSION";
        tagColor[SLOT_FUSE] = 0xFFFFD84A;
    }

    private void roll(int visit) {
        kit = false;
        kitDef = null;
        for (int i = 0; i < SLOTS; i++) {
            sold[i] = false;
            price[i] = priceOf(i, visit);
            priceText[i] = Integer.toString(price[i]);
        }
        rollOffer();
        rollGear(visit);
        supply = visit % 2 == 0 ? SUP_MEDKIT : (visit / 2) % 2 == 0 ? SUP_VACUUM : SUP_OROLOGION;
        tag[SLOT_SUPPLY] = "SUPPLY";
        tagColor[SLOT_SUPPLY] = GOOD;
        switch (supply) {
            case SUP_MEDKIT:
                name[SLOT_SUPPLY] = "Field Medkit";
                desc[SLOT_SUPPLY] = "Restores half of your maximum HP.";
                break;
            case SUP_VACUUM:
                name[SLOT_SUPPLY] = "Vacuum Charge";
                desc[SLOT_SUPPLY] = "Pulls every gem on the map to you.";
                break;
            default:
                name[SLOT_SUPPLY] = "Orologion";
                desc[SLOT_SUPPLY] = "Freezes every enemy for 10 seconds.";
        }
    }

    /** Slot 1: one card from the same candidates as a level-up (new weapon or passive, or a level of an owned one; no branches). */
    private void rollOffer() {
        List<Offer> cards = OfferGenerator.generate(s.inv, s.data.weapons.values(), 0.0, vr);
        offer = cards.isEmpty() ? new Offer(Offer.HEAL, null, 0) : cards.get(0);
        sold[SLOT_OFFER] = false;
        name[SLOT_OFFER] = offer.title();
        desc[SLOT_OFFER] = Describe.offer(offer);
        switch (offer.kind) {
            case Offer.NEW_WEAPON: case Offer.NEW_PASSIVE:
                tag[SLOT_OFFER] = "NEW";
                tagColor[SLOT_OFFER] = 0xFF39FF88;
                break;
            case Offer.HEAL: case Offer.GOLD:
                tag[SLOT_OFFER] = "";
                tagColor[SLOT_OFFER] = 0xFFFFD84A;
                break;
            default:
                tag[SLOT_OFFER] = offer.newLevel >= offer.def.maxLevel() ? "LV " + offer.newLevel + "  MAX" : "LV " + offer.newLevel;
                tagColor[SLOT_OFFER] = 0xFFFFD84A;
        }
    }

    /** A card rolled at arrival may be stale when the hero reaches the stall (a find or a level-up took the slot): roll it again. */
    private void refreshOffer() {
        if (sold[SLOT_OFFER] || offerValid(offer)) return;
        rollOffer();
    }

    private boolean offerValid(Offer o) {
        switch (o.kind) {
            case Offer.NEW_WEAPON: return s.inv.levelOf(o.def.key) == 0 && s.inv.weaponCount < Inventory.MAX_WEAPONS;
            case Offer.NEW_PASSIVE: return s.inv.levelOf(o.def.key) == 0 && s.inv.passiveCount < Inventory.MAX_PASSIVES;
            case Offer.WEAPON_LEVEL: case Offer.PASSIVE_LEVEL: {
                int lv = s.inv.levelOf(o.def.key);
                return lv == o.newLevel - 1 && lv < o.def.maxLevel();
            }
            default: return true;
        }
    }

    /** Slot 2: a random base item, Uncommon (Rare from the third visit); one that is an upgrade for its slot when there is one. */
    private void rollGear(int visit) {
        gearRar = visit >= 2 ? Equipment.RARE : Equipment.UNCOMMON;
        List<EquipDef> all = s.data.baseItems, better = new ArrayList<>();
        for (EquipDef d : all) {
            EquipDef cur = s.equip.def[d.slot];
            if (cur == null || Equipment.value(s.data, gearRar) > Equipment.value(s.data, s.equip.rar[d.slot]) + 1e-6f) better.add(d);
        }
        List<EquipDef> pool = better.isEmpty() ? all : better;
        gearDef = pool.isEmpty() ? null : pool.get(vr.nextInt(pool.size()));
        if (gearDef == null) { // data without equipment (tests of the base data): the slot is sold out
            sold[SLOT_GEAR] = true;
            name[SLOT_GEAR] = "Nothing in stock";
            desc[SLOT_GEAR] = "";
            tag[SLOT_GEAR] = "";
            priceText[SLOT_GEAR] = "SOLD";
            return;
        }
        name[SLOT_GEAR] = Sim.itemName(gearDef, gearRar);
        StringBuilder sb = new StringBuilder();
        for (String l : Equipment.lines(s.data, gearDef, gearRar)) {
            if (sb.length() > 0) sb.append("   ");
            sb.append(l);
        }
        desc[SLOT_GEAR] = sb.toString();
        tag[SLOT_GEAR] = EquipDef.SLOT_NAMES[gearDef.slot];
        tagColor[SLOT_GEAR] = Equipment.RARITY_COLORS[gearRar];
    }

    // ------------------------------------------------------------------ buying

    /** Buys slot {@code i}: {@link #BUY_OK}, {@link #BUY_SOLD} (already bought) or {@link #BUY_POOR} (not enough gold, nothing happens). */
    public int buy(int i) {
        if (i < 0 || i >= cards() || sold[i]) return BUY_SOLD;
        if (i == SLOT_FUSE && !s.inv.canFuse(kitDef, s.data.weapons)) return BUY_SOLD; // the pair is gone
        if (s.gold < price[i]) return BUY_POOR;
        s.gold -= price[i];
        sold[i] = true;
        priceText[i] = "SOLD";
        s.sound.emit(SoundEvents.COIN);
        switch (i) {
            case SLOT_OFFER:
                s.applyOffer(offer);
                break;
            case SLOT_FUSE:
                s.fuseNow(kitDef);
                break;
            case SLOT_GEAR:
                s.acquire(gearDef, gearRar); // a better item than the worn one waits for the Keep / Replace choice after the shop
                break;
            default:
                if (supply == SUP_MEDKIT) {
                    s.sound.emit(SoundEvents.FOOD_HEAL);
                    s.heal(s.maxHp * 0.5f);
                } else {
                    s.grantPower(supply == SUP_VACUUM ? Entities.K_VACUUM : Entities.K_OROLOGION);
                }
        }
        s.toast("PURCHASED  " + name[i], AMBER);
        return BUY_OK;
    }

    /** Test hook: the visit that just started (0-based), or -1. */
    public int visitIndex() {
        return present ? visits - 1 : -1;
    }
}
