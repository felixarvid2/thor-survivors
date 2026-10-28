package se.felixarvid.thorsurvivors.render.stagefx;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.stage.FlatsRules;
import se.felixarvid.thorsurvivors.render.WorldRenderer;

/**
 * Cinder Flats: the falling ash and the storm haze and banner (the drawing itself stays in {@link WorldRenderer#drawAsh}, unchanged),
 * and the chunk E add-ons: the lightning rings and flash and wind streaks of a storm, the convoy's route and marker, the Lumen Blooms
 * with their harvest arc, and the minimap markers of the convoy and the blooms. Nothing here allocates (own Paints, no shaders).
 */
final class FlatsFx implements StageFx {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    FlatsFx() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void drawGround(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        if (!(s.rules instanceof FlatsRules) || !s.rules.addonsEnabled) return;
        FlatsRules fr = (FlatsRules) s.rules;
        float sc = h / Sim.VIEW_H, t = (float) s.time;
        for (int i = 0; i < fr.bloomN; i++) {
            float x = (fr.bloomX[i] - s.px) * sc + w / 2f, y = (fr.bloomY[i] - s.py) * sc + h / 2f;
            if (x < -300 || x > w + 300 || y < -300 || y > h + 300) continue;
            float pulse = 0.6f + 0.4f * (float) Math.sin(t * 2.4f + i);
            float rr = FlatsRules.BLOOM_R * sc;
            for (int k = 3; k >= 1; k--) { // a soft glow: three stacked discs
                fill.setColor(((int) ((10 + 10 * pulse) * (4 - k)) << 24) | 0x39FF88);
                c.drawCircle(x, y, rr * (0.55f + 0.3f * k), fill);
            }
            line.setStrokeWidth(Math.max(2f, 2.5f * sc));
            line.setColor(((int) (120 + 80 * pulse) << 24) | 0x39FF88);
            c.drawCircle(x, y, rr, line);
            if (fr.bloomNear == i && fr.bloomHold > 0f) { // the harvest arc
                box.set(x - rr * 1.15f, y - rr * 1.15f, x + rr * 1.15f, y + rr * 1.15f);
                line.setStrokeWidth(Math.max(4f, 6f * sc));
                line.setColor(0xFFFFFFFF);
                c.drawArc(box, -90f, 360f * Math.min(1f, fr.bloomHold / FlatsRules.BLOOM_HOLD), false, line);
            }
        }
        // the convoy's route ahead of the tanker
        if (fr.tanker != null) {
            float x = (fr.tanker.x - s.px) * sc + w / 2f, y = (fr.tanker.y - s.py) * sc + h / 2f;
            line.setStrokeWidth(Math.max(2f, 3f * sc));
            line.setColor(0x55FFB347);
            c.drawLine(x, y, x + fr.tankDx * 900f * sc, y + fr.tankDy * 900f * sc, line);
        }
    }

    @Override
    public void drawOver(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        r.drawAsh(c, s, w, h);
        if (!(s.rules instanceof FlatsRules) || !s.rules.addonsEnabled) return;
        FlatsRules fr = (FlatsRules) s.rules;
        float sc = h / Sim.VIEW_H, t = (float) s.time;
        float storm = s.stormLevel();
        if (storm > 0f) { // wind streaks along the drift
            line.setStrokeWidth(Math.max(1.5f, 2f * sc));
            for (int i = 0; i < 26; i++) {
                float u = (i * 0.6180339f + t * 0.35f * (0.6f + (i % 5) * 0.12f)) % 1f;
                float yy = ((i * 0.381966f) % 1f) * h;
                float xx = ((u + fr.windX * 0.0f) * (w + 400f)) - 200f;
                float len = 70f * sc * (0.6f + 0.4f * ((i * 7) % 3));
                line.setColor(((int) (46 * storm) << 24) | 0xD8D0C0);
                float dx = fr.windX * len, dy = fr.windY * len;
                float ox = fr.windX >= 0 ? xx : w - xx;
                c.drawLine(ox, yy, ox + dx, yy + dy, line);
            }
        }
        for (int i = 0; i < fr.strikeN; i++) { // the warning rings fill as the strike nears
            float x = (fr.strikeX[i] - s.px) * sc + w / 2f, y = (fr.strikeY[i] - s.py) * sc + h / 2f;
            float k = 1f - Math.max(0f, fr.strikeT[i]) / FlatsRules.STRIKE_WARN;
            float rr = FlatsRules.STRIKE_R * sc;
            fill.setColor(((int) (30 + 70 * k) << 24) | 0xBFD8FF);
            c.drawCircle(x, y, rr * k, fill);
            line.setStrokeWidth(Math.max(2f, 3f * sc));
            line.setColor(((int) (140 + 100 * k) << 24) | 0xE8F4FF);
            c.drawCircle(x, y, rr, line);
        }
        if (fr.sinceStrike < 0.25f) { // the bolt and a flash of the whole frame
            float k = 1f - fr.sinceStrike / 0.25f;
            float x = (fr.lastStrikeX - s.px) * sc + w / 2f, y = (fr.lastStrikeY - s.py) * sc + h / 2f;
            fill.setColor(((int) (70 * k) << 24) | 0xE8F4FF);
            c.drawRect(0, 0, w, h, fill);
            line.setStrokeWidth(Math.max(3f, 5f * sc) * k + 1f);
            line.setColor(((int) (255 * k) << 24) | 0xFFFFFF);
            float px = x;
            float py = y;
            for (int i = 1; i <= 5; i++) { // a jagged bolt from above
                float nx = x + (((i * 37) % 11) - 5) * 4f * sc * (6 - i) / 5f, ny = y - i * (h * 0.22f);
                c.drawLine(px, py, nx, ny, line);
                px = nx;
                py = ny;
            }
        }
    }

    @Override
    public void minimap(Canvas c, Sim s, float cx, float cy, float kk, float side, float u) {
        if (!(s.rules instanceof FlatsRules) || !s.rules.addonsEnabled) return;
        FlatsRules fr = (FlatsRules) s.rules;
        float lim = side / 2 - 12f * u;
        for (int i = 0; i < fr.bloomN; i++) {
            float dx = (fr.bloomX[i] - s.px) * kk, dy = (fr.bloomY[i] - s.py) * kk;
            if (Math.abs(dx) > lim || Math.abs(dy) > lim) continue;
            fill.setColor(0xFF39FF88);
            c.drawCircle(cx + dx, cy + dy, 6f * u, fill);
            fill.setColor(0xFF05080A);
            c.drawCircle(cx + dx, cy + dy, 2.5f * u, fill);
        }
        if (fr.tanker != null) { // the tanker: an amber square clamped to the edge, with its heading
            float dx = Math.max(-lim, Math.min(lim, (fr.tanker.x - s.px) * kk)), dy = Math.max(-lim, Math.min(lim, (fr.tanker.y - s.py) * kk));
            float pulse = 0.8f + 0.2f * (float) Math.sin(s.time * 6.0);
            fill.setColor(0xFFFFB347);
            c.drawRect(cx + dx - 9f * u * pulse, cy + dy - 9f * u * pulse, cx + dx + 9f * u * pulse, cy + dy + 9f * u * pulse, fill);
            line.setStrokeWidth(3.5f * u);
            line.setColor(0xFFFFB347);
            c.drawLine(cx + dx, cy + dy, cx + dx + fr.tankDx * 30f * u, cy + dy + fr.tankDy * 30f * u, line);
        }
    }
}
