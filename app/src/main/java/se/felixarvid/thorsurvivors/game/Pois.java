package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.EquipDef;
import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.Random;

/**
 * Places on the map (v1.3 design section 5), Ashfall stages only: Abandoned Outposts, Lumen Nests, Relay Terminals, hazard
 * fields (slag vents on Cinder Flats, steam jets in Deep Mines) and the Sealed Vault. Like {@link Merchant} this is a state
 * of its own that {@link Sim} calls once per step; the legacy FOREST / LIBRARY stages get nothing.
 *
 * <p>Nothing here draws from {@link Sim#rng}, so seeded runs and tests that never go near a place stay identical. The fixed
 * places (4 outposts, the vault) come from {@code Random(seed ^ salt)}; the procedural ones (nests, terminals, hazard
 * fields) are pure functions of the cell hash, like the destructibles. Guards and hounds are spawned with
 * {@link Sim#poiSpawn}, which takes its animation phase from this class's own generator. The hazard fields keep their
 * own state and never use the 32-slot hazard pool, so its reserve for telegraphs and boss attacks is untouched.
 * Nothing allocates while a run is stepping (the Randoms are created at init and only re-seeded).
 */
public final class Pois {
    private static final long SALT = 0x0B5E7A11ED0C7L, SALT_GUARD = 0x6A12D5C0FFEE1L, SALT_ANIM = 0x1CEB00DA5L, SALT_PICK = 0x51DE0F7E12AL;

    // ------------------------------------------------------------------ abandoned outposts
    /** Outposts per run, their distances from the start (a spot is drawn up to {@link #OUT_SPREAD} beyond), the least gap to another place. */
    public static final int MAX_OUT = 4;
    public static final float[] OUT_DIST = {800f, 1400f, 2200f, 2850f};
    public static final float OUT_SPREAD = 150f, APART = 500f, FIND_APART = 150f;
    /** Guards wake when the hero is this close (u); they are put on the map when the hero comes within {@link #SPAWN_R} (less than the nearest outpost's distance from the start, so the first minutes never see one). */
    public static final float WAKE_R = 260f, SPAWN_R = 700f, GUARD_RING = 118f, LOOT_R = 36f, LOCKER_DX = 30f;
    /** Loot bits of an outpost: the Rare gear locker and the supply chest. */
    public static final int LOOT_LOCKER = 1, LOOT_CHEST = 2;
    public static final int GUARDS_MIN = 6, GUARDS_SPAN = 5;

    public int outN;
    public final float[] outX = new float[MAX_OUT], outY = new float[MAX_OUT];
    public final boolean[] outSpawned = new boolean[MAX_OUT], outWoken = new boolean[MAX_OUT], outCleared = new boolean[MAX_OUT];
    public final int[] outLoot = new int[MAX_OUT], outGuards = new int[MAX_OUT];
    /** True when the outpost has an elite-flagged guard (the two farthest). */
    public final boolean[] outElite = new boolean[MAX_OUT];

    // ------------------------------------------------------------------ sealed vault
    public static final int V_SEALED = 0, V_OPEN = 1, V_DONE = 2;
    /** The barrels stand in front of the wall, each within a blast ({@link Sim#BARREL_R}) of it and of each other. */
    static final float[] BARREL_DX = {-44f, 0f, 44f}, BARREL_DY = {50f, 64f, 50f};
    public static final float VAULT_DIST = 1900f, VAULT_SEE = 600f, CORE_R = 38f;
    public boolean hasVault, vaultFound;
    public int vaultState;
    public float vaultX, vaultY;
    public final float[] vbX = new float[3], vbY = new float[3];
    /** Seconds since the wall broke (the renderer's rubble and burst). */
    public float vaultOpenT;

    // ------------------------------------------------------------------ relay terminals
    public static final float TERM_CELL = 2500f, TERM_R = 44f, TERM_HOLD = 2f, HYST = 24f, TERM_P = 0.7f;
    public static final int MAX_TERM = 12, CARDS = 3;
    public static final int B_OVERCLOCK = 0, B_REINFORCE = 1, B_LENS = 2, B_SCRAP = 3, BUFFS = 4;
    public static final float BUFF_TIME = 300f;
    public static final String[] BUFF_NAME = {"OVERCLOCK", "REINFORCE", "LUMEN LENS", "SCRAP"};
    public static final String[] BUFF_GAIN = {"+15% might", "+20% max HP", "+0.1 luck", "+1 level on a random weapon"};
    public static final String[] BUFF_COST = {"Enemies move 10% faster", "-10% move speed", "Light radius -20%", "Enemy spawns +10%"};
    public int termN;
    public final float[] termX = new float[MAX_TERM], termY = new float[MAX_TERM];
    final long[] termKey = new long[MAX_TERM];
    private final long[] used = new long[64];
    private int usedN, usedHead;
    private int termCx = Integer.MIN_VALUE, termCy;
    /** Seconds the hero has stood on a terminal, and the one under him; the screen opens at {@link #TERM_HOLD}. */
    public float termHold;
    public int termNear = -1;
    private boolean termArmed = true, termWants;
    /** The screen's three cards (BUFF_* ids) of the terminal being used. */
    public final int[] cardBuff = new int[CARDS];
    private int termOpen = -1;
    /** The buff running (-1 none), when it ends, and the costs it brings: enemies' speed and the spawn rate (multipliers, 1 = none). */
    public int buff = -1;
    public double buffUntil;
    public float enemySpeedMul = 1f, spawnMul = 1f;

    // ------------------------------------------------------------------ hazard fields
    public static final float HAZ_CELL = 700f, HAZ_P = 0.12f, HAZ_CLEAR = 1000f;
    public static final int MAX_VENT = 24;
    public static final float VENT_PERIOD = 4f, VENT_WARN = 1f, VENT_R = 50f, VENT_HERO = 18f, VENT_ENEMY = 30f, VENT_BURST = 0.6f;
    public static final float JET_LEN = 170f, JET_HALF = 0.4f, JET_TICK = 0.5f, JET_HERO = 10f, JET_ENEMY = 16f;
    public int ventN;
    public final float[] ventX = new float[MAX_VENT], ventY = new float[MAX_VENT], ventOff = new float[MAX_VENT],
            ventSpin = new float[MAX_VENT], ventBurst = new float[MAX_VENT];
    private final int[] ventCyc = new int[MAX_VENT];
    private int hazCx = Integer.MIN_VALUE, hazCy;
    private float jetAcc;

    // ------------------------------------------------------------------ lumen nests
    public static final float NEST_CELL = 1500f, NEST_P = 0.25f, NEST_CLEAR = 1000f, NEST_R = 700f, NEST_EVERY = 8f, NEST_HP = 80f;
    public static final int NEST_PACK = 4, NEST_HOUNDS_MAX = 16, MAX_ACTIVE_NESTS = 2;
    /** Dest indexes of the nests spawning now (at most two) and their count; refreshed every step. */
    public int nestActiveN;
    public final int[] nestActive = new int[MAX_ACTIVE_NESTS];
    /** {@link Enemy#poi} of a nest's hound. */
    public static final int POI_HOUND = -2;

    // ------------------------------------------------------------------ state
    private final Sim s;
    /** False on the legacy stages and for headless soaks that must not be disturbed. */
    public boolean enabled;
    private long seed;
    private final Random pr = new Random(), gr = new Random(), ar = new Random();
    private float scanT;
    /** Run time before which the outposts, nests and hazard fields do nothing: the first seconds are calm (tests that teleport the hero set it to 0). */
    public double calmUntil = 20.0;
    private boolean hinted;

    Pois(Sim s) {
        this.s = s;
    }

    /** The places' own generator for the first timers of their spawns (a Sim.rng stand-in). */
    java.util.Random quiet() {
        return ar;
    }

    // ================================================================== placement of the fixed places

    void init(long runSeed, boolean enable) {
        seed = runSeed;
        enabled = enable;
        outN = 0;
        hasVault = false;
        vaultFound = false;
        vaultState = V_SEALED;
        ar.setSeed(runSeed ^ SALT_ANIM);
        if (!enable) return;
        Random fr = new Random(runSeed ^ SALT);
        for (int i = 0; i < MAX_OUT; i++) {
            for (int attempt = 0; attempt < 800; attempt++) {
                float a = fr.nextFloat() * 6.2831855f;
                float r = OUT_DIST[i] + fr.nextFloat() * OUT_SPREAD;
                float x = (float) Math.cos(a) * r, y = (float) Math.sin(a) * r;
                if (!apartFromOthers(x, y) || !ringFree(x, y, 90f) || !ringFree(x, y, 150f)) continue;
                outX[outN] = x;
                outY[outN] = y;
                outSpawned[outN] = outWoken[outN] = outCleared[outN] = false;
                outLoot[outN] = outGuards[outN] = 0;
                outElite[outN] = i >= 2;
                outN++;
                break;
            }
        }
        for (int attempt = 0; attempt < 800; attempt++) {
            float a = fr.nextFloat() * 6.2831855f;
            float r = VAULT_DIST + fr.nextFloat() * OUT_SPREAD;
            float x = (float) Math.cos(a) * r, y = (float) Math.sin(a) * r;
            if (!apartFromOthers(x, y) || !ringFree(x, y, 80f) || !ringFree(x, y, 140f)) continue;
            boolean ok = true;
            for (int k = 0; k < 3 && ok; k++) ok = !s.map.blocked(x + BARREL_DX[k], y + BARREL_DY[k]);
            if (!ok) continue;
            vaultX = x;
            vaultY = y;
            for (int k = 0; k < 3; k++) {
                vbX[k] = x + BARREL_DX[k];
                vbY[k] = y + BARREL_DY[k];
            }
            hasVault = true;
            break;
        }
    }

    /** Centre and eight points of a ring all free of props and walls. */
    private boolean ringFree(float x, float y, float r) {
        if (s.map.blocked(x, y)) return false;
        for (int k = 0; k < 8; k++) {
            float a = k * 0.7853982f;
            if (s.map.blocked(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r)) return false;
        }
        return true;
    }

    private boolean apartFromOthers(float x, float y) {
        for (int i = 0; i < outN; i++) {
            float dx = x - outX[i], dy = y - outY[i];
            if (dx * dx + dy * dy < APART * APART) return false;
        }
        if (hasVault) {
            float dx = x - vaultX, dy = y - vaultY;
            if (dx * dx + dy * dy < APART * APART) return false;
        }
        for (int j = 0; j < s.findN; j++) {
            float dx = x - s.findX[j], dy = y - s.findY[j];
            if (dx * dx + dy * dy < FIND_APART * FIND_APART) return false;
        }
        return true;
    }

    // ================================================================== per step

    void update(float dt) {
        if (!enabled) return;
        if (!hinted && s.time >= 6.0) {
            hinted = true;
            s.toast("Outposts and a sealed vault - check the minimap", 0xFFFF8A2A);
        }
        boolean was = s.quietRng;
        s.quietRng = true; // sparks and first timers of everything done here come from the places' own random
        updateBuff();
        boolean calm = s.time < calmUntil;
        if (!calm) updateOutposts(dt);
        updateVault();
        updateTerminals(dt);
        if (!calm) {
            updateHazards(dt);
            updateNests(dt);
        } else {
            nestActiveN = 0;
        }
        s.quietRng = was;
    }

    // ------------------------------------------------------------------ outposts

    private void updateOutposts(float dt) {
        scanT -= dt;
        boolean scan = scanT <= 0f;
        if (scan) scanT = 0.25f;
        for (int i = 0; i < outN; i++) {
            float dx = s.px - outX[i], dy = s.py - outY[i], d2 = dx * dx + dy * dy;
            if (!outSpawned[i]) {
                if (d2 < SPAWN_R * SPAWN_R) spawnGuards(i);
                continue;
            }
            if (!outCleared[i]) {
                if (!outWoken[i] && d2 < WAKE_R * WAKE_R) wake(i);
                if (scan) {
                    if (!outWoken[i] && guardHurt(i)) wake(i);
                    if (liveWith(i) == 0) cleared(i);
                }
                continue;
            }
            if (d2 > 160f * 160f) continue;
            tryLoot(i, LOOT_LOCKER, outX[i] - LOCKER_DX, outY[i]);
            tryLoot(i, LOOT_CHEST, outX[i] + LOCKER_DX, outY[i]);
        }
    }

    /** Puts the outpost's ring of dormant guards on the map: 6 to 10 sergeants and welders, one elite in the far outposts. */
    private void spawnGuards(int i) {
        gr.setSeed(seed ^ SALT_GUARD ^ ((i + 1) * 0x9E3779B97F4A7C15L));
        int n = GUARDS_MIN + gr.nextInt(GUARDS_SPAN);
        if (s.enemyN + n > Sim.MAX_ENEMIES - 6) return; // no room in the pool right now: try again next step
        EnemyType sgt = s.type("SERGEANT"), wld = s.type("WELDER");
        if (sgt == null || wld == null) {
            outSpawned[i] = outWoken[i] = outCleared[i] = true; // data without the guards (tests of the base data): nothing to fight
            return;
        }
        float a0 = gr.nextFloat() * 6.2831855f;
        int eliteAt = outElite[i] ? gr.nextInt(n) : -1;
        for (int k = 0; k < n; k++) {
            float a = a0 + k * 6.2831855f / n + (gr.nextFloat() - 0.5f) * 0.2f;
            float r = GUARD_RING * (0.85f + 0.3f * gr.nextFloat());
            float x = outX[i] + (float) Math.cos(a) * r, y = outY[i] + (float) Math.sin(a) * r;
            if (s.map.blocked(x, y)) { // pull it in until it stands free
                x = outX[i] + (float) Math.cos(a) * r * 0.55f;
                y = outY[i] + (float) Math.sin(a) * r * 0.55f;
            }
            boolean elite = k == eliteAt;
            Enemy e = s.poiSpawn((k & 1) == 0 ? sgt : wld, x, y, i, elite, elite ? Elites.roll(gr, Elites.modCount(s.data, s.time)) : 0);
            if (e == null) break;
            e.sleeping = true;
            outGuards[i]++;
        }
        outSpawned[i] = true;
    }

    private void wake(int i) {
        outWoken[i] = true;
        for (int k = 0; k < s.enemyN; k++) {
            Enemy e = s.enemies[k];
            if (e.poi == i) e.sleeping = false;
        }
        s.toast("THE OUTPOST WAKES", 0xFFFF8A2A);
        s.sound.emit(SoundEvents.ELITE_SPAWN);
    }

    private boolean guardHurt(int i) {
        for (int k = 0; k < s.enemyN; k++) {
            Enemy e = s.enemies[k];
            if (e.poi == i && e.sleeping && e.hp < e.maxHp) return true;
        }
        return false;
    }

    /** Live enemies that belong to outpost {@code i} (guards, asleep or awake). */
    private int liveWith(int i) {
        int n = 0;
        for (int k = 0; k < s.enemyN; k++) {
            Enemy e = s.enemies[k];
            if (e.poi == i && !e.dead) n++;
        }
        return n;
    }

    private void cleared(int i) {
        outCleared[i] = true;
        outWoken[i] = true;
        s.sound.emit(SoundEvents.CHEST_OPEN);
        s.toast("OUTPOST CLEARED  -  OPEN THE LOCKER AND CHEST", 0xFFFFB347);
    }

    private void tryLoot(int i, int bit, float x, float y) {
        if ((outLoot[i] & bit) != 0) return;
        float dx = s.px - x, dy = s.py - y;
        if (dx * dx + dy * dy >= LOOT_R * LOOT_R) return;
        outLoot[i] |= bit;
        s.sound.emit(SoundEvents.CHEST_OPEN);
        if (bit == LOOT_LOCKER) { // a Rare piece of gear (a random base item from the run's own Random)
            s.toast("RARE GEAR LOCKER", 0xFFFFD84A);
            if (!s.data.baseItems.isEmpty()) {
                pr.setSeed(seed ^ SALT_PICK ^ (i * 31L + 7L));
                EquipDef d = s.data.baseItems.get(pr.nextInt(s.data.baseItems.size()));
                s.acquire(d, Equipment.RARE);
            }
            return;
        }
        s.toast("SUPPLY CHEST", 0xFFFFB347);
        s.addPickup(Entities.K_COINBAG, x, y, 20f);
        s.addPickup(Entities.K_COIN, x + 12f, y + 6f, 1f);
        s.addPickup(Entities.K_ROAST, x - 12f, y + 8f, 0f);
        float each = s.xpNeed * 0.6f / 5f;
        for (int k = 0; k < 5; k++) {
            float a = k * 1.2566371f;
            s.addPickup(Entities.K_GEM, x + (float) Math.cos(a) * 18f, y + (float) Math.sin(a) * 12f, each);
        }
    }

    /** Nearest outpost index by distance, or -1 (tests and the minimap). */
    public int liveGuards(int i) {
        return liveWith(i);
    }

    // ------------------------------------------------------------------ the vault

    private void updateVault() {
        if (!hasVault || vaultState == V_DONE) return;
        float dx = s.px - vaultX, dy = s.py - vaultY, d2 = dx * dx + dy * dy;
        if (!vaultFound && d2 < VAULT_SEE * VAULT_SEE) {
            vaultFound = true;
            s.toast("A SEALED VAULT - BLAST THE WALL", 0xFFFFD84A);
        }
        if (vaultState == V_OPEN) {
            vaultOpenT += 1f / 60f;
            if (d2 < CORE_R * CORE_R) takeCore();
        }
    }

    /** A barrel blast went off at (x, y): the vault wall breaks when it is inside the blast. Only barrels call this; no weapon opens the vault. */
    void onBlast(float x, float y) {
        if (!enabled || !hasVault || vaultState != V_SEALED) return;
        float dx = x - vaultX, dy = y - vaultY;
        if (dx * dx + dy * dy >= Sim.BARREL_R * Sim.BARREL_R) return;
        boolean was = s.quietRng;
        s.quietRng = true;
        vaultState = V_OPEN;
        vaultOpenT = 0f;
        vaultFound = true;
        s.sound.emit(SoundEvents.BOSS_SLAM);
        s.addFx(Entities.FX_SLAM, vaultX, vaultY, 110f, 0f, 0.5f, 0, false);
        s.spark(vaultX, vaultY - 20f, 0xFFFFD84A, 16);
        s.spark(vaultX, vaultY, 0xFF8A8070, 10);
        s.toast("THE VAULT IS OPEN  -  TAKE THE FUSION CORE", 0xFFFFD84A);
        s.quietRng = was;
    }

    /** The Fusion Core: fuses a ready pair; with none ready, +1 level on every weapon that is not at its maximum. */
    private void takeCore() {
        vaultState = V_DONE;
        s.sound.emit(SoundEvents.EVOLUTION);
        WeaponDef f = s.data.fusions.length == 0 ? null : s.inv.readyFusion(s.data.fusions, s.data.weapons);
        if (f != null && s.fuseNow(f)) {
            s.toast("FUSION CORE: " + f.name, 0xFFFFD84A);
            return;
        }
        int n = 0;
        for (int i = 0; i < s.inv.weaponCount; i++) if (s.inv.levelUp(s.inv.weapons[i].def.key) > 0) n++;
        s.recomputeAll();
        s.toast(n == 0 ? "FUSION CORE: every weapon is maxed" : "FUSION CORE: +1 LEVEL ON " + n + (n == 1 ? " WEAPON" : " WEAPONS"), 0xFFFFD84A);
    }

    // ------------------------------------------------------------------ relay terminals

    private static long cellKey(int i, int j, long salt) {
        return (((long) i << 32) ^ (j & 0xFFFFFFFFL)) ^ salt;
    }

    private float placeX, placeY;

    /** The terminal of 2500 u cell (cx, cy) into placeX / placeY: a pure function of the cell and the layout. */
    private boolean termAt(int cx, int cy) {
        if (WorldGen.unit(WorldGen.hash(cx, cy, 811)) >= TERM_P) return false;
        for (int a = 0; a < 5; a++) {
            float x = cx * TERM_CELL + 250f + WorldGen.unit(WorldGen.hash(cx, cy, 812 + a * 2)) * (TERM_CELL - 500f);
            float y = cy * TERM_CELL + 250f + WorldGen.unit(WorldGen.hash(cx, cy, 813 + a * 2)) * (TERM_CELL - 500f);
            if (x * x + y * y < 1100f * 1100f || s.map.blocked(x, y)) continue;
            placeX = x;
            placeY = y;
            return true;
        }
        return false;
    }

    private boolean isUsed(long key) {
        for (int i = 0; i < usedN; i++) if (used[i] == key) return true;
        return false;
    }

    public boolean termUsed(int i) {
        return isUsed(termKey[i]);
    }

    private void refreshTerminals() {
        int cx = Math.floorDiv((int) Math.floor(s.px), (int) TERM_CELL), cy = Math.floorDiv((int) Math.floor(s.py), (int) TERM_CELL);
        if (cx == termCx && cy == termCy) return;
        termCx = cx;
        termCy = cy;
        termN = 0;
        for (int j = cy - 1; j <= cy + 1; j++) {
            for (int i = cx - 1; i <= cx + 1; i++) {
                if (termN >= MAX_TERM || !termAt(i, j)) continue;
                termX[termN] = placeX;
                termY[termN] = placeY;
                termKey[termN++] = cellKey(i, j, 0x7E00000000000000L);
            }
        }
    }

    private void updateTerminals(float dt) {
        refreshTerminals();
        int near = -1;
        float nearD2 = Float.MAX_VALUE;
        for (int i = 0; i < termN; i++) {
            if (isUsed(termKey[i])) continue;
            float dx = s.px - termX[i], dy = s.py - termY[i], d2 = dx * dx + dy * dy;
            if (d2 < nearD2) {
                nearD2 = d2;
                near = i;
            }
        }
        termWants = false;
        if (near < 0 || nearD2 > (TERM_R + HYST) * (TERM_R + HYST)) {
            termNear = -1;
            termHold = 0f;
            termArmed = true;
            return;
        }
        if (nearD2 >= TERM_R * TERM_R || !termArmed) { // in the hysteresis band, or declined and not yet left
            termNear = -1;
            termHold = 0f;
            return;
        }
        termNear = near;
        termHold += dt;
        termWants = termHold >= TERM_HOLD;
    }

    /** True while the hero has stood on a terminal long enough and the Engine may open the choice screen. */
    public boolean wantsTerminal() {
        return enabled && termWants && termNear >= 0;
    }

    /** The Engine opened the screen: rolls the three cards (all four buffs less one, chosen by the terminal) and disarms it until the hero walks off. */
    void opened() {
        termOpen = termNear;
        termWants = false;
        termArmed = false;
        termHold = 0f;
        long k = termKey[termOpen];
        int skip = (int) (((k >>> 17) ^ k) & 3L), c = 0;
        for (int b = 0; b < BUFFS; b++) if (b != skip) cardBuff[c++] = b;
    }

    /** Takes card {@code i} of the open screen: the terminal is used up, the buff starts (a running one is replaced). */
    public void pick(int i) {
        if (termOpen < 0 || i < 0 || i >= CARDS) return;
        long k = termKey[termOpen];
        if (!isUsed(k)) {
            used[usedHead] = k;
            usedHead = (usedHead + 1) % used.length;
            usedN = Math.min(used.length, usedN + 1);
        }
        termOpen = -1;
        startBuff(cardBuff[i]);
    }

    /** The Engine closed the screen without a pick (B): the terminal stays unused. */
    public void declined() {
        termOpen = -1;
    }

    /** Starts a buff and its cost for {@link #BUFF_TIME} s, replacing the one running. Tests call it directly. */
    public void startBuff(int b) {
        clearBuff();
        buff = b;
        buffUntil = s.time + BUFF_TIME;
        if (b == B_OVERCLOCK) enemySpeedMul = 1.1f;
        else if (b == B_SCRAP) {
            spawnMul = 1.1f;
            pr.setSeed(seed ^ SALT_PICK ^ ((long) (s.time * 10.0) * 0x9E3779B97F4A7C15L));
            int n = 0, pick = -1;
            for (int i = 0; i < s.inv.weaponCount; i++) if (s.inv.weapons[i].level < s.inv.weapons[i].def.maxLevel()) n++;
            if (n > 0) pick = pr.nextInt(n);
            for (int i = 0; i < s.inv.weaponCount && pick >= 0; i++) {
                if (s.inv.weapons[i].level >= s.inv.weapons[i].def.maxLevel() || pick-- > 0) continue;
                s.inv.levelUp(s.inv.weapons[i].def.key);
                break;
            }
        }
        s.recomputeAll();
        s.sound.emit(SoundEvents.EVOLUTION);
        s.toast("RELAY: " + BUFF_NAME[b] + "  (5 MIN)", 0xFF40D8FF);
    }

    private void clearBuff() {
        buff = -1;
        enemySpeedMul = 1f;
        spawnMul = 1f;
    }

    private void updateBuff() {
        if (buff < 0 || s.time < buffUntil) return;
        String name = BUFF_NAME[buff];
        clearBuff();
        s.recomputeAll();
        s.toast("RELAY: " + name + " ENDED", 0xFF9AA8B0);
    }

    /** Seconds of the running buff left (0 when none). */
    public float buffLeft() {
        return buff < 0 ? 0f : (float) Math.max(0.0, buffUntil - s.time);
    }

    /** Called from {@link Sim#recomputeAll}: the running buff's gain and its stat cost on top of the computed stats. */
    void applyBuff(PlayerStats st) {
        switch (buff) {
            case B_OVERCLOCK:
                st.might *= 1.15;
                break;
            case B_REINFORCE:
                st.maxHpMul *= 1.2;
                st.moveSpeedMul *= 0.9;
                break;
            case B_LENS:
                st.luck += 0.1;
                st.lightRadius *= 0.8;
                break;
            default:
        }
    }

    // ------------------------------------------------------------------ hazard fields

    /** Hazard field of 700 u cell (cx, cy): its centre into placeX / placeY. About 12% of the cells, never near the start. */
    private boolean fieldAt(int cx, int cy) {
        if (WorldGen.unit(WorldGen.hash(cx, cy, 831)) >= HAZ_P) return false;
        float x = (cx + 0.5f) * HAZ_CELL, y = (cy + 0.5f) * HAZ_CELL;
        if (x * x + y * y < HAZ_CLEAR * HAZ_CLEAR) return false;
        placeX = x;
        placeY = y;
        return true;
    }

    private void refreshVents() {
        int cx = Math.floorDiv((int) Math.floor(s.px), (int) HAZ_CELL), cy = Math.floorDiv((int) Math.floor(s.py), (int) HAZ_CELL);
        if (cx == hazCx && cy == hazCy) return;
        hazCx = cx;
        hazCy = cy;
        ventN = 0;
        int per = s.map.mines ? 3 : 4;
        for (int j = cy - 2; j <= cy + 2; j++) {
            for (int i = cx - 2; i <= cx + 2; i++) {
                if (!fieldAt(i, j)) continue;
                float fx = placeX, fy = placeY;
                for (int k = 0; k < per && ventN < MAX_VENT; k++) {
                    float a = WorldGen.unit(WorldGen.hash(i, j, 840 + k)) * 6.2831855f;
                    float r = 60f + WorldGen.unit(WorldGen.hash(i, j, 850 + k)) * 190f;
                    float x = fx + (float) Math.cos(a) * r, y = fy + (float) Math.sin(a) * r;
                    if (s.map.blocked(x, y)) continue;
                    ventX[ventN] = x;
                    ventY[ventN] = y;
                    ventOff[ventN] = WorldGen.unit(WorldGen.hash(i, j, 860 + k)) * VENT_PERIOD;
                    ventSpin[ventN] = (WorldGen.unit(WorldGen.hash(i, j, 870 + k)) < 0.5f ? -0.55f : 0.55f);
                    ventBurst[ventN] = 0f;
                    ventCyc[ventN] = (int) ((s.time + ventOff[ventN]) / VENT_PERIOD);
                    ventN++;
                }
            }
        }
    }

    /** Seconds into this vent's 4 s cycle (0 .. 4); the last {@link #VENT_WARN} s are the warning, the burst comes as it wraps. */
    public float ventPhase(int i) {
        return (float) ((s.time + ventOff[i]) % VENT_PERIOD);
    }

    /** Angle of a steam jet now (Deep Mines): it turns slowly. */
    public float jetAngle(int i) {
        return ventOff[i] * 1.57f + ventSpin[i] * (float) s.time;
    }

    private void updateHazards(float dt) {
        refreshVents();
        if (ventN == 0) return;
        boolean jets = s.map.mines;
        if (jets) jetAcc += dt;
        boolean tick = jets && jetAcc >= JET_TICK;
        if (tick) jetAcc -= JET_TICK;
        float near2 = 900f * 900f;
        for (int i = 0; i < ventN; i++) {
            float dx = s.px - ventX[i], dy = s.py - ventY[i];
            if (dx * dx + dy * dy > near2) continue;
            if (jets) {
                if (tick) jetTick(i);
                continue;
            }
            if (ventBurst[i] > 0f) ventBurst[i] -= dt;
            int cyc = (int) ((s.time + ventOff[i]) / VENT_PERIOD);
            if (cyc != ventCyc[i]) {
                ventCyc[i] = cyc;
                erupt(i);
            }
        }
    }

    /** A slag vent erupts: damage to the hero and every enemy inside {@link #VENT_R}. */
    private void erupt(int i) {
        float x = ventX[i], y = ventY[i];
        ventBurst[i] = VENT_BURST;
        s.spark(x, y - 6f, 0xFFFF8A2A, 8);
        s.spark(x, y, 0xFF3A3028, 4);
        float hx = s.px - x, hy = s.py - y, rr = VENT_R + Sim.PLAYER_R * 0.4f;
        if (hx * hx + hy * hy < 600f * 600f) s.sound.emit(SoundEvents.CARRIER_BURST);
        if (hx * hx + hy * hy < rr * rr && s.invuln <= 0f && s.ended == 0) {
            s.hurtHero(Math.max(1f, VENT_HERO - (float) s.stats.armor), 0.4f, true, Sim.DMG_AREA);
        }
        int n = s.query(x, y, VENT_R + 30f);
        for (int k = 0; k < n; k++) {
            Enemy e = s.enemies[s.qbuf[k]];
            if (e.dead) continue;
            float ex = e.x - x, ey = e.y - y, er = VENT_R + e.radius * 0.5f;
            if (ex * ex + ey * ey < er * er) s.hurt(e, VENT_ENEMY, 0f, 1f, 0f, 0f, 0f);
        }
    }

    private boolean inCone(int i, float tx, float ty, float pad) {
        float dx = tx - ventX[i], dy = ty - ventY[i], d2 = dx * dx + dy * dy;
        if (d2 > (JET_LEN + pad) * (JET_LEN + pad)) return false;
        if (d2 < 24f * 24f) return true;
        float da = (float) Math.atan2(dy, dx) - jetAngle(i);
        da = (float) Math.abs(Math.atan2(Math.sin(da), Math.cos(da)));
        return da < JET_HALF + pad / Math.max(40f, (float) Math.sqrt(d2)) * 0.5f;
    }

    /** One 0.5 s tick of a steam jet: the hero and every enemy inside its cone. */
    private void jetTick(int i) {
        if (inCone(i, s.px, s.py, Sim.PLAYER_R * 0.4f) && s.invuln <= 0f && s.ended == 0) {
            s.hurtHero(Math.max(1f, JET_HERO - (float) s.stats.armor), 0.3f, false, Sim.DMG_AREA);
        }
        int n = s.query(ventX[i], ventY[i], JET_LEN + 30f);
        for (int k = 0; k < n; k++) {
            Enemy e = s.enemies[s.qbuf[k]];
            if (!e.dead && inCone(i, e.x, e.y, e.radius * 0.5f)) s.hurt(e, JET_ENEMY, 0f, 1f, 0f, 0f, 0f);
        }
    }

    // ------------------------------------------------------------------ lumen nests

    /** Lumen nest of 1500 u cell (cx, cy) into placeX / placeY: 25% of the cells, never near the start. */
    private boolean nestAt(int cx, int cy) {
        if (WorldGen.unit(WorldGen.hash(cx, cy, 821)) >= NEST_P) return false;
        for (int a = 0; a < 5; a++) {
            float x = cx * NEST_CELL + 200f + WorldGen.unit(WorldGen.hash(cx, cy, 822 + a * 2)) * (NEST_CELL - 400f);
            float y = cy * NEST_CELL + 200f + WorldGen.unit(WorldGen.hash(cx, cy, 823 + a * 2)) * (NEST_CELL - 400f);
            if (x * x + y * y < NEST_CLEAR * NEST_CLEAR || s.map.blocked(x, y)) continue;
            placeX = x;
            placeY = y;
            return true;
        }
        return false;
    }

    /**
     * Called by {@link Sim} when the player enters a new 500 u cell: puts the nests and the vault's barrels of the 5 x 5 cells
     * around into the destructible list.
     */
    void addDests(int cx, int cy) {
        if (!enabled) return;
        int n0x = (int) Math.floor((cx - 2) * Sim.DEST_CELL / NEST_CELL), n1x = (int) Math.floor((cx + 3) * Sim.DEST_CELL / NEST_CELL);
        int n0y = (int) Math.floor((cy - 2) * Sim.DEST_CELL / NEST_CELL), n1y = (int) Math.floor((cy + 3) * Sim.DEST_CELL / NEST_CELL);
        for (int j = n0y; j <= n1y; j++) {
            for (int i = n0x; i <= n1x; i++) {
                if (!nestAt(i, j)) continue;
                int ccx = (int) Math.floor(placeX / Sim.DEST_CELL), ccy = (int) Math.floor(placeY / Sim.DEST_CELL);
                if (ccx < cx - 2 || ccx > cx + 2 || ccy < cy - 2 || ccy > cy + 2) continue;
                s.addDest(placeX, placeY, Sim.DK_NEST, cellKey(i, j, 0x4E00000000000000L));
            }
        }
        if (!hasVault || vaultState == V_DONE) return;
        for (int k = 0; k < 3; k++) {
            int ccx = (int) Math.floor(vbX[k] / Sim.DEST_CELL), ccy = (int) Math.floor(vbY[k] / Sim.DEST_CELL);
            if (ccx < cx - 2 || ccx > cx + 2 || ccy < cy - 2 || ccy > cy + 2) continue;
            s.addDest(vbX[k], vbY[k], Sim.DK_BARREL, 0x6A00000000000000L + k);
        }
    }

    /** A nest was destroyed: a gem pile worth one level and a 25% chance of a Vacuum (decided by the nest's own cell, never by Sim.rng). */
    void nestBroken(float x, float y, long key) {
        boolean was = s.quietRng;
        s.quietRng = true;
        s.sound.emit(SoundEvents.CRATE_BREAK);
        s.addFx(Entities.FX_BLAST, x, y, 60f, 1f, 0.35f, 0, false);
        s.spark(x, y - 10f, 0xFF39FF88, 16);
        s.spark(x, y, 0xFF2A4A30, 8);
        float each = s.xpNeed / 5f;
        for (int k = 0; k < 5; k++) {
            float a = k * 1.2566371f;
            s.addPickup(Entities.K_GEM, x + (float) Math.cos(a) * 20f, y + (float) Math.sin(a) * 14f, each);
        }
        if (WorldGen.unit(WorldGen.hash((int) key, (int) (key >>> 32), 917)) < 0.25f) s.addPickup(Entities.K_VACUUM, x, y - 18f, 0f);
        s.toast("LUMEN NEST DESTROYED", 0xFF39FF88);
        s.quietRng = was;
    }

    private void updateNests(float dt) {
        nestActiveN = 0;
        if (s.destN == 0) return;
        float lim = NEST_R * NEST_R, b0 = Float.MAX_VALUE, b1 = Float.MAX_VALUE;
        int i0 = -1, i1 = -1;
        for (int i = 0; i < s.destN; i++) {
            if (s.destKind[i] != Sim.DK_NEST) continue;
            float dx = s.px - s.destX[i], dy = s.py - s.destY[i], d2 = dx * dx + dy * dy;
            if (d2 >= lim) continue;
            if (d2 < b0) {
                b1 = b0;
                i1 = i0;
                b0 = d2;
                i0 = i;
            } else if (d2 < b1) {
                b1 = d2;
                i1 = i;
            }
        }
        if (i0 < 0) return;
        nestActive[nestActiveN++] = i0;
        if (i1 >= 0) nestActive[nestActiveN++] = i1;
        int hounds = -1;
        for (int a = 0; a < nestActiveN; a++) {
            int i = nestActive[a];
            if ((s.destT[i] -= dt) > 0f) continue;
            s.destT[i] = NEST_EVERY;
            if (hounds < 0) hounds = houndsAlive();
            EnemyType t = s.type("SPORE_HOUND");
            if (t == null || hounds + NEST_PACK > NEST_HOUNDS_MAX || s.enemyN + NEST_PACK > Sim.MAX_ENEMIES - 6) continue;
            for (int k = 0; k < NEST_PACK; k++) {
                float ang = k * 1.5707964f + s.destX[i] * 0.013f;
                Enemy e = s.poiSpawn(t, s.destX[i] + (float) Math.cos(ang) * 34f, s.destY[i] + (float) Math.sin(ang) * 26f, POI_HOUND, false, 0);
                if (e != null) hounds++;
            }
            s.addFx(Entities.FX_FLASH, s.destX[i], s.destY[i] - 10f, 90f, 0f, 0.25f, 0, false);
            s.spark(s.destX[i], s.destY[i] - 6f, 0xFF39FF88, 6);
        }
    }

    private int houndsAlive() {
        int n = 0;
        for (int k = 0; k < s.enemyN; k++) if (s.enemies[k].poi == POI_HOUND && !s.enemies[k].dead) n++;
        return n;
    }

    /** Test hook: the nest at dest index {@code i} spawns its next pack at once. */
    void nestDueForTest(int i) {
        s.destT[i] = 0f;
    }
}
