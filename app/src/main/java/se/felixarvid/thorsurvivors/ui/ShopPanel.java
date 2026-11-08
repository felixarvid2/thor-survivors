package se.felixarvid.thorsurvivors.ui;

import android.graphics.Canvas;
import android.graphics.Paint;

import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.PowerUpDef;
import se.felixarvid.thorsurvivors.game.Achievements;
import se.felixarvid.thorsurvivors.game.Describe;
import se.felixarvid.thorsurvivors.game.Engine;
import se.felixarvid.thorsurvivors.game.MetaProgress;
import se.felixarvid.thorsurvivors.game.RunMods;
import se.felixarvid.thorsurvivors.game.ShopCatalog;
import se.felixarvid.thorsurvivors.render.SpriteAtlas;

/**
 * v1.5 B4: the salvage terminal in four categories (Stats, Run, Cosmetics, Awards): tabs on top, a page of 2 x 4 cards (each one owned,
 * affordable, too dear, maxed or locked), the total gold invested, a REFUND toggle (100% back, for respecs) and a detail strip. Hit ids:
 * the entries 0..n-1, n BACK, n+1 REFUND, n+2 PREV, n+3 NEXT, n+4.. the tabs. Nothing is built while drawing: texts are made once or cached
 * until their numbers change.
 */
final class ShopPanel {
    /** The panel's way to register a touch region and to draw an atlas icon. */
    interface Host {
        void hit(float l, float t, float r, float b, int idx, int sub);
    }

    private static final int GOLD = Skin.GOLD, DIM = Skin.DIM, TEXT = Skin.TEXT, LOCK = 0xFF5A524C;

    private final Skin k;
    private final SpriteAtlas atlas;
    private final Host host;
    private final Engine eng = Engine.get();

    // text caches (own slots): per card two numbers, plus the header and strip
    private final String[] csStr = new String[256];
    private final int[] csA = new int[256], csB = new int[256];
    private String[] nameC, subC;

    ShopPanel(Skin k, SpriteAtlas atlas, Host host) {
        this.k = k;
        this.atlas = atlas;
        this.host = host;
    }

    private String cs(int slot, String pre, int a, String mid, int b, String post) {
        String c = csStr[slot];
        if (c == null || csA[slot] != a || csB[slot] != b) {
            c = pre + a + mid + (mid.isEmpty() ? "" : b) + post;
            csStr[slot] = c;
            csA[slot] = a;
            csB[slot] = b;
        }
        return c;
    }

    private String cs(int slot, String pre, int a, String post) {
        return cs(slot, pre, a, "", 0, post);
    }

    /** Static name and sub line of each entry, made once. */
    private void texts() {
        if (nameC != null && nameC.length == eng.catalog.entries.size()) return;
        int n = eng.catalog.entries.size();
        nameC = new String[n];
        subC = new String[n];
        for (int i = 0; i < n; i++) {
            ShopCatalog.Entry e = eng.catalog.entries.get(i);
            switch (e.kind) {
                case ShopCatalog.K_POWER: {
                    PowerUpDef d = eng.data.powerUps.get(e.key);
                    Describe.PowerUpText pt = eng.shopText.get(e.key);
                    nameC[i] = d.name;
                    subC[i] = pt == null ? "" : pt.perRank;
                    break;
                }
                case ShopCatalog.K_REROLL:
                    nameC[i] = "REROLL CHARGES";
                    subC[i] = "+1 level-up reroll per run";
                    break;
                case ShopCatalog.K_BANISH:
                    nameC[i] = "BANISH CHARGES";
                    subC[i] = "+1 banish per run: strike a card for good";
                    break;
                case ShopCatalog.K_ASC: {
                    se.felixarvid.thorsurvivors.data.StageDef sd = eng.data.stages.get(e.key);
                    nameC[i] = "ASCENSION  " + (sd == null ? e.key : sd.name);
                    subC[i] = "Win to open the next tier";
                    break;
                }
                case ShopCatalog.K_DAILY:
                    nameC[i] = "DAILY CHALLENGE";
                    subC[i] = "Fixed seed, stage, hero and modifier";
                    break;
                case ShopCatalog.K_TINT: {
                    se.felixarvid.thorsurvivors.data.HeroDef hd = eng.data.heroes.get(e.key);
                    nameC[i] = (hd == null ? e.key : hd.name) + "  " + MetaProgress.tintName(e.key, e.idx);
                    subC[i] = e.idx == MetaProgress.TINTS - 1 ? "Win a stage with this hero, or buy" : "Hero colour";
                    break;
                }
                default:
                    nameC[i] = Achievements.NAME[e.idx];
                    subC[i] = Achievements.DESC[e.idx];
            }
        }
    }

    void draw(Canvas c, int w, int h, float x, float cw) {
        float u = k.u;
        texts();
        MetaProgress meta = eng.meta;
        ShopCatalog cat = eng.catalog;
        int n = eng.shopKeys.size();
        int pages = cat.pages();
        int page = eng.focus >= 0 && eng.focus < n ? cat.pageOf(eng.focus) : Math.min(pages - 1, eng.shopPage);
        int first = cat.pageStart[page], last = first + cat.pageLen[page], curCat = cat.pageCat[page];
        // header: title, what is invested, the gold in the pocket
        k.plate(c, x, 16f * u, x + cw, 150f * u, 0x28104030, Skin.EDGE, 3f * u);
        k.rivets(c, x, 16f * u, x + cw, 150f * u, 11f * u, 3f * u);
        k.text(c, "SALVAGE TERMINAL", x + 30f * u, 84f * u, 62f * u, Skin.ORANGE, Paint.Align.LEFT);
        k.body(c, cs(250, "INVESTED  ", meta.totalSpent(), " GOLD"), x + 30f * u, 124f * u, 24f * u, DIM, Paint.Align.LEFT);
        k.well(c, x + cw - 320f * u, 34f * u, x + cw - 24f * u, 134f * u, 6f * u);
        k.body(c, "GOLD", x + cw - 40f * u, 62f * u, 22f * u, DIM, Paint.Align.RIGHT);
        k.text(c, cs(251, "", meta.gold, ""), x + cw - 40f * u, 120f * u, 58f * u, GOLD, Paint.Align.RIGHT);
        // category tabs
        float tabY = 162f * u, tabH = 46f * u, tg = 8f * u, tw = (cw - 3 * tg) / ShopCatalog.CATS;
        for (int t = 0; t < ShopCatalog.CATS; t++) {
            float tx = x + t * (tw + tg);
            boolean on = t == curCat;
            k.plate(c, tx, tabY, tx + tw, tabY + tabH, on ? 0x55FF8A2A : 0x18000000, on ? Skin.ORANGE : Skin.EDGE, on ? 4f * u : 2f * u);
            k.text(c, ShopCatalog.CAT_NAME[t], tx + tw / 2f, tabY + tabH * 0.7f, 30f * u, on ? Skin.ORANGE : DIM, Paint.Align.CENTER);
            host.hit(tx, tabY, tx + tw, tabY + tabH, n + 4 + t, 0);
        }
        float stripH = 150f * u;
        float top = tabY + tabH + 12f * u, gap = 12f * u, bottom = h - 132f * u - stripH - gap;
        float rh = Math.min(136f * u, (bottom - top - 3 * gap) / 4f);
        float cwid = (cw - gap) / 2f;
        for (int i = first; i < last; i++) {
            int l = i - first;
            float cx = x + (l % 2) * (cwid + gap), cy = top + (l / 2) * (rh + gap);
            card(c, i, l, cx, cy, cx + cwid, cy + rh, u);
        }
        // detail strip
        float sy = bottom + gap;
        k.plate(c, x, sy, x + cw, sy + stripH, 0x28104030, Skin.EDGE, 3f * u);
        strip(c, x, sy, cw, stripH, n, u);
        // bottom row: BACK, REFUND, < PREV, page, NEXT >
        float by = h - 112f * u, bh = 92f * u;
        float backW = cw * 0.24f, refW = cw * 0.24f, pw = (cw - backW - refW - 4f * gap) / 3f;
        k.button(c, x, by, x + backW, by + bh, "BACK", eng.focus == n);
        host.hit(x, by, x + backW, by + bh, n, 0);
        float rx = x + backW + gap;
        boolean rf = eng.shopRefund;
        k.plate(c, rx, by, rx + refW, by + bh, rf ? 0x66FF8A2A : 0x28104030, eng.focus == n + 1 || rf ? Skin.ORANGE : Skin.EDGE, eng.focus == n + 1 ? 5f * u : 3f * u);
        if (eng.focus == n + 1) k.focusMarks(c, rx, by, rx + refW, by + bh);
        k.text(c, rf ? "REFUND: ON" : "REFUND", rx + refW / 2f, by + bh * 0.68f, 36f * u, rf ? Skin.ORANGE : TEXT, Paint.Align.CENTER);
        host.hit(rx, by, rx + refW, by + bh, n + 1, 0);
        float p0 = rx + refW + gap;
        pageButton(c, p0, by, p0 + pw, by + bh, "< PREV", page > 0, n + 2);
        k.plate(c, p0 + pw + gap, by, p0 + 2 * pw + gap, by + bh, 0x28104030, Skin.EDGE, 3f * u);
        k.text(c, cs(252, "PAGE ", page + 1, "/", pages, ""), p0 + pw * 1.5f + gap, by + bh * 0.68f, 40f * u, TEXT, Paint.Align.CENTER);
        pageButton(c, p0 + 2 * (pw + gap), by, p0 + 3 * pw + 2 * gap, by + bh, "NEXT >", page < pages - 1, n + 3);
    }

    private void pageButton(Canvas c, float l, float t, float r, float b, String label, boolean on, int id) {
        if (on) {
            k.button(c, l, t, r, b, label, false);
            host.hit(l, t, r, b, id, 0);
        } else {
            k.plate(c, l, t, r, b, 0x18000000, Skin.EDGE, 2f * k.u);
            k.text(c, label, (l + r) / 2f, t + (b - t) * 0.68f, 36f * k.u, LOCK, Paint.Align.CENTER);
        }
    }

    private int stateColor(boolean owned, boolean max, boolean afford) {
        return max ? GOLD : owned && afford ? Skin.GOOD : afford ? Skin.GOOD : Skin.BAD;
    }

    /** One card: icon well, name, sub line, and the state on the right (price, rank, OWNED, LOCKED ...). */
    private void card(Canvas c, int i, int l, float cl, float ct, float cr, float cb, float u) {
        MetaProgress meta = eng.meta;
        ShopCatalog.Entry e = eng.catalog.entries.get(i);
        boolean f = eng.focus == i;
        float rh = cb - ct;
        k.plate(c, cl, ct, cr, cb, f ? 0x44FF8A2A : 0, f ? Skin.ORANGE : Skin.EDGE, f ? 4f * u : 2f * u);
        if (f) k.focusMarks(c, cl, ct, cr, cb);
        host.hit(cl, ct, cr, cb, i, 0);
        float is = rh - 24f * u;
        float ix = cl + 12f * u, iy = ct + 12f * u;
        k.slotWell(c, ix, iy, is);
        float tx = ix + is + 14f * u, tw = cr - tx - 14f * u;
        int s0 = l * 24; // cache slots of this card
        String right;
        int rightCol;
        String line3 = null; // the lower left line
        int line3Col = TEXT;
        boolean dim = false;
        switch (e.kind) {
            case ShopCatalog.K_POWER: {
                int rank = meta.rank(e.key);
                boolean max = meta.isMaxed(e.key);
                int mr = eng.data.powerUps.get(e.key).maxRank();
                atlas.drawIcon(c, null, e.key, ix + is / 2, iy + is / 2, is * 0.8f);
                atlas.drawIcon(c, null, rank > 0 ? "frame_rare" : "frame_common", ix + is / 2, iy + is / 2, is);
                line3 = cs(s0, "", rank, "/", mr, "");
                line3Col = max ? GOLD : TEXT;
                int price = meta.nextPrice(e.key);
                if (eng.shopRefund) {
                    right = rank > 0 ? cs(s0 + 2, "+", meta.refundValue(e.key), "") : "--";
                    rightCol = rank > 0 ? Skin.ORANGE : LOCK;
                } else {
                    right = max ? "MAX" : cs(s0 + 2, "", price, "");
                    rightCol = stateColor(false, max, meta.gold >= price);
                }
                break;
            }
            case ShopCatalog.K_REROLL:
            case ShopCatalog.K_BANISH: {
                boolean rr = e.kind == ShopCatalog.K_REROLL;
                int have = rr ? meta.rerollCharges() : meta.banishCharges();
                int mx = rr ? MetaProgress.REROLL_PRICE.length : MetaProgress.BANISH_PRICE.length;
                k.glyph(c, rr ? Skin.G_BOLT : Skin.G_FROST, ix + is / 2, iy + is / 2, is * 0.34f, GOLD);
                line3 = cs(s0, "", have, "/", mx, "");
                line3Col = have >= mx ? GOLD : TEXT;
                int price = rr ? meta.nextRerollPrice() : meta.nextBanishPrice();
                if (eng.shopRefund) {
                    right = have > 0 ? cs(s0 + 2, "+", rr ? MetaProgress.REROLL_PRICE[have - 1] : MetaProgress.BANISH_PRICE[have - 1], "") : "--";
                    rightCol = have > 0 ? Skin.ORANGE : LOCK;
                } else {
                    right = price == 0 ? "MAX" : cs(s0 + 2, "", price, "");
                    rightCol = stateColor(false, price == 0, meta.gold >= price);
                }
                break;
            }
            case ShopCatalog.K_ASC: {
                int mx = meta.ascMax(e.key);
                boolean open = mx > 0;
                k.glyph(c, Skin.G_ARMORED, ix + is / 2, iy + is / 2, is * 0.34f, open ? Skin.ORANGE : LOCK);
                for (int p = 0; p < RunMods.MAX_TIER; p++) k.pip(c, tx + p * 30f * u, ct + rh - 44f * u, 0f, p < mx, Skin.ORANGE, 24f * u, 20f * u);
                line3 = null;
                right = open ? cs(s0, "TIER ", mx, "") : "LOCKED";
                rightCol = open ? Skin.ORANGE : LOCK;
                dim = !open;
                break;
            }
            case ShopCatalog.K_DAILY: {
                boolean open = meta.dailyBonusOpen(eng.today());
                k.glyph(c, Skin.G_VOLCANO, ix + is / 2, iy + is / 2, is * 0.34f, open ? GOLD : LOCK);
                right = open ? "GOLD x1.5" : "DONE TODAY";
                rightCol = open ? Skin.GOOD : DIM;
                break;
            }
            case ShopCatalog.K_TINT: {
                boolean own = meta.tintOwned(e.key, e.idx);
                boolean worn = meta.wornTint(e.key) == e.idx;
                float sw = is * 0.62f;
                fillSwatch(c, ix + (is - sw) / 2f, iy + (is - sw) / 2f, sw, MetaProgress.tintColor(e.key, e.idx), own);
                if (worn) atlas.drawIcon(c, null, "frame_unique", ix + is / 2, iy + is / 2, is);
                if (eng.shopRefund) {
                    right = meta.tintBought(e.key, e.idx) ? "+" + MetaProgress.TINT_PRICE : own ? "WON" : "--";
                    rightCol = meta.tintBought(e.key, e.idx) ? Skin.ORANGE : LOCK;
                } else if (worn) {
                    right = "WORN";
                    rightCol = GOLD;
                } else if (own) {
                    right = "OWNED";
                    rightCol = Skin.GOOD;
                } else {
                    right = cs(s0 + 2, "", MetaProgress.TINT_PRICE, "");
                    rightCol = stateColor(false, false, meta.gold >= MetaProgress.TINT_PRICE);
                }
                break;
            }
            default: {
                boolean done = meta.achDone(e.idx);
                k.glyph(c, Skin.G_BOLT, ix + is / 2, iy + is / 2, is * 0.34f, done ? GOLD : LOCK);
                right = done ? "DONE" : cs(s0, "+", Achievements.REWARD[e.idx], "");
                rightCol = done ? GOLD : DIM;
                dim = !done;
            }
        }
        k.fit(c, nameC[i], tx, ct + 44f * u, e.kind == ShopCatalog.K_ACH ? 36f * u : 44f * u, tw, dim ? DIM : TEXT, Paint.Align.LEFT);
        k.fit(c, subC[i], tx, ct + 82f * u, 28f * u, tw, DIM, Paint.Align.LEFT);
        if (line3 != null) k.text(c, line3, tx, ct + rh - 14f * u, 38f * u, line3Col, Paint.Align.LEFT);
        k.text(c, right, cr - 14f * u, ct + rh - 14f * u, 36f * u, rightCol, Paint.Align.RIGHT);
    }

    private void fillSwatch(Canvas c, float x, float y, float s, int color, boolean owned) {
        k.fill.setColor(owned ? color : (color & 0x00FFFFFF) | 0x55000000);
        c.drawRect(x, y, x + s, y + s, k.fill);
        k.stroke.setColor(owned ? 0xFFE8E0D6 : LOCK);
        k.stroke.setStrokeWidth(2f * k.u);
        k.stroke.setStyle(Paint.Style.STROKE);
        c.drawRect(x, y, x + s, y + s, k.stroke);
    }

    /** The strip under the cards: the focused entry in full (name, what it does, the state). */
    private void strip(Canvas c, float x, float sy, float cw, float sh, int n, float u) {
        MetaProgress meta = eng.meta;
        float px = x + 24f * u;
        int f = eng.focus;
        if (f < 0 || f >= n) {
            k.body(c, f == n ? "RETURN TO THE MENU" : eng.shopRefund ? "REFUND ON: selecting an upgrade gives back its last rank at 100%"
                    : "REFUND OFF: switch on to take purchases back at 100% and respec", px, sy + sh * 0.6f, 32f * u, DIM, Paint.Align.LEFT);
            return;
        }
        ShopCatalog.Entry e = eng.catalog.entries.get(f);
        k.fit(c, nameC[f], px, sy + 46f * u, 46f * u, cw * 0.62f, Skin.ORANGE, Paint.Align.LEFT);
        k.fit(c, subC[f], px, sy + 90f * u, 32f * u, cw - 48f * u, TEXT, Paint.Align.LEFT);
        switch (e.kind) {
            case ShopCatalog.K_POWER: {
                PowerUpDef d = eng.data.powerUps.get(e.key);
                Describe.PowerUpText pt = eng.shopText.get(e.key);
                int rank = meta.rank(e.key), mr = d.maxRank();
                k.text(c, cs(240, "RANK ", rank, "/", mr, ""), x + cw - 24f * u, sy + 46f * u, 38f * u, TEXT, Paint.Align.RIGHT);
                if (pt != null) k.fit(c, pt.totals[Math.min(rank, mr)], px, sy + 132f * u, 30f * u, cw * 0.6f, DIM, Paint.Align.LEFT);
                int price = meta.nextPrice(e.key);
                k.text(c, meta.isMaxed(e.key) ? "MAX" : cs(241, "", price, " GOLD"), x + cw - 24f * u, sy + 134f * u, 40f * u,
                        meta.isMaxed(e.key) ? GOLD : meta.gold >= price ? Skin.GOOD : Skin.BAD, Paint.Align.RIGHT);
                break;
            }
            case ShopCatalog.K_REROLL:
            case ShopCatalog.K_BANISH: {
                boolean rr = e.kind == ShopCatalog.K_REROLL;
                int price = rr ? meta.nextRerollPrice() : meta.nextBanishPrice();
                k.text(c, price == 0 ? "MAX" : cs(241, "", price, " GOLD"), x + cw - 24f * u, sy + 134f * u, 40f * u,
                        price == 0 ? GOLD : meta.gold >= price ? Skin.GOOD : Skin.BAD, Paint.Align.RIGHT);
                break;
            }
            case ShopCatalog.K_ASC: {
                int mx = meta.ascMax(e.key);
                k.fit(c, mx == 0 ? "Win this stage to open tier 1" : mx >= RunMods.MAX_TIER ? "All tiers open" : "Win on the top tier to open the next",
                        px, sy + 132f * u, 30f * u, cw * 0.7f, DIM, Paint.Align.LEFT);
                break;
            }
            case ShopCatalog.K_ACH:
                k.text(c, meta.achDone(e.idx) ? "REWARD CLAIMED" : cs(241, "REWARD  ", Achievements.REWARD[e.idx], " GOLD"), px, sy + 134f * u, 36f * u,
                        meta.achDone(e.idx) ? GOLD : Skin.GOOD, Paint.Align.LEFT);
                break;
            default:
        }
    }
}
