package se.felixarvid.thorsurvivors.ui.menu;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Inventory;
import se.felixarvid.thorsurvivors.game.RunStats;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.render.SpriteAtlas;
import se.felixarvid.thorsurvivors.ui.Skin;

/**
 * v1.5 B3 (item 23): the lower half of the run results screen: the cause of death (or the win), the final build (weapons and
 * passives with their levels) and the damage dealt per weapon, top five, from {@link RunStats}. The upper half (time, kills,
 * level, gold, equipment, branches) is the panel's own. Everything is built once per finished run, drawing allocates nothing.
 */
public final class ResultsScreen {
    private static final String[] NUMS = new String[40];
    private static final int TOP = 5;

    static {
        for (int i = 0; i < NUMS.length; i++) NUMS[i] = String.valueOf(i);
    }

    private final SpriteAtlas atlas = SpriteAtlas.get();
    private Sim cSim;
    private long cTotal = -1;
    private boolean cWin;
    private String cause = "";
    private final String[] topName = new String[TOP], topVal = new String[TOP];
    private final float[] topFrac = new float[TOP];
    private int topN;
    private String totalText = "";

    private void build(Engine eng, Sim s) {
        RunStats rs = s.run;
        cSim = s;
        cTotal = rs.total;
        cWin = eng.resultWin;
        // cause of death
        if (eng.resultWin) {
            cause = "THE RUN IS WON";
        } else {
            String why = rs.deathText();
            String boss = null;
            for (int i = 0; i < s.enemyN; i++) {
                Enemy e = s.enemies[i];
                if (e.boss && !e.dead && !e.dormant) {
                    boss = e.type.def.name;
                    break;
                }
            }
            if (why.isEmpty()) why = "Fell in battle";
            cause = ("CAUSE OF DEATH: " + why + (boss != null ? ", fighting " + boss : "")).toUpperCase(java.util.Locale.ROOT);
        }
        // top five buckets by damage
        boolean[] used = new boolean[RunStats.N];
        topN = 0;
        long max = 0;
        for (int r = 0; r < TOP; r++) {
            int best = -1;
            for (int i = 0; i < RunStats.N; i++) if (!used[i] && rs.dmg[i] > 0 && (best < 0 || rs.dmg[i] > rs.dmg[best])) best = i;
            if (best < 0) break;
            used[best] = true;
            if (r == 0) max = rs.dmg[best];
            topName[topN] = bucketName(s, best);
            topVal[topN] = compact(rs.dmg[best] / RunStats.SCALE);
            topFrac[topN] = max <= 0 ? 0f : (float) rs.dmg[best] / max;
            topN++;
        }
        totalText = "TOTAL " + compact(rs.total / RunStats.SCALE);
    }

    private static String bucketName(Sim s, int b) {
        if (b < RunStats.SLOTS) {
            return b < s.inv.weaponCount && s.inv.weapons[b].def != null ? s.inv.weapons[b].def.name.toUpperCase(java.util.Locale.ROOT) : "WEAPON " + (b + 1);
        }
        if (b == RunStats.MAIN) return s.main != null ? s.main.def.name.toUpperCase(java.util.Locale.ROOT) : "MAIN WEAPON";
        return "HAZARDS AND OTHER";
    }

    /** 950, 12.4K, 3.1M. */
    public static String compact(long v) {
        if (v < 10000) return String.valueOf(v);
        if (v < 1000000) return (v / 100) / 10f + "K";
        return (v / 100000) / 10f + "M";
    }

    public void draw(Canvas c, int w, int h, Skin k, Engine eng, float y0, float y1) {
        Sim s = eng.sim;
        if (s == null) return;
        if (s != cSim || s.run.total != cTotal || eng.resultWin != cWin) build(eng, s);
        float u = k.u;
        float cw = Math.min(w * 0.94f, 1100f * u), x = (w - cw) / 2f;
        k.plate(c, x, y0, x + cw, y1);
        k.rivets(c, x, y0, x + cw, y1, 11f * u, 3f * u);
        int col = eng.resultWin ? Skin.LUMEN : Skin.DANGER;
        k.fit(c, cause, x + 28f * u, y0 + 44f * u, 30f * u, cw - 56f * u, col, Paint.Align.LEFT);
        k.divider(c, x + 24f * u, y0 + 62f * u, x + cw - 24f * u);
        // the build
        float bx = x + 28f * u, by = y0 + 98f * u;
        k.body(c, "FINAL BUILD", bx, by, 22f * u, Skin.DIM, Paint.Align.LEFT);
        Inventory inv = s.inv;
        float ws = 64f * u, wg = 8f * u;
        for (int i = 0; i < inv.weaponCount; i++) {
            WeaponDef d = inv.weapons[i].def;
            if (d != null) tile(c, k, d, inv.weapons[i].level, bx + i * (ws + wg), by + 14f * u, ws, u);
        }
        float ps = 44f * u, py = by + 14f * u + ws + 16f * u;
        if (py + ps > y1 - 8f * u) ps = 0f; // v1.5 integration: B4's achievement lines took the room, the weapons stay
        for (int i = 0; ps > 0f && i < inv.passiveCount; i++) {
            WeaponDef d = inv.passives[i].def;
            if (d != null) tile(c, k, d, inv.passives[i].level, bx + i * (ps + wg), py, ps, u);
        }
        // the damage per weapon
        float dx = x + cw * 0.52f, dr = x + cw - 28f * u;
        k.body(c, "DAMAGE DEALT", dx, by, 22f * u, Skin.DIM, Paint.Align.LEFT);
        k.body(c, totalText, dr, by, 22f * u, Skin.GOLD, Paint.Align.RIGHT);
        float rh = 34f * u;
        int rows = Math.min(topN, Math.max(1, (int) ((y1 - 8f * u - by - 14f * u) / rh))); // v1.5 integration: what fits above y1
        for (int i = 0; i < rows; i++) {
            float ry = by + 14f * u + i * rh;
            k.fill.setColor(0xFF1A1512);
            c.drawRect(dx, ry, dr, ry + rh - 6f * u, k.fill);
            k.fill.setColor(i == 0 ? Skin.ORANGE : 0xFF9A5A2A);
            c.drawRect(dx, ry, dx + (dr - dx) * Math.max(0.02f, topFrac[i]), ry + rh - 6f * u, k.fill);
            k.fit(c, topName[i], dx + 10f * u, ry + rh - 11f * u, 20f * u, (dr - dx) * 0.6f, Skin.TEXT, Paint.Align.LEFT);
            k.text(c, topVal[i], dr - 10f * u, ry + rh - 11f * u, 20f * u, Skin.TEXT, Paint.Align.RIGHT);
        }
        if (topN == 0) k.body(c, "NO DAMAGE RECORDED", dx, by + 40f * u, 22f * u, Skin.DIM, Paint.Align.LEFT);
    }

    private void tile(Canvas c, Skin k, WeaponDef d, int level, float x, float y, float s, float u) {
        k.slotWell(c, x, y, s);
        if (!atlas.drawIcon(c, null, Icons.base(d.key), x + s / 2, y + s / 2, s * 0.76f)) {
            k.text(c, d.name.substring(0, 1), x + s / 2, y + s * 0.66f, s * 0.5f, Skin.TEXT, Paint.Align.CENTER);
        }
        boolean max = level >= d.maxLevel();
        atlas.drawIcon(c, null, d.evolution ? "frame_unique" : max ? "frame_rare" : "frame_common", x + s / 2, y + s / 2, s);
        k.shadowed(c, NUMS[Math.max(0, Math.min(NUMS.length - 1, level))], x + s - 5f * u, y + s - 6f * u, s * 0.34f,
                max ? Skin.GOLD : Skin.TEXT, Paint.Align.RIGHT);
    }
}
