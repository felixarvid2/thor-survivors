package se.felixarvid.thorsurvivors.render.stagefx;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import se.felixarvid.thorsurvivors.game.Sim;
import se.felixarvid.thorsurvivors.game.WorldGen;
import se.felixarvid.thorsurvivors.game.layout.DomesLayout;
import se.felixarvid.thorsurvivors.game.stage.HydroRules;
import se.felixarvid.thorsurvivors.render.WorldRenderer;

/**
 * Hydroponic Vaults: the flooded domes, the growth patches round the Bloom Hearts (and the welder's sprouts), tanker floods, spore
 * clouds, the Gardener's pulse rings, the Purge Terminal, the spore motes of the air and the Rootmind's shield. Minimap: hearts with
 * their patch, the terminal, the finale. Every ground ring is an ellipse (0.6 high) like the other rings of the game; nothing here
 * allocates per frame (the paints and the rectangle are made once).
 */
final class HydroFx implements StageFx {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rc = new RectF();
    private static final float SQUASH = 0.6f;

    private static HydroRules rules(Sim s) {
        return s.rules instanceof HydroRules ? (HydroRules) s.rules : null;
    }

    private void disc(Canvas c, int cx, int cy, float r, int argb) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(argb);
        c.drawOval(cx - r, cy - r * SQUASH, cx + r, cy + r * SQUASH, fill);
    }

    private void ring(Canvas c, int cx, int cy, float r, float width, int argb) {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(width);
        line.setColor(argb);
        c.drawOval(cx - r, cy - r * SQUASH, cx + r, cy + r * SQUASH, line);
    }

    private static int alpha(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public void drawGround(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        HydroRules hr = rules(s);
        float sc = r.fxScale(), ks = r.fxK();
        float t = r.fxNow();
        int ox = r.fxX(0f), oy = r.fxY(0f);
        // flooded domes in view
        float cellU = DomesLayout.CELL * WorldGen.TILE;
        int cx0 = (int) Math.floor(((0 - ox) / sc - cellU) / cellU), cx1 = (int) Math.floor(((w - ox) / sc + cellU) / cellU);
        int cy0 = (int) Math.floor(((0 - oy) / sc - cellU) / cellU), cy1 = (int) Math.floor(((h - oy) / sc + cellU) / cellU);
        for (int j = cy0; j <= cy1; j++) {
            for (int i = cx0; i <= cx1; i++) {
                if (!DomesLayout.flooded(i, j)) continue;
                int px = r.fxX(DomesLayout.centerX(i) * WorldGen.TILE), py = r.fxY(DomesLayout.centerX(j) * WorldGen.TILE);
                float rad = DomesLayout.WATER_R * WorldGen.TILE * sc;
                disc(c, px, py, rad, 0x5A2A8FA8);
                disc(c, px, py, rad * 0.8f, 0x302A8FA8);
                float rip = (t * 0.35f + i * 0.3f + j * 0.17f) % 1f;
                ring(c, px, py, rad * (0.3f + 0.65f * rip), 2f * ks, alpha((int) (110 * (1f - rip)), 0x9FE4F4));
                ring(c, px, py, rad, 3f * ks, 0xAA6FC8E0);
            }
        }
        if (hr == null) return;
        for (int k = 0; k < hr.flN; k++) {
            int px = r.fxX(hr.flX[k]), py = r.fxY(hr.flY[k]);
            float f = Math.min(1f, hr.flT[k] / 2f), rad = HydroRules.FLOOD_R * sc;
            disc(c, px, py, rad, alpha((int) (80 * f), 0x4FB4E8));
            ring(c, px, py, rad, 3f * ks, alpha((int) (170 * f), 0x9FE4F4));
            float rip = (t * 0.6f + k * 0.4f) % 1f;
            ring(c, px, py, rad * rip, 2f * ks, alpha((int) (120 * (1f - rip) * f), 0xFFFFFF));
        }
        for (int k = 0; k < hr.trN; k++) { // sprouts and fading patches
            float rad = hr.trRadius(k) * sc;
            if (rad < 2f) continue;
            int px = r.fxX(hr.tx[k]), py = r.fxY(hr.ty[k]);
            disc(c, px, py, rad, 0x2E39FF88);
            ring(c, px, py, rad, 2.5f * ks, 0x9939FF88);
        }
        for (int k = 0; k < hr.heartN; k++) {
            int px = r.fxX(hr.hx[k]), py = r.fxY(hr.hy[k]);
            float rad = hr.heartR(k) * sc, pu = 0.5f + 0.5f * (float) Math.sin(t * 2.2 + k);
            disc(c, px, py, rad, alpha(38 + (int) (14 * pu), 0x39FF88));
            disc(c, px, py, rad * 0.62f, alpha(26 + (int) (12 * pu), 0x7CFFB0));
            ring(c, px, py, rad, 3f * ks, alpha(150 + (int) (60 * pu), 0x39FF88));
            ring(c, px, py, rad * (0.55f + 0.2f * pu), 1.5f * ks, alpha(90, 0xB8FFD0));
            if (hr.hBurn[k] > 0f) ring(c, px, py, 34f * sc * (1f + 0.15f * pu), 3f * ks, 0xCCFF8A2A);
        }
        for (int k = 0; k < hr.scN; k++) { // spore clouds
            int px = r.fxX(hr.scX[k]), py = r.fxY(hr.scY[k]);
            float f = Math.min(1f, hr.scT[k]), rad = HydroRules.CLOUD_R * sc * (0.85f + 0.15f * (float) Math.sin(t * 4 + k));
            disc(c, px, py, rad, alpha((int) (70 * f), 0xA8E040));
            ring(c, px, py, rad, 2f * ks, alpha((int) (150 * f), 0xD8F870));
        }
        for (int k = 0; k < hr.fdN; k++) { // feeder decay clouds
            int px = r.fxX(hr.fdX[k]), py = r.fxY(hr.fdY[k]);
            float f = Math.min(1f, hr.fdT[k]);
            disc(c, px, py, HydroRules.FEEDER_R * sc, alpha((int) (60 * f), 0x7CE04A));
            ring(c, px, py, HydroRules.FEEDER_R * sc, 2f * ks, alpha((int) (140 * f), 0xB8F070));
        }
        for (int k = 0; k < hr.gpN; k++) { // gardener pulse rings
            int px = r.fxX(hr.gpX[k]), py = r.fxY(hr.gpY[k]);
            float f = 1f - hr.gpT[k] / 0.5f;
            ring(c, px, py, HydroRules.GARDEN_R * sc * f, 3f * ks, alpha((int) (210 * (1f - f)), 0x8CFF9C));
            disc(c, px, py, HydroRules.GARDEN_R * sc * f, alpha((int) (40 * (1f - f)), 0x8CFF9C));
        }
        if (hr.purgeOn) {
            int px = r.fxX(hr.purgeX), py = r.fxY(hr.purgeY);
            float rad = HydroRules.PURGE_R * sc, pu = 0.5f + 0.5f * (float) Math.sin(t * 3.0);
            disc(c, px, py, rad, alpha(30 + (int) (20 * pu), 0x4FD8FF));
            ring(c, px, py, rad, 3f * ks, alpha(170, 0x4FD8FF));
            float f = hr.purgeProg / HydroRules.PURGE_NEED;
            ring(c, px, py, rad * f, 2f * ks, alpha(220, 0xFFFFFF));
            fill.setStyle(Paint.Style.FILL); // the terminal: a post with a lit head and a beam
            fill.setColor(0xFF1E2A30);
            c.drawRect(px - 9f * ks, py - 44f * ks, px + 9f * ks, py, fill);
            fill.setColor(alpha(200 + (int) (55 * pu), 0x4FD8FF));
            c.drawRect(px - 7f * ks, py - 40f * ks, px + 7f * ks, py - 28f * ks, fill);
            fill.setColor(alpha(60 + (int) (40 * pu), 0x4FD8FF));
            c.drawRect(px - 3f * ks, py - 300f * ks, px + 3f * ks, py - 44f * ks, fill);
        }
        if (hr.rmOn) { // the Rootmind: tethers to its shield hearts
            int bx = r.fxX(hr.rmBossX), by = r.fxY(hr.rmBossY);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(3f * ks);
            for (int k = 0; k < HydroRules.SHIELDS; k++) {
                if (!hr.rmUp[k]) continue;
                line.setColor(alpha(150, 0x39FF88));
                c.drawLine(bx, by - 30f * ks, r.fxX(hr.rmX[k]), r.fxY(hr.rmY[k]) - 14f * ks, line);
            }
            ring(c, bx, by, HydroRules.PATCH_MAX * sc, 2f * ks, alpha(80, 0x39FF88));
        }
    }

    @Override
    public void drawOver(WorldRenderer r, Canvas c, Sim s, int w, int h) {
        HydroRules hr = rules(s);
        float ks = r.fxK(), t = r.fxNow();
        boolean weather = hr != null && hr.sporefallOn();
        int n = weather ? 70 : 22; // the spores in the air: a few always, a shower in the Sporefall
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float sp = 14f + (i * 37 % 23), sway = (float) Math.sin(t * 0.7 + i) * 18f;
            float x = ((i * 211 + 40f * i) % w) + sway, y = (i * 97 + t * sp * (weather ? 5f : 1f) * ks * 3f) % (h + 40) - 20f;
            fill.setColor(alpha(weather ? 150 : 90, i % 3 == 0 ? 0xD8F870 : 0x7CE0A0));
            c.drawCircle(x, y, (weather ? 3.2f : 2.2f) * ks, fill);
        }
        if (hr == null) return;
        if (hr.bloomOn()) {
            fill.setColor(alpha(22, 0x39FF88));
            c.drawRect(0, 0, w, h, fill);
        }
        for (int k = 0; k < hr.heartN; k++) { // heart health
            if (hr.hHp[k] >= hr.hMax[k]) continue;
            int px = r.fxX(hr.hx[k]), py = r.fxY(hr.hy[k]);
            if (px < -100 || px > w + 100 || py < -200 || py > h + 100) continue;
            float bw = 54f * ks, y = py - 92f * ks, f = Math.max(0f, hr.hHp[k] / hr.hMax[k]);
            fill.setColor(0xCC000000);
            c.drawRect(px - bw / 2 - 2f * ks, y - 2f * ks, px + bw / 2 + 2f * ks, y + 7f * ks, fill);
            fill.setColor(0xFF39FF88);
            c.drawRect(px - bw / 2, y, px - bw / 2 + bw * f, y + 5f * ks, fill);
        }
        if (hr.rmOn) {
            int bx = r.fxX(hr.rmBossX), by = r.fxY(hr.rmBossY);
            float pu = 0.5f + 0.5f * (float) Math.sin(t * 4.0);
            if (hr.rmMul >= 1f) { // exposed: a golden ring round it
                ring(c, bx, Math.round(by - 20f * ks), 110f * ks, 4f * ks, alpha(180 + (int) (60 * pu), 0xFFD84A));
            } else { // shielded: a dim teal dome
                ring(c, bx, Math.round(by - 20f * ks), 110f * ks, 3f * ks, alpha(90 + (int) (50 * pu), 0x39FFC8));
            }
            for (int k = 0; k < HydroRules.SHIELDS; k++) { // shield hearts waiting to grow back
                if (hr.rmUp[k] || hr.rmT[k] <= 0f) continue;
                int px = r.fxX(hr.rmX[k]), py = r.fxY(hr.rmY[k]);
                ring(c, px, py, 22f * ks, 2f * ks, alpha(140, 0x39FF88));
                ring(c, px, py, 22f * ks * (1f - Math.min(1f, hr.rmT[k] / 20f)), 2f * ks, alpha(220, 0xB8FFD0));
            }
        }
    }

    private float edge(float v, float lim) {
        return Math.max(-lim, Math.min(lim, v));
    }

    @Override
    public void minimap(Canvas c, Sim s, float cx, float cy, float kk, float side, float u) {
        HydroRules hr = rules(s);
        if (hr == null) return;
        float lim = side / 2 - 14f * u;
        for (int k = 0; k < hr.flN; k++) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x554FB4E8);
            c.drawCircle(cx + (hr.flX[k] - s.px) * kk, cy + (hr.flY[k] - s.py) * kk, HydroRules.FLOOD_R * kk, fill);
        }
        float pulse = 0.8f + 0.2f * (float) Math.sin(s.time * 5.0);
        for (int k = 0; k < hr.heartN; k++) {
            float dx = (hr.hx[k] - s.px) * kk, dy = (hr.hy[k] - s.py) * kk;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x2E39FF88);
            c.drawCircle(cx + dx, cy + dy, hr.heartR(k) * kk, fill);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2f * u);
            line.setColor(0x9939FF88);
            c.drawCircle(cx + dx, cy + dy, hr.heartR(k) * kk, line);
            float ex = edge(dx, lim), ey = edge(dy, lim);
            fill.setColor(hr.hBurn[k] > 0f ? 0xFFFF8A2A : 0xFF39FF88);
            c.drawCircle(cx + ex, cy + ey, (ex != dx || ey != dy ? 6f : 8f) * u * pulse, fill);
        }
        for (int k = 0; k < hr.trN; k++) {
            if (hr.tType[k] != 0) continue;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x4439FF88);
            c.drawCircle(cx + (hr.tx[k] - s.px) * kk, cy + (hr.ty[k] - s.py) * kk, hr.trRadius(k) * kk, fill);
        }
        if (hr.purgeOn) {
            float ex = edge((hr.purgeX - s.px) * kk, lim), ey = edge((hr.purgeY - s.py) * kk, lim), z = 18f * u * pulse;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(3f * u);
            line.setColor(0xFF4FD8FF);
            c.drawCircle(cx + ex, cy + ey, z, line);
            rc.set(cx + ex - z, cy + ey - z, cx + ex + z, cy + ey + z);
            line.setStrokeWidth(5f * u);
            c.drawArc(rc, -90f, 360f * Math.max(0.02f, hr.purgeProg / HydroRules.PURGE_NEED), false, line);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFF4FD8FF);
            c.drawCircle(cx + ex, cy + ey, z * 0.35f, fill);
        }
        if (hr.rmOn) { // the finale: a diamond, gold while it can be hurt, plus its shield hearts
            float ex = edge((hr.rmBossX - s.px) * kk, lim), ey = edge((hr.rmBossY - s.py) * kk, lim), z = 14f * u * pulse;
            fill.setStyle(Paint.Style.FILL);
            for (int k = 0; k < HydroRules.SHIELDS; k++) {
                if (!hr.rmUp[k]) continue;
                fill.setColor(0xFF39FF88);
                c.drawCircle(cx + (hr.rmX[k] - s.px) * kk, cy + (hr.rmY[k] - s.py) * kk, 4f * u, fill);
            }
            fill.setColor(hr.rmMul >= 1f ? 0xFFFFD84A : 0xFF39FFC8);
            c.save();
            c.rotate(45f, cx + ex, cy + ey);
            c.drawRect(cx + ex - z * 0.7f, cy + ey - z * 0.7f, cx + ex + z * 0.7f, cy + ey + z * 0.7f, fill);
            c.restore();
        }
    }
}
