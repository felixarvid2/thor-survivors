package se.felixarvid.thorsurvivors.game.boss;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.game.Entities;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Entities.Hazard;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.SoundEvents;
import se.felixarvid.thorsurvivors.game.WreckOps;
import se.felixarvid.thorsurvivors.game.layout.DebrisLayout;
import se.felixarvid.thorsurvivors.game.stage.WreckRules;

/**
 * Boss pattern 13 (chunk D), the Meridian Wreck family, called from {@code BossAi.init} / {@code BossAi.update} (the marked v1.4
 * blocks). The data param {@code mode} picks the boss:
 * <ul>
 * <li>0 <b>THE PILLAR</b> (the finale, 28:00): stands still at its spire in the arena. Phase 1 (above 66%): four rotating lumen
 * beams (the tendril lane hazard, 0.85 rad/s) and the arena's exit pad (WreckRules). Phase 2 (66-33%): two gravity wells orbit the
 * arena and an echo wave of bolts goes out every 8 s. Phase 3 (below 33%): a full Vacuum Breach inside the arena, the bubble
 * follows the Pillar's pulse, and every 6 s a pulse (1.5 s warning) deals 28 to anyone outside it. It sleeps while the hero is far
 * (WreckRules wakes it inside 850 u). Killing it wins the run (gold x2, see {@link WreckRules#onEnemyDeath}).</li>
 * <li>1 <b>Hull Breacher Alpha</b> (7:30): walks at the hero and stomps (ring, 0.9 s warning); each stomp tears a gravity well
 * (r 160, 6 s) open at the hero's position and every second stomp drops three crates.</li>
 * <li>2 <b>Salvage Warden</b> (12:30): hovers at about 230 u, fires a fan of bolts every 3.2 s and every 9 s blinks away after a
 * shimmer, leaving decoys that burst when their time is up.</li>
 * </ul>
 * Everything the boss touches in the Sim goes through {@link WreckOps}; random numbers come from the stage's own generator.
 */
public final class PillarAi {
    private static final float TAU = 6.2831855f;
    // states (the same numbers as EnemyAi / BossAi, which this package cannot see)
    private static final int WALK = 0, REST = 5, CAST = 7, TELE = 3, PULSE = 15;

    private PillarAi() {
    }

    /** First timers of a freshly spawned boss of this pattern. */
    public static void init(Sim s, Enemy e) {
        EnemyDef d = e.type.def;
        WreckRules wr = WreckRules.of(s);
        switch (Math.round(d.param("mode", 0f))) {
            case 1:
                e.actT = 2.0f;
                break;
            case 2:
                e.actT = 1.5f;
                e.mt0 = d.param("blinkEvery", 9f) * 0.6f;
                break;
            default: // the Pillar: at its spire, still, asleep until the hero comes
                e.x = DebrisLayout.pillarX();
                e.y = DebrisLayout.pillarY();
                e.speed = 0f;
                e.mt0 = 2.5f;
                e.mt1 = 4f;
                e.mt2 = 3f;
                float dx = s.px - e.x, dy = s.py - e.y;
                e.sleeping = dx * dx + dy * dy > 850f * 850f;
                if (wr != null) wr.pillarSpawned(e);
        }
    }

    /** One step of the boss ({@code moving} is false while staggered; (dx, dy) points at the hero, {@code d} is the distance). */
    public static void update(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        switch (Math.round(e.type.def.param("mode", 0f))) {
            case 1: alpha(s, e, dt, moving, dx, dy, d); break;
            case 2: warden(s, e, dt, moving, dx, dy, d); break;
            default: pillar(s, e, dt, dx, dy);
        }
    }

    private static float rnd(WreckRules wr, float a, float b) {
        return a + (wr == null ? 0.5f : wr.rand()) * (b - a);
    }

    // ------------------------------------------------------------------ 0 The Pillar

    private static void pillar(Sim s, Enemy e, float dt, float dx, float dy) {
        EnemyDef def = e.type.def;
        WreckRules wr = WreckRules.of(s);
        e.x = DebrisLayout.pillarX();
        e.y = DebrisLayout.pillarY();
        e.kvx = e.kvy = 0f;
        e.clip = 0;
        int want = e.hp < e.maxHp * def.param("phase2Hp", 0.33f) ? 2 : e.hp < e.maxHp * def.param("phase1Hp", 0.66f) ? 1 : 0;
        if (want > e.phase) { // 66% and 33%: the spire roars, the beams go out, the next set starts
            e.phase = want;
            for (int i = 0; i < s.hazN; i++) if (s.hazards[i].kind == Entities.HZ_LANE) s.hazards[i].life = Math.min(s.hazards[i].life, 0.05f);
            WreckOps.fx(s, Entities.FX_SLAM, e.x, e.y, 220f, 0f, 0.6f);
            WreckOps.spark(s, e.x, e.y - 60f, 0xFF4FD8FF, 24);
            WreckOps.shake(s, 0.5f);
            s.screenFlash = Math.max(s.screenFlash, 0.3f);
            s.sound.emit(SoundEvents.THE_FIRST_ROAR);
            e.ai = WALK;
            e.tele = 0f;
            e.mt0 = 2f;
            e.mt1 = 3f;
            e.mt2 = 3f;
        }
        switch (e.phase) {
            case 0:
                if ((e.mt0 -= dt) <= 0f) {
                    e.mt0 = def.param("beamEvery", 8f);
                    beams(s, e, def, wr);
                }
                break;
            case 1:
                if (wr != null) wr.setOrbit(true, e.x, e.y, def.param("orbitDist", 260f), e.anim * def.param("orbitSpeed", 0.6f), 190f);
                echo(s, e, def, dt, def.param("echoEvery", 8f));
                break;
            default:
                if (wr != null) {
                    wr.setOrbit(false, 0f, 0f, 0f, 0f, 0f);
                    float a = (float) s.time * 0.45f, bd = def.param("bubbleDist", 140f);
                    float r = def.param("bubbleR", 170f) * (0.88f + 0.12f * (float) Math.sin(s.time * 1.05));
                    wr.setArenaBubble(true, e.x + (float) Math.cos(a) * bd, e.y + (float) Math.sin(a) * bd, r);
                    pulse(s, e, def, wr, dt);
                }
                echo(s, e, def, dt, def.param("echoEvery", 8f) * 1.25f);
        }
    }

    /** Four lumen beams from the spire, a thin line for a second and then they sweep; the turn direction alternates. */
    private static void beams(Sim s, Enemy e, EnemyDef def, WreckRules wr) {
        if (s.hazN > Sim.MAX_HAZ - 8) return;
        int n = Math.max(1, Math.round(def.param("beams", 4f)));
        float spin = (e.cnt++ % 2 == 0 ? 1f : -1f) * def.param("beamSpin", 0.85f);
        float base = rnd(wr, 0f, TAU), warn = def.param("beamWarn", 1f), life = def.param("beamLife", 5f);
        for (int k = 0; k < n; k++) {
            Hazard h = WreckOps.hazard(s, Entities.HZ_LANE, e.x, e.y, def.param("beamLen", 520f), life, def.param("beamDps", 22f), warn);
            if (h == null) continue;
            h.ang = base + k * TAU / n;
            h.spin = spin;
            h.w = def.param("beamW", 46f);
        }
        s.sound.emit(SoundEvents.TENTACLE_EMERGE);
    }

    /** The echo wave: a ring of slow bolts, each ring turned a little against the last. */
    private static void echo(Sim s, Enemy e, EnemyDef def, float dt, float every) {
        if ((e.mt1 -= dt) > 0f) return;
        e.mt1 = every;
        int n = Math.round(def.param("echoN", 16f));
        float sp = def.param("echoSpeed", 190f), dmg = def.param("echoDmg", 14f) * e.dmgMul;
        float a0 = e.cnt++ * 0.31f;
        for (int k = 0; k < n; k++) {
            float a = a0 + k * TAU / n;
            WreckOps.shoot(s, e.x, e.y - 20f, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, dmg, 2.6f);
        }
        s.sound.emit(SoundEvents.SPORE_BURST);
    }

    /** Phase 3's pulse: 1.5 s of warning (the renderer flashes everything outside the bubble), then 28 to the hero if he is outside it. */
    private static void pulse(Sim s, Enemy e, EnemyDef def, WreckRules wr, float dt) {
        if (e.ai == PULSE) {
            e.tele -= dt;
            if ((e.aiT -= dt) <= 0f) {
                if (!wr.inBubble(s.px, s.py) && s.invuln <= 0f && s.ended == 0) {
                    WreckOps.hurtHero(s, Math.max(1f, def.param("pulseDmg", 28f) - (float) s.stats.armor), 0.5f, Sim.DMG_AREA);
                }
                WreckOps.fx(s, Entities.FX_SLAM, e.x, e.y, 280f, 0f, 0.6f);
                WreckOps.shake(s, 0.35f);
                s.sound.emit(SoundEvents.BOSS_SLAM);
                e.ai = WALK;
                e.tele = 0f;
            }
            return;
        }
        if ((e.mt2 -= dt) <= 0f) {
            e.ai = PULSE;
            e.aiT = e.tele = e.teleMax = def.param("pulseWarn", 1.5f);
            e.mt2 = def.param("pulseEvery", 6f);
        }
    }

    // ------------------------------------------------------------------ 1 Hull Breacher Alpha

    private static void alpha(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        EnemyDef def = e.type.def;
        WreckRules wr = WreckRules.of(s);
        switch (e.ai) {
            case CAST: // the stomp winds up on the spot (its ring is on the floor); on landing a well opens under the hero
                e.clip = Entities.CL_CAST;
                e.tele -= dt;
                if ((e.aiT -= dt) <= 0f) {
                    if (wr != null) wr.addTempWell(s.px, s.py, def.param("wellR", 160f), def.param("wellT", 6f));
                    WreckOps.fx(s, Entities.FX_BLINK, s.px, s.py, 110f, 0f, 0.5f);
                    if ((++e.cnt & 1) == 0) crates(s, def, wr);
                    e.ai = REST;
                    e.aiT = 0.9f;
                    e.tele = 0f;
                }
                return;
            case REST:
                e.clip = 0;
                if ((e.aiT -= dt) <= 0f) {
                    e.ai = WALK;
                    e.actT = def.param("stompEvery", 4.2f) * rnd(wr, 0.8f, 1.2f);
                }
                return;
            default:
        }
        e.clip = 0;
        e.actT -= dt;
        if (moving && d > def.param("stopDist", 60f)) WreckOps.walkTo(s, e, dt, dx, dy, d, 1f);
        if (e.actT > 0f || d > 420f) return;
        float warn = def.param("stompWarn", 0.9f);
        e.ai = CAST;
        e.aiT = e.tele = e.teleMax = warn;
        e.teleLen = 0f;
        Hazard h = WreckOps.hazard(s, Entities.HZ_BOMB, e.x, e.y, def.param("stompR", 130f), 0.5f, def.param("stompDmg", 22f), warn);
        if (h != null) h.variant = Entities.BV_STOMP;
    }

    /** Crates fall round the hero after the stomp: a ring each, then a blast. */
    private static void crates(Sim s, EnemyDef def, WreckRules wr) {
        int n = Math.round(def.param("crates", 3f));
        for (int k = 0; k < n; k++) {
            float x = s.px, y = s.py;
            if (k > 0) {
                float a = rnd(wr, 0f, TAU), r = rnd(wr, 60f, 190f);
                x += (float) Math.cos(a) * r;
                y += (float) Math.sin(a) * r * 0.8f;
            }
            Hazard h = WreckOps.hazard(s, Entities.HZ_BOMB, x, y, def.param("crateR", 44f), 0.4f, def.param("crateDmg", 16f),
                    def.param("crateWarn", 1f) + 0.3f * k);
            if (h != null) h.variant = Entities.BV_CRATE;
        }
    }

    // ------------------------------------------------------------------ 2 Salvage Warden

    private static void warden(Sim s, Enemy e, float dt, boolean moving, float dx, float dy, float d) {
        EnemyDef def = e.type.def;
        WreckRules wr = WreckRules.of(s);
        if (e.ai == TELE) { // the shimmer, then it is somewhere else and decoys stand where it was
            e.clip = 0;
            e.tele -= dt;
            if ((e.aiT -= dt) <= 0f) {
                int n = Math.round(def.param("decoys", 2f));
                for (int k = 0; k < n; k++) {
                    float x = e.x, y = e.y;
                    if (k > 0) {
                        float a = rnd(wr, 0f, TAU), r = rnd(wr, 120f, 200f);
                        x = s.px + (float) Math.cos(a) * r;
                        y = s.py + (float) Math.sin(a) * r;
                    }
                    WreckOps.spawnPinned(s, "WARDEN_DECOY", x, y);
                }
                float a = rnd(wr, 0f, TAU), r = rnd(wr, def.param("blinkMin", 200f), def.param("blinkMax", 300f));
                e.x = s.px + (float) Math.cos(a) * r;
                e.y = s.py + (float) Math.sin(a) * r;
                WreckOps.fx(s, Entities.FX_BLINK, e.x, e.y, 80f, 0f, 0.35f);
                s.sound.emit(SoundEvents.VIRE_BLINK);
                e.ai = WALK;
                e.tele = 0f;
                e.mt0 = def.param("blinkEvery", 9f) * rnd(wr, 0.85f, 1.15f);
            }
            return;
        }
        e.clip = 0;
        float hover = def.param("hover", 230f);
        if (moving) {
            if (d > hover + 30f) WreckOps.walkTo(s, e, dt, dx, dy, d, 1f);
            else if (d < hover - 50f) WreckOps.walkTo(s, e, dt, dx, dy, d, -0.7f);
        }
        if ((e.actT -= dt) <= 0f && d < 520f) { // a fan of bolts at the hero
            e.actT = def.param("barrageEvery", 3.2f);
            int n = Math.round(def.param("fan", 7f));
            float spread = def.param("fanSpread", 0.55f), v = def.param("boltSpeed", 210f), dmg = def.param("boltDmg", 9f) * e.dmgMul;
            float base = (float) Math.atan2(dy, dx);
            for (int k = 0; k < n; k++) {
                float a = base + (n == 1 ? 0f : (k / (float) (n - 1) - 0.5f) * 2f * spread);
                WreckOps.shoot(s, e.x, e.y - 14f, (float) Math.cos(a) * v, (float) Math.sin(a) * v, dmg, 2.6f);
            }
            s.sound.emit(SoundEvents.DRONE_SHOT);
        }
        if ((e.mt0 -= dt) <= 0f) {
            e.ai = TELE;
            e.aiT = e.tele = e.teleMax = def.param("blinkTele", 0.6f);
            WreckOps.fx(s, Entities.FX_BLINK, e.x, e.y, 80f, 0f, 0.5f);
        }
    }
}
