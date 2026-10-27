package se.felixarvid.thorsurvivors.game.boss;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.HydroOps;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;
import se.felixarvid.thorsurvivors.game.WorldGen;
import se.felixarvid.thorsurvivors.game.layout.DomesLayout;
import se.felixarvid.thorsurvivors.game.stage.HydroRules;

/**
 * Boss pattern 11 (chunk B): THE ROOTMIND, the Hydroponic Vaults finale (28:00). Called from {@code BossAi.init} and
 * {@code BossAi.update} (the marked v1.4 blocks) for an enemy whose {@code pattern} param is 11.
 *
 * <p>It stands still at the centre of the dome nearest 450 u from the hero. Three shield hearts (enemies {@code ROOT_HEART}, 120
 * health) ring it at r 180: while any lives the Rootmind takes x0.2 damage; killing all three opens a 12 s window of x1.0; a
 * heart grows back 20 s after its death (12 s in phase 3). The state lives in {@link HydroRules} (rmMul, rmWindow ...), so the
 * damage hook of the rules and the drawing see it. Phase 1 (above 66%): two sweeping vine lines (length 560, width 46, 1.0 s
 * warning). Phase 2 (66-33%): a ring of 12 shots every 3.5 s and 6 hounds every 9 s. Phase 3: a full-map Sporefall every 10 s,
 * the ring every 5 s, faster hearts. Dying ends the run in victory like The First (Sim.bossRewards).
 */
public final class RootmindAi {
    private RootmindAi() {
    }

    private static final float TAU = 6.2831855f;
    public static final float SHIELD_R = 180f, SHIELD_HP = 120f, WINDOW = 12f, RESPAWN = 20f, RESPAWN3 = 12f;
    private static final int ROAR = 1;

    private static HydroRules rules(Sim s) {
        return s.rules instanceof HydroRules ? (HydroRules) s.rules : null;
    }

    /** Centre (world units) of the dome whose centre is nearest 450 u from (hx, hy) in the direction {@code ang}; fills {@code out}. */
    public static void domeCentre(float hx, float hy, float ang, float[] out) {
        float cellU = DomesLayout.CELL * WorldGen.TILE;
        float px = hx + (float) Math.cos(ang) * 450f, py = hy + (float) Math.sin(ang) * 450f;
        int c0x = (int) Math.floor(px / cellU), c0y = (int) Math.floor(py / cellU);
        float best = Float.MAX_VALUE;
        out[0] = px;
        out[1] = py;
        for (int j = c0y - 1; j <= c0y + 1; j++) {
            for (int i = c0x - 1; i <= c0x + 1; i++) {
                float cx = DomesLayout.centerX(i) * WorldGen.TILE, cy = DomesLayout.centerX(j) * WorldGen.TILE;
                float dx = cx - hx, dy = cy - hy, d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 300f || d > 650f) continue;
                float score = Math.abs(d - 450f);
                if (score < best) {
                    best = score;
                    out[0] = cx;
                    out[1] = cy;
                }
            }
        }
    }

    private static final float[] centre = new float[2];

    /** First timers of a freshly spawned boss of this pattern. */
    public static void init(Sim s, Enemy e) {
        HydroRules r = rules(s);
        float ang = WorldGen.unit(WorldGen.hash((int) s.runSeed, (int) (s.runSeed >>> 32), 950)) * TAU;
        float hx = s.px, hy = s.py;
        domeCentre(hx, hy, ang, centre);
        e.x = centre[0];
        e.y = centre[1];
        e.mt0 = 3f;
        e.mt1 = 5f;
        e.mt2 = 2f;
        e.actT = 2f;
        e.phase = 0;
        e.ai = 0;
        e.kbMul = 0f;
        if (r == null) return;
        r.rmOn = true;
        r.rmBossX = e.x;
        r.rmBossY = e.y;
        r.rmMul = 0.2f;
        r.rmWindow = 0f;
        r.rmAlive = 0;
        for (int j = 0; j < HydroRules.SHIELDS; j++) {
            r.rmId[j] = 0;
            r.rmT[j] = 0f;
            r.rmUp[j] = false;
            float a = j * TAU / 3f + 0.5f;
            r.rmX[j] = e.x + (float) Math.cos(a) * SHIELD_R;
            r.rmY[j] = e.y + (float) Math.sin(a) * SHIELD_R;
        }
    }

    /** One step of the boss ({@code moving} is false while staggered; (dx, dy) points at the hero, {@code d} is the distance). */
    public static void update(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        HydroRules r = rules(s);
        EnemyDef def = e.type.def;
        e.kvx = e.kvy = 0f;
        if (r != null) {
            e.x = r.rmBossX;
            e.y = r.rmBossY;
            shields(s, e, r, dt);
        }
        int want = e.hp < e.maxHp * def.param("phase2Hp", 0.33f) ? 2 : e.hp < e.maxHp * def.param("phase1Hp", 0.66f) ? 1 : 0;
        if (want > e.phase) { // 66% / 33%: a roar and a short pause, then the next set of attacks
            e.phase = want;
            e.ai = ROAR;
            e.aiT = 1.3f;
            e.tele = e.teleMax = 1.3f;
            for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_LANE) s.hazards[i].life = Math.min(s.hazards[i].life, 0.05f);
            s.sound.emit(SoundEvents.THE_FIRST_ROAR);
            HydroOps.fx(s, Entities.FX_SLAM, e.x, e.y, 200f, 0.6f);
            HydroOps.shake(s, 0.5f);
            s.screenFlash = Math.max(s.screenFlash, 0.3f);
            HydroOps.spark(s, e.x, e.y - 30f, 0xFF39FF88, 24);
            e.mt0 = 1.5f;
            e.mt1 = 3f;
            e.mt2 = 2f;
            e.actT = 2f;
        }
        e.clip = 0;
        if (e.ai == ROAR) {
            e.clip = Entities.CL_CAST2;
            e.tele -= dt;
            if ((e.aiT -= dt) <= 0f) {
                e.ai = 0;
                e.tele = 0f;
            }
            return;
        }
        switch (e.phase) {
            case 0:
                if ((e.actT -= dt) <= 0f) vines(s, e, r, dx, dy);
                break;
            case 1:
                ring(s, e, dt, def.param("ringEvery", 3.5f));
                if ((e.mt1 -= dt) <= 0f) {
                    e.mt1 = def.param("houndEvery", 9f);
                    hounds(s, e);
                }
                break;
            default:
                ring(s, e, dt, def.param("ringEvery3", 5f));
                if ((e.mt2 -= dt) <= 0f) {
                    e.mt2 = def.param("sporeEvery", 10f);
                    if (r != null) r.startSporefall(6f, true);
                }
        }
    }

    // ------------------------------------------------------------------ shield hearts

    private static void shields(Sim s, Enemy boss, HydroRules r, float dt) {
        int before = r.rmAlive, alive = 0;
        float respawn = boss.phase >= 2 ? RESPAWN3 : RESPAWN;
        for (int j = 0; j < HydroRules.SHIELDS; j++) {
            if (r.rmId[j] != 0) {
                Enemy h = find(s, r.rmId[j]);
                if (h == null || h.dead) { // it died (or was lost)
                    r.rmId[j] = 0;
                    r.rmT[j] = respawn;
                    r.rmUp[j] = false;
                } else {
                    r.rmUp[j] = !h.dormant;
                    alive++;
                    continue;
                }
            }
            if (r.rmT[j] > 0f) {
                r.rmT[j] -= dt;
                if (r.rmT[j] > 0f) continue;
            }
            Enemy h = HydroOps.spawnPinned(s, "ROOT_HEART", r.rmX[j], r.rmY[j]);
            if (h == null) continue;
            h.hp = h.maxHp = SHIELD_HP;
            r.rmId[j] = h.id;
            alive++;
        }
        r.rmAlive = alive;
        if (before > 0 && alive == 0) {
            r.rmWindow = WINDOW;
            s.toast("THE ROOTMIND IS EXPOSED", 0xFF39FF88);
            s.sound.emit(SoundEvents.EVOLUTION);
        }
        if (r.rmWindow > 0f) r.rmWindow -= dt;
        r.rmMul = alive == 0 && r.rmWindow > 0f ? 1f : 0.2f;
    }

    private static Enemy find(Sim s, int id) {
        for (int i = 0; i < s.enemyN; i++) if (s.enemies[i].id == id) return s.enemies[i];
        return null;
    }

    // ------------------------------------------------------------------ attacks

    /** Two vine lines opposite each other, first aimed near the hero; a thin line for a second, then they sweep. */
    private static void vines(Sim s, Enemy e, HydroRules r, float dx, float dy) {
        EnemyDef def = e.type.def;
        float warn = def.param("laneWarn", 1f), life = def.param("laneLife", 3.2f);
        float sign = r != null && r.rand() < 0.5f ? -1f : 1f;
        float base = (float) Math.atan2(dy, dx) + sign * (0.5f + (r != null ? r.rand() : 0.5f) * 0.6f);
        float spin = sign * def.param("laneSpin", 0.85f);
        int n = Math.max(1, Math.round(def.param("lanes", 2f)));
        for (int k = 0; k < n; k++) {
            Entities.Hazard h = HydroOps.addHazard(s, Entities.HZ_LANE, e.x, e.y, def.param("laneLen", 560f), life, def.param("laneDps", 22f), warn);
            if (h == null) continue;
            h.ang = base + k * TAU / n;
            h.spin = spin;
            h.w = def.param("laneW", 46f);
        }
        e.clip = Entities.CL_CAST;
        e.actT = warn + life + def.param("laneGap", 2f);
        s.sound.emit(SoundEvents.TENTACLE_EMERGE);
    }

    private static void ring(Sim s, Enemy e, float dt, float every) {
        if ((e.mt0 -= dt) > 0f) return;
        e.mt0 = every;
        EnemyDef def = e.type.def;
        int n = Math.round(def.param("ring", 12f));
        if (s.eprojN > Sim.MAX_EPROJ - n) return; // the bolt pool is nearly full: this ring is skipped
        float sp = def.param("ringSpeed", 200f), dmg = def.param("ringDmg", 12f) * e.dmgMul;
        float a0 = e.cnt++ * 0.29f;
        for (int k = 0; k < n; k++) {
            float a = a0 + k * TAU / n;
            HydroOps.shoot(s, e.x, e.y - 12f, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, dmg, 1.9f);
        }
        e.clip = Entities.CL_CAST;
        s.sound.emit(SoundEvents.SPORE_BURST);
    }

    private static void hounds(Sim s, Enemy e) {
        int n = Math.round(e.type.def.param("hounds", 6f));
        HydroRules r = rules(s);
        float a0 = (r != null ? r.rand() : 0.3f) * TAU;
        for (int k = 0; k < n; k++) {
            float a = a0 + k * 0.35f;
            HydroOps.spawnMinion(s, "SPORE_HOUND", s.px + (float) Math.cos(a) * 340f, s.py + (float) Math.sin(a) * 260f);
        }
    }
}
