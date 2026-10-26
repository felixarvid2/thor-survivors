package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;

import java.util.HashMap;
import java.util.Random;

/**
 * Damage of a single weapon against a seeded field of 200 stationary sponges round a standing hero (not a JUnit test):
 * {@code main} prints each fusion's DPS at level 1 and 6 against its parents (max level and evolved), the numbers behind the
 * 1.3x / 0.9x rule of the design (docs/TEKNIK.md "Vapenfusioner").
 */
public final class FusionBalance {
    private FusionBalance() {
    }

    /** Damage per second dealt to the field in {@code seconds}, the weapon at {@code level} (0 = its max level). */
    public static double dps(GameData d, String key, int level, long seed, float seconds) {
        Sim s = new Sim(d, d.stages.get("FLATS"), key, new HashMap<String, Double>(), null, seed);
        s.noSpawns = true;
        s.enemyN = 0;
        s.findN = 0;
        s.merchant.enabled = false;
        s.pois.enabled = false;
        s.inv.weapons[0].level = level == 0 ? s.inv.weapons[0].def.maxLevel() : level;
        s.recomputeAll();
        Random r = new Random(seed * 31 + 5);
        Enemy[] field = new Enemy[200];
        float[] fx = new float[200], fy = new float[200];
        for (int i = 0; i < field.length; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = Math.sqrt(r.nextDouble()) * 260 + 25;
            fx[i] = (float) (Math.cos(a) * rad);
            fy[i] = (float) (Math.sin(a) * rad);
            field[i] = s.addEnemy("COLONIST", fx[i], fy[i], false);
            field[i].hp = field[i].maxHp = 1e6f;
            field[i].speed = 0;
        }
        Input in = new Input();
        int steps = Math.round(seconds * 60f);
        for (int i = 0; i < steps; i++) {
            s.hp = s.maxHp;
            for (int k = 0; k < field.length; k++) {
                Enemy e = field[k];
                e.x = fx[k];
                e.y = fy[k];
                e.kvx = e.kvy = 0;
            }
            s.update(1f / 60f, in);
        }
        double total = 0;
        for (Enemy e : field) total += e.maxHp - e.hp;
        return total / seconds;
    }

    private static double avg(GameData d, String key, int level, float seconds) {
        double t = 0;
        for (long seed = 1; seed <= 3; seed++) t += dps(d, key, level, seed, seconds);
        return t / 3;
    }

    public static void main(String[] a) throws Exception {
        GameData d = StageSoak.data();
        float secs = a.length > 0 ? Float.parseFloat(a[0]) : 30f;
        System.out.println("fusion              F1       F6   parentA(max/evo)    parentB(max/evo)   F1/best  F6/sum");
        for (WeaponDef f : d.fusions) {
            String pa = f.parents.get(0), pb = f.parents.get(1);
            double a1 = avg(d, pa, 0, secs), b1 = avg(d, pb, 0, secs);
            double ae = avg(d, d.weapons.get(pa).evoInto, 0, secs), be = avg(d, d.weapons.get(pb).evoInto, 0, secs);
            double f1 = avg(d, f.key, 1, secs), f6 = avg(d, f.key, 6, secs);
            System.out.printf("%-18s %7.0f %8.0f   %6.0f/%-6.0f      %6.0f/%-6.0f     %.2f/%.2f  %.2f/%.2f%n", f.key, f1, f6, a1, ae, b1, be,
                    f1 / Math.max(a1, b1), f1 / Math.max(ae, be), f6 / (a1 + b1), f6 / (ae + be));
        }
    }
}
