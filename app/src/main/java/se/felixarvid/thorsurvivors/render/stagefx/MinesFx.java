package se.felixarvid.thorsurvivors.render.stagefx;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.game.Entities.Enemy;
import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.stage.MinesRules;
import se.felixarvid.thorsurvivors.render.WorldRenderer;

/**
 * Deep Mines: the chunk E add-ons on screen. The cart lane warning (a pulsing strip with chevrons) and the cart itself, the methane
 * haze of the firedamp pockets with the flashing fuse and the blast ring, and the minimap markers (lane warning, pockets). Drawn
 * after the darkness so the warnings read in the dark. Nothing here allocates.
 */
final class MinesFx implements StageFx {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);

    MinesFx() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void drawOver(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        if (!(s.rules instanceof MinesRules) || !s.rules.addonsEnabled) return;
        MinesRules mr = (MinesRules) s.rules;
        float sc = h / Sim.VIEW_H, t = (float) s.time;
        float ox = w / 2f - s.px * sc, oy = h / 2f - s.py * sc;
        // firedamp: faint yellow haze, a fuse that flashes faster as it burns down
        for (int i = 0; i < mr.pocketN; i++) {
            float x = mr.pocketX[i] * sc + ox, y = mr.pocketY[i] * sc + oy, rr = MinesRules.POCKET_R * sc;
            if (x < -rr * 2 || x > w + rr * 2 || y < -rr * 2 || y > h + rr * 2) continue;
            float fuse = mr.pocketFuse[i];
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.7f + i * 2f);
            boolean lit = fuse >= 0f;
            float flash = lit ? (0.5f + 0.5f * (float) Math.sin(fuse * (8f + 14f * (1f - fuse / MinesRules.FUSE)) * 3.1416f)) : 0f;
            for (int k = 3; k >= 1; k--) {
                int a = (int) ((12 + 8 * pulse + (lit ? 40 * flash : 0)) * (4 - k) / 2f);
                fill.setColor((Math.min(150, a) << 24) | (lit && flash > 0.5f ? 0xFFF4B0 : 0xE8D040));
                c.drawCircle(x + (float) Math.sin(t * 0.8f + i + k) * 6f * sc, y, rr * (0.45f + 0.3f * k), fill);
            }
            if (lit) { // the blast radius it will have
                line.setStrokeWidth(Math.max(2f, 3f * sc));
                line.setColor(((int) (90 + 120 * flash) << 24) | 0xFFD040);
                c.drawCircle(x, y, MinesRules.BLAST_R * sc * (1f - 0.1f * flash), line);
            }
        }
        if (mr.sinceBlast < 0.35f) {
            float k = 1f - mr.sinceBlast / 0.35f;
            float x = mr.lastBlastX * sc + ox, y = mr.lastBlastY * sc + oy;
            fill.setColor(((int) (120 * k) << 24) | 0xFFE080);
            c.drawCircle(x, y, MinesRules.BLAST_R * sc * (1.2f - 0.4f * k), fill);
        }
        // the cart: the strip warns while the countdown runs, the cart rolls through it afterwards
        if (mr.cartOn) {
            float y0 = (-MinesRules.CART_HALF_W) * sc + oy, y1 = MinesRules.CART_HALF_W * sc + oy;
            float xa = Math.min(mr.cartX0, mr.cartX1) * sc + ox, xb = Math.max(mr.cartX0, mr.cartX1) * sc + ox;
            float x0 = Math.max(0f, xa), x1 = Math.min((float) w, xb);
            boolean warn = mr.cartT < MinesRules.CART_WARN;
            float pulse = 0.5f + 0.5f * (float) Math.sin(mr.cartT * 14f);
            if (x1 > x0) {
                fill.setColor(((int) (warn ? 34 + 34 * pulse : 22) << 24) | 0xFF5A30);
                c.drawRect(x0, y0, x1, y1, fill);
                line.setStrokeWidth(Math.max(2f, 3f * sc));
                line.setColor(((int) (warn ? 120 + 110 * pulse : 80) << 24) | 0xFF7A40);
                c.drawLine(x0, y0, x1, y0, line);
                c.drawLine(x0, y1, x1, y1, line);
                if (warn) { // chevrons running the way the cart will come
                    float step = 90f * sc, phase = (mr.cartT * 260f * sc) % step;
                    for (float x = x0 - step + phase * mr.cartDir; x < x1; x += step) {
                        float cx = mr.cartDir > 0 ? x : x1 - (x - x0);
                        c.drawLine(cx, y0 + 6f * sc, cx + mr.cartDir * 16f * sc, (y0 + y1) / 2f, line);
                        c.drawLine(cx + mr.cartDir * 16f * sc, (y0 + y1) / 2f, cx, y1 - 6f * sc, line);
                    }
                }
            }
            if (!warn) drawCart(c, mr, sc, ox, oy, t);
        }
    }

    private void drawCart(Canvas c, MinesRules mr, float sc, float ox, float oy, float t) {
        float x = mr.cartX * sc + ox, y = oy, hw = MinesRules.CART_LEN * 0.5f * sc, hh = 34f * sc;
        fill.setColor(0xFF1A1512); // shadow
        c.drawRect(x - hw, y + hh * 0.5f, x + hw, y + hh, fill);
        fill.setColor(0xFF6A4A2E); // body
        c.drawRect(x - hw, y - hh * 0.8f, x + hw, y + hh * 0.7f, fill);
        fill.setColor(0xFF8F6840);
        c.drawRect(x - hw, y - hh * 0.8f, x + hw, y - hh * 0.4f, fill);
        fill.setColor(0xFF2A2420); // ore load and wheels
        c.drawRect(x - hw * 0.8f, y - hh * 1.05f, x + hw * 0.8f, y - hh * 0.7f, fill);
        fill.setColor(0xFF111111);
        c.drawCircle(x - hw * 0.55f, y + hh * 0.75f, hh * 0.28f, fill);
        c.drawCircle(x + hw * 0.55f, y + hh * 0.75f, hh * 0.28f, fill);
        line.setStrokeWidth(Math.max(2f, 2f * sc));
        line.setColor(0xCCFFC060); // sparks off the rails behind
        for (int i = 0; i < 4; i++) {
            float sx = x - mr.cartDir * (hw + (i * 9 + (t * 90f) % 9f) * sc);
            c.drawLine(sx, y + hh * 0.9f, sx - mr.cartDir * 6f * sc, y + hh * (0.6f + 0.1f * i), line);
        }
    }

    @Override
    public void minimap(Canvas c, Sim s, float cx, float cy, float kk, float side, float u) {
        if (!(s.rules instanceof MinesRules) || !s.rules.addonsEnabled) return;
        MinesRules mr = (MinesRules) s.rules;
        float lim = side / 2 - 8f * u;
        float ly = Math.max(-lim, Math.min(lim, (0f - s.py) * kk));
        if (mr.cartOn) { // the lane strip, bright while it warns
            boolean warn = mr.cartT < MinesRules.CART_WARN;
            float pulse = 0.5f + 0.5f * (float) Math.sin(mr.cartT * 14f);
            fill.setColor(((int) (warn ? 90 + 90 * pulse : 70) << 24) | 0xFF5A30);
            float half = MinesRules.CART_HALF_W * kk;
            c.drawRect(cx - lim, cy + ly - Math.max(half, 2f * u), cx + lim, cy + ly + Math.max(half, 2f * u), fill);
            if (!mr.cartOn) return;
            float mx = cx + Math.max(-lim, Math.min(lim, (mr.cartX - s.px) * kk));
            if (!warn) {
                fill.setColor(0xFFFFB347);
                c.drawCircle(mx, cy + ly, 6f * u, fill);
            }
        }
        for (int i = 0; i < mr.pocketN; i++) {
            float dx = (mr.pocketX[i] - s.px) * kk, dy = (mr.pocketY[i] - s.py) * kk;
            if (Math.abs(dx) > lim || Math.abs(dy) > lim) continue;
            boolean lit = mr.pocketFuse[i] >= 0f;
            fill.setColor(lit ? 0xFFFF5A30 : 0xFFE8D040);
            c.drawCircle(cx + dx, cy + dy, (lit ? 7f : 5f) * u, fill);
        }
    }
}
