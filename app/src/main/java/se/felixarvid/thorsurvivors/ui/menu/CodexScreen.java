package se.felixarvid.thorsurvivors.ui.menu;

import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;

import se.felixarvid.thorsurvivors.data.EnemyDef;
import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.WeaponDef;
import se.felixarvid.thorsurvivors.game.Codex;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.EnemyType;
import se.felixarvid.thorsurvivors.render.SpriteAtlas;
import se.felixarvid.thorsurvivors.ui.Skin;

/**
 * v1.5 B3 (item 25): the bestiary and codex. A grid (6 columns, 4 rows a page) per tab: enemies, weapons, passives. An entry that was
 * not met yet is a black silhouette with a question mark; the focused one shows its stats and the stage it was first met on in
 * the panel below. Strings are built when the focus or the tab changes, never per frame.
 */
public final class CodexScreen {
    private static final int ROWS = 4;
    private final SpriteAtlas atlas = SpriteAtlas.get();
    private final Paint sil = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint plain = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect(), dst = new Rect();
    private EnemyType[] types;
    private String[][] upper;
    // detail text cache
    private int dTab = -1, dFocus = -1, dSeen = -1;
    private String dName = "", dLine1 = "", dLine2 = "";
    private String progress = "", pageText;
    private int pageNo, pageOf;
    private int progSeen = -1;
    private final String[] tabText = new String[Codex.KINDS];
    private final int[] tabSeen = {-1, -1, -1};

    public CodexScreen() {
        ColorMatrix cm = new ColorMatrix(new float[]{
                0.05f, 0.05f, 0.05f, 0, 0,
                0.05f, 0.05f, 0.05f, 0, 0,
                0.05f, 0.05f, 0.05f, 0, 0,
                0, 0, 0, 1, 0});
        sil.setColorFilter(new ColorMatrixColorFilter(cm));
    }

    private void prepare(Engine eng) {
        if (types != null) return;
        Codex cx = eng.codex;
        types = new EnemyType[cx.size(Codex.ENEMY)];
        for (int i = 0; i < types.length; i++) types[i] = new EnemyType(eng.data.enemies.get(cx.key(Codex.ENEMY, i)));
        upper = new String[Codex.KINDS][];
        for (int kd = 0; kd < Codex.KINDS; kd++) {
            upper[kd] = new String[cx.size(kd)];
            for (int i = 0; i < upper[kd].length; i++) upper[kd][i] = nameOf(eng, kd, i).toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static String nameOf(Engine eng, int kind, int i) {
        String key = eng.codex.key(kind, i);
        if (kind == Codex.ENEMY) return eng.data.enemies.get(key).name;
        return eng.data.weapons.get(key).name;
    }

    private static String stageName(Engine eng, int kind, int i) {
        String sk = eng.codex.firstStageKey(kind, i);
        if (sk == null) return "";
        se.felixarvid.thorsurvivors.data.StageDef sd = eng.data.stages.get(sk);
        return sd == null ? sk : sd.name.toUpperCase(java.util.Locale.ROOT);
    }

    public void draw(Canvas c, int w, int h, Skin k, Engine eng, MenuHits hits) {
        prepare(eng);
        Codex cx = eng.codex;
        float u = k.u;
        float cw = Math.min(w * 0.94f, 1100f * u), x = (w - cw) / 2f;
        int seenAll = cx.seenCount();
        if (seenAll != progSeen) {
            progSeen = seenAll;
            progress = seenAll + " OF " + cx.total() + " DISCOVERED";
        }
        MenuUi.title(c, k, w, "CODEX", progress, u);
        int tab = eng.codexTab, n = cx.size(tab);
        // tabs
        float ty = 150f * u, th = 54f * u, tw = (cw - 2 * 10f * u) / 3f;
        for (int i = 0; i < Codex.KINDS; i++) {
            float l = x + i * (tw + 10f * u);
            int sn = cx.seenCount(i);
            if (sn != tabSeen[i]) {
                tabSeen[i] = sn;
                tabText[i] = Codex.TITLES[i] + "  " + sn + "/" + cx.size(i);
            }
            boolean on = i == tab;
            k.plate(c, l, ty, l + tw, ty + th, on ? 0x55FF8A2A : 0, on ? Skin.ORANGE : Skin.EDGE, on ? 4f * u : 2f * u);
            k.fit(c, tabText[i], l + tw / 2f, ty + th * 0.7f, 28f * u, tw - 20f * u, on ? 0xFFFFF1DC : Skin.DIM, Paint.Align.CENTER);
            hits.hit(l, ty, l + tw, ty + th, 1000 + i, 0);
        }
        // grid
        int page = Math.min(eng.focus, Math.max(0, n - 1)) / Engine.CODEX_PAGE;
        int pages = Math.max(1, (n + Engine.CODEX_PAGE - 1) / Engine.CODEX_PAGE);
        float gap = 12f * u, cell = Math.min((cw - 5 * gap) / Engine.CODEX_COLS, 126f * u);
        float gw = Engine.CODEX_COLS * cell + (Engine.CODEX_COLS - 1) * gap, gx = (w - gw) / 2f, gy = ty + th + 22f * u;
        int first = page * Engine.CODEX_PAGE;
        for (int j = 0; j < Engine.CODEX_PAGE; j++) {
            int i = first + j;
            if (i >= n) break;
            float cx0 = gx + (j % Engine.CODEX_COLS) * (cell + gap), cy0 = gy + (j / Engine.CODEX_COLS) * (cell + gap);
            boolean seen = cx.seen(tab, i), f = eng.focus == i;
            k.slotWell(c, cx0, cy0, cell);
            drawEntry(c, k, eng, tab, i, seen, cx0, cy0, cell);
            if (f) {
                k.stroke.setColor(Skin.ORANGE);
                k.stroke.setStrokeWidth(Math.max(3f, 5f * u));
                k.r.set(cx0, cy0, cx0 + cell, cy0 + cell);
                c.drawRoundRect(k.r, cell * 0.1f, cell * 0.1f, k.stroke);
            }
            hits.hit(cx0, cy0, cx0 + cell, cy0 + cell, i, 0);
        }
        float gb = gy + ROWS * (cell + gap) - gap;
        if (pages > 1) {
            if (pageText == null || pageNo != page + 1 || pageOf != pages) {
                pageNo = page + 1;
                pageOf = pages;
                pageText = "PAGE " + pageNo + " OF " + pages;
            }
            k.body(c, pageText, gx + gw, gb + 28f * u, 22f * u, Skin.DIM, Paint.Align.RIGHT);
        }
        // detail of the focused entry
        float dy = gb + 40f * u, db = h - 120f * u;
        if (db - dy > 70f * u) detail(c, k, eng, tab, Math.min(eng.focus, n), n, x, dy, x + cw, db, u);
        // back
        float by = h - 100f * u;
        boolean fb = eng.focus >= n;
        k.button(c, x, by, x + cw, by + 80f * u, "BACK", fb);
        hits.hit(x, by, x + cw, by + 80f * u, n, 0);
    }

    private void drawEntry(Canvas c, Skin k, Engine eng, int kind, int i, boolean seen, float x, float y, float s) {
        Paint p = seen ? plain : sil;
        boolean ok;
        String key = eng.codex.key(kind, i);
        if (kind == Codex.ENEMY) {
            ok = atlas.drawIcon(c, p, bossKey(key), x + s / 2, y + s / 2, s * 0.8f);
            if (!ok) ok = model(c, types[i].model, x + s / 2, y + s / 2, s * 0.78f, p);
        } else {
            ok = atlas.drawIcon(c, p, Icons.base(key), x + s / 2, y + s / 2, s * 0.76f);
        }
        if (!ok) {
            String t = seen ? upper[kind][i].substring(0, 1) : "";
            k.text(c, t, x + s / 2, y + s * 0.66f, s * 0.5f, Skin.TEXT, Paint.Align.CENTER);
        }
        if (!seen) k.text(c, "?", x + s / 2, y + s * 0.68f, s * 0.46f, 0xFF6F655A, Paint.Align.CENTER);
    }

    private final java.util.HashMap<String, String> bossKeys = new java.util.HashMap<>();

    private String bossKey(String key) {
        String v = bossKeys.get(key);
        if (v == null) {
            v = "boss_" + key;
            bossKeys.put(key, v);
        }
        return v;
    }

    /** First frame (facing the camera) of the model's idle, static or walk clip, fitted into a square. */
    private boolean model(Canvas c, String name, float cx, float cy, float size, Paint p) {
        SpriteAtlas.Model m = atlas.model(name);
        if (m == null) return false;
        SpriteAtlas.Clip cl = m.clip(SpriteAtlas.C_IDLE);
        if (cl == null) cl = m.clip(SpriteAtlas.C_STATIC);
        if (cl == null) cl = m.clip(SpriteAtlas.C_WALK);
        if (cl == null || cl.count == 0) return false;
        int w = cl.r[2], h = cl.r[3];
        if (w <= 0 || h <= 0) return false;
        float sc = size / Math.max(w, h);
        int dw = Math.max(1, Math.round(w * sc)), dh = Math.max(1, Math.round(h * sc));
        src.set(cl.r[0], cl.r[1], cl.r[0] + w, cl.r[1] + h);
        dst.set(Math.round(cx - dw / 2f), Math.round(cy - dh / 2f), Math.round(cx - dw / 2f) + dw, Math.round(cy - dh / 2f) + dh);
        c.drawBitmap(m.sheet, src, dst, p);
        return true;
    }

    private void detail(Canvas c, Skin k, Engine eng, int tab, int f, int n, float l, float t, float rr, float b, float u) {
        k.plate(c, l, t, rr, b);
        if (f >= n) {
            k.body(c, "SELECT AN ENTRY", (l + rr) / 2f, (t + b) / 2f + 8f * u, 26f * u, Skin.DIM, Paint.Align.CENTER);
            return;
        }
        Codex cx = eng.codex;
        int seen = cx.seen(tab, f) ? 1 : 0;
        if (tab != dTab || f != dFocus || seen != dSeen) {
            dTab = tab;
            dFocus = f;
            dSeen = seen;
            buildDetail(eng, tab, f, seen != 0);
        }
        k.shadowed(c, dName, l + 28f * u, t + 52f * u, 40f * u, seen != 0 ? 0xFFFFF4E6 : Skin.DIM, Paint.Align.LEFT);
        k.wrap(c, dLine1, l + 28f * u, t + 92f * u, rr - l - 56f * u, 28f * u, 2, 24f * u, 0xFFE2D8CA);
        k.body(c, dLine2, l + 28f * u, b - 18f * u, 22f * u, Skin.ORANGE, Paint.Align.LEFT);
    }

    private void buildDetail(Engine eng, int tab, int f, boolean seen) {
        if (!seen) {
            dName = "???";
            dLine1 = tab == Codex.ENEMY ? "Not met yet. Survive long enough to find it." : tab == Codex.WEAPON ? "Not found yet." : "Not found yet.";
            dLine2 = "";
            return;
        }
        String key = eng.codex.key(tab, f);
        dName = upper[tab][f];
        String st = stageName(eng, tab, f);
        dLine2 = st.isEmpty() ? "" : "FIRST MET: " + st;
        if (tab == Codex.ENEMY) {
            EnemyDef d = eng.data.enemies.get(key);
            String role = d.archetype >= 0 && d.archetype < EnemyDef.ARCH_NAMES.length ? EnemyDef.ARCH_NAMES[d.archetype] : "";
            dLine1 = "HP " + Math.round(d.maxHp) + "     SPEED " + Math.round(d.speed) + "     " + role
                    + (d.archetype == EnemyDef.ARCH_BOSS ? "     BOSS" : "");
        } else {
            WeaponDef d = eng.data.weapons.get(key);
            dLine1 = d.description == null ? "" : d.description;
        }
    }
}
