package se.felixarvid.thorsurvivors.render.stagefx;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.WorldGen;
import se.felixarvid.thorsurvivors.game.layout.DebrisLayout;
import se.felixarvid.thorsurvivors.game.stage.WreckRules;
import se.felixarvid.thorsurvivors.render.WorldRenderer;

/**
 * Meridian Wreck's drawing (v1.4 chunk D): gravity wells (swirling rings, a dark core, a flicker before they change), teleporter
 * pad rings with their recharge and channel arcs, the Pillar Gate, the Vacuum Breach bubble (a green ring, the red outside), the
 * warning and gate banners, the telegraphs of the Rift Hound and the Breacher, and the minimap (pads paired by a line, wells,
 * bubble, the Pillar). {@link #paintHull} bakes the hull plates and the starry void into the ground chunks. Nothing allocates per
 * frame: paints, path and text are made on first use, banner strings come from a table.
 */
public final class WreckFx implements StageFx {
    private static final float TAU = 6.2831855f, YK = 0.8f;
    private static final int CYAN = 0x4FD8FF, VIOLET = 0xB070FF, GREEN = 0x39FF88, RED = 0xFF3B2A, YELLOW = 0xE8C23A;
    private static final String[] BREACH_TXT = new String[27];
    private static final String GATE_TXT = "PILLAR GATE OPEN", GATE_LABEL = "PILLAR GATE", WARN_TXT = "VACUUM BREACH";

    static {
        for (int i = 0; i < BREACH_TXT.length; i++) BREACH_TXT[i] = "VACUUM BREACH  " + i + " s";
    }

    private Paint fill, stroke, text;
    private Path hole;
    private final RectF oval = new RectF();

    private void paints() {
        if (fill != null) return;
        fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        hole = new Path();
        hole.setFillType(Path.FillType.EVEN_ODD);
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | rgb;
    }

    private void ring(Canvas c, float x, float y, float r, float w, int color, int alpha) {
        stroke.setStrokeWidth(Math.max(1.5f, w));
        stroke.setColor(argb(alpha, color));
        oval.set(x - r, y - r * YK, x + r, y + r * YK);
        c.drawOval(oval, stroke);
    }

    private void disc(Canvas c, float x, float y, float r, int color, int alpha) {
        fill.setColor(argb(alpha, color));
        oval.set(x - r, y - r * YK, x + r, y + r * YK);
        c.drawOval(oval, fill);
    }

    private void arc(Canvas c, float x, float y, float r, float start, float sweep, float w, int color, int alpha) {
        stroke.setStrokeWidth(Math.max(1.5f, w));
        stroke.setColor(argb(alpha, color));
        oval.set(x - r, y - r * YK, x + r, y + r * YK);
        c.drawArc(oval, start, sweep, false, stroke);
    }

    // ------------------------------------------------------------------ the world layer

    @Override
    public void drawGround(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        WreckRules wr = WreckRules.of(s);
        if (wr == null) return;
        paints();
        float sc = r.fxScale(), ks = r.fxUi();
        float t = (float) s.time;
        drawArena(r, c, s, w, h, sc, ks, t);
        for (int i = 0; i < wr.wellN; i++) {
            float x = r.fxX(wr.wellX[i]), y = r.fxY(wr.wellY[i]), rad = wr.wellR[i] * sc;
            if (x < -rad || x > w + rad || y < -rad || y > h + rad) continue;
            boolean on = wr.wellOn(i), flick = wr.wellFlicker(i) && ((int) (t * 14f) & 1) == 0;
            drawWell(c, x, y, rad, on || flick, !on, wr.wellHeldOff(i), sc, ks, t);
        }
        for (int k = 0; k < WreckRules.MAX_TEMP; k++) {
            if (wr.tmpLife[k] <= 0f) continue;
            float x = r.fxX(wr.tmpX[k]), y = r.fxY(wr.tmpY[k]), rad = wr.tmpR[k] * sc;
            if (x < -rad || x > w + rad || y < -rad || y > h + rad) continue;
            drawWell(c, x, y, rad, true, false, false, sc, ks, t);
        }
        for (int i = 0; i < wr.padN; i++) {
            float x = r.fxX(wr.padX[i]), y = r.fxY(wr.padY[i]);
            if (x < -200 || x > w + 200 || y < -300 || y > h + 200) continue;
            drawPad(c, wr, i, x, y, sc, ks, t);
        }
        if (wr.bubbleOn || wr.breachWarn > 0f) drawBubble(r, c, s, wr, w, h, sc, ks, t);
        drawTelegraphs(r, c, s, w, h, sc, ks, t);
    }

    /** The Pillar's arena: a faint ring on the floor while the hero is within sight of it. */
    private void drawArena(WorldRenderer r, Canvas c, Sim s, int w, int h, float sc, float ks, float t) {
        float px = DebrisLayout.pillarX(), py = DebrisLayout.pillarY();
        float dx = px - s.px, dy = py - s.py;
        if (dx * dx + dy * dy > 1500f * 1500f) return;
        float x = r.fxX(px), y = r.fxY(py), rad = DebrisLayout.ARENA_R * sc;
        ring(c, x, y, rad, 3f * ks, CYAN, 60 + (int) (30 * Math.sin(t * 2f)));
        ring(c, x, y, rad * 0.97f, 1.5f * ks, CYAN, 40);
    }

    private void drawWell(Canvas c, float x, float y, float rad, boolean on, boolean flickerOff, boolean held, float sc, float ks, float t) {
        int base = held ? 0x707078 : VIOLET;
        disc(c, x, y, rad, base, on ? 34 : 14);
        ring(c, x, y, rad, 2.5f * ks, base, on ? 170 : 70);
        if (on) {
            for (int k = 0; k < 3; k++) { // three arms of a swirl, turning inward faster toward the centre
                float rr = rad * (0.88f - 0.26f * k);
                arc(c, x, y, rr, t * (70f + 55f * k) + k * 120f, 78f, 3f * ks, VIOLET, 190 - 30 * k);
                arc(c, x, y, rr, t * (70f + 55f * k) + k * 120f + 180f, 52f, 2f * ks, CYAN, 130 - 20 * k);
            }
        }
        float core = 24f * sc;
        disc(c, x, y, core * 1.7f, 0x000000, on ? 120 : 70);
        disc(c, x, y, core, 0x000000, 235);
        ring(c, x, y, core, 2.5f * ks, on ? CYAN : 0x606878, on ? 150 + (int) (90 * Math.sin(t * 9f)) : 80);
    }

    private void drawPad(Canvas c, WreckRules wr, int i, float x, float y, float sc, float ks, float t) {
        boolean gate = wr.padKind[i] == WreckRules.PK_GATE;
        float rad = (gate ? 62f : wr.padKind[i] == WreckRules.PK_EXIT ? 50f : wr.padRadius()) * sc;
        float cd = wr.padCd[i];
        boolean ready = cd <= 0f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * (gate ? 3.2f : 2.4f) + i);
        int col = wr.padKind[i] == WreckRules.PK_EXIT ? GREEN : CYAN;
        disc(c, x, y, rad * 1.25f, col, ready ? (int) (26 + 26 * pulse) : 10);
        ring(c, x, y, rad, 3f * ks, col, ready ? (int) (150 + 80 * pulse) : 70);
        if (gate) {
            ring(c, x, y, rad * 1.45f, 2f * ks, col, (int) (90 + 90 * pulse));
            ring(c, x, y, rad * 1.8f * (0.8f + 0.2f * pulse), 2f * ks, col, 60);
        }
        if (!ready) { // the recharge: an arc that fills as the pad rests
            float frac = 1f - Math.min(1f, cd / wr.padRestMax());
            arc(c, x, y, rad * 0.78f, -90f, 360f * frac, 4f * ks, 0x8FA8C0, 160);
        } else { // a faint column of light shows a pad that is ready
            float bh = (gate ? 260f : 120f) * ks;
            fill.setColor(argb((int) (22 + 28 * pulse), col));
            c.drawRect(x - 0.45f * rad, y - bh, x + 0.45f * rad, y, fill);
            fill.setColor(argb((int) (60 + 60 * pulse), col));
            c.drawRect(x - 0.12f * rad, y - bh * 0.85f, x + 0.12f * rad, y, fill);
        }
        if (wr.padChanIdx == i) { // channelling: the ring closes round the hero's feet
            float need = gate ? 0.4f : 0.5f;
            arc(c, x, y, rad * 0.62f, -90f, 360f * Math.min(1f, wr.padChan / need), 6f * ks, 0xFFFFFF, 235);
        }
        if (gate) {
            text.setTextSize(30f * ks);
            text.setColor(argb(230, CYAN));
            text.setShadowLayer(3f, 0f, 2f, 0xFF000000);
            c.drawText(GATE_LABEL, x, y - 150f * ks, text);
            text.clearShadowLayer();
        }
    }

    /** The bubble: a green ring and a red wash over everything outside it; only a faint ring in the two seconds before a breach. */
    private void drawBubble(WorldRenderer r, Canvas c, Sim s, WreckRules wr, int w, int h, float sc, float ks, float t) {
        float x = r.fxX(wr.bubbleX), y = r.fxY(wr.bubbleY), rad = wr.bubbleR * sc;
        boolean warn = !wr.bubbleOn;
        if (!warn) {
            hole.reset();
            hole.addRect(-20f, -20f, w + 20f, h + 20f, Path.Direction.CW);
            oval.set(x - rad, y - rad * YK, x + rad, y + rad * YK);
            hole.addOval(oval, Path.Direction.CW);
            boolean pulseWarn = false;
            Enemy p = wr.pillar();
            if (p != null && p.ai == 15 && p.tele > 0f) pulseWarn = true; // the Pillar's pulse is about to hit everything outside
            int a = pulseWarn ? (int) (80 + 70 * Math.sin(t * 22f)) : 46 + (int) (12 * Math.sin(t * 3f));
            fill.setColor(argb(a, RED));
            c.drawPath(hole, fill);
        }
        ring(c, x, y, rad, 4f * ks, GREEN, warn ? 90 : 220);
        if (!warn) {
            ring(c, x, y, rad * 0.985f, 1.5f * ks, 0xFFFFFF, 90);
            disc(c, x, y, rad, GREEN, 14);
        }
    }

    private void drawTelegraphs(WorldRenderer r, Canvas c, Sim s, int w, int h, float sc, float ks, float t) {
        for (int i = 0; i < s.enemyN; i++) {
            Enemy e = s.enemies[i];
            if (e.dead || e.tele <= 0f) continue;
            int arch = e.type.arch;
            if (arch != EnemyDef.ARCH_RIFT && arch != EnemyDef.ARCH_BREACHER) continue;
            float x = r.fxX(e.x), y = r.fxY(e.y);
            if (x < -300 || x > w + 300 || y < -300 || y > h + 300) continue;
            float prog = 1f - e.tele / Math.max(0.01f, e.teleMax);
            if (arch == EnemyDef.ARCH_RIFT) { // a violet shimmer where it stands, and a ring at the end of the blink
                disc(c, x, y - 10f * ks, 26f * ks * (1.3f - 0.5f * prog), VIOLET, (int) (60 + 120 * prog));
                ring(c, x, y - 10f * ks, 34f * ks * (1.2f - 0.6f * prog), 2f * ks, VIOLET, 200);
            } else { // a thin line along the dive, filling as the time runs out, and the burst ring at its end
                float len = e.teleLen * sc, wd = Math.max(5f * ks, e.teleW * sc);
                float deg = (float) Math.atan2(e.ay, e.ax) * 57.29578f;
                c.save();
                c.translate(x, y - 8f * ks);
                c.rotate(deg);
                fill.setColor(argb(70, RED));
                c.drawRect(0f, -wd / 2f, len, wd / 2f, fill);
                fill.setColor(argb(230, YELLOW));
                c.drawRect(0f, -wd / 2f, len * prog, wd / 2f, fill);
                c.restore();
                float ex = x + e.ax * len, ey = y + e.ay * len;
                ring(c, ex, ey, 60f * sc * (0.5f + 0.5f * prog), 2f * ks, YELLOW, (int) (80 + 120 * prog));
            }
        }
    }

    // ------------------------------------------------------------------ the screen layer

    @Override
    public void drawOver(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        WreckRules wr = WreckRules.of(s);
        if (wr == null) return;
        paints();
        float ks = r.fxUi();
        float t = (float) s.time;
        float edge = 0f;
        if (wr.breachWarn > 0f) edge = 0.55f + 0.45f * (float) Math.sin(t * 16f);
        else if (wr.bubbleOn && wr.outsideSeconds() > 0f) edge = Math.min(1f, 0.35f + wr.outsideSeconds() * 0.12f);
        if (edge > 0f) { // the red edge of the screen
            int th = Math.round(40f * ks);
            for (int k = 0; k < 4; k++) {
                fill.setColor(argb((int) (95 * edge * (1f - k * 0.22f)), RED));
                int o = k * th / 3;
                c.drawRect(o, o, w - o, o + th / 3f, fill);
                c.drawRect(o, h - o - th / 3f, w - o, h - o, fill);
                c.drawRect(o, o, o + th / 3f, h - o, fill);
                c.drawRect(w - o - th / 3f, o, w - o, h - o, fill);
            }
        }
        if (wr.breachWarn > 0f || wr.bubbleOn && wr.breachIdx >= 0) {
            int left = wr.breachWarn > 0f ? (int) Math.ceil(wr.breachWarn) : Math.max(0, Math.min(26, (int) Math.ceil(25f - wr.bubbleT)));
            text.setTextSize(34f * ks);
            text.setColor(argb(235, wr.breachWarn > 0f ? RED : GREEN));
            text.setShadowLayer(4f, 0f, 2f, 0xFF000000);
            c.drawText(wr.breachWarn > 0f ? WARN_TXT : BREACH_TXT[left], w / 2f, 150f * ks, text);
            text.clearShadowLayer();
        }
        if (wr.gateBannerT > 0f) {
            text.setTextSize(52f * ks);
            text.setColor(argb((int) (255 * Math.min(1f, wr.gateBannerT)), CYAN));
            text.setShadowLayer(5f, 0f, 3f, 0xFF000000);
            c.drawText(GATE_TXT, w / 2f, h * 0.17f, text);
            text.clearShadowLayer();
        }
    }

    // ------------------------------------------------------------------ the minimap

    @Override
    public void minimap(Canvas c, Sim s, float cx, float cy, float kk, float side, float u) {
        WreckRules wr = WreckRules.of(s);
        if (wr == null) return;
        paints();
        float lim = side / 2f - 10f * u;
        stroke.setStrokeWidth(Math.max(1.5f, 1.5f * u));
        for (int i = 0; i < wr.padN; i++) { // twins joined by a line
            int tw = wr.padTwin[i];
            if (wr.padKind[i] != WreckRules.PK_PAIR || tw < i) continue;
            stroke.setColor(argb(95, CYAN));
            c.drawLine(cx + (wr.padX[i] - s.px) * kk, cy + (wr.padY[i] - s.py) * kk, cx + (wr.padX[tw] - s.px) * kk, cy + (wr.padY[tw] - s.py) * kk, stroke);
        }
        for (int i = 0; i < wr.wellN; i++) { // wells: a ring of their radius, filled while they pull
            float x = cx + (wr.wellX[i] - s.px) * kk, y = cy + (wr.wellY[i] - s.py) * kk, rad = wr.wellR[i] * kk;
            if (Math.abs(x - cx) > side / 2f + rad || Math.abs(y - cy) > side / 2f + rad) continue;
            boolean on = wr.wellOn(i);
            fill.setColor(argb(on ? 80 : 26, wr.wellHeldOff(i) ? 0x808088 : VIOLET));
            c.drawCircle(x, y, rad, fill);
            stroke.setColor(argb(on ? 210 : 100, VIOLET));
            c.drawCircle(x, y, rad, stroke);
            fill.setColor(0xFF000000);
            c.drawCircle(x, y, Math.max(2f, 24f * kk), fill);
        }
        for (int k = 0; k < WreckRules.MAX_TEMP; k++) {
            if (wr.tmpLife[k] <= 0f) continue;
            fill.setColor(argb(110, VIOLET));
            c.drawCircle(cx + (wr.tmpX[k] - s.px) * kk, cy + (wr.tmpY[k] - s.py) * kk, wr.tmpR[k] * kk, fill);
        }
        for (int i = 0; i < wr.padN; i++) { // pads: cyan diamonds (the gate bigger and white, the exit green)
            float x = cx + (wr.padX[i] - s.px) * kk, y = cy + (wr.padY[i] - s.py) * kk;
            if (Math.abs(x - cx) > lim || Math.abs(y - cy) > lim) continue;
            int kind = wr.padKind[i];
            float rad = (kind == WreckRules.PK_GATE ? 11f : 7f) * u;
            fill.setColor(wr.padCd[i] > 0f ? 0xFF607080 : kind == WreckRules.PK_GATE ? 0xFFE8FFFF : kind == WreckRules.PK_EXIT ? 0xFF39FF88 : 0xFF4FD8FF);
            c.save();
            c.rotate(45f, x, y);
            c.drawRect(x - rad * 0.7f, y - rad * 0.7f, x + rad * 0.7f, y + rad * 0.7f, fill);
            c.restore();
        }
        if (wr.bubbleOn) {
            stroke.setStrokeWidth(Math.max(2f, 2.5f * u));
            stroke.setColor(argb(230, GREEN));
            c.drawCircle(cx + (wr.bubbleX - s.px) * kk, cy + (wr.bubbleY - s.py) * kk, wr.bubbleR * kk, stroke);
        }
        // the Pillar: always on the map, clamped to its edge so it can be found
        float dx = (DebrisLayout.pillarX() - s.px) * kk, dy = (DebrisLayout.pillarY() - s.py) * kk;
        boolean edge = Math.abs(dx) > lim || Math.abs(dy) > lim;
        if (edge) {
            dx = Math.max(-lim, Math.min(lim, dx));
            dy = Math.max(-lim, Math.min(lim, dy));
        }
        fill.setColor(0xFF4FD8FF);
        c.drawCircle(cx + dx, cy + dy, 8f * u, fill);
        stroke.setStrokeWidth(Math.max(2f, 2.5f * u));
        stroke.setColor(0xFFFFFFFF);
        c.drawCircle(cx + dx, cy + dy, 12f * u, stroke);
        if (!edge) {
            stroke.setColor(argb(110, CYAN));
            stroke.setStrokeWidth(Math.max(1.5f, 1.5f * u));
            c.drawCircle(cx + dx, cy + dy, DebrisLayout.ARENA_R * kk, stroke);
        }
    }

    // ------------------------------------------------------------------ ground chunks

    /**
     * Bakes the hull into a ground window of {@code w x h} tiles at tile (x0, y0), {@code T} px per tile: the hull keeps the rock
     * texture and gets checkered plates and seams; the void between the islands is near-black with a few stars; hull tiles next to the
     * void get a lighter rim. Every tile depends only on its own coordinates and its four neighbours, so chunk borders match.
     */
    public static void paintHull(Canvas c, WorldGen gen, int x0, int y0, int w, int h, int T) {
        Paint p = new Paint();
        float seam = Math.max(1f, T / 40f), rim = Math.max(2f, T / 12f), star = Math.max(1.5f, T / 28f);
        for (int ty = y0; ty < y0 + h; ty++) {
            for (int tx = x0; tx < x0 + w; tx++) {
                float l = (tx - x0) * T, t = (ty - y0) * T;
                if ((gen.surface(tx, ty) & WorldGen.SURF_VOID) != 0) {
                    p.setColor(0xFF05070C);
                    c.drawRect(l, t, l + T, t + T, p);
                    int hs = WorldGen.hash(tx, ty, 77);
                    int n = hs & 3;
                    for (int k = 0; k < n; k++) {
                        float sx = l + ((hs >>> (4 + k * 9)) & 255) / 255f * (T - star), sy = t + ((hs >>> (7 + k * 8)) & 255) / 255f * (T - star);
                        p.setColor(((hs >>> (20 + k)) & 1) == 0 ? 0xFFB8C8E0 : 0xFF6A7C9A);
                        c.drawRect(sx, sy, sx + star, sy + star, p);
                    }
                    continue;
                }
                if (((tx ^ ty) & 1) == 0) { // plates in a checker
                    p.setColor(0x401C2430);
                    c.drawRect(l, t, l + T, t + T, p);
                }
                p.setColor(0x50080C12); // seams on the left and top edge of every tile
                c.drawRect(l, t, l + T, t + seam, p);
                c.drawRect(l, t, l + seam, t + T, p);
                p.setColor(0xFF2A3646); // the rim of the island
                if ((gen.surface(tx, ty - 1) & WorldGen.SURF_VOID) != 0) c.drawRect(l, t, l + T, t + rim, p);
                if ((gen.surface(tx, ty + 1) & WorldGen.SURF_VOID) != 0) c.drawRect(l, t + T - rim, l + T, t + T, p);
                if ((gen.surface(tx - 1, ty) & WorldGen.SURF_VOID) != 0) c.drawRect(l, t, l + rim, t + T, p);
                if ((gen.surface(tx + 1, ty) & WorldGen.SURF_VOID) != 0) c.drawRect(l + T - rim, t, l + T, t + T, p);
            }
        }
    }
}
