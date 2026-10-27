package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.List;
import java.util.Random;

/**
 * The {@code skilled} policy of {@link StageSoak} (v1.4): a bot that plays like a competent human, as a yardstick for the difficulty.
 * The old {@code mid} bot only flees the crowd and dodges when six enemies are close; against a human that meant the game was
 * tuned far too easy. This one:
 * <ul>
 * <li><b>Kites</b>: every 5th step it scores 16 headings (and standing still) by simulating the hero 1.4 s ahead against the
 * enemies (chasing at their speed), their bolts, the ground hazards and warnings (rings, lanes), charge lines and boss bodies, and
 * picks the safest heading with some persistence. When nothing threatens it circles the crowd at a distance where the weapons reach.</li>
 * <li><b>Dodges</b> on imminent contact with a heavy hitter, a bolt about to land, a bomb about to blast under it, or a charge line.</li>
 * <li><b>Collects</b>: food when hurt, crates and chests, gems and coins when it is safe, vacuum and clover.</li>
 * <li><b>Builds</b>: at most four attack weapons plus the passives that evolve them, fusions and branches first, defence when hurt.</li>
 * </ul>
 */
final class SkilledBot {
    private static final int DIRS = 16;
    private static final float[] T = {0.35f, 0.7f, 1.05f, 1.4f};
    private static final float[] TW = {1.0f, 0.9f, 0.7f, 0.5f};
    private final float[] cs = new float[DIRS], sn = new float[DIRS];
    private float lastX, lastY;
    int bought;
    private int tick;
    private final Random shopRng = new Random(77);
    private float goalX, goalY;
    /** Near enemies copied once per decision. */
    private final float[] ex = new float[600], ey = new float[600], er = new float[600], ev = new float[600], ew = new float[600];
    private int en;
    /** Contact danger of the best-path enemy (for the dodge). */
    float minPathDist;

    SkilledBot() {
        for (int i = 0; i < DIRS; i++) {
            cs[i] = (float) Math.cos(i * Math.PI * 2 / DIRS);
            sn[i] = (float) Math.sin(i * Math.PI * 2 / DIRS);
        }
    }

    // ------------------------------------------------------------------ movement

    void steer(Sim s, Input in) {
        shop(s, shopRng);
        boolean decide = (tick++ % 5) == 0;
        if (decide) decideHeading(s);
        in.stickX = lastX;
        in.stickY = lastY;
        // aim: the boss when near, else the nearest enemy
        Enemy aimAt = null;
        float bd = 1e18f;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.dead || e.dormant) continue;
            float dx = e.x - s.px, dy = e.y - s.py, q = dx * dx + dy * dy;
            if (q < bd) {
                bd = q;
                aimAt = e;
            }
        }
        if (s.bossNow != null && Math.hypot(s.bossNow.x - s.px, s.bossNow.y - s.py) < 420) aimAt = s.bossNow;
        if (aimAt != null) {
            float dx = aimAt.x - s.px, dy = aimAt.y - s.py, m = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
            in.aimStickX = dx / m;
            in.aimStickY = dy / m;
        }
        if (s.dodgeCd <= 0f && s.dodgeT <= 0f && s.invuln <= 0f && imminent(s)) in.dodgeReq = true;
    }

    private static boolean heavy(Sim s, Enemy e) {
        return e.power * e.dmgMul - s.stats.armor >= 0.05f * s.maxHp || s.hp < 0.55f * s.maxHp;
    }

    /** True when something is about to hurt the hero within ~0.2 s and a dodge is worth it. */
    private boolean imminent(Sim s) {
        float px = s.px, py = s.py;
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.dead || e.sleeping || e.dormant || e.stun > 0f) continue;
            float dx = e.x - px, dy = e.y - py, d2 = dx * dx + dy * dy;
            if (e.type.arch == EnemyDef.ARCH_CHARGER && e.tele > 0f && e.tele < 0.22f) {
                float along = -dx * e.ax - dy * e.ay, across = dx * e.ay - dy * e.ax;
                if (along > -20f && along < e.teleLen + 30f && Math.abs(across) < e.radius + 26f) return true;
            }
            if (e.power <= 0f) continue;
            float rr = e.radius * 0.8f + Sim.PLAYER_R * 0.8f + 16f + e.speed * 0.12f;
            if (d2 < rr * rr && heavy(s, e)) return true;
        }
        for (int i = 0; i < s.eprojN; i++) {
            Entities.EProj p = s.eprojs[i];
            if (p.friendly) continue;
            float rx = p.x - px, ry = p.y - py, v2 = p.vx * p.vx + p.vy * p.vy;
            if (v2 < 1f || rx * rx + ry * ry > 140f * 140f) continue;
            float t = -(rx * p.vx + ry * p.vy) / v2;
            if (t <= 0f || t > 0.2f) continue;
            float hx = rx + p.vx * t, hy = ry + p.vy * t;
            if (hx * hx + hy * hy < (p.radius + 14f) * (p.radius + 14f) && (p.dmg * 1f >= 0.04f * s.maxHp || s.hp < 0.55f * s.maxHp)) return true;
        }
        for (int i = 0; i < s.hazN; i++) {
            Entities.Hazard hz = s.hazards[i];
            if (hz.kind == Entities.HZ_BOMB && hz.warn > 0f && hz.warn < 0.22f) {
                float dx = px - hz.x, dy = py - hz.y;
                if (dx * dx + dy * dy < hz.r * hz.r) return true;
            }
            if (hz.kind == Entities.HZ_LANE && hz.variant != Entities.LV_MARK && hz.warn > 0f && hz.warn < 0.22f && inLane(hz, px, py, 6f)) return true;
        }
        return false;
    }

    private static boolean inLane(Entities.Hazard hz, float x, float y, float pad) {
        float ca = (float) Math.cos(hz.ang), sa = (float) Math.sin(hz.ang);
        float dx = x - hz.x, dy = y - hz.y;
        float along = dx * ca + dy * sa, across = -dx * sa + dy * ca;
        return along > -pad && along < hz.r + pad && Math.abs(across) < hz.w * 0.5f + pad;
    }

    private void decideHeading(Sim s) {
        float px = s.px, py = s.py;
        boolean melee = s.main != null && s.main.def.kind == se.felixarvid.thorsurvivors.data.MainWeaponDef.CONE; // the hammer: a player stands in the crowd
        float vh = Sim.PLAYER_SPEED_K * (float) s.stage.playerSpeed * (float) s.stats.moveSpeedMul * (s.heroSlowT > 0f ? 0.75f : 1f);
        // near enemies
        en = 0;
        float cxs = 0, cys = 0;
        int cn = 0;
        float nearest = 1e9f, nearChaser = 1e9f, ncx = 0, ncy = 0;
        for (int i = 0; i < s.enemyN && en < ex.length; i++) {
            Enemy e = s.enemies[i];
            if (e.dead || e.sleeping) continue;
            float dx = e.x - px, dy = e.y - py, d2 = dx * dx + dy * dy;
            if (d2 > 560f * 560f) continue;
            float danger = e.power * e.dmgMul;
            float rad = e.radius * 0.8f + Sim.PLAYER_R * 0.8f;
            float vel = e.speed * e.slow;
            float margin = melee ? 24f : 34f;
            if (e.dormant) { // a tentacle still warning: stay out of its ring
                rad = e.type.def.param("hitR", 34f) + 10f;
                vel = 0f;
                danger = 25f;
            } else if (e.type.arch == EnemyDef.ARCH_TENTACLE) {
                rad = e.type.def.param("hitR", 34f) + 6f;
                vel = 0f;
            } else if (e.type.arch == EnemyDef.ARCH_EXPLODER) {
                rad = 62f;
                danger = Math.max(danger, 8f);
            } else if (e.type.ranged) {
                vel = 0f; // they hold their distance; their bolts are handled below
                danger = Math.min(danger, 4f);
                rad = e.radius * 0.8f + 12f;
                margin = 10f;
            }
            if (e.boss && !e.type.ranged) {
                rad = e.radius * 0.9f + (melee ? 48f : 100f); // the hammer has to reach the boss body
                margin = melee ? 40f : 60f;
            }
            if (e.elite) margin = 50f;
            if (e.type.arch == EnemyDef.ARCH_TANK) rad += 14f;
            ex[en] = e.x;
            ey[en] = e.y;
            er[en] = rad + margin;
            ev[en] = vel;
            ew[en] = Math.max(0.35f, Math.min(3f, danger / (0.06f * s.maxHp))) * (e.boss ? 1.5f : 1f);
            en++;
            if (!e.type.ranged && e.type.arch != EnemyDef.ARCH_TENTACLE && d2 < 380f * 380f) {
                cxs += e.x;
                cys += e.y;
                cn++;
            }
            if (d2 < nearest * nearest) nearest = (float) Math.sqrt(d2);
            if (!e.type.ranged && !e.dormant && e.type.arch != EnemyDef.ARCH_TENTACLE && e.type.arch != EnemyDef.ARCH_EXPLODER && !e.boss && d2 < nearChaser * nearChaser) {
                nearChaser = (float) Math.sqrt(d2);
                ncx = dx;
                ncy = dy;
            }
        }
        // goals
        float gx = 0, gy = 0;
        float roastW = s.hp < 0.75f * s.maxHp ? 2.2f * (1f - s.hp / s.maxHp) + 0.6f : 0f;
        float bestRoast = 1e18f, bestGem = 1e18f, bestCrate = 1e18f, bestCoin = 1e18f, bestUtil = 1e18f;
        float[] rg = new float[2], gg = new float[2], cg = new float[2], kg = new float[2], ug = new float[2];
        for (int i = 0; i < s.pickN; i++) {
            Entities.Pickup pk = s.picks[i];
            float dx = pk.x - px, dy = pk.y - py, q = dx * dx + dy * dy;
            switch (pk.kind) {
                case Entities.K_ROAST:
                    if (roastW > 0f && q < bestRoast && q < 800f * 800f) { bestRoast = q; rg[0] = dx; rg[1] = dy; }
                    break;
                case Entities.K_GEM:
                    if (q < bestGem && q < 340f * 340f) { bestGem = q; gg[0] = dx; gg[1] = dy; }
                    break;
                case Entities.K_CHEST:
                case Entities.K_EQUIP:
                    if (q < bestCrate && q < 1000f * 1000f) { bestCrate = q; cg[0] = dx; cg[1] = dy; }
                    break;
                case Entities.K_COIN:
                case Entities.K_COINBAG:
                    if (q < bestCoin && q < 220f * 220f) { bestCoin = q; kg[0] = dx; kg[1] = dy; }
                    break;
                case Entities.K_VACUUM:
                case Entities.K_CLOVER:
                case Entities.K_ROSARY:
                case Entities.K_OROLOGION:
                    if (q < bestUtil && q < 450f * 450f) { bestUtil = q; ug[0] = dx; ug[1] = dy; }
                    break;
                default:
                    break;
            }
        }
        if (bestRoast < 1e17f) { float m = (float) Math.sqrt(bestRoast) + 1e-3f; gx += rg[0] / m * roastW; gy += rg[1] / m * roastW; }
        if (bestCrate < 1e17f) { float m = (float) Math.sqrt(bestCrate) + 1e-3f; gx += cg[0] / m * 1.1f; gy += cg[1] / m * 1.1f; }
        if (bestUtil < 1e17f) { float m = (float) Math.sqrt(bestUtil) + 1e-3f; gx += ug[0] / m * 0.8f; gy += ug[1] / m * 0.8f; }
        if (bestCoin < 1e17f) { float m = (float) Math.sqrt(bestCoin) + 1e-3f; gx += kg[0] / m * 0.5f; gy += kg[1] / m * 0.5f; }
        if (bestGem < 1e17f) { float m = (float) Math.sqrt(bestGem) + 1e-3f; gx += gg[0] / m * 0.95f; gy += gg[1] / m * 0.95f; }
        // map finds (weapon crates, gear cabinets) and the Salvager: a player walks to them when nothing is on top of him
        if (nearest > 140f && s.hp > 0.5f * s.maxHp) {
            float bf = 1e18f, fx = 0, fy = 0;
            for (int i = 0; i < s.findN; i++) {
                if (s.findTaken[i]) continue;
                float dx = s.findX[i] - px, dy = s.findY[i] - py, q = dx * dx + dy * dy;
                if (q < bf && q < 2600f * 2600f) { bf = q; fx = dx; fy = dy; }
            }
            if (bf < 1e17f) { float m = (float) Math.sqrt(bf) + 1e-3f; gx += fx / m * 0.9f; gy += fy / m * 0.9f; }
            Merchant mc = s.merchant;
            if (mc.present && cheapestAffordable(s) > 0) {
                float dx = mc.x - px, dy = mc.y - py, m = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
                if (m > 30f && m < 600f && mc.timeLeft() > m / Math.max(60f, vh) + 4f) { gx += dx / m * 1.4f; gy += dy / m * 1.4f; }
            }
        }
        // a boss is fought from about 250 units
        Enemy boss = s.bossNow;
        if (boss != null && !boss.type.ranged) {
            float dx = boss.x - px, dy = boss.y - py, dd = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
            if (dd > (melee ? 170f : 270f) && dd < 1000f) { gx += dx / dd * 0.7f; gy += dy / dd * 0.7f; }
        } else if (boss != null) {
            float dx = boss.x - px, dy = boss.y - py, dd = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
            if (dd > 260f && dd < 1000f) { gx += dx / dd * 0.6f; gy += dy / dd * 0.6f; }
        }
        // no pickup goal: keep the weapons in reach of the crowd by circling it (tangent around the centroid) at about 150-200 units
        if (cn > 0) {
            float ccx = cxs / cn - px, ccy = cys / cn - py, cd = (float) Math.sqrt(ccx * ccx + ccy * ccy) + 1e-3f;
            float ux = ccx / cd, uy = ccy / cd;
            if (cd > 260f) { gx += ux * 0.35f; gy += uy * 0.35f; }
            else { gx += -uy * 0.3f; gy += ux * 0.3f; } // tangent
        } else {
            // nothing around: wander towards unexplored directions in a slow circle
            double a = s.time * 0.25;
            gx += (float) Math.cos(a) * 0.3f;
            gy += (float) Math.sin(a) * 0.3f;
        }
        // short-range weapons (the hammer, whips, auras) need the crowd inside their reach: step in when healthy and nothing heavy shoots
        if (s.hp > 0.55f * s.maxHp && nearChaser > (melee ? 85f : 105f) && nearChaser < 420f) {
            gx += ncx / nearChaser * 0.55f;
            gy += ncy / nearChaser * 0.55f;
        }
        float gl = (float) Math.sqrt(gx * gx + gy * gy);
        if (gl > 1.5f) { gx = gx / gl * 1.5f; gy = gy / gl * 1.5f; }
        goalX = gx;
        goalY = gy;

        int bestK = -1;
        float bestCost = 1e18f;
        float lastLen = (float) Math.sqrt(lastX * lastX + lastY * lastY);
        for (int k = -1; k < DIRS; k++) {
            float ux = k < 0 ? 0f : cs[k], uy = k < 0 ? 0f : sn[k];
            float cost = 0f;
            for (int ti = 0; ti < T.length; ti++) {
                float t = T[ti], hx = px + ux * vh * t, hy = py + uy * vh * t;
                float c = 0f;
                for (int i = 0; i < en; i++) {
                    float dx = hx - ex[i], dy = hy - ey[i];
                    float d = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
                    float adv = Math.min(d, ev[i] * t);
                    float dist = d - adv; // the enemy has closed this much towards where the hero will be
                    float R = er[i];
                    if (dist < R) {
                        float f = (R - dist) / R;
                        c += ew[i] * f * f * 6f;
                    }
                }
                // enemy bolts
                for (int i = 0; i < s.eprojN; i++) {
                    Entities.EProj p = s.eprojs[i];
                    if (p.friendly) continue;
                    float bx = p.x + p.vx * t - hx, by = p.y + p.vy * t - hy;
                    float rr = p.radius + 24f;
                    float q = bx * bx + by * by;
                    if (q < rr * rr) c += 5f * (1f - (float) Math.sqrt(q) / rr) * Math.max(0.5f, Math.min(2.5f, p.dmg / (0.05f * s.maxHp)));
                }
                for (int i = 0; i < s.hazN; i++) {
                    Entities.Hazard hz = s.hazards[i];
                    if (hz.kind == Entities.HZ_ASH) continue;
                    if (hz.kind == Entities.HZ_LANE) {
                        if (hz.variant != Entities.LV_MARK || hz.warn > 0f) {
                            if (inLane(hz, hx, hy, 28f)) c += 5f;
                        }
                        continue;
                    }
                    if (hz.kind == Entities.HZ_BOMB && hz.warn <= 0f && hz.life < 0.05f) continue;
                    float dx = hx - hz.x, dy = hy - hz.y, rr = hz.r + 26f;
                    float q = dx * dx + dy * dy;
                    if (q < rr * rr) c += (hz.kind == Entities.HZ_FROST ? 1.5f : 4f) * (1f - (float) Math.sqrt(q) / rr * 0.5f);
                }
                // enemies with a charge line
                for (int i = 0; i < s.enemyN; i++) {
                    Enemy e = s.enemies[i];
                    if (e.tele <= 0f || e.dead || e.type.arch != EnemyDef.ARCH_CHARGER) continue;
                    float dx = hx - e.x, dy = hy - e.y;
                    float along = dx * e.ax + dy * e.ay, across = -dx * e.ay + dy * e.ax;
                    if (along > -30f && along < e.teleLen + 50f && Math.abs(across) < e.radius + 44f) c += 4f;
                }
                cost += c * TW[ti];
            }
            // goal pull, a little persistence, a small cost for standing still when nothing forces it
            cost -= (ux * goalX + uy * goalY) * 0.9f;
            if (lastLen > 0.1f) cost -= (ux * lastX + uy * lastY) * 0.25f;
            if (k < 0) cost += 0.25f;
            if (cost < bestCost) {
                bestCost = cost;
                bestK = k;
            }
        }
        if (bestK < 0) {
            lastX = 0f;
            lastY = 0f;
        } else {
            lastX = cs[bestK];
            lastY = sn[bestK];
        }
    }

    /** Price of the cheapest card the hero can pay now (0 = none) - only worth the walk when it is something. */
    private static int cheapestAffordable(Sim s) {
        Merchant m = s.merchant;
        int best = 0;
        for (int i = 0; i < m.cards(); i++) {
            if (m.sold[i] || m.price[i] > s.gold) continue;
            if (i == Merchant.SLOT_SUPPLY && m.supply == Merchant.SUP_MEDKIT && s.hp > 0.7f * s.maxHp) continue;
            if (best == 0 || m.price[i] < best) best = m.price[i];
        }
        return best;
    }

    /** At the stall (headless: the Engine would open PH_MERCHANT): buys what a player would, best first, as long as the gold lasts. */
    void shop(Sim s, Random rng) {
        Merchant m = s.merchant;
        if (!m.wantsOpen()) return;
        m.opened();
        for (int pass = 0; pass < 4; pass++) {
            int pick = -1;
            int bestV = -1;
            for (int i = 0; i < m.cards(); i++) {
                if (m.sold[i] || m.price[i] > s.gold) continue;
                int v;
                switch (i) {
                    case Merchant.SLOT_FUSE: v = 100; break;
                    case Merchant.SLOT_OFFER: v = m.offer.kind == Offer.HEAL || m.offer.kind == Offer.GOLD ? 0 : score(s, m.offer, rng) / 100; break;
                    case Merchant.SLOT_GEAR: v = 40; break;
                    default: v = m.supply == Merchant.SUP_MEDKIT ? (s.hp < 0.6f * s.maxHp ? 70 : 0) : 20; break;
                }
                if (v > bestV) {
                    bestV = v;
                    pick = i;
                }
            }
            if (pick < 0 || bestV < 25) break;
            if (m.buy(pick) == Merchant.BUY_OK) bought++;
        }
    }

    // ------------------------------------------------------------------ build

    private static int pref(String key) {
        switch (key) {
            case "MAGIC_MISSILE": return 9;
            case "FIREBALL": return 8;
            case "AXE": return 8;
            case "LIGHTNING": return 7;
            case "KNIFE": return 7;
            case "GARLIC": return 7;
            case "HOLYBOOK": return 6;
            case "WHIP": return 6;
            case "HOLYWATER": return 5;
            case "CROSS": return 5;
            default: return 4;
        }
    }

    /** True when an owned weapon (any level) evolves with this passive. */
    private static boolean synergyOfOwned(Inventory inv, String passive) {
        for (int i = 0; i < inv.weaponCount; i++) {
            for (String p : inv.weapons[i].def.evoSynergy) if (p.equals(passive)) return true;
        }
        return false;
    }

    private static boolean partnerOfOwned(Inventory inv, WeaponDef w) {
        for (int i = 0; i < inv.weaponCount; i++) {
            for (WeaponDef f : fusions) {
                if (f.parents.contains(w.key) && f.parents.contains(inv.weapons[i].def.key)) return true;
            }
        }
        return false;
    }

    static WeaponDef[] fusions = new WeaponDef[0];

    int score(Sim s, Offer c, Random rng) {
        Inventory inv = s.inv;
        float hpf = s.hp / s.maxHp;
        int base;
        switch (c.kind) {
            case Offer.FUSE: base = 10000; break;
            case Offer.BRANCH: base = 9000; break;
            case Offer.WEAPON_LEVEL: {
                base = 5000 + pref(c.def.key) * 10;
                if (!c.def.evoSynergy.isEmpty() && holdsSynergy(inv, c.def)) base += 1500;
                if (c.newLevel >= c.def.maxLevel() - 1) base += 400;
                if (partnerOfOwned(inv, c.def)) base += 200;
                break;
            }
            case Offer.PASSIVE_LEVEL:
            case Offer.NEW_PASSIVE: {
                String k = c.def.key;
                boolean syn = synergyOfOwned(inv, k);
                int generic;
                switch (k) {
                    case "POWER": generic = 3900; break;
                    case "COOLDOWN": generic = 3700; break;
                    case "AREA": generic = 3500; break;
                    case "AMOUNT": generic = 3800; break;
                    case "MAXHEALTH": generic = 3600 + (hpf < 0.5f ? 1500 : 0); break;
                    case "ARMOR": generic = 3600 + (hpf < 0.5f ? 1500 : 0); break;
                    case "REGEN": generic = 3400 + (hpf < 0.5f ? 1500 : 0); break;
                    case "GROWTH": generic = 3000; break;
                    default: generic = 1500; break;
                }
                base = syn ? 4800 + (c.kind == Offer.PASSIVE_LEVEL ? 200 : 0) : generic;
                if (c.kind == Offer.PASSIVE_LEVEL) base += 300;
                else if (inv.passiveCount >= 5 && !syn) base -= 1200; // keep a slot for what the weapons need
                break;
            }
            case Offer.NEW_WEAPON: {
                int coreCount = 0;
                for (int i = 0; i < inv.weaponCount; i++) if (!inv.weapons[i].def.fusion) coreCount++;
                base = coreCount < 4 ? 4200 + pref(c.def.key) * 10 + (partnerOfOwned(inv, c.def) ? 250 : 0) : 500;
                break;
            }
            case Offer.HEAL: base = hpf < 0.5f ? 4000 : 100; break;
            default: base = 10; break;
        }
        return base + rng.nextInt(10);
    }

    private static boolean holdsSynergy(Inventory inv, WeaponDef w) {
        for (String p : w.evoSynergy) if (inv.hasPassive(p)) return true;
        return false;
    }

    Offer pick(Sim s, List<Offer> offers, Random rng) {
        Offer best = offers.get(0);
        int bs = -1;
        for (Offer c : offers) {
            int sc = score(s, c, rng);
            if (sc > bs) {
                bs = sc;
                best = c;
            }
        }
        return best;
    }
}
