package se.felixarvid.thorsurvivors.render.stagefx;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.game.Events;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.WorldGen;
import se.felixarvid.thorsurvivors.game.layout.StreetsLayout;
import se.felixarvid.thorsurvivors.game.stage.RefineryRules;
import se.felixarvid.thorsurvivors.render.WorldRenderer;

/**
 * Refinery District drawing (v1.4 chunk C). On the floor, under the darkness and every sprite: conveyor belts (a dark lane with
 * stripes that scroll the way the belt runs, arrows that flash orange during the 1.5 s before a flip), furnace gates (a glowing 3 x 3),
 * molten channels with their catwalk bridges and the orange glow under furnaces. Over the world: the Heat Wave band and its warning at
 * the screen edge, coolant fog, the Cooling Tower ring, the gate ring of the finale, the heat vignette and a small heat bar under
 * the hero. The bottom screen gets the heat bar ({@link #hud}) and the minimap the wave, gates, valves and tanks.
 *
 * <p>Allocation free: paints are made once, loops walk the visible tiles, the heat label strings are precomputed.
 */
final class RefineryFx implements StageFx {
    private static final String[] HEAT_TXT = new String[131];

    static {
        for (int i = 0; i < HEAT_TXT.length; i++) HEAT_TXT[i] = "HEAT " + i;
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] v = new float[2], fp = new float[2];

    RefineryFx() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    }

    private static RefineryRules rules(Sim s) {
        return s.rules instanceof RefineryRules ? (RefineryRules) s.rules : null;
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ floor

    @Override
    public void drawFloor(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        RefineryRules rl = rules(s);
        if (rl == null) return;
        float sc = r.fxScale(), ts = WorldGen.TILE * sc, now = r.fxNow();
        int tx0 = (int) Math.floor(r.fxWx(0) / WorldGen.TILE) - 1, tx1 = (int) Math.floor(r.fxWx(w) / WorldGen.TILE) + 1;
        int ty0 = (int) Math.floor(r.fxWy(0) / WorldGen.TILE) - 1, ty1 = (int) Math.floor(r.fxWy(h) / WorldGen.TILE) + 1;
        float flash = rl.flipFlash() ? 0.5f + 0.5f * (float) Math.sin(now * 16.0) : 0f;
        float scroll = (now * 0.9f * rl.beltMul()) % 1f;
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                int b = StreetsLayout.surfaceAt(tx, ty);
                if (b == 0) {
                    if (StreetsLayout.bridge(tx, ty)) bridgeTile(c, r.fxX(tx * WorldGen.TILE), r.fxY(ty * WorldGen.TILE), ts);
                    continue;
                }
                float x = r.fxX(tx * WorldGen.TILE), y = r.fxY(ty * WorldGen.TILE);
                if ((b & WorldGen.SURF_MOLTEN) != 0) moltenTile(c, x, y, ts, tx, ty, now);
                else if ((b & WorldGen.SURF_FURNACE_GATE) != 0) gateTile(c, x, y, ts, tx, ty, now);
                else beltTile(c, rl, r, x, y, ts, tx, ty, scroll, flash);
            }
        }
        // the glow of the furnaces on the ground around them
        int cx0 = StreetsLayout.cell(tx0 - 6), cx1 = StreetsLayout.cell(tx1 + 6), cy0 = StreetsLayout.cell(ty0 - 6), cy1 = StreetsLayout.cell(ty1 + 6);
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (!StreetsLayout.furnace(cx, cy, fp)) continue;
                float px = r.fxX(fp[0]), py = r.fxY(fp[1]);
                if (px < -400 || px > w + 400 || py < -400 || py > h + 400) continue;
                float pu = 0.85f + 0.15f * (float) Math.sin(now * 3.1 + cx * 1.7 + cy);
                for (int k = 0; k < 3; k++) {
                    fill.setColor(argb((int) (26 * pu), 0xFF6A1E));
                    c.drawOval(px - (190 - k * 55) * sc, py - (190 - k * 55) * sc * 0.6f, px + (190 - k * 55) * sc, py + (190 - k * 55) * sc * 0.6f, fill);
                }
            }
        }
    }

    private void beltTile(Canvas c, RefineryRules rl, WorldRenderer r, float x, float y, float ts, int tx, int ty, float scroll, float flash) {
        float cx = (tx + 0.5f) * WorldGen.TILE, cy = (ty + 0.5f) * WorldGen.TILE;
        boolean has = rl.push(cx, cy, v);
        float ux = has ? v[0] : 0f, uy = has ? v[1] : 0f;
        fill.setColor(0xFF231F1C);
        c.drawRect(x, y, x + ts + 1, y + ts + 1, fill);
        boolean vertical = Math.abs(uy) >= Math.abs(ux);
        float dir = vertical ? uy : ux;
        float sgn = dir >= 0f ? 1f : -1f;
        fill.setColor(0xFF4C433B);
        for (int k = 0; k < 4; k++) {
            float p = ((k / 4f) + (sgn > 0 ? scroll : -scroll) / 4f + 1f) % 1f * ts;
            if (vertical) c.drawRect(x + ts * 0.06f, y + p, x + ts * 0.94f, y + p + ts * 0.09f, fill);
            else c.drawRect(x + p, y + ts * 0.06f, x + p + ts * 0.09f, y + ts * 0.94f, fill);
        }
        // the rails
        fill.setColor(0xFF6B5E4F);
        if (vertical) {
            c.drawRect(x, y, x + ts * 0.05f, y + ts + 1, fill);
            c.drawRect(x + ts * 0.95f, y, x + ts + 1, y + ts + 1, fill);
        } else {
            c.drawRect(x, y, x + ts + 1, y + ts * 0.05f, fill);
            c.drawRect(x, y + ts * 0.95f, x + ts + 1, y + ts + 1, fill);
        }
        if (flash > 0f) { // the arrow of the way it is about to run: orange, flashing
            float fs = rl.manualFlash > 0f ? 1f : -1f; // before a scheduled flip the arrow shows the new way; after a stomp the one just taken
            float ax = ux * (rl.manualFlash > 0f ? 1f : fs), ay = uy * (rl.manualFlash > 0f ? 1f : fs);
            float mx = x + ts / 2f, my = y + ts / 2f, a = ts * 0.28f;
            line.setColor(argb((int) (120 + 135 * flash), 0xFFA030));
            line.setStrokeWidth(Math.max(2f, ts * 0.1f));
            float px = -ay, py = ax;
            c.drawLine(mx + ax * a, my + ay * a, mx - ax * a * 0.2f + px * a, my - ay * a * 0.2f + py * a, line);
            c.drawLine(mx + ax * a, my + ay * a, mx - ax * a * 0.2f - px * a, my - ay * a * 0.2f - py * a, line);
        }
    }

    private void gateTile(Canvas c, float x, float y, float ts, int tx, int ty, float now) {
        float pu = 0.75f + 0.25f * (float) Math.sin(now * 5.0 + tx * 0.9 + ty * 1.3);
        fill.setColor(0xFF2A1208);
        c.drawRect(x, y, x + ts + 1, y + ts + 1, fill);
        fill.setColor(argb((int) (150 + 100 * pu), 0xFF5A14));
        c.drawRect(x + ts * 0.08f, y + ts * 0.08f, x + ts * 0.92f, y + ts * 0.92f, fill);
        line.setColor(0xFF2A1208);
        line.setStrokeWidth(Math.max(2f, ts * 0.07f));
        for (int k = 1; k < 4; k++) c.drawLine(x + ts * 0.08f, y + ts * k / 4f, x + ts * 0.92f, y + ts * k / 4f, line);
        fill.setColor(argb((int) (90 * pu), 0xFFE070));
        c.drawRect(x + ts * 0.3f, y + ts * 0.3f, x + ts * 0.7f, y + ts * 0.7f, fill);
    }

    private void moltenTile(Canvas c, float x, float y, float ts, int tx, int ty, float now) {
        float f = 0.5f + 0.5f * (float) Math.sin(now * 2.4 + tx * 0.8 + ty * 1.1 + Math.sin(now * 0.7 + ty) * 2.0);
        int red = 0xD8 + (int) (0x27 * f), green = 0x3C + (int) (0x70 * f);
        fill.setColor(0xFF000000 | (red << 16) | (green << 8) | 0x0C);
        c.drawRect(x, y, x + ts + 1, y + ts + 1, fill);
        fill.setColor(argb((int) (50 + 70 * f), 0xFFE890));
        c.drawRect(x + ts * (0.2f + 0.3f * f), y + ts * 0.25f, x + ts * (0.55f + 0.3f * f), y + ts * 0.5f, fill);
        if (StreetsLayout.bridge(tx, ty)) return;
        if ((StreetsLayout.surfaceAt(tx, ty - 1) & WorldGen.SURF_MOLTEN) == 0) { // a dark crust on the rim
            fill.setColor(0xFF3A1E10);
            c.drawRect(x, y, x + ts + 1, y + ts * 0.12f, fill);
        }
    }

    private void bridgeTile(Canvas c, float x, float y, float ts) {
        fill.setColor(0xFF5A5650);
        c.drawRect(x, y, x + ts + 1, y + ts + 1, fill);
        line.setColor(0xFF2E2B27);
        line.setStrokeWidth(Math.max(2f, ts * 0.06f));
        for (int k = 1; k < 4; k++) c.drawLine(x, y + ts * k / 4f, x + ts, y + ts * k / 4f, line);
        fill.setColor(0xFF8A8478);
        c.drawRect(x, y, x + ts + 1, y + ts * 0.07f, fill);
        c.drawRect(x, y + ts * 0.93f, x + ts + 1, y + ts + 1, fill);
    }

    // ------------------------------------------------------------------ lights

    @Override
    public void lights(WorldRenderer r, Sim s) {
        RefineryRules rl = rules(s);
        if (rl == null) return;
        float now = r.fxNow();
        float x0 = s.px - 760f, x1 = s.px + 760f, y0 = s.py - 480f, y1 = s.py + 480f;
        int tx0 = (int) Math.floor(x0 / WorldGen.TILE), tx1 = (int) Math.floor(x1 / WorldGen.TILE);
        int ty0 = (int) Math.floor(y0 / WorldGen.TILE), ty1 = (int) Math.floor(y1 / WorldGen.TILE);
        int cx0 = StreetsLayout.cell(tx0 - 6), cx1 = StreetsLayout.cell(tx1 + 6), cy0 = StreetsLayout.cell(ty0 - 6), cy1 = StreetsLayout.cell(ty1 + 6);
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (!StreetsLayout.furnace(cx, cy, fp)) continue;
                r.fxLight(fp[0], fp[1] - 50f, 230f * (0.92f + 0.08f * (float) Math.sin(now * 3.0 + cx + cy)), 0.95f);
            }
        }
        // a light on the centre tile of each visible gate and on every fourth molten tile
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                int b = StreetsLayout.surfaceAt(tx, ty);
                if ((b & WorldGen.SURF_FURNACE_GATE) != 0) {
                    if ((StreetsLayout.surfaceAt(tx - 1, ty) & StreetsLayout.surfaceAt(tx + 1, ty) & StreetsLayout.surfaceAt(tx, ty - 1)
                            & StreetsLayout.surfaceAt(tx, ty + 1) & WorldGen.SURF_FURNACE_GATE) != 0) {
                        r.fxLight((tx + 0.5f) * WorldGen.TILE, (ty + 0.5f) * WorldGen.TILE, 150f, 0.9f);
                    }
                } else if ((b & WorldGen.SURF_MOLTEN) != 0 && ((tx + 2 * ty) & 3) == 0) {
                    r.fxLight((tx + 0.5f) * WorldGen.TILE, (ty + 0.5f) * WorldGen.TILE, 120f, 0.8f);
                }
            }
        }
        for (int k = 0; k < rl.vgN; k++) r.fxLight(rl.vgX[k], rl.vgY[k], 140f, 0.9f);
    }

    // ------------------------------------------------------------------ over the world

    @Override
    public void drawGround(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        RefineryRules rl = rules(s);
        if (rl == null) return;
        float sc = r.fxScale(), now = r.fxNow();
        for (int k = 0; k < rl.vgN; k++) { // phase 3 of the finale: a ring of open gates
            float px = r.fxX(rl.vgX[k]), py = r.fxY(rl.vgY[k]);
            float pu = 0.7f + 0.3f * (float) Math.sin(now * 6.0 + k);
            fill.setColor(argb((int) (55 * pu), 0xFF5A14));
            c.drawCircle(px, py, RefineryRules.VGATE_R * sc, fill);
            line.setColor(argb(230, 0xFFC060));
            line.setStrokeWidth(3f * r.fxK());
            c.drawCircle(px, py, RefineryRules.VGATE_R * sc, line);
        }
        for (int i = 0; i < RefineryRules.MAXF; i++) { // coolant fog
            if (rl.fogT[i] <= 0f) continue;
            float px = r.fxX(rl.fogX[i]), py = r.fxY(rl.fogY[i]), fade = Math.min(1f, rl.fogT[i] / 1.5f);
            for (int k = 0; k < 3; k++) {
                float rr = RefineryRules.FOG_R * sc * (0.55f + 0.2f * k + 0.04f * (float) Math.sin(now * 2.0 + k));
                fill.setColor(argb((int) (60 * fade), 0xA8F0FF));
                c.drawOval(px - rr, py - rr * 0.7f, px + rr, py + rr * 0.7f, fill);
            }
        }
        Events ev = s.events;
        if (ev.towerOn) { // Cooling Tower: a coolant ring round the Signal Tower
            float px = r.fxX(ev.tx), py = r.fxY(ev.ty);
            line.setColor(argb(200, 0x4FD8FF));
            line.setStrokeWidth(4f * r.fxK());
            float pulse = 1f + 0.04f * (float) Math.sin(now * 4.0);
            c.drawOval(px - Events.TOWER_R * sc * pulse, py - Events.TOWER_R * sc * 0.7f * pulse, px + Events.TOWER_R * sc * pulse,
                    py + Events.TOWER_R * sc * 0.7f * pulse, line);
        }
    }

    @Override
    public void drawOver(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        RefineryRules rl = rules(s);
        if (rl == null) return;
        float sc = r.fxScale(), k1 = r.fxK(), now = r.fxNow();
        for (int i = 0; i < RefineryRules.MAXW; i++) {
            if (!rl.wLive[i]) continue;
            float edge = rl.waveEdge(i, s.time), dir = rl.wDir[i];
            if (rl.waveWarning(i)) { // 1.5 s warning: a flashing orange bar at the screen edge it comes from
                float fl = 0.5f + 0.5f * (float) Math.sin(now * 18.0);
                float bw = 26f * k1;
                float xe = dir > 0f ? 0f : w - bw;
                fill.setColor(argb((int) (70 + 150 * fl), 0xFF7A18));
                c.drawRect(xe, 0, xe + bw, h, fill);
                line.setColor(argb((int) (150 + 100 * fl), 0xFFE080));
                line.setStrokeWidth(4f * k1);
                for (int y = 1; y < 6; y++) {
                    float ay = h * y / 6f, ax = dir > 0f ? bw + 30f * k1 : w - bw - 30f * k1;
                    c.drawLine(ax - dir * 22f * k1, ay - 22f * k1, ax, ay, line);
                    c.drawLine(ax - dir * 22f * k1, ay + 22f * k1, ax, ay, line);
                }
                continue;
            }
            float xl = r.fxX(edge), xt = r.fxX(edge - dir * RefineryRules.WAVE_THICK);
            float a = Math.min(xl, xt), b = Math.max(xl, xt);
            if (b < 0 || a > w) continue;
            for (int k = 0; k < 4; k++) { // brighter toward the leading edge
                float f0 = k / 4f, f1 = (k + 1) / 4f;
                float p0 = xt + (xl - xt) * f0, p1 = xt + (xl - xt) * f1;
                fill.setColor(argb(26 + 24 * k, 0xFF6A18));
                c.drawRect(Math.min(p0, p1), 0, Math.max(p0, p1), h, fill);
            }
            line.setColor(0xFFFFE890);
            line.setStrokeWidth(6f * k1);
            c.drawLine(xl, 0, xl, h, line);
        }
        if (rl.pulseFlash > 0f) { // Overdrive: the gates flash
            fill.setColor(argb((int) (110 * Math.min(1f, rl.pulseFlash / 0.6f)), 0xFF8A30));
            c.drawRect(0, 0, w, h, fill);
        }
        float lim = rl.heatLimit();
        if (rl.heat > 50f) { // the heat vignette: red-orange edges, layered so they fade inward (from 50 heat, the burn)
            float f = Math.min(1f, (rl.heat - 50f) / (lim - 50f));
            if (rl.overheated) f = 0.8f + 0.2f * (float) Math.sin(now * 9.0);
            int layers = 6, al = (int) (95 * f) / layers + 1;
            for (int k = 0; k < layers; k++) {
                float e = (30f + 45f * k) * k1 * (0.7f + 0.5f * f);
                fill.setColor(argb(al, 0xFF3A10));
                c.drawRect(0, 0, e, h, fill);
                c.drawRect(w - e, 0, w, h, fill);
                c.drawRect(e, 0, w - e, e, fill);
                c.drawRect(e, h - e, w - e, h, fill);
            }
        }
        if (rl.heat > 0.5f) { // a small heat bar under the hero
            float px = r.fxX(s.px), py = r.fxY(s.py) + 12f * k1, bw = 84f * k1, bh = 8f * k1, f = rl.heat / lim;
            fill.setColor(0xB0000000);
            c.drawRect(px - bw / 2 - 2, py - 2, px + bw / 2 + 2, py + bh + 2, fill);
            fill.setColor(heatColor(f, rl.overheated, now));
            c.drawRect(px - bw / 2, py, px - bw / 2 + bw * f, py + bh, fill);
        }
    }

    private static int heatColor(float f, boolean over, float now) {
        if (over) return ((int) (now * 8f) & 1) == 0 ? 0xFFFF3A20 : 0xFFFFC040;
        int g = (int) (0xE0 - 0xB0 * Math.min(1f, f * 1.15f));
        return 0xFF000000 | (0xFF << 16) | (Math.max(0x30, g) << 8) | (f < 0.5f ? 0x30 : 0x18);
    }

    // ------------------------------------------------------------------ bottom screen

    @Override
    public boolean hud(Canvas c, Sim s, float x0, float y0, float x1, float y1, float u) {
        RefineryRules rl = rules(s);
        if (rl == null) return false;
        float lim = rl.heatLimit(), f = Math.max(0f, Math.min(1f, rl.heat / lim));
        fill.setColor(0xFF1A110C);
        c.drawRect(x0, y0, x1, y1, fill);
        fill.setColor(heatColor(f, rl.overheated, (float) s.time));
        c.drawRect(x0 + 2f * u, y0 + 2f * u, x0 + 2f * u + (x1 - x0 - 4f * u) * f, y1 - 2f * u, fill);
        fill.setColor(0xFFB8A090); // the 50 mark: the burn starts here
        float m50 = x0 + (x1 - x0) * (RefineryRules.HEAT_BURN_AT / lim);
        c.drawRect(m50 - u, y0, m50 + u, y1, fill);
        text.setTextSize(Math.max(8f, (y1 - y0) * 0.82f));
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(rl.overheated ? 0xFFFFE0A0 : 0xFFF2DCC8);
        int hv = Math.max(0, Math.min(HEAT_TXT.length - 1, Math.round(rl.heat)));
        c.drawText(rl.overheated ? "OVERHEAT" : HEAT_TXT[hv], x0 + 8f * u, y1 - (y1 - y0) * 0.2f, text);
        return true;
    }

    // ------------------------------------------------------------------ minimap

    @Override
    public void minimap(Canvas c, Sim s, float cx, float cy, float kk, float side, float u) {
        RefineryRules rl = rules(s);
        if (rl == null) return;
        float half = side / 2f, range = half / kk;
        int tx0 = (int) Math.floor((s.px - range) / WorldGen.TILE), tx1 = (int) Math.floor((s.px + range) / WorldGen.TILE);
        int ty0 = (int) Math.floor((s.py - range) / WorldGen.TILE), ty1 = (int) Math.floor((s.py + range) / WorldGen.TILE);
        float ts = WorldGen.TILE * kk;
        for (int ty = ty0; ty <= ty1; ty++) { // gates (orange) and molten metal (red), belts as a faint lane
            for (int tx = tx0; tx <= tx1; tx++) {
                int b = StreetsLayout.surfaceAt(tx, ty);
                if (b == 0) continue;
                float x = cx + (tx * WorldGen.TILE - s.px) * kk, y = cy + (ty * WorldGen.TILE - s.py) * kk;
                int col = (b & WorldGen.SURF_FURNACE_GATE) != 0 ? 0xFFFF7A20 : (b & WorldGen.SURF_MOLTEN) != 0 ? 0xFFD02010 : 0x50907868;
                fill.setColor(col);
                c.drawRect(x, y, x + ts + 1f, y + ts + 1f, fill);
            }
        }
        for (int i = 0; i < s.destN; i++) { // coolant valves (cyan) and fuel tanks (orange squares)
            int kd = s.destKind[i];
            if (kd != RefineryRules.DK_COOLANT && kd != RefineryRules.DK_FUEL && kd != RefineryRules.DK_VALVE_HEAVY) continue;
            float dx = (s.destX[i] - s.px) * kk, dy = (s.destY[i] - s.py) * kk;
            if (Math.abs(dx) > half || Math.abs(dy) > half) continue;
            fill.setColor(kd != RefineryRules.DK_FUEL ? 0xFF7FE8FF : 0xFFFFA030);
            c.drawRect(cx + dx - 4f * u, cy + dy - 4f * u, cx + dx + 4f * u, cy + dy + 4f * u, fill);
        }
        line.setStrokeWidth(Math.max(2f, 3f * u));
        for (int i = 0; i < RefineryRules.MAXW; i++) { // the Heat Wave: its leading edge as a vertical line (flashing while it only warns)
            if (!rl.wLive[i]) continue;
            boolean warn = rl.waveWarning(i);
            float edge = rl.waveEdge(i, s.time);
            float dx = (edge - s.px) * kk;
            if (warn && ((int) (s.time * 8) & 1) == 0) continue;
            dx = Math.max(-half, Math.min(half, dx));
            line.setColor(warn ? 0xFFFFC060 : 0xFFFF6A18);
            c.drawLine(cx + dx, cy - half, cx + dx, cy + half, line);
        }
    }
}
