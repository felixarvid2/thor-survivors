package se.felixarvid.thorsurvivors.game;

import se.felixarvid.thorsurvivors.data.GameData;
import se.felixarvid.thorsurvivors.data.StageDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The single shared game state. The game thread calls {@link #advance}/{@link #update}; every window (top surface,
 * bottom panel, overlay) only reads these fields and posts commands through {@link #input} (a thread-safe queue).
 *
 * Threading: the game thread holds {@link #lock} while it steps the simulation and while it draws the world; the
 * panel (UI thread) holds it while it draws, so it never sees a half-updated Sim.
 */
public final class Engine {
    public static final int PH_MENU = 0, PH_SHOP = 1, PH_PLAYING = 2, PH_LEVELUP = 3, PH_PAUSED = 4, PH_CHEST = 5,
            PH_RESULT = 6, PH_EQUIP = 7, PH_MERCHANT = 8, PH_TERMINAL = 9,
            PH_OPTIONS = 10, PH_CODEX = 11, PH_REMAP = 12; // v1.5 B3: options, bestiary / codex, button remap
    // v1.5 B3: CODEX and OPTIONS sit beside POWER-UPS on one row (left/right moves between the three)
    public static final int MENU_START = 0, MENU_STAGE = 1, MENU_HERO = 2, MENU_WEAPON = 3, MENU_SHOP = 4,
            MENU_CODEX = 5, MENU_OPTIONS = 6, MENU_MUSIC = 7, MENU_SFX = 8, MENU_LIGHT = 9,
            MENU_ASC = 10, MENU_COUNT = 11; // v1.5 B4: MENU_ASC = ascension tier / daily challenge (after B3's rows)
    /** Rows of the options screen (v1.5 B3): music, SFX and lighting are the same settings as the menu rows. */
    public static final int OPT_MUSIC = 0, OPT_SFX = 1, OPT_LIGHT = 2, OPT_SHAKE = 3, OPT_NUMBERS = 4, OPT_TELE = 5, OPT_TEXT = 6,
            OPT_AIM = 7, OPT_HAPTICS = 8, OPT_REMAP = 9, OPT_BACK = 10, OPT_COUNT = 11;
    /** Rows of the remap screen: the four actions (ButtonMap.CONFIRM..DASH), reset, back. */
    public static final int REMAP_RESET = ButtonMap.COUNT, REMAP_BACK = ButtonMap.COUNT + 1, REMAP_COUNT = ButtonMap.COUNT + 2;
    /** Codex grid: columns and entries per page. */
    public static final int CODEX_COLS = 6, CODEX_PAGE = 24;

    /** True for the phases drawn as menus (the title on the top screen, menu music): v1.5 B3 added the options, codex and remap screens. */
    public static boolean isMenuPhase(int ph) {
        return ph == PH_MENU || ph == PH_SHOP || ph == PH_OPTIONS || ph == PH_CODEX || ph == PH_REMAP;
    }

    private static final Engine INSTANCE = new Engine();

    public static Engine get() {
        return INSTANCE;
    }

    /** Held while the Sim is mutated or read as a whole (update, world draw, panel draw). Reentrant. */
    public final Object lock = new Object();
    /** Largest simulation step; a longer frame is split into equal substeps no bigger than this. */
    public static final float MAX_STEP = 1f / 60f;
    /** v1.5 B8: at most this many simulation steps per rendered frame, so a stall can never spiral (see {@link #stepsFor}). */
    public static final int MAX_STEPS = 5;
    /** Longest frame the simulation follows (5 steps of just under 1/60 s); a longer one is cut, the game slows instead. */
    public static final float MAX_FRAME = MAX_STEPS * MAX_STEP * 1.05f;

    public final Input input = new Input();
    /** Sound events for {@code audio.AudioEngine}; shared by every run (the Sim is given this instance). */
    public final SoundEvents sound = new SoundEvents();
    public GameData data;
    public MetaProgress meta;
    /** Seed for the next run only (tests and screenshots); null means a fresh random run. */
    public Long nextRunSeed;
    public volatile int phase = PH_MENU;
    public volatile Sim sim;
    public volatile int focus;
    public volatile float menuTime;
    public volatile String toast = "";
    public volatile float toastTimer;

    // menu selections
    public volatile int stageIdx, weaponIdx;
    public List<String> stageKeys = new ArrayList<>();
    public List<String> starters = new ArrayList<>();
    public List<String> shopKeys = new ArrayList<>();
    /** The salvage terminal shows this many power-ups per page (2 columns x 4 rows). */
    public static final int SHOP_PAGE = 8;
    /** The shown shop page; the focus decides it, except on BACK where it stays. */
    public volatile int shopPage;
    /** v1.5 B4: the terminal's categories and pages (built in init), the refund toggle and the page shown. */
    public ShopCatalog catalog;
    public volatile boolean shopRefund;
    /** v1.5 B4: ascension tier picked on the menu row, or the daily challenge; {@code dateOverride} (yyyymmdd) fixes "today" for tests. */
    public volatile int ascTier;
    public volatile boolean dailyPick;
    public volatile int dateOverride;
    /** v1.5 B4: the run's modifiers, the level-up charges left, the banish mode (the next card picked is struck from the pool) and the struck keys. */
    public volatile RunMods runMods = RunMods.NONE;
    public volatile int rerollsLeft, banishesLeft;
    public volatile boolean banishMode;
    private final java.util.HashSet<String> banned = new java.util.HashSet<>();
    /** v1.5 B4: results screen: a record was set, and the achievements unlocked by the run (name and reward lines). */
    public volatile boolean resultRecord;
    public volatile String[] resultAch = new String[0];
    /** Shop texts per power-up, built in init (no string building while drawing). */
    public final java.util.Map<String, Describe.PowerUpText> shopText = new java.util.HashMap<>();

    // transient dialog data
    public volatile List<Offer> offers = new ArrayList<>();
    public volatile String[] chestLines = new String[0];
    public volatile boolean resultWin;
    public volatile int resultTime, resultKills, resultGold, resultLevel;
    /** The equipment choice on the bottom screen (PH_EQUIP): the item offered, its rarity, and the stat lines of the offer. */
    public volatile se.felixarvid.thorsurvivors.data.EquipDef equipNew, equipOld;
    public volatile int equipNewRar, equipOldRar;
    public volatile String[] equipNewLines = new String[0], equipOldLines = new String[0];
    /** The run ended with The First dead: the gold on the result screen counts twice. */
    public volatile boolean resultFirst;
    public volatile Equipment.Diff[] equipDiff = new Equipment.Diff[0];
    public volatile boolean resultNewBest;

    // v1.5 B3: codex, first-run hints, loading tips, the remap screen
    public Codex codex;
    public final Tutorial tutorial = new Tutorial();
    public volatile int codexTab;
    /** Remap screen: the action waiting for a button press, or -1. */
    public volatile int remapListening = -1;
    /** Rolled after every run; {@link Tips#pick} turns it into the line shown on the menu and the results screen. */
    public volatile int tipSeed = 1;
    private final Random tipRng = new Random();
    private float codexT;
    private int lastPhase = PH_MENU;

    /** The Salvager's cards: a touch focuses a card first and buys on the second tap of the same card. */
    private boolean merchantTapped;
    private final Random rng = new Random();
    private final float[] tmp = new float[2];

    private Engine() {
    }

    public void init(GameData d, MetaProgress m) {
        if (data != null) return;
        data = d;
        meta = m;
        for (String k : GameData.STAGE_ORDER) if (d.stages.containsKey(k)) stageKeys.add(k);
        if (stageKeys.isEmpty()) stageKeys.addAll(d.stages.keySet()); // data without the Ashfall stages (tests of the base data)
        starters = d.starterWeapons();
        catalog = new ShopCatalog(d, stageKeys);
        for (ShopCatalog.Entry e : catalog.entries) shopKeys.add(e.kind == ShopCatalog.K_POWER ? e.key : e.key + "#" + e.idx);
        codex = new Codex(d, stageKeys); // v1.5 B3
        tipSeed = tipRng.nextInt();
        for (se.felixarvid.thorsurvivors.data.PowerUpDef p : d.powerUps.values()) shopText.put(p.key, Describe.powerUp(p));
    }

    /** v1.5 B3: attaches the persistent store to the options, the button map, the codex and the tutorial (the app calls it once at start-up). */
    public void attachStore(MetaProgress.Store st) {
        Settings.get().load(st);
        if (codex != null) codex.load(st);
        tutorial.load(st);
    }

    /** The tip for the stage in the menu (a line of text, no allocation). */
    public String tip() {
        return Tips.pick(data == null ? null : stage().key, tipSeed);
    }

    public boolean isReady() {
        return data != null;
    }

    /** Debug / test hook: a stage that is not in the menu (the Vampire Survivors FOREST and LIBRARY), until the menu changes the stage. */
    public volatile String stageOverride;

    public StageDef stage() {
        String o = stageOverride;
        return data.stages.get(o != null ? o : stageKeys.get(stageIdx));
    }

    /**
     * Picks the stage of the next run. The menu stages (Cinder Flats, Deep Mines) set the menu row; any other key of
     * the data (FOREST, LIBRARY) is only reachable through here, for tests and balance runs.
     */
    public void selectStage(String key) {
        int i = stageKeys.indexOf(key);
        stageOverride = i >= 0 ? null : (data.stages.containsKey(key) ? key : null);
        if (i >= 0) stageIdx = i;
    }

    public boolean gameActive() {
        return phase == PH_PLAYING;
    }

    // ------------------------------------------------------------------ update (game thread)

    /**
     * Advances by one display frame of real time: the time is clamped to {@link #MAX_FRAME} and split into the fewest
     * equal substeps of at most {@link #MAX_STEP}, so 60 Hz gives one step per frame, 120 Hz gives steps of 8.3 ms
     * and a stall never turns into a burst.
     */
    public void advance(float frameDt) {
        if (frameDt <= 0f) return;
        if (frameDt > MAX_FRAME) frameDt = MAX_FRAME;
        int n = stepsFor(frameDt);
        float h = frameDt / n;
        synchronized (lock) {
            for (int i = 0; i < n; i++) update(h);
        }
    }

    /** Substeps for a frame of {@code frameDt} s: every one is at most {@link #MAX_STEP}, never more than {@link #MAX_STEPS}. */
    public static int stepsFor(float frameDt) {
        return Math.max(1, Math.min(MAX_STEPS, (int) Math.ceil(Math.min(frameDt, MAX_FRAME) / (MAX_STEP * 1.05f))));
    }

    public void update(float dt) {
        if (data == null) return;
        synchronized (lock) {
            step(dt);
        }
    }

    private void step(float dt) {
        int[] c;
        while ((c = input.poll()) != null) handle(c);
        if (toastTimer > 0) toastTimer -= dt;
        if (lockShake > 0) lockShake -= dt;
        menuTime += dt;
        if (phase != PH_PLAYING) sound.advance(dt); // while playing, Sim.update advances the clock
        if (phase == PH_PLAYING) {
            Sim s = sim;
            s.update(dt, input);
            if (s.ended != 0) finishRun(s.ended == 2);
            else if (s.pendingLevelUps > 0) openLevelUp();
            else if (s.pendingChests > 0) {
                chestLines = s.openChest();
                phase = PH_CHEST;
                focus = 0;
            } else if (s.equipN > 0) openEquip();
            else if (s.merchant.wantsOpen()) openMerchant();
            else if (s.pois.wantsTerminal()) openTerminal();
        }
        v15Step(dt);
    }

    /** v1.5 B3: the first-run hints, the codex scan and the tip roll; runs every step in every phase. */
    private void v15Step(float dt) {
        input.move(tmp);
        tutorial.update(phase, dt, (float) Math.sqrt(tmp[0] * tmp[0] + tmp[1] * tmp[1]));
        if (phase == PH_PLAYING && sim != null && codex != null) {
            codexT += dt;
            if (codexT >= 0.5f) {
                codexT = 0f;
                codex.scan(sim, stageOverride != null ? -1 : stageIdx);
            }
        }
        int ph = phase;
        if (ph != lastPhase) {
            boolean wasRun = lastPhase != PH_RESULT && !isMenuPhase(lastPhase);
            if (wasRun && (ph == PH_MENU || ph == PH_RESULT)) tipSeed = tipRng.nextInt(); // a new tip for the next run
            lastPhase = ph;
        }
    }

    private void openLevelUp() {
        Sim s = sim;
        // the level this card is for: with several level-ups pending, the first one is for the lowest of those levels
        int offerLevel = s.level - s.pendingLevelUps + 1;
        offerLevelNow = offerLevel;
        offers = rollOffers();
        s.addFuseCard(offers);
        banishMode = false;
        phase = PH_LEVELUP;
        focus = 0;
    }

    private int offerLevelNow;

    /** v1.5 B4: the cards for the level-up; cards the player banished are drawn again until none is in the set (then struck, with Field Repair / Salvage Haul if nothing is left). */
    private List<Offer> rollOffers() {
        Sim s = sim;
        List<Offer> o = null;
        for (int t = 0; t < 24; t++) {
            o = OfferGenerator.generate(s.inv, data.weapons.values(), s.stats.luck, rng,
                    s.main == null ? null : s.main.def, s.main == null ? null : s.main.tier, offerLevelNow);
            if (banned.isEmpty() || !hasBanned(o)) return o;
        }
        for (int i = o.size() - 1; i >= 0; i--) if (isBanned(o.get(i))) o.remove(i);
        if (o.isEmpty()) {
            o.add(new Offer(Offer.HEAL, null, 0));
            o.add(new Offer(Offer.GOLD, null, 0));
        }
        return o;
    }

    private boolean isBanned(Offer o) {
        return o.def != null && o.kind != Offer.FUSE && banned.contains(o.def.key);
    }

    private boolean hasBanned(List<Offer> l) {
        for (int i = 0; i < l.size(); i++) if (isBanned(l.get(i))) return true;
        return false;
    }

    /** Reroll charges and banish charges as buttons below the cards: shown from the run's first charge on. */
    public boolean levelUpTools() {
        return runMods.extraRerolls() + runMods.extraBanishes() > 0;
    }

    private void rerollOffers() {
        Offer fuse = null;
        for (Offer o : offers) if (o.kind == Offer.FUSE) fuse = o;
        List<Offer> n = rollOffers();
        if (fuse != null && !n.isEmpty()) { // the FUSE card stays on a reroll: it replaces the last non-branch card again
            int at = n.size() - 1;
            while (at > 0 && n.get(at).kind == Offer.BRANCH) at--;
            if (n.get(at).kind == Offer.BRANCH) n.add(fuse);
            else n.set(at, fuse);
        }
        offers = n;
    }

    /** The hero walked up to the Salvager (nothing else is pending): the shop pauses the run on the bottom screen. */
    private void openMerchant() {
        sim.merchant.opened();
        merchantTapped = false;
        phase = PH_MERCHANT;
        focus = 0;
    }

    /** The hero stood on a Relay Terminal long enough: the choose-one screen pauses the run on the bottom screen. */
    private void openTerminal() {
        sim.pois.opened();
        merchantTapped = false;
        phase = PH_TERMINAL;
        focus = 0;
    }

    /** A better item than the worn one was picked up: the choice screen (Keep or Replace) pauses the run like a level-up. */
    private void openEquip() {
        Sim s = sim;
        equipNew = s.equipQueuedDef();
        equipNewRar = s.equipQueuedRar();
        equipOld = s.equip.def[equipNew.slot];
        equipOldRar = s.equip.rar[equipNew.slot];
        equipNewLines = Equipment.lines(data, equipNew, equipNewRar);
        equipOldLines = equipOld == null ? new String[0] : Equipment.lines(data, equipOld, equipOldRar);
        equipDiff = Equipment.diff(data, equipOld, equipOldRar, equipNew, equipNewRar);
        phase = PH_EQUIP;
        focus = 1; // Replace is the better item: it starts selected
    }

    /** Answers the choice: replace = wear the new item, else keep the old one; then the next choice or back to the run. */
    private void answerEquip(boolean replace) {
        Sim s = sim;
        s.resolveEquip(replace);
        if (s.equipN > 0) openEquip();
        else resume();
    }

    /** Seconds the stage card still shakes after a refused start (a locked stage); read by the panel. */
    public volatile float lockShake;

    /** A locked stage cannot be started: false, with a short shake and a toast saying how to open it. */
    private boolean tryStartRun() {
        String key = stage().key;
        if (!meta.unlocked(key)) {
            lockShake = LOCK_SHAKE;
            toast("Locked: " + meta.unlockLine(key));
            return false;
        }
        startRun();
        return true;
    }

    public static final float LOCK_SHAKE = 0.45f;

    private void startRun() {
        long seed = nextRunSeed != null ? nextRunSeed : System.nanoTime(); // a new run every time unless a test fixed it
        String heroKey = meta.heroKey();
        RunMods.Daily daily = null;
        if (dailyPick) { // v1.5 B4: the date fixes stage, hero, seed and one modifier
            daily = dailyToday();
            if (daily != null) {
                String sk = unlockedStages().get(daily.stage);
                selectStage(sk);
                heroKey = heroKeyAt(daily.hero, heroKey);
                if (nextRunSeed == null) seed = daily.seed;
            }
        }
        if (daily != null) runMods = RunMods.daily(daily.mod, meta.dailyBonusOpen(today()), meta.rerollCharges(), meta.banishCharges());
        else runMods = RunMods.ascension(Math.min(ascTier, meta.ascMax(stage().key)), meta.rerollCharges(), meta.banishCharges());
        CurseMod.setTierHpMul(runMods.hpMult()); // v1.5 integration: B4's tier HP feeds B7's capped curse HP; Sim snapshots it
        sim = new Sim(data, stage(), starters.get(weaponIdx), meta.bonuses(), heroKey, seed);
        CurseMod.setTierHpMul(1.0); // the static stays neutral for anything that builds a Sim outside a run start
        sim.applyMods(runMods);
        rerollsLeft = runMods.extraRerolls();
        banishesLeft = runMods.extraBanishes();
        banishMode = false;
        banned.clear();
        rng.setSeed(seed * 31L + 7L); // the level-up cards follow the run seed too, so a fixed seed repeats the whole run
        nextRunSeed = null;
        sim.sound = sound;
        input.clearMovement();
        phase = PH_PLAYING;
        focus = 0;
    }

    private void finishRun(boolean win) {
        Sim s = sim;
        resultWin = win;
        resultTime = (int) s.time;
        resultKills = s.kills;
        resultGold = s.endGold();
        resultFirst = s.firstKilled;
        resultLevel = s.level;
        resultNewBest = resultTime > meta.bestTimeSec;
        meta.addGold(resultGold);
        meta.recordRun(resultTime, s.kills, s.stage.key);
        if (win) meta.markCleared(s.stage.key);
        finishMeta(s, win);
        phase = PH_RESULT;
        focus = 0;
        sound.emit(win ? SoundEvents.VICTORY : SoundEvents.GAME_OVER);
    }

    /** v1.5 B4: what a finished run changes besides gold: the records, the next ascension tier, the daily bonus and the achievements. */
    private void finishMeta(Sim s, boolean win) {
        RunMods m = runMods;
        String hero = s.hero == null ? null : s.hero.key;
        resultRecord = meta.records.submit(s.stage.key, hero, resultTime, s.kills, win);
        if (win && !m.daily) meta.recordAscWin(s.stage.key, m.tier);
        if (m.daily && m.dailyBonus) meta.claimDaily(today());
        Achievements.Facts f = new Achievements.Facts();
        f.stage = s.stage.key;
        f.hero = hero;
        f.win = win;
        f.daily = m.daily;
        f.timeSec = resultTime;
        f.kills = s.kills;
        f.level = s.level;
        f.tier = m.tier;
        f.gold = resultGold;
        f.passives = s.inv.passiveCount;
        for (int i = 0; i < s.inv.weaponCount; i++) if (s.inv.weapons[i].def.evolution || s.inv.weapons[i].def.fusion) f.evolutions++;
        resultAch = achLines(Achievements.check(meta, f));
        if (resultAch.length > 0) sound.emit(SoundEvents.ACHIEVEMENT); // v1.5 integration: B8 sound id 58
    }

    private static String[] achLines(int[] ids) {
        String[] out = new String[Math.min(3, ids.length)];
        for (int i = 0; i < out.length; i++) out[i] = "ACHIEVEMENT  " + Achievements.NAME[ids[i]] + "  +" + Achievements.REWARD[ids[i]];
        if (ids.length > 3) out[2] = "ACHIEVEMENTS  +" + (ids.length - 2) + " more";
        return out;
    }

    private int dateCache;
    private long dateAt;

    /** Today as yyyymmdd; the calendar is asked once a minute (the menu reads it every frame). */
    public int today() {
        if (dateOverride != 0) return dateOverride;
        long now = System.currentTimeMillis();
        if (dateCache == 0 || now - dateAt > 60000L || now < dateAt) {
            dateCache = RunMods.today();
            dateAt = now;
        }
        return dateCache;
    }

    /** Stages the player may start (the daily challenge picks among these). */
    public List<String> unlockedStages() {
        List<String> l = new ArrayList<>();
        for (String k : stageKeys) if (meta.unlocked(k)) l.add(k);
        if (l.isEmpty()) l.add(stageKeys.get(0));
        return l;
    }

    private String heroKeyAt(int i, String dflt) {
        List<String> hk = data.heroKeys();
        return hk.isEmpty() ? dflt : hk.get(Math.floorMod(i, hk.size()));
    }

    /** Today's challenge, or null when the data has no stage. */
    public RunMods.Daily dailyToday() {
        if (stageKeys.isEmpty()) return null;
        return RunMods.dailyOf(today(), unlockedStages().size(), Math.max(1, data.heroKeys().size()));
    }

    /** v1.5 B4: one confirm on a terminal entry (buy, or refund in the refund mode). False = refused (the buzz). */
    private boolean shopAct(ShopCatalog.Entry e, String key) {
        boolean ok = true;
        switch (e.kind) {
            case ShopCatalog.K_POWER:
                if (shopRefund) {
                    int r = meta.refund(key);
                    if (r > 0) toast("Refunded " + data.powerUps.get(key).name + "  +" + r);
                    else ok = refused("Nothing to refund");
                } else if (meta.isMaxed(key)) ok = refused("Already maxed");
                else if (!meta.buy(key)) ok = refused("Not enough gold");
                else toast("Purchased " + data.powerUps.get(key).name);
                break;
            case ShopCatalog.K_REROLL:
            case ShopCatalog.K_BANISH: {
                boolean rr = e.kind == ShopCatalog.K_REROLL;
                String nm = rr ? "Reroll charge" : "Banish charge";
                if (shopRefund) {
                    int r = rr ? meta.refundReroll() : meta.refundBanish();
                    if (r > 0) toast("Refunded " + nm + "  +" + r);
                    else ok = refused("Nothing to refund");
                } else if ((rr ? meta.nextRerollPrice() : meta.nextBanishPrice()) == 0) ok = refused("Already maxed");
                else if (!(rr ? meta.buyReroll() : meta.buyBanish())) ok = refused("Not enough gold");
                else toast("Purchased " + nm);
                break;
            }
            case ShopCatalog.K_TINT:
                if (shopRefund) {
                    int r = meta.refundTint(e.key, e.idx);
                    if (r > 0) toast("Refunded " + MetaProgress.tintName(e.key, e.idx) + " tint  +" + r);
                    else ok = refused(meta.tintOwned(e.key, e.idx) ? "Won tints cannot be refunded" : "Nothing to refund");
                } else if (meta.tintOwned(e.key, e.idx)) {
                    meta.wearTint(e.key, e.idx);
                    toast(meta.wornTint(e.key) == e.idx ? "Wearing " + MetaProgress.tintName(e.key, e.idx) : "Tint removed");
                } else if (!meta.buyTint(e.key, e.idx)) ok = refused("Not enough gold");
                else {
                    meta.wearTint(e.key, e.idx);
                    toast("Purchased " + MetaProgress.tintName(e.key, e.idx) + " tint");
                }
                break;
            case ShopCatalog.K_ASC:
                toast(ascMax(key) == 0 ? "Win this stage to open ascension" : "Ascension open up to tier " + ascMax(key));
                break;
            case ShopCatalog.K_DAILY:
                toast(meta.dailyBonusOpen(today()) ? "Daily: first run today pays x1.5 gold" : "Daily bonus already taken today");
                break;
            default:
                toast(Achievements.NAME[e.idx] + ": " + Achievements.DESC[e.idx] + " (+" + Achievements.REWARD[e.idx] + ")");
        }
        if (ok && !shopRefund && e.kind != ShopCatalog.K_ACH) {
            int[] got = Achievements.checkShop(meta);
            if (got.length > 0) toast(achLines(got)[0]);
        }
        return ok;
    }

    private boolean refused(String msg) {
        toast(msg);
        return false;
    }

    private int ascMax(String stage) {
        return meta.ascMax(stage);
    }

    /** v1.5 B4: the text of the ascension row on the menu: the tier and what it adds, or the daily challenge. Built when the row changes. */
    public String ascLabel() {
        if (dailyPick) {
            RunMods.Daily d = dailyToday();
            if (d == null) return "DAILY";
            String hk = heroKeyAt(d.hero, "");
            se.felixarvid.thorsurvivors.data.HeroDef hd = data.heroes.get(hk);
            StageDef sd = data.stages.get(unlockedStages().get(d.stage));
            return "DAILY  " + (sd == null ? "" : sd.name) + " / " + (hd == null ? hk : hd.name) + " / " + RunMods.MOD_SHORT[d.mod]
                    + (meta.dailyBonusOpen(today()) ? "  GOLD x1.5" : "");
        }
        int t = Math.min(ascTier, meta.ascMax(stage().key));
        if (t == 0) return "OFF";
        return "TIER " + t + "  " + RunMods.MOD_SHORT[t - 1] + "  GOLD +" + Math.round(t * RunMods.GOLD_PER_TIER * 100) + "%";
    }

    private void toast(String t) {
        toast = t;
        toastTimer = 1.8f;
    }

    // ------------------------------------------------------------------ commands

    private void handle(int[] c) {
        switch (c[0]) {
            case Input.CMD_NAV: nav(c[1]); break;
            case Input.CMD_CONFIRM: confirm(0); break;
            case Input.CMD_BACK: {
                int before = phase;
                back();
                if (phase != before && before != PH_PLAYING) sound.emit(SoundEvents.UI_BACK);
                break;
            }
            case Input.CMD_START: start(); break;
            case Input.CMD_BIND: bindButton(c[1]); break; // v1.5 B3
            case Input.CMD_TAP:
                if (c[1] == phase) {
                    // first tap on a tile only focuses it (the shop and the Salvager); LEAVE acts at once
                    if (phase == PH_SHOP && c[2] > shopKeys.size()) { // v1.5 B4: ids n+1 REFUND, n+2 PREV, n+3 NEXT, n+4.. the category tabs
                        int id = c[2] - shopKeys.size();
                        if (id == 1) {
                            focus = shopKeys.size() + 1;
                            confirm(0);
                        } else if (id <= 3) shopFlip(id == 2 ? -1 : 1);
                        else if (id - 4 < ShopCatalog.CATS) shopTab(id - 4);
                        break;
                    }
                    if (phase == PH_CODEX) { // v1.5 B3: tab buttons are ids 1000+, BACK is the entry count, the rest are entries
                        if (c[2] >= 1000) setCodexTab(c[2] - 1000);
                        else if (c[2] <= codex.size(codexTab)) {
                            focus = c[2];
                            if (c[2] == codex.size(codexTab)) confirm(0);
                        }
                        break;
                    }
                    boolean pick = phase == PH_SHOP && c[2] < shopKeys.size() && c[2] != focus
                            || phase == PH_MERCHANT && c[2] < sim.merchant.cards() && (c[2] != focus || !merchantTapped)
                            || phase == PH_TERMINAL && c[2] < Pois.CARDS && (c[2] != focus || !merchantTapped);
                    if (phase == PH_MERCHANT) merchantTapped = c[2] < sim.merchant.cards();
                    else if (phase == PH_TERMINAL) merchantTapped = c[2] < Pois.CARDS;
                    focus = c[2];
                    if (phase == PH_SHOP && focus < shopKeys.size()) shopPage = catalog.pageOf(focus);
                    if (!pick) confirm(c[3]);
                }
                break;
            case Input.CMD_AUTOPAUSE:
                if (phase == PH_PLAYING) {
                    phase = PH_PAUSED;
                    focus = 0;
                }
                break;
            default:
        }
    }

    private int itemCount() {
        switch (phase) {
            case PH_MENU: return MENU_COUNT;
            case PH_SHOP: return shopKeys.size() + 2; // the entries, BACK and the REFUND toggle
            case PH_LEVELUP: return offers.size() + (levelUpTools() ? 2 : 0); // v1.5 B4: REROLL and BANISH below the cards
            case PH_EQUIP: return 2;
            case PH_MERCHANT: return sim.merchant.cards() + 1; // the cards (three, four with the Fuse Kit) and LEAVE
            case PH_TERMINAL: return Pois.CARDS + 1; // the three buff cards and DECLINE
            case PH_PAUSED: return 2;
            case PH_OPTIONS: return OPT_COUNT;
            case PH_REMAP: return REMAP_COUNT;
            case PH_CODEX: return codex.size(codexTab) + 1; // the entries and BACK
            default: return 1;
        }
    }

    private void nav(int dir) {
        int n = itemCount();
        int f = focus;
        int before = f;
        boolean changed = false;
        switch (phase) {
            case PH_MENU:
                if (f == MENU_SHOP || f == MENU_CODEX || f == MENU_OPTIONS) { // v1.5 B3: one row of three buttons
                    if (dir == Input.LEFT) f = Math.max(MENU_SHOP, f - 1);
                    else if (dir == Input.RIGHT) f = Math.min(MENU_OPTIONS, f + 1);
                    else if (dir == Input.UP) f = MENU_WEAPON;
                    else if (dir == Input.DOWN) f = MENU_MUSIC;
                }
                else if (dir == Input.UP) f = f == MENU_MUSIC ? MENU_SHOP : (f + n - 1) % n;
                else if (dir == Input.DOWN) f = f == MENU_WEAPON ? MENU_SHOP : (f + 1) % n;
                else if (isCycleRow(f)) {
                    changed = cycle(f, dir == Input.RIGHT ? 1 : -1);
                }
                break;
            case PH_OPTIONS: // v1.5 B3
                if (dir == Input.UP) f = (f + n - 1) % n;
                else if (dir == Input.DOWN) f = (f + 1) % n;
                else if (f < OPT_REMAP) changed = optCycle(f, dir == Input.RIGHT ? 1 : -1);
                break;
            case PH_REMAP:
                if (remapListening >= 0) break; // waiting for a button
                if (dir == Input.UP || dir == Input.LEFT) f = (f + n - 1) % n;
                else if (dir == Input.DOWN || dir == Input.RIGHT) f = (f + 1) % n;
                break;
            case PH_CODEX:
                f = codexNav(dir, f, n - 1);
                break;
            case PH_SHOP:
                if (f < n - 2) shopPage = catalog.pageOf(f); // the focus may have been set directly
                f = shopNav(dir, f, n - 2);
                if (f < n - 2) shopPage = catalog.pageOf(f); // n counts BACK and REFUND as well
                break;
            case PH_LEVELUP:
            case PH_PAUSED:
            case PH_EQUIP:
            case PH_MERCHANT:
            case PH_TERMINAL:
                if (dir == Input.UP || dir == Input.LEFT) f = (f + n - 1) % n;
                else f = (f + 1) % n;
                break;
            default:
        }
        focus = f;
        if (phase == PH_MERCHANT || phase == PH_TERMINAL) merchantTapped = false; // moving the focus with the pad: a tap focuses first again
        if (phase != PH_PLAYING && phase != PH_CHEST && phase != PH_RESULT && (f != before || changed)) {
            sound.emit(SoundEvents.UI_MOVE);
        }
    }

    /**
     * Shop focus move on a page of 2 columns x 4 rows (the pages of {@link ShopCatalog}: every category starts on a page of its own).
     * Left past the first column / right past the last flips the page, down from the bottom row goes to BACK (right of it: the REFUND
     * toggle), up from BACK to the page's bottom row; L1 / R1 flip the page.
     */
    private int shopNav(int dir, int f, int items) {
        int pages = catalog.pages();
        if (f > items) { // REFUND
            if (dir == Input.LEFT) return items;
            if (dir == Input.UP) return Math.max(0, catalog.pageStart[shopPage] + Math.min(catalog.pageLen[shopPage] - 1, 6));
            return f;
        }
        if (f >= items) { // BACK
            if (dir == Input.UP) return Math.max(0, catalog.pageStart[shopPage] + Math.min(catalog.pageLen[shopPage] - 1, 6));
            if (dir == Input.RIGHT) return items + 1;
            if (dir == Input.PAGE_PREV || dir == Input.PAGE_NEXT) {
                int pg = Math.max(0, Math.min(pages - 1, shopPage + (dir == Input.PAGE_NEXT ? 1 : -1)));
                return catalog.pageStart[pg];
            }
            return f;
        }
        int pg = catalog.pageOf(f), st = catalog.pageStart[pg], len = catalog.pageLen[pg];
        int l = f - st, col = l % 2, row = l / 2;
        switch (dir) {
            case Input.LEFT:
                if (col == 1) return f - 1;
                if (pg > 0) return catalog.pageStart[pg - 1] + Math.min(catalog.pageLen[pg - 1] - 1, row * 2 + 1);
                return f;
            case Input.RIGHT:
                if (col == 0 && l + 1 < len) return f + 1;
                if (col == 1 && pg < pages - 1) return catalog.pageStart[pg + 1] + Math.min(catalog.pageLen[pg + 1] - 1, row * 2);
                return f;
            case Input.UP:
                return row > 0 ? f - 2 : f;
            case Input.DOWN:
                return row < 3 && l + 2 < len ? f + 2 : items;
            case Input.PAGE_PREV:
                return pg > 0 ? catalog.pageStart[pg - 1] + Math.min(catalog.pageLen[pg - 1] - 1, l) : f;
            case Input.PAGE_NEXT:
                return pg < pages - 1 ? catalog.pageStart[pg + 1] + Math.min(catalog.pageLen[pg + 1] - 1, l) : f;
            default:
                return f;
        }
    }

    /** A touch on PREV / NEXT: the focus moves to the same slot on the other page. */
    private void shopFlip(int d) {
        int items = shopKeys.size();
        int f = shopNav(d < 0 ? Input.PAGE_PREV : Input.PAGE_NEXT, focus < items ? focus : items, items);
        if (f != focus) {
            focus = f;
            if (f < items) shopPage = catalog.pageOf(f);
            sound.emit(SoundEvents.UI_MOVE);
        }
    }

    /** A touch on a category tab: the focus goes to the first entry of that category. */
    private void shopTab(int cat) {
        int pg = Math.min(catalog.pages() - 1, catalog.catFirstPage[cat]);
        if (pg < 0 || catalog.pageCat[pg] != cat) return;
        focus = catalog.pageStart[pg];
        shopPage = pg;
        sound.emit(SoundEvents.UI_MOVE);
    }

    /** Menu rows whose value is stepped with left/right (stage, hero, weapon, volumes, lighting). */
    public static boolean isCycleRow(int f) {
        return f == MENU_STAGE || f == MENU_HERO || f == MENU_WEAPON || f == MENU_MUSIC || f == MENU_SFX
                || f == MENU_LIGHT || f == MENU_ASC;
    }

    /** Step a menu row's value; true if it changed (a one-entry list or a clamped setting does not). */
    private boolean cycle(int item, int delta) {
        int before = cycleValue(item);
        if (item == MENU_STAGE) {
            stageOverride = null;
            stageIdx = (stageIdx + delta + stageKeys.size()) % stageKeys.size();
        }
        else if (item == MENU_HERO) meta.stepHero(delta);
        else if (item == MENU_MUSIC) meta.stepMusic(delta);
        else if (item == MENU_SFX) meta.stepSfx(delta);
        else if (item == MENU_LIGHT) meta.stepLighting(delta);
        else if (item == MENU_ASC) { // v1.5 B4: tiers 0..max of the stage, then the daily challenge
            int max = meta.ascMax(stage().key);
            int pos = dailyPick ? max + 1 : Math.min(ascTier, max);
            pos = (pos + delta + max + 2) % (max + 2);
            dailyPick = pos == max + 1;
            if (!dailyPick) ascTier = pos;
        }
        else weaponIdx = (weaponIdx + delta + starters.size()) % starters.size();
        return cycleValue(item) != before;
    }

    private int cycleValue(int item) {
        if (item == MENU_STAGE) return stageIdx;
        if (item == MENU_HERO) return meta.heroIdx;
        if (item == MENU_MUSIC) return meta.musicVol;
        if (item == MENU_SFX) return meta.sfxVol;
        if (item == MENU_LIGHT) return meta.lighting;
        if (item == MENU_ASC) return (dailyPick ? 100 : 0) + ascTier;
        return weaponIdx;
    }

    // ------------------------------------------------------------------ v1.5 B3: options, codex, remap

    /** Steps an options row; true when its value changed. Music, SFX and lighting are the menu's own settings. */
    private boolean optCycle(int row, int delta) {
        Settings st = Settings.get();
        switch (row) {
            case OPT_MUSIC: meta.stepMusic(delta); break;
            case OPT_SFX: meta.stepSfx(delta); break;
            case OPT_LIGHT: meta.stepLighting(delta); break;
            case OPT_SHAKE: st.stepShake(delta); break;
            case OPT_NUMBERS: st.stepNumbers(delta); break;
            case OPT_TELE: st.setShapes(!st.colorblindShapes()); break;
            case OPT_TEXT: st.setLarge(!st.largeText()); break;
            case OPT_AIM: st.stepAim(delta); break;
            case OPT_HAPTICS: st.setHaptics(!st.haptics()); break;
            default: return false;
        }
        return true;
    }

    /** Move in the codex grid (6 columns); {@code items} is the entry count, the focus {@code items} is BACK. */
    private int codexNav(int dir, int f, int items) {
        if (dir == Input.PAGE_PREV || dir == Input.PAGE_NEXT) {
            setCodexTab(codexTab + (dir == Input.PAGE_NEXT ? 1 : Codex.KINDS - 1));
            return 0;
        }
        if (f >= items) { // BACK
            if (dir == Input.UP && items > 0) return items - 1;
            return f;
        }
        switch (dir) {
            case Input.LEFT: return Math.max(0, f - 1);
            case Input.RIGHT: return Math.min(items - 1, f + 1);
            case Input.UP: return f >= CODEX_COLS ? f - CODEX_COLS : f;
            case Input.DOWN:
                if (f + CODEX_COLS < items) return f + CODEX_COLS;
                return f / CODEX_COLS == (items - 1) / CODEX_COLS ? items : items - 1;
            default: return f;
        }
    }

    private void setCodexTab(int t) {
        int n = ((t % Codex.KINDS) + Codex.KINDS) % Codex.KINDS;
        if (n != codexTab) sound.emit(SoundEvents.UI_MOVE);
        codexTab = n;
        focus = 0;
    }

    /** A gamepad button pressed on the remap screen while an action waits for one. */
    private void bindButton(int code) {
        int a = remapListening;
        if (phase != PH_REMAP || a < 0) return;
        int r = Settings.get().buttons.bind(a, code);
        if (r == ButtonMap.BIND_OK) {
            remapListening = -1;
            toast(ButtonMap.NAMES[a] + " = " + ButtonMap.buttonName(code));
            sound.emit(SoundEvents.UI_CONFIRM);
        } else if (r >= 0) {
            toast(ButtonMap.buttonName(code) + " is already " + ButtonMap.NAMES[r]);
            sound.emit(SoundEvents.UI_BACK);
        }
    }

    private void confirm(int sub) {
        int f = focus;
        int ph = phase;
        boolean ok = true;
        switch (phase) {
            case PH_MENU:
                if (f == MENU_START) ok = tryStartRun();
                else if (isCycleRow(f)) {
                    cycle(f, sub == 0 ? 1 : sub);
                }
                else if (f == MENU_SHOP) {
                    phase = PH_SHOP;
                    focus = 0;
                    shopPage = 0;
                }
                else if (f == MENU_CODEX) { // v1.5 B3
                    phase = PH_CODEX;
                    focus = 0;
                }
                else if (f == MENU_OPTIONS) {
                    phase = PH_OPTIONS;
                    focus = 0;
                }
                break;
            case PH_OPTIONS:
                if (f < OPT_REMAP) optCycle(f, sub == 0 ? 1 : sub);
                else if (f == OPT_REMAP) {
                    phase = PH_REMAP;
                    focus = 0;
                    remapListening = -1;
                } else {
                    phase = PH_MENU;
                    focus = MENU_OPTIONS;
                }
                break;
            case PH_REMAP:
                if (remapListening >= 0) break;
                if (f < ButtonMap.COUNT) {
                    remapListening = f;
                } else if (f == REMAP_RESET) {
                    Settings.get().buttons.reset();
                    toast("Buttons reset");
                } else {
                    phase = PH_OPTIONS;
                    focus = OPT_REMAP;
                }
                break;
            case PH_CODEX:
                if (f >= codex.size(codexTab)) {
                    phase = PH_MENU;
                    focus = MENU_CODEX;
                }
                break;
            case PH_SHOP:
                if (f == shopKeys.size()) {
                    phase = PH_MENU;
                    focus = MENU_SHOP;
                    shopRefund = false;
                } else if (f > shopKeys.size()) {
                    shopRefund = !shopRefund;
                    toast(shopRefund ? "Refund mode: select to refund (100%)" : "Refund mode off");
                } else ok = shopAct(catalog.entries.get(f), shopKeys.get(f));
                break;
            case PH_LEVELUP: {
                List<Offer> o = offers;
                if (f < o.size() && banishMode) { // v1.5 B4: strike the card's weapon or passive from this run's pool
                    Offer b = o.get(f);
                    if (b.def == null || b.kind == Offer.FUSE || b.kind == Offer.BRANCH || banishesLeft <= 0) {
                        ok = false;
                    } else {
                        banned.add(b.def.key);
                        banishesLeft--;
                        banishMode = false;
                        offers = rollOffers();
                        focus = 0;
                    }
                } else if (f < o.size()) {
                    Sim s = sim;
                    s.applyOffer(o.get(f));
                    s.pendingLevelUps--;
                    banishMode = false;
                    if (s.pendingLevelUps > 0) openLevelUp();
                    else resume();
                } else if (f == o.size()) { // REROLL
                    if (rerollsLeft > 0) {
                        rerollsLeft--;
                        banishMode = false;
                        rerollOffers();
                        focus = 0;
                    } else ok = false;
                } else if (f == o.size() + 1) { // BANISH: the next card tapped is struck instead of taken
                    if (banishesLeft > 0) banishMode = !banishMode;
                    else ok = false;
                }
                break;
            }
            case PH_PAUSED:
                if (f == 0) resume();
                else quitRun();
                break;
            case PH_MERCHANT:
                if (f >= sim.merchant.cards()) {
                    resume();
                } else {
                    Merchant m = sim.merchant;
                    int r = m.buy(f);
                    if (r == Merchant.BUY_OK) toast("Purchased " + m.name[f]);
                    else {
                        toast(r == Merchant.BUY_POOR ? "Not enough gold" : "Already sold");
                        ok = false;
                    }
                }
                break;
            case PH_TERMINAL:
                if (f < Pois.CARDS) sim.pois.pick(f);
                else sim.pois.declined();
                resume();
                break;
            case PH_EQUIP:
                answerEquip(f == 1); // card 0 = Keep, card 1 = Replace
                break;
            case PH_CHEST:
                resume();
                break;
            case PH_RESULT:
                phase = PH_MENU;
                focus = 0;
                break;
            case PH_PLAYING:
                phase = PH_PAUSED;
                focus = 0;
                break;
            default:
        }
        if (ph != PH_PLAYING) sound.emit(ok ? SoundEvents.UI_CONFIRM : SoundEvents.UI_BACK);
    }

    private void resume() {
        input.clearMovement();
        phase = PH_PLAYING;
        focus = 0;
    }

    private void quitRun() {
        // abandoning still banks the gold collected so far
        Sim s = sim;
        if (s != null) {
            meta.addGold(s.gold);
            meta.recordRun((int) s.time, s.kills, s.stage.key);
        }
        phase = PH_MENU;
        focus = 0;
    }

    private void back() {
        switch (phase) {
            case PH_SHOP: phase = PH_MENU; focus = MENU_SHOP; shopRefund = false; break;
            case PH_OPTIONS: phase = PH_MENU; focus = MENU_OPTIONS; break; // v1.5 B3
            case PH_CODEX: phase = PH_MENU; focus = MENU_CODEX; break;
            case PH_REMAP:
                if (remapListening >= 0) remapListening = -1; // B cancels the wait
                else { phase = PH_OPTIONS; focus = OPT_REMAP; }
                break;
            case PH_PLAYING: phase = PH_PAUSED; focus = 0; break;
            case PH_PAUSED: resume(); break;
            case PH_LEVELUP: banishMode = false; break; // v1.5 B4: B leaves the banish mode
            case PH_EQUIP: answerEquip(false); break; // B = keep what you wear
            case PH_MERCHANT: resume(); break; // B = leave the stall
            case PH_TERMINAL: sim.pois.declined(); resume(); break; // B = decline: the terminal stays unused
            case PH_RESULT: phase = PH_MENU; focus = 0; break;
            default:
        }
    }

    private void start() {
        switch (phase) {
            case PH_PLAYING: phase = PH_PAUSED; focus = 0; break;
            case PH_PAUSED: resume(); break;
            case PH_MENU:
                if (!tryStartRun()) sound.emit(SoundEvents.UI_BACK); // the refusal buzz (the same sound as a refused purchase)
                break;
            default:
        }
    }
}
