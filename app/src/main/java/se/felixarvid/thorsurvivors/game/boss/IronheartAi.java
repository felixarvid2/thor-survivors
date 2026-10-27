package se.felixarvid.thorsurvivors.game.boss;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Hazard;
import se.felixarvid.thorsurvivors.game.RefineryOps;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;
import se.felixarvid.thorsurvivors.game.layout.StreetsLayout;
import se.felixarvid.thorsurvivors.game.stage.RefineryRules;

/**
 * Boss pattern 12 (chunk C): the three Refinery bosses, told apart by the data param {@code variant}. Called from {@code BossAi.init}
 * and {@code BossAi.update} (the marked v1.4 blocks).
 * <ul>
 * <li>0, <b>THE IRONHEART</b> (the finale at 28:00; Slagborn x3.2): born in a plaza with three coolant valves around it. Phase 1: every
 * belt within 1,200 u points at it, and a slam (r 150, 28 damage, 1.0 s warning) when the hero is close. Phase 2 (66%): two Heat Wave
 * bands cross every 12 s, plus a slag trail. Phase 3 (33%): the belts run away from it, a ring of furnace gates opens at r 300 and it
 * vents (heat). A slam that smashes a coolant valve is a <b>Thermal Shock</b>: stunned 4 s and +50% damage for 6 s.</li>
 * <li>1, <b>Foreman Brass</b> (7:30): a carpet of slag pools and a stomp, and every stomp reverses all the belts.</li>
 * <li>2, <b>Stoker Captain</b> (12:30): three chained dashes toward the hero, each ending in a fire ring and a burning pool.</li>
 * </ul>
 * All state is on the {@link Enemy}; nothing allocates; a full hazard pool just skips the attack.
 */
public final class IronheartAi {
    private static final int A_WALK = 0, A_TELE = 3, A_DASH = 4, A_REST = 5, A_CAST = 7;
    private static final float TAU = 6.2831855f;

    private IronheartAi() {
    }

    private static RefineryRules rules(Sim s) {
        return s.rules instanceof RefineryRules ? (RefineryRules) s.rules : null;
    }

    /** First timers of a freshly spawned boss of this pattern. */
    public static void init(Sim s, Enemy e) {
        int v = Math.round(e.type.def.param("variant", 0f));
        e.actT = 2.5f;
        if (v == 0) {
            e.actT = 3f;
            e.mt0 = 12f; // first crossing waves (phase 2) / vent (phase 3) timer
            RefineryRules r = rules(s);
            if (r != null) {
                r.setFinale(e);
                r.setBelts(RefineryRules.BELTS_TO_BOSS, e.x, e.y);
            }
        } else if (v == 2) {
            e.actT = 2f;
        }
    }

    /** One step of the boss ({@code moving} is false while staggered; (dx, dy) points at the hero, {@code d} is the distance). */
    public static void update(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        switch (Math.round(e.type.def.param("variant", 0f))) {
            case 1: brass(s, e, dt, moving, dx, dy, d); break;
            case 2: captain(s, e, dt, moving, dx, dy, d); break;
            default: heart(s, e, dt, moving, dx, dy, d);
        }
    }

    // ------------------------------------------------------------------ 1 Foreman Brass

    private static void brass(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        EnemyDef def = e.type.def;
        if (e.ai == A_CAST) { // the stomp lands: the belts turn around
            e.clip = Entities.CL_CAST;
            e.tele -= dt;
            if ((e.aiT -= dt) <= 0f) {
                RefineryRules r = rules(s);
                if (r != null) r.reverseBelts();
                RefineryOps.shake(s, 0.3f);
                s.sound.emit(SoundEvents.BOSS_SLAM);
                e.ai = A_WALK;
                e.actT = def.param("castEvery", 6f);
            }
            return;
        }
        e.clip = 0;
        e.actT -= dt;
        if (moving && d > 1f) RefineryOps.walk(s, e, dt, dx, dy, d, 1f);
        if (e.actT > 0f || d > 420f) return;
        e.ai = A_CAST;
        e.clip = Entities.CL_CAST;
        float warn = def.param("castTime", 0.9f);
        e.aiT = e.tele = e.teleMax = warn;
        int n = Math.max(1, Math.round(def.param("pools", 5f)));
        float r = def.param("poolR", 60f), ux = d > 1f ? dx / d : 1f, uy = d > 1f ? dy / d : 0f;
        for (int k = 0; k < n; k++) { // the carpet of slag pools toward the hero
            if (!RefineryOps.hazardRoom(s, Sim.MAX_HAZ / 4)) break;
            RefineryOps.hazard(s, Entities.HZ_SLAG, e.x + ux * r * 1.5f * (k + 1), e.y + uy * r * 1.5f * (k + 1), r,
                    def.param("poolT", 3f), def.param("poolDps", 8f), warn + 0.08f * k);
        }
        Hazard h = RefineryOps.hazard(s, Entities.HZ_BOMB, e.x, e.y, def.param("stompR", 110f), 0.5f, def.param("stompDmg", 20f), warn);
        if (h != null) h.variant = Entities.BV_STOMP;
    }

    // ------------------------------------------------------------------ 2 Stoker Captain

    private static void captain(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        EnemyDef def = e.type.def;
        switch (e.ai) {
            case A_TELE:
                e.clip = 0;
                e.tele -= dt;
                if ((e.aiT -= dt) <= 0f) {
                    e.ai = A_DASH;
                    e.aiT = def.param("dash", 0.45f);
                    e.tele = 0f;
                    e.t2 = 0f;
                }
                return;
            case A_DASH: {
                e.clip = Entities.CL_RUN;
                float sp = def.param("dashLen", 200f) / def.param("dash", 0.45f);
                e.x += e.ax * sp * dt;
                e.y += e.ay * sp * dt;
                float hx = s.px - e.x, hy = s.py - e.y, rr = e.radius * 0.8f + Sim.PLAYER_R * 0.8f + def.param("lineW", 46f) * 0.25f;
                if (e.t2 == 0f && s.ended == 0 && s.invuln <= 0f && hx * hx + hy * hy < rr * rr) {
                    e.t2 = 1f;
                    RefineryOps.hurtHero(s, Math.max(1f, def.param("dashDmg", 16f) - (float) s.stats.armor), 0.5f, Sim.DMG_AREA);
                }
                if ((e.aiT -= dt) <= 0f) { // the dash ends in a fire ring (and a burning pool)
                    float ringR = def.param("ringR", 120f);
                    Hazard h = RefineryOps.hazard(s, Entities.HZ_BOMB, e.x, e.y, ringR, 0.4f, def.param("ringDmg", 14f), def.param("ringWarn", 0.55f));
                    if (h != null) {
                        h.variant = Entities.BV_SLAG;
                        h.w = def.param("poolR", 44f);
                        h.spin = def.param("poolT", 3f);
                        h.acc = def.param("poolDps", 7f);
                    }
                    e.cnt++;
                    if (e.cnt < Math.round(def.param("dashes", 3f))) {
                        e.ai = A_REST; // a short gap, then the next dash from where it stands
                        e.aiT = def.param("gap", 0.25f);
                        e.mt0 = 1f;
                    } else {
                        e.ai = A_REST;
                        e.aiT = def.param("recover", 2f);
                        e.mt0 = 0f;
                        e.cnt = 0;
                    }
                }
                return;
            }
            case A_REST:
                e.clip = 0;
                if ((e.aiT -= dt) <= 0f) {
                    if (e.mt0 > 0f) { // chained: aim again at once
                        e.mt0 = 0f;
                        aimDash(e, def.param("pre", 0.9f) * 0.4f, dx, dy, d);
                    } else {
                        e.ai = A_WALK;
                        e.actT = def.param("gapMin", 2f) + s.rng.nextFloat() * (def.param("gapMax", 3.5f) - def.param("gapMin", 2f));
                    }
                }
                return;
            default:
        }
        e.clip = 0;
        e.actT -= dt;
        if (moving && d > 1f) RefineryOps.walk(s, e, dt, dx, dy, d, 1f);
        if (e.actT <= 0f && d < def.param("range", 380f)) {
            e.cnt = 0;
            aimDash(e, def.param("pre", 0.9f), dx, dy, d);
            s.sound.emit(SoundEvents.WELDER_CHARGE);
        }
    }

    private static void aimDash(Enemy e, float pre, float dx, float dy, float d) {
        EnemyDef def = e.type.def;
        e.ai = A_TELE;
        e.ax = dx / Math.max(d, 1f);
        e.ay = dy / Math.max(d, 1f);
        e.aiT = e.tele = e.teleMax = pre;
        e.teleLen = def.param("dashLen", 200f);
        e.teleW = def.param("lineW", 46f);
    }

    // ------------------------------------------------------------------ 0 The Ironheart

    private static void heart(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        EnemyDef def = e.type.def;
        RefineryRules r = rules(s);
        if (e.bag == 0) { // the first step: move to the nearest plaza (the caller places a spawn after init) and set the valves out
            e.bag = 1;
            e.x = StreetsLayout.plazaCentre(StreetsLayout.nearestPlaza(e.x));
            e.y = StreetsLayout.plazaCentre(StreetsLayout.nearestPlaza(e.y));
            if (r != null) r.placeFinaleValves(e.x, e.y);
            return;
        }
        float frac = e.hp / e.maxHp;
        int want = frac < def.param("phase2Hp", 0.33f) ? 2 : frac < def.param("phase1Hp", 0.66f) ? 1 : 0;
        if (want > e.phase) {
            e.phase = want;
            RefineryOps.shake(s, 0.35f);
            RefineryOps.fx(s, Entities.FX_SLAM, e.x, e.y, 200f, 0f, 0.6f);
            s.sound.emit(SoundEvents.BOSS_SLAM);
            e.mt0 = 1.5f;
            if (r != null && want == 2) {
                r.setBelts(RefineryRules.BELTS_FROM_BOSS, e.x, e.y); // phase 3: the belts run away, the gates open
                r.openGateRing(e.x, e.y);
                s.toast("THE IRONHEART VENTS  -  the gates are open", 0xFFFF8A30);
            } else if (want == 1) {
                s.toast("THE IRONHEART  -  heat waves from both sides", 0xFFFFB347);
            }
        }
        if (r != null) r.setBelts(e.phase == 2 ? RefineryRules.BELTS_FROM_BOSS : RefineryRules.BELTS_TO_BOSS, e.x, e.y);
        if (e.phase >= 1) { // phase 2 and 3: the bands cross every 12 s, a slag trail behind it
            if (r != null && (e.mt0 -= dt) <= 0f) {
                e.mt0 = def.param("waveEvery", 12f);
                double t0 = s.time + RefineryRules.WAVE_TELE;
                r.startWave(1f, s.px - RefineryRules.WAVE_START, t0);
                r.startWave(-1f, s.px + RefineryRules.WAVE_START, t0);
                if (e.phase == 2) vent(s, e, r);
            }
            if (moving && (e.trail += RefineryOps.speedOf(s, e) * dt) >= def.param("trail", 45f) && RefineryOps.hazardRoom(s, 8)) {
                e.trail = 0f;
                RefineryOps.hazard(s, Entities.HZ_SLAG, e.x, e.y + 4f, 24f, def.param("trailT", 4f), def.param("trailDps", 6f), 0f);
            }
        }
        if (e.t3 > 0f && (e.t3 -= dt) <= 0f && r != null && d < def.param("ventR", 230f) + 40f) r.addHeat(def.param("ventHeat", 30f)); // the vent lands
        if (e.ai == A_CAST) { // the slam
            e.clip = Entities.CL_CAST;
            e.tele -= dt;
            if ((e.aiT -= dt) <= 0f) {
                slamLands(s, e, r, def.param("slamR", 150f));
                e.ai = A_WALK;
                e.actT = def.param("slamEvery", 3.5f);
            }
            return;
        }
        e.clip = 0;
        e.actT -= dt;
        if (moving && d > 1f) RefineryOps.walk(s, e, dt, dx, dy, d, 1f);
        if (e.actT <= 0f && d < def.param("slamR", 150f) + 20f) {
            e.ai = A_CAST;
            float warn = def.param("slamWarn", 1.0f);
            e.aiT = e.tele = e.teleMax = warn;
            Hazard h = RefineryOps.hazard(s, Entities.HZ_BOMB, e.x, e.y, def.param("slamR", 150f), 0.5f, def.param("slamDmg", 28f), warn);
            if (h != null) h.variant = Entities.BV_STOMP;
        }
    }

    private static void vent(Sim s, Enemy e, RefineryRules r) {
        EnemyDef def = e.type.def;
        float warn = def.param("ventWarn", 1.2f);
        Hazard h = RefineryOps.hazard(s, Entities.HZ_BOMB, e.x, e.y, def.param("ventR", 230f), 0.5f, def.param("ventDmg", 14f), warn);
        if (h != null) h.variant = Entities.BV_VOLCANO;
        e.t3 = warn;
    }

    /** The slam is down: a coolant valve inside the ring breaks, and that is a Thermal Shock. */
    private static void slamLands(Sim s, Enemy e, RefineryRules r, float slamR) {
        if (r == null) return;
        for (int i = s.destN - 1; i >= 0; i--) {
            if (s.destKind[i] != RefineryRules.DK_COOLANT && s.destKind[i] != RefineryRules.DK_VALVE_HEAVY) continue;
            float dx = s.destX[i] - e.x, dy = s.destY[i] - e.y;
            if (dx * dx + dy * dy > slamR * slamR) continue;
            r.slamBreaks = true; // the armoured valves of the plaza give to nothing but this
            RefineryOps.hitDest(s, s.destX[i], s.destY[i], 1f);
            r.slamBreaks = false;
            r.thermalShock(e);
            return; // one shock per slam
        }
    }
}
