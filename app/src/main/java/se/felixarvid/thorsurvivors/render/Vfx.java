package se.felixarvid.thorsurvivors.render;

import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Entities.Corpse;
import se.felixarvid.thorsurvivors.game.Entities.DamageText;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;

/**
 * v1.5 batch 1 (game feel). The render-side juice, kept out of the Sim: it only <em>reads</em> the Sim, the Engine and the
 * sound-event counters each frame ({@link #observe}), and owns its own fixed pools. No android.*, no allocation after
 * construction, and nothing here touches the Sim's rng, so seeded runs are unchanged.
 *
 * <p>What it holds: hit-stop (the game loop asks {@link #filterDt}), the trauma-model screen shake, 256 pooled
 * particles (hit sparks, death shards, level-up gems, chest motes), the damage-number pool (crit pop-in, merging, a
 * cap), the short effects (level-up ring and flash, chest lid and light pillar, hero hit flash and edge pulses) and
 * the "+1" gem blips. WorldRenderer draws them from the public fields.
 *
 * <p>Time: the effects run on the wall clock ({@link #update}), so a level-up burst keeps playing while the level-up
 * cards pause the Sim, and hit-stop freezes the world but not the effects.
 */
final class Vfx {
    // ---- v1.5 integration: the B1 stubs read B3's Settings
    /** Screen-shake strength 0, 0.5 or 1 (options: SCREEN SHAKE). */
    static float settingShake() { return se.felixarvid.thorsurvivors.game.Settings.get().shake(); }

    /** Whether damage numbers are drawn at all (options: DAMAGE NUMBERS; CRITS ONLY is filtered where Sim makes the text). */
    static boolean settingDamageNumbers() { return se.felixarvid.thorsurvivors.game.Settings.get().damageNumbers() != se.felixarvid.thorsurvivors.game.Settings.NUM_OFF; }
    // ---- end stub

    // ---- hit-stop (1)
    static final float STOP_ELITE = 0.04f, STOP_BOSS = 0.07f, STOP_MAX = 0.1f;
    private float stop;

    /** Adds {@code seconds} of world freeze; never more than {@link #STOP_MAX} pending at once. */
    void hitStop(float seconds) {
        stop = Math.min(STOP_MAX, stop + seconds);
    }

    float stopLeft() {
        return stop;
    }

    /** The part of the frame's {@code dt} the world may advance: the pending hit-stop eats the front of it. */
    float filterDt(float dt) {
        if (stop <= 0f) return dt;
        if (dt <= stop) {
            stop -= dt;
            return 0f;
        }
        float rest = dt - stop;
        stop = 0f;
        return rest;
    }

    // ---- screen shake as trauma (8)
    static final float TRAUMA_DECAY = 1.6f, SHAKE_PX = 10f;
    float trauma;
    /** Camera offset in 1080p pixels, set by {@link #update}. */
    float shakeX, shakeY;

    void addTrauma(float t) {
        trauma = Math.min(1f, trauma + t);
    }

    /** trauma^2 x the maximum, scaled by the setting; the noise is smooth, so the camera glides instead of jittering. */
    static float shakeAmount(float trauma) {
        return trauma * trauma * SHAKE_PX;
    }

    static float noise(float t, int seed) {
        int i = (int) Math.floor(t);
        float f = t - i;
        f = f * f * (3f - 2f * f);
        float a = hash(i, seed), b = hash(i + 1, seed);
        return a + (b - a) * f;
    }

    private static float hash(int i, int seed) {
        int h = i * 374761393 + seed * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 32767.5f - 1f;
    }

    // ---- the particle pool (2, 5, 6, 9)
    static final int MAX_PART = 256;
    static final int P_SPARK = 0, P_SHARD = 1, P_GEM = 2, P_MOTE = 3;
    static final float SPARK_LIFE = 0.18f;
    final float[] ppx = new float[MAX_PART], ppy = new float[MAX_PART], pvx = new float[MAX_PART], pvy = new float[MAX_PART];
    final float[] plife = new float[MAX_PART], pmax = new float[MAX_PART], psize = new float[MAX_PART];
    final int[] pcol = new int[MAX_PART];
    final byte[] pkind = new byte[MAX_PART];
    private int pHead, rnd = 0x2545F491;

    private float rand() {
        rnd ^= rnd << 13;
        rnd ^= rnd >>> 17;
        rnd ^= rnd << 5;
        return (rnd & 0xFFFFFF) / 16777216f;
    }

    private int newPart(int kind, float x, float y, float vx, float vy, float life, float size, int color) {
        int i = pHead;
        pHead = (pHead + 1) & (MAX_PART - 1);
        ppx[i] = x;
        ppy[i] = y;
        pvx[i] = vx;
        pvy[i] = vy;
        plife[i] = pmax[i] = life;
        psize[i] = size;
        pcol[i] = color;
        pkind[i] = (byte) kind;
        return i;
    }

    int livePart() {
        int n = 0;
        for (int i = 0; i < MAX_PART; i++) if (plife[i] > 0f) n++;
        return n;
    }

    /** 3 to 5 sparks of {@link #SPARK_LIFE} seconds from a hit, tinted {@code color} (RGB). */
    void sparks(float x, float y, int color) {
        int n = 3 + (int) (rand() * 3f);
        for (int k = 0; k < n; k++) {
            float a = rand() * 6.2831853f, sp = 90f + rand() * 170f;
            newPart(P_SPARK, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 30f, SPARK_LIFE, 3f + rand() * 3f, color);
        }
    }

    /** Hit-spark colour by what the enemy is suffering: the status of the weapon that hit it, else a warm white. */
    static int sparkColor(Enemy e) {
        if (e.burn > 0) return 0xFF9A3C;
        if (e.frost > 0 || e.freezeT > 0f) return 0x8FE8FF;
        if (e.elec > 0 || e.softFlash) return 0xFFF06A;
        if (e.decay > 0) return 0xA8E060;
        return 0xFFE9B8;
    }

    /** Six shards of a large enemy dissolving. */
    void shards(float x, float y, int color) {
        for (int k = 0; k < 6; k++) {
            float a = (k + rand()) * 1.0471976f, sp = 60f + rand() * 120f;
            newPart(P_SHARD, x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 70f, 0.3f + rand() * 0.15f, 3f + rand() * 3f, color);
        }
    }

    // ---- damage numbers (3, 4)
    static final int MAX_NUM = 24;
    static final float NUM_LIFE = 0.6f, CRIT_LIFE = 0.8f, NUM_MERGE = 0.15f, POP = 0.12f;
    static final int N_HIT = 0, N_HEAL = 1, N_STATUS = 2, N_BLIP = 3;
    final float[] nx = new float[MAX_NUM], ny = new float[MAX_NUM], nage = new float[MAX_NUM], nlife = new float[MAX_NUM];
    final int[] nval = new int[MAX_NUM], nowner = new int[MAX_NUM], ncolor = new int[MAX_NUM], nseq = new int[MAX_NUM];
    final byte[] nkind = new byte[MAX_NUM];
    final boolean[] ncrit = new boolean[MAX_NUM], nlive = new boolean[MAX_NUM];
    final char[][] ndig = new char[MAX_NUM][8];
    final int[] nlen = new int[MAX_NUM];
    private int numSeq;

    int liveNum() {
        int n = 0;
        for (int i = 0; i < MAX_NUM; i++) if (nlive[i]) n++;
        return n;
    }

    /**
     * Adds a number, or sums it into the live one of the same {@code owner} and kind that is younger than
     * {@link #NUM_MERGE}. At {@link #MAX_NUM} live numbers the oldest is dropped. Returns the slot.
     */
    int addNumber(float x, float y, int value, boolean crit, int kind, int color, int owner) {
        if (owner != 0) {
            for (int i = 0; i < MAX_NUM; i++) {
                if (nlive[i] && nowner[i] == owner && nkind[i] == kind && ncolor[i] == color && nage[i] < NUM_MERGE) {
                    nval[i] = Math.min(9999999, nval[i] + Math.max(0, value));
                    if (crit && !ncrit[i]) { // the pop-in plays when the merged number turns into a crit
                        ncrit[i] = true;
                        nage[i] = 0f;
                        nlife[i] = CRIT_LIFE;
                    }
                    digits(i);
                    return i;
                }
            }
        }
        int slot = -1, oldest = -1;
        for (int i = 0; i < MAX_NUM; i++) {
            if (!nlive[i]) {
                slot = i;
                break;
            }
            if (oldest < 0 || nseq[i] - nseq[oldest] < 0) oldest = i;
        }
        if (slot < 0) slot = oldest; // the cap: the oldest number goes
        nlive[slot] = true;
        nx[slot] = x;
        ny[slot] = y;
        nage[slot] = 0f;
        nkind[slot] = (byte) kind;
        ncrit[slot] = crit;
        nlife[slot] = kind == N_BLIP ? 0.45f : crit ? CRIT_LIFE : NUM_LIFE;
        ncolor[slot] = color;
        nowner[slot] = owner;
        nval[slot] = Math.max(0, Math.min(9999999, value));
        nseq[slot] = numSeq++;
        digits(slot);
        return slot;
    }

    private void digits(int i) {
        int v = nval[i], len = 0;
        char[] d = ndig[i];
        if (v == 0) {
            d[0] = '0';
            len = 1;
        } else {
            for (int c = v; c > 0; c /= 10) len++;
            for (int j = len - 1, c = v; j >= 0; j--, c /= 10) d[j] = (char) ('0' + c % 10);
        }
        nlen[i] = len;
    }

    /** Text scale of a number at {@code age}: a crit is 1.4x with a 1.0, 1.25, 1.0 overshoot in its first 0.12 s. */
    static float popScale(boolean crit, float age) {
        if (!crit) return 1f;
        float k = 1f;
        if (age < POP) k = age < POP / 2 ? 1f + 0.25f * (age / (POP / 2)) : 1.25f - 0.25f * ((age - POP / 2) / (POP / 2));
        return 1.4f * k;
    }

    // ---- short effects: ring, chest lid, pillar (5, 6)
    static final int E_RING = 0, E_LID = 1, E_PILLAR = 2;
    static final int MAX_EFF = 8;
    static final float RING_TIME = 0.4f, RING_RADIUS = 140f, FLASH_TIME = 0.15f, FLASH_ALPHA = 0.06f, LID_TIME = 0.6f, PILLAR_TIME = 3f;
    final int[] ekind = new int[MAX_EFF];
    final float[] ex = new float[MAX_EFF], ey = new float[MAX_EFF], eage = new float[MAX_EFF], edur = new float[MAX_EFF];
    private int eHead;
    /** Seconds left of the white screen flash (level-up). */
    float flash;

    private void effect(int kind, float x, float y, float dur) {
        int i = eHead;
        eHead = (eHead + 1) % MAX_EFF;
        ekind[i] = kind;
        ex[i] = x;
        ey[i] = y;
        eage[i] = 0f;
        edur[i] = dur;
    }

    boolean effectLive(int i) {
        return edur[i] > 0f && eage[i] < edur[i];
    }

    /** Ring radius in 1080p pixels at {@code age} (0 to 140 over 0.4 s, easing out). */
    static float ringRadius(float age) {
        float u = Math.min(1f, age / RING_TIME);
        return RING_RADIUS * (1f - (1f - u) * (1f - u));
    }

    /** Level-up: the ring, 12 gems, the 6 % flash. */
    void levelUp(float x, float y) {
        effect(E_RING, x, y, RING_TIME);
        flash = FLASH_TIME;
        for (int k = 0; k < 12; k++) {
            float a = (k + rand() * 0.6f) * 0.5235988f, sp = 130f + rand() * 130f;
            newPart(P_GEM, x, y - 18f, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp * 0.6f - 200f, 0.75f + rand() * 0.25f, 5f + rand() * 3f,
                    k % 3 == 0 ? 0xFFFFFF : k % 3 == 1 ? 0x70FF98 : 0xA8FFF0);
        }
    }

    /** A chest opens: the lid pops, 20 gold motes arc out and home to the hero, an evolution chest also gets the pillar. */
    void chestOpen(float x, float y, boolean evolution) {
        effect(E_LID, x, y, LID_TIME);
        for (int k = 0; k < 20; k++) {
            float a = -1.5707963f + (rand() - 0.5f) * 2.6f, sp = 140f + rand() * 160f;
            newPart(P_MOTE, x, y - 14f, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 1.3f, 4f + rand() * 3f, rand() < 0.3f ? 0xFFF4B0 : 0xFFD04A);
        }
        if (evolution) effect(E_PILLAR, x, y, PILLAR_TIME);
    }

    /** 0..1 height of the pillar envelope at {@code age}: a quick rise, a long hold, a fade out. */
    static float pillarAlpha(float age) {
        if (age < 0.25f) return age / 0.25f;
        if (age > PILLAR_TIME - 0.9f) return Math.max(0f, (PILLAR_TIME - age) / 0.9f);
        return 1f;
    }

    /** Strongest effect light for the lightmap: 0 when none. */
    float lightStrength() {
        float s = flash > 0f ? 0.7f * flash / FLASH_TIME : 0f;
        for (int i = 0; i < MAX_EFF; i++) {
            if (!effectLive(i)) continue;
            if (ekind[i] == E_PILLAR) s = Math.max(s, 0.9f * pillarAlpha(eage[i]));
            else if (ekind[i] == E_RING) s = Math.max(s, 0.8f * (1f - eage[i] / edur[i]));
        }
        return s;
    }

    // ---- hero hit feedback (7)
    static final float HERO_FLASH = 0.1f, PULSE_TIME = 0.45f, PULSE_MAX = 0.35f, LOW_HP = 0.25f, LOW_HZ = 1.2f;
    float heroFlash, pulse, pulseStrength;
    float clock;

    void heroHit(float fraction) {
        heroFlash = HERO_FLASH;
        pulse = PULSE_TIME;
        pulseStrength = Math.min(1f, 0.2f + 3f * Math.max(0f, fraction));
    }

    /** Alpha 0..{@link #PULSE_MAX} of the red screen-edge pulse: the last hit, or below 25 % HP the slow 1.2 Hz beat. */
    float edgeAlpha(float hpFraction) {
        float a = pulse > 0f ? PULSE_MAX * pulseStrength * (pulse / PULSE_TIME) : 0f;
        if (hpFraction < LOW_HP) {
            float need = 1f - hpFraction / LOW_HP; // deeper = stronger
            float beat = 0.5f + 0.5f * (float) Math.sin(clock * 6.2831853f * LOW_HZ);
            a = Math.max(a, PULSE_MAX * (0.35f + 0.65f * need) * beat);
        }
        return Math.min(PULSE_MAX, a);
    }

    // ---- observing the Sim
    private Sim last;
    private boolean primed;
    private float lastHp, lastHitShake;
    private int lastLevel, lastPhase, lastGemCount, bossId = -1, bossPhase;
    private float lastGemAt = -10f;
    private int gemChain;
    private final float[] hitSeen = new float[4096];
    private final boolean[] cpSeen = new boolean[Sim.MAX_CORPSES];
    private final float[] cpAge = new float[Sim.MAX_CORPSES];
    private final float[] txLife = new float[Sim.MAX_TEXT];
    private final int[] txTotal = new int[Sim.MAX_TEXT];
    float heroX, heroY;

    /** Forgets everything and clears the pools (a new run). */
    void reset() {
        stop = 0f;
        trauma = 0f;
        shakeX = shakeY = 0f;
        flash = heroFlash = pulse = 0f;
        for (int i = 0; i < MAX_PART; i++) plife[i] = 0f;
        for (int i = 0; i < MAX_NUM; i++) nlive[i] = false;
        for (int i = 0; i < MAX_EFF; i++) edur[i] = 0f;
        java.util.Arrays.fill(hitSeen, 0f);
        java.util.Arrays.fill(txLife, 0f);
        java.util.Arrays.fill(txTotal, 0);
        primed = false;
        bossId = -1;
    }

    /**
     * Looks at the Sim once per drawn frame and starts what happened since the last look. The first look at a Sim only takes a
     * snapshot (no burst for a level or a corpse that already was there). Damage numbers are taken from the first frame on.
     */
    void observe(Sim s, Engine e) {
        if (s != last) {
            reset();
            last = s;
        }
        heroX = s.px;
        heroY = s.py;
        // damage numbers: a text whose life went up is new, one whose total went up was merged into by the Sim
        for (int i = 0; i < Sim.MAX_TEXT; i++) {
            DamageText d = s.texts[i];
            if (d.life <= 0f) {
                txLife[i] = 0f;
                txTotal[i] = 0;
                continue;
            }
            int kind = d.heal ? N_HEAL : d.color != 0 ? N_STATUS : N_HIT;
            if (d.life > txLife[i] + 0.001f) {
                addNumber(d.x, d.y, d.total, d.crit, kind, d.color, d.owner);
            } else if (d.total > txTotal[i]) {
                addNumber(d.x, d.y, d.total - txTotal[i], d.crit, kind, d.color, d.owner);
            }
            txLife[i] = d.life;
            txTotal[i] = d.total;
        }
        // enemy hits (sparks) and a boss changing phase
        int boss = -1;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy en = s.enemies[i];
            int hs = en.id & (hitSeen.length - 1);
            if (en.flash > hitSeen[hs] + 0.004f && !en.dormant && primed) {
                sparks(en.x, en.y - en.radius * 0.6f, sparkColor(en) | 0xFF000000);
            }
            hitSeen[hs] = en.flash;
            if (en.boss) boss = i;
        }
        if (boss >= 0) {
            Enemy b = s.enemies[boss];
            if (b.id == bossId && b.phase > bossPhase && primed) {
                hitStop(STOP_BOSS);
                addTrauma(0.4f);
            }
            bossId = b.id;
            bossPhase = b.phase;
        } else {
            bossId = -1;
        }
        // kills: a corpse that was not active (or is younger than before) is new
        for (int i = 0; i < Sim.MAX_CORPSES; i++) {
            Corpse c = s.corpses[i];
            boolean fresh = c.active && (!cpSeen[i] || c.age < cpAge[i]);
            cpSeen[i] = c.active;
            cpAge[i] = c.age;
            if (!fresh || !primed) continue;
            if (c.boss) {
                hitStop(STOP_BOSS);
                addTrauma(0.5f);
            } else if (c.elite) {
                hitStop(STOP_ELITE);
                addTrauma(0.25f);
            }
            if (!c.boss && c.radius >= LARGE_R) shards(c.x, c.y - c.radius * 0.5f, 0xFF000000 | (c.elite ? 0xFFC060 : 0xC8B8A8));
        }
        // the hero
        if (primed) {
            float lost = lastHp - s.hp;
            if (lost > 0.01f && s.maxHp > 0f) heroHit(lost / s.maxHp);
            if (s.hitShake > lastHitShake + 0.001f) addTrauma(Math.min(0.7f, s.hitShake * 2.5f));
            if (s.level > lastLevel) levelUp(s.px, s.py);
            if (e.phase == Engine.PH_CHEST && lastPhase != Engine.PH_CHEST) chestOpen(s.px, s.py, evolutionLines(e.chestLines));
            int gems = e.sound.emitted[SoundEvents.GEM];
            if (gems > lastGemCount) gemBlip(s);
            lastGemCount = gems;
        } else {
            lastGemCount = e.sound.emitted[SoundEvents.GEM];
        }
        lastHp = s.hp;
        lastHitShake = s.hitShake;
        lastLevel = s.level;
        lastPhase = e.phase;
        primed = true;
    }

    /** Enemies with at least this radius dissolve (shrink and fade over 0.25 s) instead of the plain pop. */
    static final float LARGE_R = 18f;
    static final float DISSOLVE_TIME = 0.25f;

    private static boolean evolutionLines(String[] lines) {
        if (lines == null) return false;
        for (String l : lines) if (l != null && (l.startsWith("EVOLUTION") || l.startsWith("FUSION"))) return true;
        return false;
    }

    /** A "+1" over the hero when a gem is picked up: the pitch chain of the chime (SoundEvents.GEM_CHAIN) sets its size and colour. */
    private void gemBlip(Sim s) {
        double t = e_now(s);
        gemChain = t - lastGemAt < SoundEvents.GEM_CHAIN ? Math.min(SoundEvents.GEM_MAX_CHAIN, gemChain + 1) : 0;
        lastGemAt = (float) t;
        addNumber(s.px + (gemChain % 3 - 1) * 8f, s.py - 34f, gemChain, false, N_BLIP, 0, 0);
    }

    private static double e_now(Sim s) {
        return s.time;
    }

    /** Chain 0..7 of the latest gem pickup (for the blip colour). */
    int gemChain() {
        return gemChain;
    }

    /** Advances every effect by {@code dt} wall-clock seconds. */
    void update(float dt) {
        if (dt <= 0f) return;
        clock += dt;
        trauma = Math.max(0f, trauma - TRAUMA_DECAY * dt);
        float amp = shakeAmount(trauma) * settingShake();
        shakeX = amp * noise(clock * 22f, 1);
        shakeY = amp * noise(clock * 22f, 2);
        if (heroFlash > 0f) heroFlash -= dt;
        if (pulse > 0f) pulse -= dt;
        if (flash > 0f) flash -= dt;
        for (int i = 0; i < MAX_PART; i++) {
            if (plife[i] <= 0f) continue;
            plife[i] -= dt;
            int k = pkind[i];
            if (k == P_MOTE) {
                float age = pmax[i] - plife[i];
                if (age > 0.3f) { // after the arc the motes home in on the hero, faster and faster
                    float dx = heroX - ppx[i], dy = heroY - 14f - ppy[i];
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < 14f) {
                        plife[i] = 0f;
                        continue;
                    }
                    float sp = 260f + 900f * (age - 0.3f);
                    pvx[i] += (dx / d * sp - pvx[i]) * Math.min(1f, 10f * dt);
                    pvy[i] += (dy / d * sp - pvy[i]) * Math.min(1f, 10f * dt);
                } else {
                    pvy[i] += 380f * dt;
                }
            } else if (k == P_GEM || k == P_SHARD) {
                pvy[i] += 520f * dt;
            } else {
                float drag = Math.max(0f, 1f - 6f * dt);
                pvx[i] *= drag;
                pvy[i] *= drag;
            }
            ppx[i] += pvx[i] * dt;
            ppy[i] += pvy[i] * dt;
        }
        for (int i = 0; i < MAX_NUM; i++) {
            if (!nlive[i]) continue;
            nage[i] += dt;
            ny[i] -= (nkind[i] == N_BLIP ? 30f : 40f) * dt;
            if (nage[i] >= nlife[i]) nlive[i] = false;
        }
        for (int i = 0; i < MAX_EFF; i++) if (edur[i] > 0f) eage[i] += dt;
    }

    /** Dissolve progress 0..1 of a large corpse, 1 when done. */
    static float dissolve(float corpseAge) {
        return Math.min(1f, corpseAge / DISSOLVE_TIME);
    }
}
