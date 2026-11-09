package se.felixarvid.thorsurvivors.ui;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The ASHFALL look of the panels and the top-screen overlays (design section 11): dark steel plates with a brushed grain,
 * rivets, hazard-stripe dividers, orange accents, metal bars and the condensed typeface. Pure drawing helpers on one set of
 * reused paints, so a frame allocates nothing. One instance per renderer (paints are not thread safe); the textures are shared
 * through {@link UiArt}.
 */
public final class Skin {
    public static final int BASE = 0xFF15110F, PLATE_LIFT = 0x1CFFE2C8, EDGE = 0xFF4A3F36, EDGE_LO = 0xFF2A231E, INK = 0xFF0A0807,
            ORANGE = 0xFFFF8A2A, ORANGE_DIM = 0xFF8A4A1A, LUMEN = 0xFF39FF88, LUMEN_DARK = 0xFF117A3C, DANGER = 0xFFE0402A,
            DANGER_DARK = 0xFF4A130C, GOLD = 0xFFFFD84A, TEXT = 0xFFEFE8DC, DIM = 0xFFA89B8C, GOOD = 0xFF5CF08E, BAD = 0xFFFF6A52,
            COOL = 0xFF40D8FF;

    /** Glyph kinds: the six elite modifiers in {@code Elites} order, then the hero statuses that are not one of them. */
    public static final int G_FIRE = 0, G_FROST = 1, G_BARRAGE = 2, G_VOLCANO = 3, G_RAISER = 4, G_ARMORED = 5, G_DECAY = 6, G_BOLT = 7,
            G_DASH = 8, G_LINE = 9, G_RING = 10, // v1.5 B2: the dash status, and the line / area attack of the boss panel
            G_SHIELD = 11, G_SPLIT = 12, G_BLAST = 13; // v1.5 B6: elite modifiers Shielded, Splitter, Volatile

    public final UiArt art = UiArt.get();
    public final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final RectF r = new RectF();
    public final Path path = new Path();
    /** The scale of the frame: 1 at 1080 px height. Set by the owner each frame. */
    public float u = 1f;
    /**
     * v1.5 B2 item 19: large-text and high-contrast mode, set by the owner each frame. Text +20 %, a 2 px dark outline, and key
     * text lightened to at least 4.5:1 against the steel. Off by default.
     */
    public boolean large;
    public static final float LARGE_SCALE = 1.2f;
    public static final double MIN_CONTRAST = 4.5;
    private int ccIn, ccOut;

    private final Paint steel = new Paint(), stripe = new Paint(), stripeDim = new Paint(), vig = new Paint();
    private boolean shaders;
    private int vigW, vigH;

    public Skin() {
        tp.setTypeface(art.bold);
    }

    private void ensureShaders() {
        if (shaders || art.steelTile == null) return;
        shaders = true;
        steel.setShader(new BitmapShader(art.steelTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        stripe.setShader(new BitmapShader(art.stripeTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        stripeDim.setShader(new BitmapShader(art.stripeDimTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        tp.setTypeface(art.bold);
    }

    // ------------------------------------------------------------------ text

    public void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align a) {
        tp.setTypeface(art.bold);
        put(c, s, x, y, size, color, a);
    }

    /** Body text in the lighter weight. */
    public void body(Canvas c, String s, float x, float y, float size, int color, Paint.Align a) {
        tp.setTypeface(art.semi);
        put(c, s, x, y, size, color, a);
    }

    /** The drawing of every text call; the typeface is set by the caller. In large mode: +20 %, lightened colour, a 2 px outline. */
    private void put(Canvas c, String s, float x, float y, float size, int color, Paint.Align a) {
        tp.setTextAlign(a);
        if (!large) {
            tp.setTextSize(size);
            tp.setColor(color);
            c.drawText(s, x, y, tp);
            return;
        }
        tp.setTextSize(size * LARGE_SCALE);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(4f * Math.max(0.5f, u)); // 2 px each side of the glyph edge
        tp.setStrokeJoin(Paint.Join.ROUND);
        tp.setColor(0xFF000000);
        c.drawText(s, x, y, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(legible(color));
        c.drawText(s, x, y, tp);
    }

    /** The colour lightened until it reads at 4.5:1 on the steel (the last answer is remembered, the maths is not free). */
    public int legible(int color) {
        if (color != ccIn || ccOut == 0) {
            ccIn = color;
            ccOut = se.felixarvid.thorsurvivors.game.HudInfo.ensureContrast(color, BASE, MIN_CONTRAST);
        }
        return ccOut;
    }

    /** Heading text with a dark drop shadow (on top of paintings). */
    public void shadowed(Canvas c, String s, float x, float y, float size, int color, Paint.Align a) {
        tp.setShadowLayer(Math.max(2f, size * 0.06f), 0f, Math.max(2f, size * 0.05f), 0xE0000000);
        text(c, s, x, y, size, color, a);
        tp.clearShadowLayer();
    }

    /** Text shrunk (down to 70 %) so it fits in maxW. */
    public void fit(Canvas c, String s, float x, float y, float size, float maxW, int color, Paint.Align a) {
        tp.setTypeface(art.bold);
        tp.setTextSize(large ? size * LARGE_SCALE : size);
        float w = tp.measureText(s);
        if (w > maxW) size = Math.max(size * 0.7f, size * maxW / w);
        text(c, s, x, y, size, color, a);
    }

    /** Wrapped body text; returns the number of lines drawn. */
    public int wrap(Canvas c, String s, float x, float y, float maxW, float lineH, int maxLines, float size, int color) {
        tp.setTypeface(art.semi);
        if (large) {
            size *= LARGE_SCALE;
            lineH *= LARGE_SCALE;
            color = legible(color);
        }
        tp.setTextSize(size);
        tp.setColor(color);
        tp.setTextAlign(Paint.Align.LEFT);
        int lines = 0, pos = 0;
        while (pos < s.length() && lines < maxLines) {
            int n = tp.breakText(s, pos, s.length(), true, maxW, null);
            if (n <= 0) break;
            int end = pos + n;
            if (end < s.length()) {
                int sp = s.lastIndexOf(' ', end - 1);
                if (sp > pos) end = sp;
            }
            if (lines == maxLines - 1 && end < s.length()) {
                c.drawText(s, pos, end, x, y + lines * lineH, tp); // the cut-off line and its dots, drawn apart so nothing is concatenated
                c.drawText("...", x + tp.measureText(s, pos, end), y + lines * lineH, tp);
            } else {
                c.drawText(s, pos, end, x, y + lines * lineH, tp);
            }
            pos = end;
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
            lines++;
        }
        return lines;
    }

    // ------------------------------------------------------------------ surfaces

    /** The whole panel: brushed steel with a darker edge. */
    public void background(Canvas c, int w, int h) {
        ensureShaders();
        if (shaders) c.drawRect(0, 0, w, h, steel);
        else {
            fill.setColor(BASE);
            c.drawRect(0, 0, w, h, fill);
        }
        if (vigW != w || vigH != h) {
            vigW = w;
            vigH = h;
            float rad = (float) Math.sqrt((double) w * w + (double) h * h) * 0.55f;
            vig.setShader(new RadialGradient(w / 2f, h * 0.42f, rad, new int[]{0x00000000, 0x00000000, 0xCC000000},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        }
        c.drawRect(0, 0, w, h, vig);
    }

    /** Chamfered top-left and bottom-right corners: the plate outline. */
    private void chamfer(float l, float t, float rr, float b, float k) {
        path.reset();
        path.moveTo(l + k, t);
        path.lineTo(rr, t);
        path.lineTo(rr, b - k);
        path.lineTo(rr - k, b);
        path.lineTo(l, b);
        path.lineTo(l, t + k);
        path.close();
    }

    /** A raised steel plate. {@code tint} (ARGB, may be 0) is laid over the grain, {@code edge} is the outline colour. */
    public void plate(Canvas c, float l, float t, float rr, float b, int tint, int edge, float edgeW) {
        ensureShaders();
        float k = Math.min(14f * u, (b - t) * 0.25f);
        chamfer(l, t, rr, b, k);
        if (shaders) c.drawPath(path, steel);
        fill.setColor(PLATE_LIFT);
        c.drawPath(path, fill);
        if (tint != 0) {
            fill.setColor(tint);
            c.drawPath(path, fill);
        }
        // bevel: light along the top, dark along the bottom
        fill.setColor(0x33FFFFFF);
        c.drawRect(l + k, t + 1f, rr - 2f, t + 1f + Math.max(1f, 2f * u), fill);
        fill.setColor(0x88000000);
        c.drawRect(l + 2f, b - Math.max(1f, 3f * u), rr - k, b - 1f, fill);
        stroke.setColor(edge);
        stroke.setStrokeWidth(edgeW);
        c.drawPath(path, stroke);
    }

    public void plate(Canvas c, float l, float t, float rr, float b) {
        plate(c, l, t, rr, b, 0, EDGE, Math.max(2f, 2f * u));
    }

    /** A sunken dark field (bar backs, empty slots, pads). */
    public void well(Canvas c, float l, float t, float rr, float b, float radius) {
        r.set(l, t, rr, b);
        fill.setColor(INK);
        c.drawRoundRect(r, radius, radius, fill);
        stroke.setColor(EDGE_LO);
        stroke.setStrokeWidth(Math.max(2f, 2f * u));
        c.drawRoundRect(r, radius, radius, stroke);
    }

    public void rivet(Canvas c, float x, float y, float rad) {
        fill.setColor(0xFF0A0807);
        c.drawCircle(x, y + rad * 0.2f, rad * 1.1f, fill);
        fill.setColor(0xFF5A4E44);
        c.drawCircle(x, y, rad, fill);
        fill.setColor(0xFF9A8C7C);
        c.drawCircle(x - rad * 0.3f, y - rad * 0.3f, rad * 0.45f, fill);
    }

    public void rivets(Canvas c, float l, float t, float rr, float b, float inset, float rad) {
        rivet(c, l + inset, t + inset, rad);
        rivet(c, rr - inset, t + inset, rad);
        rivet(c, l + inset, b - inset, rad);
        rivet(c, rr - inset, b - inset, rad);
    }

    /** A hazard-stripe band (orange and black diagonals). */
    public void stripe(Canvas c, float l, float t, float rr, float b, boolean dim) {
        ensureShaders();
        if (shaders) c.drawRect(l, t, rr, b, dim ? stripeDim : stripe);
        else {
            fill.setColor(dim ? ORANGE_DIM : ORANGE);
            c.drawRect(l, t, rr, b, fill);
        }
        fill.setColor(0xAA000000); // inner shade so the band sits in the plate
        c.drawRect(l, b - Math.max(1f, 2f * u), rr, b, fill);
    }

    /** Divider: a hazard band with a dark line above and below. */
    public void divider(Canvas c, float l, float y, float rr) {
        fill.setColor(INK);
        c.drawRect(l, y - 2f * u, rr, y + 8f * u, fill);
        stripe(c, l, y, rr, y + 6f * u, true);
    }

    // ------------------------------------------------------------------ controls

    /** A metal bar: dark well, fill in two tones with a gloss line, segment gaps and a hot tip. */
    public void bar(Canvas c, float l, float t, float rr, float b, float frac, int top, int bottom, int segments) {
        float rad = 4f * u;
        well(c, l, t, rr, b, rad);
        frac = Math.max(0f, Math.min(1f, frac));
        float pad = Math.max(2f, 3f * u);
        float il = l + pad, it = t + pad, ir = rr - pad, ib = b - pad;
        if (frac > 0f) {
            float fx = il + (ir - il) * frac;
            r.set(il, it, fx, ib);
            fill.setColor(bottom);
            c.drawRoundRect(r, rad * 0.5f, rad * 0.5f, fill);
            r.set(il, it, fx, it + (ib - it) * 0.55f);
            fill.setColor(top);
            c.drawRoundRect(r, rad * 0.5f, rad * 0.5f, fill);
            r.set(il + 2f, it + 1f, Math.max(il + 2f, fx - 2f), it + Math.max(2f, (ib - it) * 0.2f));
            fill.setColor(0x44FFFFFF);
            c.drawRect(r, fill);
            if (frac < 1f) { // the hot leading edge
                fill.setColor(0xAA000000 | (top & 0xFFFFFF));
                c.drawRect(fx - Math.max(2f, 3f * u), it, fx, ib, fill);
            }
        }
        if (segments > 1) { // steel ribs between the segments
            fill.setColor(INK);
            float gw = Math.max(2f, 2.5f * u);
            for (int i = 1; i < segments; i++) {
                float x = il + (ir - il) * i / segments;
                c.drawRect(x - gw / 2f, it, x + gw / 2f, ib, fill);
            }
        }
    }

    public void pip(Canvas c, float x, float y, float s, boolean on, int color) {
        pip(c, x, y, 0f, on, color, s, s);
    }

    public void pip(Canvas c, float x, float y, float unused, boolean on, int color, float w, float s) {
        r.set(x, y, x + w, y + s);
        fill.setColor(on ? color : 0xFF2A231E);
        c.drawRect(r, fill);
        if (on) {
            fill.setColor(0x55FFFFFF);
            c.drawRect(x, y, x + w, y + Math.max(1f, s * 0.3f), fill);
        } else {
            stroke.setColor(0xFF000000);
            stroke.setStrokeWidth(1f);
            c.drawRect(r, stroke);
        }
    }

    /** A pushable steel button with a centred label. */
    public void button(Canvas c, float l, float t, float rr, float b, String label, boolean focus) {
        plate(c, l, t, rr, b, focus ? 0x40FF8A2A : 0, focus ? ORANGE : EDGE, focus ? Math.max(3f, 4f * u) : Math.max(2f, 2f * u));
        float size = Math.min((b - t) * 0.5f, 60f * u);
        text(c, label, (l + rr) / 2f, (t + b) / 2f + size * 0.34f, size, focus ? 0xFFFFF1DC : TEXT, Paint.Align.CENTER);
        if (focus) focusMarks(c, l, t, rr, b);
    }

    /** The marks of the focused control: orange corner brackets. */
    public void focusMarks(Canvas c, float l, float t, float rr, float b) {
        float k = Math.min(26f * u, (b - t) * 0.4f), w = Math.max(4f, 5f * u), o = 5f * u;
        fill.setColor(ORANGE);
        c.drawRect(l - o, t - o, l - o + k, t - o + w, fill);
        c.drawRect(l - o, t - o, l - o + w, t - o + k, fill);
        c.drawRect(rr + o - k, b + o - w, rr + o, b + o, fill);
        c.drawRect(rr + o - w, b + o - k, rr + o, b + o, fill);
    }

    /** An item slot: a dark tile; {@code frame} (an atlas key) goes over the icon by the caller. */
    public void slotWell(Canvas c, float x, float y, float s) {
        r.set(x, y, x + s, y + s);
        fill.setColor(0xFF0C0908);
        c.drawRoundRect(r, s * 0.1f, s * 0.1f, fill);
        stroke.setColor(0xFF2E2620);
        stroke.setStrokeWidth(Math.max(2f, 2f * u));
        c.drawRoundRect(r, s * 0.1f, s * 0.1f, stroke);
    }

    /**
     * v1.5 B2 item 11: the cooldown sweep of a weapon slot. The part of the wait still to go is a dark pie that shrinks clockwise
     * from 12 o'clock, with a bright leading edge; {@code ready} is 0..1 (1 draws nothing). Clipped to the slot, no allocation.
     */
    public void sweep(Canvas c, float x, float y, float s, float ready) {
        if (ready >= 1f) return;
        float cx = x + s / 2f, cy = y + s / 2f, rad = s * 0.75f;
        float start = -90f + 360f * ready;
        c.save();
        c.clipRect(x, y, x + s, y + s);
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        fill.setColor(0xB00A0807);
        c.drawArc(r, start, 360f * (1f - ready), true, fill);
        double a = Math.toRadians(start);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0xCCFFB060);
        stroke.setStrokeWidth(Math.max(2f, 2.5f * u));
        c.drawLine(cx, cy, cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, stroke);
        c.restore();
    }

    /** A steady ring round a slot: the weapon has no volley to wait for (auras, very fast weapons). */
    public void steadyRing(Canvas c, float x, float y, float s, int color) {
        r.set(x + 2f * u, y + 2f * u, x + s - 2f * u, y + s - 2f * u);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.max(2f, 3f * u));
        c.drawRoundRect(r, s * 0.1f, s * 0.1f, stroke);
    }

    // ------------------------------------------------------------------ glyphs

    /** A small vector glyph in a coloured disc: modifier and status icons (flame, flake, fan, mountain, up arrows, shield, drop). */
    public void glyph(Canvas c, int kind, float cx, float cy, float rad, int color) {
        fill.setColor(0xFF0C0908);
        c.drawCircle(cx, cy, rad, fill);
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.max(2f, rad * 0.14f));
        c.drawCircle(cx, cy, rad - stroke.getStrokeWidth() * 0.5f, stroke);
        float q = rad * 0.5f;
        fill.setColor(color);
        path.reset();
        switch (kind) {
            case G_FIRE:
                path.moveTo(cx, cy - q * 1.35f);
                path.quadTo(cx + q * 1.1f, cy - q * 0.1f, cx + q * 0.7f, cy + q * 0.7f);
                path.quadTo(cx, cy + q * 1.25f, cx - q * 0.7f, cy + q * 0.7f);
                path.quadTo(cx - q * 1.0f, cy, cx - q * 0.2f, cy - q * 0.5f);
                path.quadTo(cx - q * 0.1f, cy - q * 0.9f, cx, cy - q * 1.35f);
                c.drawPath(path, fill);
                break;
            case G_FROST:
                stroke.setStrokeWidth(Math.max(2f, rad * 0.12f));
                for (int i = 0; i < 3; i++) {
                    double a = Math.PI * i / 3.0 + 0.0;
                    float dx = (float) Math.cos(a) * q * 1.3f, dy = (float) Math.sin(a) * q * 1.3f;
                    c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, stroke);
                }
                break;
            case G_BARRAGE:
                stroke.setStrokeWidth(Math.max(2f, rad * 0.13f));
                c.drawLine(cx, cy + q, cx - q * 1.1f, cy - q, stroke);
                c.drawLine(cx, cy + q, cx, cy - q * 1.25f, stroke);
                c.drawLine(cx, cy + q, cx + q * 1.1f, cy - q, stroke);
                break;
            case G_VOLCANO:
                path.moveTo(cx - q * 1.3f, cy + q * 1.0f);
                path.lineTo(cx - q * 0.4f, cy - q * 0.6f);
                path.lineTo(cx + q * 0.4f, cy - q * 0.6f);
                path.lineTo(cx + q * 1.3f, cy + q * 1.0f);
                path.close();
                c.drawPath(path, fill);
                c.drawCircle(cx, cy - q * 1.0f, q * 0.3f, fill);
                break;
            case G_RAISER:
                for (int i = 0; i < 2; i++) {
                    float oy = i * q * 0.9f;
                    path.moveTo(cx, cy - q * 1.2f + oy);
                    path.lineTo(cx + q * 1.1f, cy - q * 0.1f + oy);
                    path.lineTo(cx + q * 0.55f, cy - q * 0.1f + oy);
                    path.lineTo(cx, cy - q * 0.6f + oy);
                    path.lineTo(cx - q * 0.55f, cy - q * 0.1f + oy);
                    path.lineTo(cx - q * 1.1f, cy - q * 0.1f + oy);
                    path.close();
                }
                c.drawPath(path, fill);
                break;
            case G_ARMORED:
                path.moveTo(cx - q * 1.0f, cy - q * 1.1f);
                path.lineTo(cx + q * 1.0f, cy - q * 1.1f);
                path.lineTo(cx + q * 1.0f, cy + q * 0.1f);
                path.lineTo(cx, cy + q * 1.3f);
                path.lineTo(cx - q * 1.0f, cy + q * 0.1f);
                path.close();
                c.drawPath(path, fill);
                break;
            case G_BOLT:
                path.moveTo(cx + q * 0.3f, cy - q * 1.3f);
                path.lineTo(cx - q * 0.7f, cy + q * 0.1f);
                path.lineTo(cx - q * 0.05f, cy + q * 0.1f);
                path.lineTo(cx - q * 0.3f, cy + q * 1.3f);
                path.lineTo(cx + q * 0.7f, cy - q * 0.2f);
                path.lineTo(cx + q * 0.05f, cy - q * 0.2f);
                path.close();
                c.drawPath(path, fill);
                break;
            case G_DASH: // two chevrons
                stroke.setStrokeWidth(Math.max(2f, rad * 0.16f));
                for (int i = 0; i < 2; i++) {
                    float ox = (i - 0.5f) * q * 0.9f;
                    path.moveTo(cx + ox - q * 0.35f, cy - q * 0.9f);
                    path.lineTo(cx + ox + q * 0.35f, cy);
                    path.lineTo(cx + ox - q * 0.35f, cy + q * 0.9f);
                }
                stroke.setStyle(Paint.Style.STROKE);
                c.drawPath(path, stroke);
                break;
            case G_LINE: // a lunge: a line with an arrow head
                stroke.setStrokeWidth(Math.max(2f, rad * 0.16f));
                c.drawLine(cx - q * 1.2f, cy, cx + q * 0.6f, cy, stroke);
                path.moveTo(cx + q * 1.3f, cy);
                path.lineTo(cx + q * 0.4f, cy - q * 0.7f);
                path.lineTo(cx + q * 0.4f, cy + q * 0.7f);
                path.close();
                c.drawPath(path, fill);
                break;
            case G_RING: // an area: a ring round a dot
                stroke.setStrokeWidth(Math.max(2f, rad * 0.16f));
                c.drawCircle(cx, cy, q * 1.1f, stroke);
                c.drawCircle(cx, cy, q * 0.3f, fill);
            case G_SHIELD: // v1.5 B6: a round shield with a bar across it
                c.drawCircle(cx, cy, q * 1.15f, fill);
                fill.setColor(0xFF0C0908);
                c.drawRect(cx - q * 1.2f, cy - q * 0.15f, cx + q * 1.2f, cy + q * 0.15f, fill);
                fill.setColor(color);
                break;
            case G_SPLIT: // v1.5 B6: one blob becoming two
                c.drawCircle(cx - q * 0.75f, cy, q * 0.65f, fill);
                c.drawCircle(cx + q * 0.75f, cy, q * 0.65f, fill);
                break;
            case G_BLAST: // v1.5 B6: a ring with a dot
                stroke.setStrokeWidth(Math.max(2f, rad * 0.14f));
                c.drawCircle(cx, cy, q * 1.15f, stroke);
                c.drawCircle(cx, cy, q * 0.4f, fill);
                break;
            default: // decay: a drop
                path.moveTo(cx, cy - q * 1.35f);
                path.quadTo(cx + q * 1.3f, cy + q * 0.3f, cx, cy + q * 1.2f);
                path.quadTo(cx - q * 1.3f, cy + q * 0.3f, cx, cy - q * 1.35f);
                c.drawPath(path, fill);
        }
    }
}
