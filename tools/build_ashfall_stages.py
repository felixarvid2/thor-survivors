#!/usr/bin/env python3
"""Writes the Ashfall roster, the two stages and their lighting into app/src/main/assets/ashfall_data.json.

Design sections 4 (enemies, relative stats in Z units of the ZOMBIE row), 9 (stages, wave tables) and 13 (schema).
The `mods` of the stages and the treasure tables of the boss minutes are copied from game_data.json (the Vampire
Survivors stages FOREST and LIBRARY), so the XP and gold curves stay familiar. Deep Mines additionally gets enemy health
x1.25 and speed x1.05 on top of the LIBRARY mods (it is the second stage).

Run from thor-survivors/:  python3 tools/build_ashfall_stages.py
Only the keys `enemies`, `stages` (FLATS, MINES), `lighting` (FLATS, MINES), `elites`, `powerups`, `equipment`, `equipmentRules` and `fusions`
are touched, plus the balance numbers listed in HERO_TUNE and MAIN_TUNE (design phase 12: they are applied on top of the
heroes and mainWeapons that are already in the file; names, texts and branches stay as they are).
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")

# --------------------------------------------------------------------------- enemies (design section 4)
ENEMIES = {
    "COLONIST": dict(name="Infected Colonist", archetype="CHASER", maxHp=1.0, speed=100, power=12, xp=1,
                     sprite="infected_colonist", params=dict(radius=11)),
    "SPORE_HOUND": dict(name="Spore Hound", archetype="FAST_PACK", maxHp=0.5, speed=160, power=7, xp=1,
                        sprite="spore_hound",
                        params=dict(radius=9, group=4, weave=0.8, phase=1.0)),
    "SERGEANT": dict(name="Sergeant", archetype="RANGED", maxHp=1.5, speed=90, power=9, xp=2, sprite="sergeant",
                     params=dict(radius=12, stop=132, backoff=100, backoffMul=0.7, pause=0.5, burst=3, burstGap=0.15,
                                 interval=3.5, bulletSpeed=230, shotDmg=8, range=190, clip=1)),
    "SECURITY_DRONE": dict(name="Security Drone", archetype="FLIER", maxHp=0.7, speed=120, power=5, xp=2,
                           sprite="security_drone",
                           params=dict(radius=10, stop=150, backoff=120, backoffMul=0.8, pause=0.2, burst=1,
                                       burstGap=0, interval=2.4, bulletSpeed=270, shotDmg=6, range=210)),
    "WELDER": dict(name="Welder", archetype="CHARGER", maxHp=3.0, speed=110, power=18, xp=4, sprite="welder",
                   params=dict(radius=14, runSpeed=300, minDist=80, maxDist=160, gapMin=2, gapMax=6, windup=0.6,
                               dash=0.6, recover=1.0)),
    "SPORE_CARRIER": dict(name="Spore Carrier", archetype="EXPLODER", maxHp=1.2, speed=80, power=0, xp=3,
                          sprite="spore_carrier",
                          params=dict(radius=12, trigger=40, fuse=0.35, cloudR=85, cloudT=2.5, cloudDps=2)),
    "SMELTER": dict(name="Smelter", archetype="TANK", maxHp=8.0, speed=70, power=23, xp=8, knockback=0,
                    sprite="smelter",
                    params=dict(radius=18, castEvery=8, castTime=0.6, poolR=60, poolT=3, poolDps=6, warn=0.6,
                                pools=1)),
    "VIRE": dict(name="Vire", archetype="SUMMONER", maxHp=5.0, speed=90, power=16, xp=10, sprite="vire",
                 params=dict(radius=13, blinkMin=125, blinkMax=200, tele=0.2, wait=0.75, stopMin=2.5, stopMax=4,
                             raise_=3, ring=8, ringSpeed=220, ringDmg=10, castRange=180)),
    # ---- bosses (design 4.1): HP in Z at wave time 0, no extra boss factor; real patterns in game/BossAi (param `pattern`)
    "CARGO_LOADER": dict(name="Cargo Loader", archetype="BOSS", boss=True, maxHp=360, speed=70, power=22, xp=60,
                         sprite="cargo_loader",
                         params=dict(pattern=1, hpRamp=20, radius=20, runSpeed=320, minDist=80, maxDist=420, gapMin=1,
                                     gapMax=3.5, windup=1.0, dash=1.0, recover=1.0, lineW=60, stunHero=0.4,
                                     stompR=120, stompWarn=0.9, stompDmg=22, crates=3, crateR=44, crateWarn=1.0,
                                     crateDmg=16)),
    "SLAGBORN": dict(name="Slagborn", archetype="BOSS", boss=True, maxHp=520, speed=60, power=35, xp=100,
                     sprite="slagborn",
                     params=dict(pattern=2, hpRamp=20, radius=24, castEvery=8, castTime=0.8, bombs=3, bombsEnraged=5,
                                 bombR=46, bombWarn=1.0, bombDmg=12, poolR=44, poolT=3, poolDps=7, trail=45, trailT=4,
                                 trailDps=6, enrageHp=0.5, enrageMul=1.3, enrageCast=0.7)),
    "VIRE_AWAKENED": dict(name="Vire, Awakened", archetype="BOSS", boss=True, maxHp=540, speed=100, power=20,
                          xp=100, sprite="vire",
                          params=dict(pattern=3, hpRamp=20, radius=16, blinkMin=125, blinkMax=200, tele=0.2, wait=0.6,
                                      stopMin=2, stopMax=3.5, raise_=6, ring=12, ringSpeed=220, ringDmg=10,
                                      ringGap=0.1, phase1Hp=0.66, phase2Hp=0.33, carriers=4)),
    "SMELTER_FOREMAN": dict(name="Smelter, Foreman", archetype="BOSS", boss=True, maxHp=450, speed=65, power=30,
                            xp=100, sprite="smelter", scale=1.8,
                            params=dict(pattern=4, hpRamp=20, radius=12, castEvery=6, castTime=0.8, poolR=60, poolT=3,
                                        poolDps=6, warn=0.8, pools=5, pullHp=0.25, pullEvery=8, pullWarn=1.1,
                                        pullRange=340, pullSpeed=300, pullTime=0.7, slamR=70, slamDmg=18)),
    "THE_FIRST": dict(name="The First", archetype="BOSS", boss=True, maxHp=1800, speed=80, power=40, xp=250,
                      sprite="the_first",
                      params=dict(pattern=5, hpRamp=28, radius=28, phase1Hp=0.66, phase2Hp=0.33, ring=12,
                                  ringSpeed=200, ringDmg=12, ringEvery=3.5, ringEvery3=5, tentEvery=5, tentEvery3=8,
                                  tentCount=3, tentRadius=300, lanes=2, laneLen=560, laneW=46, laneDps=22,
                                  laneWarn=1.0, laneLife=3.2, laneSpin=0.85, laneGap=2.0, hounds=6, houndEvery=9,
                                  pulseEvery=6, pulseWarn=1.5, pulseDmg=28)),
    # ---- v1.3 timed bosses (design v1.3 section 3, BossAi patterns 6..10): spawned by `stages.<S>.timedBosses`, not by wave rows.
    # `tintRGB` recolours the baked model (WorldRenderer, luminance x tint), `gearSlot` is the EquipDef slot of the Rare crate it
    # drops (5 NECK, 4 RING, 0 HEAD, 2 GLOVES, 1 BODY), `xpPileLevels` the gem pile in levels, a chest of 1..3 items always comes.
    "BROOD_MOTHER": dict(name="Brood Mother", archetype="BOSS", boss=True, maxHp=220, speed=45, power=22, xp=100,
                         sprite="spore_carrier", scale=2.2,
                         description="Leaves spore clouds, lays egg pods that hatch hounds, bursts at half health.",
                         params=dict(pattern=6, hpRamp=20, radius=10, tintRGB=0x6FD060, gearSlot=5, xpPileLevels=1.5,
                                     trail=36, cloudR=50, cloudT=3, cloudDps=2.5, eggEvery=7, eggs=3, eggWarn=1.0,
                                     burstHp=0.5, burstN=6, burstR=90, burstWarn=1.0, burstDmg=10, burstDist=120,
                                     stopDist=60)),
    "FROZEN_WELDER": dict(name="Frozen Welder", archetype="BOSS", boss=True, maxHp=420, speed=70, power=24, xp=100,
                          sprite="welder", scale=2.0,
                          description="Three chained dashes, each leaving frost and ending in a freezing ring.",
                          params=dict(pattern=7, hpRamp=20, radius=12, tintRGB=0xA8F0FF, gearSlot=4, xpPileLevels=1.5,
                                      pre=0.9, dashLen=190, dash=0.45, gap=0.25, lineW=46, dashDmg=16, recover=2.0,
                                      gapMin=2.0, gapMax=3.5, range=380, patchEvery=80, patchR=38, patchT=5,
                                      ringR=140, ringExtra=0.3, ringDmg=14, ringStun=0.4)),
    "COMMANDANT_HALE": dict(name="Commandant Hale", archetype="BOSS", boss=True, maxHp=400, speed=60, power=26, xp=100,
                            sprite="sergeant", scale=2.2,
                            description="Calls artillery barrages and lines up sergeants for a synchronised volley.",
                            params=dict(pattern=8, hpRamp=20, radius=11, tintRGB=0x7FB0F0, gearSlot=0, xpPileLevels=1.5,
                                        barrageEvery=9, shells=8, shellSpan=3.0, shellWarn=1.0, shellR=52, shellDmg=14,
                                        orderEvery=12, guards=6, guardGap=46, guardDist=230, orderStand=1.8,
                                        guardLane=22, stopDist=170)),
    "OVERSEER_UNIT_7": dict(name="Overseer Unit 7", archetype="BOSS", boss=True, maxHp=300, speed=80, power=24, xp=100,
                            sprite="security_drone", scale=4.2,
                            description="Hovers out of reach: bullet spiral, sweeping laser, drone deployment.",
                            params=dict(pattern=9, hpRamp=20, radius=8, tintRGB=0xFF4A3A, gearSlot=2, xpPileLevels=1.5,
                                        hover=220, spiralTele=0.9, spiralTime=4.5, spiralArms=3, spiralGap=0.28,
                                        spiralTurn=0.3, spiralSpeed=170, spiralDmg=9, spiralLife=2.0, liveMax=24,
                                        laserLen=520, laserW=46, laserWarn=1.0, laserLife=2.4, laserSpin=0.75,
                                        laserDps=20, droneN=4, droneWarn=0.9, droneDist=120, rest=1.4,
                                        enrageHp=0.4, enrageMul=1.3)),
    "GOVERNOR_KADE": dict(name="Governor Kade", archetype="BOSS", boss=True, maxHp=650, speed=85, power=26, xp=150,
                          sprite="vire", scale=2.0,
                          description="Blinks and leaves decoys; issues Decrees that light four lanes, two of them safe.",
                          params=dict(pattern=10, hpRamp=20, radius=13, tintRGB=0xFFE08A, gearSlot=1, xpPileLevels=1.5,
                                      blinkEvery=7, blinkTele=0.8, blinkMin=130, blinkMax=200, decoys=3,
                                      decreeEvery=10, decreeWarn=1.2, laneW=64, laneGap=120, laneLen=900, laneDmg=24,
                                      carriers=4, carrierHp=0.5, stopDist=120)),
    # what the timed bosses summon: pinned like a tentacle (warn ring, then up), see BossAi.tentacle
    "BROOD_EGG": dict(name="Brood Egg", archetype="TENTACLE", maxHp=0.2, speed=0, power=0, xp=1, knockback=0,
                      sprite="spore_carrier",
                      params=dict(radius=10, warn=1.0, up=5.0, hitR=26, end=1, hounds=4, drawScale=0.7, tintRGB=0xC8E070)),
    "HALE_GUARD": dict(name="Hale's Guard", archetype="TENTACLE", maxHp=1.0, speed=0, power=0, xp=1, knockback=0,
                       sprite="sergeant",
                       params=dict(radius=12, warn=1.0, up=0.8, hitR=24, end=2, volleyN=3, volleySpread=0.14,
                                   volleySpeed=260, volleyDmg=9, tintRGB=0x7FB0F0)),
    "KADE_DECOY": dict(name="Kade's Decoy", archetype="TENTACLE", maxHp=0.1, speed=0, power=0, xp=1, knockback=0,
                       sprite="vire",
                       params=dict(radius=12, warn=0.9, up=8.0, hitR=60, ring=1, blast=12, drawScale=2.0,
                                   tintRGB=0xFFE08A)),
    # ---- v1.4 chunk E (Flats / Mines add-ons): the convoy's tanker (driven by FlatsRules, HP is set to 400 there) and the Dark Lurker
    # (a colonist variant that MinesRules draws only inside the light)
    "CONVOY_TANKER": dict(name="Convoy Tanker", archetype="CONVOY", maxHp=1.0, speed=0, power=0, xp=6, knockback=0,
                          sprite="cargo_loader",
                          params=dict(radius=26, drawScale=1.5, tintRGB=0x6C9AA8)),
    "DARK_LURKER": dict(name="Dark Lurker", archetype="LURKER", maxHp=2.0, speed=100, power=12, xp=2,
                        sprite="infected_colonist",
                        params=dict(radius=11, tintRGB=0x4A4A66)),
    # a killable tentacle: bursts from the ground (warn ring, then up for `up` seconds), see BossAi.tentacle.
    # Design 4.1 gives it 150 Z when exposed, which no build kills in the 1.2 s it is up: 8 Z here.
    "LUMEN_TENTACLE": dict(name="Lumen Tentacle", archetype="TENTACLE", maxHp=8.0, speed=0, power=25, xp=5,
                           knockback=0, sprite="lumen_tentacle", params=dict(radius=15, warn=0.8, up=1.2, hitR=34)),
}


# --------------------------------------------------------------------------- elites (design 5) and equipment (design 6)
# phase 12: an elite every 125 s (floor 90 s), the XP of an elite x4 and its pile 0.7 levels (was 8 and 2.0: the elites alone were a
# quarter of all XP late), the Armored modifier takes `armor` off every hit
ELITES = dict(first=120, interval=90, min=45, shrinkPerMin=0.02, hpMul=6, damageMul=1.5, scale=1.4, speedMul=0.9,
              xpMul=4, dist=400, adds=6, mods2At=480, mods3At=1080, xpPileGems=5, xpPileLevels=0.7, armor=3)

# --------------------------------------------------------------------------- phase 12 balance (heroes and main weapons)
# Applied on top of the heroes / mainWeapons already in ashfall_data.json: dotted path -> value. Measured with StageSoak (see the
# thor-survivors-balance skill): a main weapon is strong but no longer replaces the VS build, Spectre survives like the others.
HERO_TUNE = {
    # glass cannon, but not made of glass: HP 80 -> 105, armor 1, regen 0.5, and every level takes 0.7% less damage (-35% at level 50)
    "SPECTRE.hp": 115, "SPECTRE.armor": 2, "SPECTRE.regen": 0.7, "SPECTRE.perLevel.maxHp": 0.004,
    "SPECTRE.perLevel.damageTaken": -0.009,
    "BASTION.regen": 0.7,   # v1.4 (was 0.3): chronic attrition from bolts and slag is what kills the slow tank, see "Svårighet (v1.4)"
    # v1.3.1 hero parity (mid bot, 32 seeds): Spectre and Xenomant died of contact late (the damage curve makes a Colonist hit 96 at 30:00, Xenomant
    # had 95 HP and no armor), Bastion survived 3 in 4. Defence now grows with the level for all three.
    "BASTION.hp": 140, "BASTION.armor": 4, "BASTION.perLevel.maxHp": 0.007,                                               # was 0.010
    "SPECTRE.perLevel.power": 0.006, "SPECTRE.perLevel.maxHp": 0.005,              # new / was 0.004 (damageTaken stays -0.007)
    "XENOMANT.hp": 115, "XENOMANT.armor": 1,                                       # v1.4: 100 -> 115 (a third of the skilled Xenomants died at 5-10 min on Flats)
    "XENOMANT.perLevel.maxHp": 0.004, "XENOMANT.perLevel.damageTaken": -0.0015,    # new
}
MAIN_TUNE = {
    "HAMMER.power": 1.5, "HAMMER.params.slamPower": 2.2, "HAMMER.interval": 1200,   # was 1.6 / 2.4 / 1100
    "CASTER.power": 0.5, "CASTER.params.burstPower": 0.3,                              # was 1.2 / 0.5
    # PISTOLS stay as they were (0.6, no pierce): pierce 1 made Spectre the strongest main weapon (111% of the Whip) without helping his survival
}
HERO_TEXT = {
    "BASTION": "Hydraulic Hammer swings in a cone and slams every third swing. Tough and slow. Each level: +1% might, +0.7% max HP.",
    "SPECTRE": "Twin Pistols fire a volley down the aim line. Fragile and fast. Each level: +1% crit chance, +0.5% move speed, +0.6% might, +0.5% max HP, -0.7% damage taken.",
    "XENOMANT": "Lumen Caster sends a piercing bolt and a Spore Burst every fourth cast. Each level: +1% area, +1% pickup range, +0.4% max HP, -0.15% damage taken.",
}
# the gem value of a kill fades with the run time (StageDef.xpFade): keeps the early levels, stops the XP flood (level 140 -> about 75 at 30:00)
# v1.3.1: deeper and earlier end (level at 30:00 of the survivors 120 -> about 90): Flats 0.15 at 30:00 -> 0.02 at 23:00, Mines 0.08 at 25:00 -> 0.02 at 22:00
XP_FADE = {"FLATS": dict(**{"from": 420, "to": 1380, "end": 0.02}), "MINES": dict(**{"from": 360, "to": 1320, "end": 0.02})}
FLATS_HEALTH, MINES_HEALTH = 1.2, 1.5   # EnemyHealthMultiplier (the Ashfall bosses scale with it too)

# slot, name, icon (atlas key icon.<icon>), values (PlayerStats keys; a base item's values are the Common ones)
EQUIPMENT = {
    "COMBAT_HELM": ("HEAD", "Combat Helm", "combat_helm", {"armor": 1}),
    "HEADLAMP": ("HEAD", "Headlamp", "bio_focus", {"lightRadius": 0.10}),
    "TARGETING_VISOR": ("HEAD", "Targeting Visor", "combat_helm_2", {"critChance": 0.04}),
    "PLATED_VEST": ("BODY", "Plated Vest", "plated_vest", {"maxHp": 0.10}),
    "RIOT_SHIELD": ("BODY", "Riot Shield", "riot_shield", {"damageTaken": -0.04}),
    "INJECTOR_RIG": ("BODY", "Injector Rig", "injector_rig", {"regen": 0.2}),
    "SERVO_GAUNTLETS": ("GLOVES", "Servo Gauntlets", "servo_gauntlets", {"power": 0.05}),
    "HYDRAULIC_KNUCKLES": ("GLOVES", "Hydraulic Knuckles", "hydraulic_knuckles", {"mainDamage": 0.08}),
    "TRIGGER_GLOVES": ("GLOVES", "Trigger Gloves", "servo_gauntlets_2", {"cooldown": -0.04}),
    "MAG_BOOTS": ("FEET", "Mag Boots", "mag_boots", {"moveSpeed": 0.05}),
    "ARMORED_GREAVES": ("FEET", "Armored Greaves", "armored_greaves", {"armor": 1, "knockbackTaken": -0.10}),
    "QUICK_SOLES": ("FEET", "Quick Soles", "mag_boots_2", {"dodgeCooldown": -0.10}),
    "CONDUIT_RING": ("RING", "Conduit Ring", "conduit_ring", {"statusChance_electrify": 0.06}),
    "SIGNET_OF_ASH": ("RING", "Signet of Ash", "conduit_ring_2", {"statusChance_burn": 0.06}),
    "FROST_BAND": ("RING", "Frost Band", "conduit_ring_3", {"statusChance_frost": 0.06}),
    "SIGNAL_PENDANT": ("NECK", "Signal Pendant", "signal_pendant", {"magnet": 0.15}),
    "ECHO_RELIC": ("NECK", "Echo Relic", "echo_relic", {"growth": 0.04}),
    "LUMEN_PENDANT": ("NECK", "Lumen Pendant", "crystal_lumen", {"luck": 0.05}),
}
# uniques: fixed values (no rarity multiplier), dropped by the named boss (the Seed by a tentacle killed in a hazard burst)
UNIQUES = {
    "CROWN_OF_THE_HIVE": ("HEAD", "Crown of the Hive", "unique_crown_of_the_hive",
                          {"amount": 1, "enemySpeed": 0.05}, "VIRE_AWAKENED"),
    "ASHEN_HEARTWOOD": ("BODY", "Ashen Heartwood", "unique_ashen_heartwood",
                        {"maxHp": 0.25, "burnImmune": 1}, "SLAGBORN"),
    "FIST_OF_KHAROS": ("GLOVES", "Fist of Kharos", "unique_fist_of_kharos",
                       {"mainDamage": 0.30, "mainSize": 0.10}, "CARGO_LOADER"),
    "ASHWALKER_TREADS": ("FEET", "Ashwalker Treads", "unique_ashwalker_treads",
                         {"moveSpeed": 0.15, "ashTrail": 1}, "SMELTER_FOREMAN"),
    "HEART_OF_LUMEN": ("NECK", "Heart of Lumen", "unique_heart_of_lumen",
                       {"revivals": 1, "lightRadius": 0.15}, "THE_FIRST"),
    "SEED_OF_ACCORD": ("RING", "Seed of Accord", "unique_seed_of_accord", {"cloudHeal": 1}, "LUMEN_TENTACLE"),
}
# value multipliers Common / Uncommon / Rare, a unique counts as 3.0 when compared, salvage pays 5 gold per point,
# the rarity roll starts at 60 / 32 / 8 at minute 3 and moves 1.2 points per minute from Common to Rare
EQUIPMENT_RULES = dict(mult=[1.0, 1.6, 2.4], uniqueValue=3.0, salvage=5, common=60, uncommon=32, rare=8,
                       shiftPerMin=1.2, fromMinute=3, commonMin=10, luckPoints=20)

# --------------------------------------------------------------------------- weapon fusions (design v1.3 section 1)
# Seven fusions of two mastered weapons (base weapon at max level or its evolved form), 6 levels each. Level 1 is the full
# stat block, levels 2-6 are deltas (the same layout as the weapons of game_data.json): +8% power per level, and either
# +1 amount at levels 3 and 5 (Needle Choir, Miasma Censer, Faraday Halo) or -5% interval at level 4 (the others).
# Power starts at 1.5 x the stronger parent's maximum, interval near the mean of the parents x0.9; the numbers below are the
# result of measuring each fusion against its parents on a seeded 200-enemy field (see TEKNIK.md "Vapenfusioner").
FUSION_KEYS = ["STORMLASH", "FOUNDRY_SPILL", "NEEDLE_CHOIR", "MIASMA_CENSER", "RICOCHET_ORDNANCE", "FARADAY_HALO", "KILN_BREATH"]
FUSIONS = {
    "STORMLASH": dict(
        name="Stormlash", parents=["WHIP", "LIGHTNING"], amountSteps=False,
        description="Vibro Lash slashes that chain lightning: every enemy hit passes half the damage to the 2 nearest others.",
        l1=dict(interval=1500, repeatInterval=100, power=10.3, area=1.3, speed=1, amount=2, critChance=0.2, critMul=2)),
    "FOUNDRY_SPILL": dict(
        name="Foundry Spill", parents=["FIREBALL", "HOLYWATER"], amountSteps=False,
        description="A flask that leaves a burning pool. The pool lobs a fire burst every half second.",
        l1=dict(interval=3800, repeatInterval=300, power=1.6, area=1.75, speed=1, amount=3, duration=4000, hitBoxDelay=500, knockback=0)),
    "NEEDLE_CHOIR": dict(
        name="Needle Choir", parents=["MAGIC_MISSILE", "KNIFE"], amountSteps=True,
        description="A fan of homing needles toward the nearest enemy. Piercing, fast repeat.",
        l1=dict(interval=700, repeatInterval=100, power=6.2, area=1, speed=1.3, amount=3, penetrating=2, knockback=0.2, critChance=0.2, critMul=2)),
    "MIASMA_CENSER": dict(
        name="Miasma Censer", parents=["GARLIC", "HOLYBOOK"], amountSteps=True,
        description="Censers orbit you, each with its own toxic aura. The orbit breathes in and out.",
        l1=dict(interval=3000, repeatInterval=0, power=1.06, area=1.2, speed=1.2, amount=3, duration=4000, hitBoxDelay=600, knockback=0.1)),
    "RICOCHET_ORDNANCE": dict(
        name="Ricochet Ordnance", parents=["AXE", "CROSS"], amountSteps=False,
        description="A boomerang bomb. It detonates at the turn point and again on the way back.",
        l1=dict(interval=1800, repeatInterval=100, power=8.9, area=1.2, speed=1.5, amount=1, critChance=0.2, critMul=2)),
    "FARADAY_HALO": dict(
        name="Faraday Halo", parents=["HOLYBOOK", "LIGHTNING"], amountSteps=True,
        description="Drones orbit you and call a lightning bolt on the nearest enemy.",
        l1=dict(interval=3000, repeatInterval=0, power=2.7, area=1.5, speed=1.3, amount=4, duration=4000, hitBoxDelay=800, knockback=0.1)),
    "KILN_BREATH": dict(
        name="Kiln Breath", parents=["GARLIC", "FIREBALL"], amountSteps=False,
        description="A burning aura that sends out an expanding ring of fire every three seconds.",
        l1=dict(interval=1000, repeatInterval=0, power=0.22, area=1.4, speed=1, amount=1, knockback=0)),
}


def fusion_rows(f):
    l1 = dict(f["l1"])
    rows = [l1]
    for lv in range(2, 7):
        d = {"power": round(l1["power"] * 0.08, 4)}
        if f["amountSteps"]:
            if lv in (3, 5):
                d["amount"] = 1
        elif lv == 4:
            d["interval"] = -round(l1["interval"] * 0.05)
        rows.append(d)
    return rows


# --------------------------------------------------------------------------- wave tables (design 9.3)
K = dict(COL="COLONIST", HND="SPORE_HOUND", SGT="SERGEANT", DRN="SECURITY_DRONE", WLD="WELDER", SPC="SPORE_CARRIER",
         SML="SMELTER", VIR="VIRE")
ALL = list(K.values())


def row(minute, minimum, freq, enemies, bosses=()):
    return dict(minute=minute, minimum=minimum, frequency=freq,
                enemies=ALL if enemies == "all" else [K[e] for e in enemies.split()], bosses=list(bosses))


FLATS_ROWS = [
    row(0, 10, 1400, "COL"), row(1, 14, 1200, "COL HND"), row(2, 18, 1100, "COL HND"),
    row(3, 22, 1000, "COL HND SGT"), row(4, 26, 900, "COL HND SGT"),
    row(5, 20, 1100, "HND SGT SPC", ["CARGO_LOADER"]), row(6, 24, 1000, "COL SGT SPC"),
    row(7, 30, 900, "COL HND DRN"), row(8, 32, 850, "HND SGT DRN SPC"), row(9, 36, 800, "COL WLD DRN"),
    row(10, 40, 800, "HND WLD SPC", ["SMELTER_FOREMAN"]), row(11, 44, 750, "COL SGT WLD"),
    row(12, 48, 750, "HND DRN SPC WLD"), row(13, 52, 700, "COL SGT DRN"), row(14, 56, 700, "HND WLD SML"),
    row(15, 60, 650, "COL SGT SML SPC", ["SLAGBORN"]), row(16, 64, 650, "HND DRN WLD"),
    row(17, 68, 600, "COL SGT SML"), row(18, 72, 600, "HND SPC WLD DRN"), row(19, 76, 600, "COL SML VIR"),
    row(20, 80, 550, "HND SGT VIR WLD", ["VIRE_AWAKENED"]), row(21, 84, 550, "COL DRN SML"),
    row(22, 88, 500, "HND SPC WLD"), row(23, 92, 500, "COL SGT SML VIR"), row(24, 96, 500, "HND DRN WLD SPC"),
    row(25, 100, 450, "COL SML VIR", ["CARGO_LOADER", "CARGO_LOADER"]), row(26, 110, 450, "HND SGT DRN WLD"),
    row(27, 120, 400, "COL SML VIR SPC"), row(28, 130, 400, "HND WLD SML VIR"),
    row(29, 140, 350, "all", ["SLAGBORN", "SMELTER_FOREMAN"]),
]
# Deep Mines rows exactly as listed in 9.3; the minutes in between inherit the previous row (WaveSelector).
MINES_ROWS = [
    row(0, 12, 1300, "COL"), row(1, 16, 1100, "COL HND"), row(2, 20, 1000, "HND DRN"),
    row(3, 24, 900, "COL HND SGT"), row(4, 28, 850, "HND DRN"), row(5, 22, 1000, "HND SPC SGT", ["CARGO_LOADER"]),
    row(6, 28, 900, "COL WLD DRN"), row(7, 34, 850, "HND SPC WLD"), row(8, 38, 800, "COL SGT DRN"),
    row(9, 42, 750, "HND WLD SML"), row(10, 46, 750, "COL DRN SPC", ["SLAGBORN"]), row(12, 52, 700, "HND SGT VIR"),
    row(14, 60, 650, "COL WLD SML"), row(15, 64, 600, "HND DRN SPC", ["SMELTER_FOREMAN"]),
    row(17, 72, 600, "COL SML VIR"), row(19, 80, 550, "HND WLD DRN"),
    row(20, 84, 550, "SGT SML VIR", ["VIRE_AWAKENED"]), row(22, 92, 500, "HND SPC WLD"),
    row(24, 100, 450, "COL SML VIR DRN"),
    row(25, 110, 450, "HND WLD SML", ["CARGO_LOADER", "CARGO_LOADER", "SLAGBORN"]), row(27, 120, 400, "all"),
    row(28, 130, 375, "all", ["THE_FIRST"]), row(29, 140, 350, "all"),
]

# --------------------------------------------------------------------------- v1.4 shop prices (salvage terminal, permanent)
# GameData overlay key `powerups`: rank n+1 of a power-up costs price * (1 + n) ** exp, rounded to 10 (exp 1 = the Vampire Survivors line price * (1 + n)).
# The strongest upgrades (Might, Cooldown, Growth) get exp 1.5, Amount and Revival a higher flat price. Whole catalog 78 090 -> about 119 000 gold; measured
# income per run (StageSoak, skilled bot, 192 runs): 1 925 on average, 3 200-3 600 for a run that survives to the end (TEKNIK "Svårighet (v1.4)").
# Curse (a handicap, not an upgrade) is left at the Vampire Survivors price. A new stage does not touch this table.
POWERUP_PRICES = {
    "POWER": (400, 1.5), "ARMOR": (700, 1.3), "MAXHEALTH": (350, 1.3), "REGEN": (260, 1.3), "COOLDOWN": (1300, 1.5),
    "AREA": (350, 1.2), "SPEED": (350, 1.2), "DURATION": (350, 1.2), "AMOUNT": (10000, 1.0), "MOVESPEED": (350, 1.2),
    "MAGNET": (350, 1.2), "LUCK": (650, 1.2), "GROWTH": (700, 1.5), "GREED": (300, 1.3), "REVIVAL": (18000, 1.0),
}

# --------------------------------------------------------------------------- phase 13 / v1.4 difficulty (measured with StageSoak, see TEKNIK "Svårighet")
# Every knob lives in one dict PER STAGE (STAGE_TUNE below; a new stage lists only what differs from TUNE_DEFAULT):
#   min_table   [minute, minimum alive] linear between points, flat after; a row with a boss gets x boss_row_mul
#   freq_table  [minute, ms between spawn ticks] (never slower than the row's own frequency)
#   hp_curve / dmg_curve / speed_curve  [minute, multiplier] on HP, contact+bolt damage and walking speed of wave enemies and elites
#   hordes      [start s, minimum] bursts of horde_len s of cheap enemies (horde_types) that fill the field; horde_freq ms per spawn tick
#   elites      overrides of the global ELITES numbers for this stage (first, interval, min, shrinkPerMin, hpMul, damageMul, mods2At ...)
#   ranged_cap  ranged enemies alive at once (0 = Sim.RANGED_CAP 24); extra_types [[minute, key]] added to every wave row from that minute
# v1.4: the mid bot was far weaker than the owner, so v1.3.1 (tuned to 43 % for the mid bot) was too easy for a human. The yardstick is now
# the `skilled` policy of StageSoak (kites, dodges, plays a build): survival to 30:00 about 35-45 %, pressure in every 5 minute band.
DIFF_FROM = 0
TUNE_DEFAULT = dict(
    from_minute=0,
    min_table=[[0, 16], [2, 30], [4, 48], [6, 60], [9, 70], [12, 85], [15, 110], [18, 150], [21, 200], [24, 250], [27, 290], [29, 300]],
    freq_table=[[0, 1000], [5, 800], [10, 700], [16, 550], [20, 450], [25, 350], [29, 300]],
    hp_curve=[[0, 1], [10, 1.2], [15, 1.6], [20, 2.4], [25, 5], [30, 9]],
    dmg_curve=[[0, 1.35], [3, 1.4], [6, 1.35], [10, 1.65], [13, 1.98], [16, 2.64], [20, 3.5], [24, 5], [30, 7.5]],
    speed_curve=[[0, 1.0], [5, 1.1], [10, 1.25], [15, 1.4], [20, 1.65], [25, 1.92], [30, 2.25]],
    hordes=[[200, 50], [370, 70], [520, 90], [675, 110], [825, 140], [975, 170], [1125, 200], [1245, 250], [1420, 290], [1545, 290]],
    horde_types=["COLONIST", "SPORE_HOUND"], horde_len=16, horde_freq=250,
    boss_row_mul=0.85, elites={}, ranged_cap=0, extra_types=[[1, "SGT"]],
)
STAGE_TUNE = {
    # the new stages inherit TUNE_DEFAULT and list only what differs; their own rows carry their roster (no extra Sergeants)
    "HYDRO": dict(extra_types=[], horde_types=["COLONIST", "SPORE_HOUND", "MYCELID"],   # the 975 s horde moves to 990: the 16:00 Sporefall runs 960-980
                  hordes=[[200, 50], [370, 70], [520, 90], [675, 110], [825, 140], [990, 170], [1125, 200], [1245, 250], [1420, 290], [1545, 290]]),
    "REFINERY": dict(extra_types=[]),
    "WRECK": dict(extra_types=[]),
    "FLATS": dict(extra_types=[]),   # v1.4: the Flats rows already carry Sergeants; extra ones made 5-10 min lethal (14 of 32 Xenomants died there)
    "MINES": dict(hordes=[[200, 50], [370, 70], [520, 90], [675, 110], [825, 140], [975, 170], [1125, 200], [1245, 250], [1430, 290], [1600, 290]], extra_types=[[1, "SGT"], [3, "DRN"]],
                  dmg_curve=[[0, 1.4], [3, 1.45], [6, 1.45], [10, 1.72], [13, 2.0], [16, 2.64], [20, 3.5], [24, 5], [30, 7.5]]),
}
# Hordes sit between the bosses and events: nominal starts 675, 825, ..., 1575 (every 150 s) shifted off every timed boss (7:30, 12:30,
# 17:30, 22:30, Kade 27:30 Flats / 26:30 Mines) and event start (Signal Tower 1260, Lumen Surge 1440, Beacon Flats 1560, Collapse Mines 1405-1430).


def tune_of(stage):
    t = dict(TUNE_DEFAULT)
    t.update(STAGE_TUNE.get(stage, {}))
    ov = os.environ.get("TUNE_JSON")           # experiments: {"FLATS": {"speed_curve": ...}, "*": {...}}
    if ov:
        o = json.loads(ov)
        t.update(o.get("*", {}))
        t.update(o.get(stage, {}))
    return t


def interp(table, x):
    if x <= table[0][0]:
        return table[0][1]
    for (x0, y0), (x1, y1) in zip(table, table[1:]):
        if x <= x1:
            return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
    return table[-1][1]


def apply_difficulty(rows, tune):
    """Fills every minute from tune.from_minute (the sparse Mines table inherits composition) and sets minimum / frequency by formula."""
    out, last = [], None
    by_min = {r["minute"]: r for r in rows}
    frm = tune["from_minute"]
    mt, ft = tune["min_table"], tune["freq_table"]
    for m in range(0, 30):
        if m in by_min:
            last = by_min[m]
            r = dict(last)
        elif m >= frm:
            r = dict(last, minute=m, bosses=[])
        else:
            continue
        r["enemies"] = list(r["enemies"])
        for (mm, key) in tune["extra_types"]:
            if m >= mm and K[key] not in r["enemies"]:
                r["enemies"].append(K[key])
        if m >= frm:
            r["minimum"] = int(round(max(mt[0][1], interp(mt, m) * (tune["boss_row_mul"] if r["bosses"] else 1.0))))
            if m >= ft[0][0]:
                r["frequency"] = int(min(r["frequency"], round(interp(ft, m))))
        out.append(r)
    return out


# which Vampire Survivors treasure row each boss minute copies
# v1.3 timed bosses (seconds into the run): they come between the wave-row bosses, see Sim.updateTimedBosses
TIMED_BOSSES = {
    "FLATS": [(450, "BROOD_MOTHER"), (750, "FROZEN_WELDER"), (1050, "COMMANDANT_HALE"), (1350, "OVERSEER_UNIT_7"),
              (1650, "GOVERNOR_KADE")],
    "MINES": [(450, "BROOD_MOTHER"), (750, "FROZEN_WELDER"), (1050, "COMMANDANT_HALE"), (1350, "OVERSEER_UNIT_7"),
              (1590, "GOVERNOR_KADE")],
}
# v1.4 events of minutes 20-30 (seconds into the run), see game/Events. Shifted from design v1.3 section 4 where a timed boss
# would collide: Collapse on Deep Mines starts 23:25 (the tentacle burst of minute 23 runs 23:00-23:20), the Mines Beacon runs
# 25:30-26:30 (Governor Kade arrives 26:30); Flats keep 23:00 and 26:00. The Tide ends where the closing bosses come.
EVENTS = {
    "FLATS": [("SIGNAL_TOWER", 1260), ("SLAG_RAIN", 1380), ("LUMEN_SURGE", 1440), ("BEACON", 1560), ("TIDE", 1680)],
    "MINES": [("SIGNAL_TOWER", 1260), ("COLLAPSE", 1405), ("LUMEN_SURGE", 1440), ("BEACON", 1530), ("TIDE", 1620)],
}
FLATS_TREASURE = {5: 5, 10: 10, 15: 15, 20: 20, 25: 25, 29: 29}   # from FOREST
MINES_TREASURE = {5: 5, 10: 10, 15: 15, 20: 20, 25: 25, 28: 28}   # from LIBRARY


def treasure_rows(vs_stage, mapping):
    by_min = {r["minute"]: r for r in vs_stage}
    return {m: by_min[src]["treasure"] for m, src in mapping.items()}


# ---------------------------------------------------------------------------------------------------------------------------
# v1.4 stage registry (stage framework, chunk A). One entry per menu stage: the card text, the difficulty pips, what opens it
# (stage key, best time in seconds), the stage-rules parameters (StageDef.rules, read by game/stage/*Rules) and, for the stages
# added in v1.4, the name, the mods relative to Cinder Flats and the darkness. The new stages start as a copy of Cinder Flats (rows,
# timed bosses, events, curves, hordes) and are tuned by their own chunks; the relative mods are the first guesses of the spec.
# ---------------------------------------------------------------------------------------------------------------------------
STAGE_REGISTRY = {
    "FLATS": dict(difficulty=1, blurb="Open ash plains. Storms, vents, convoys.", unlock=None,
                  # convoy start seconds: 8:00 / 16:00 / 24:00 shifted +15 / +30 / +45 s to keep clear of the timed bosses (7:30, 12:30,
                  # 17:30, 22:30), the events (Lumen Surge starts 24:00), the horde bursts and the storms (checked in FlatsAddonTest)
                  rules=dict(convoyAt0=495, convoyAt1=990, convoyAt2=1485)),
    "MINES": dict(difficulty=2, blurb="Dark shafts and carts. Mind the firedamp.", unlock=("FLATS", 600), rules={}),
    "HYDRO": dict(name="Hydroponic Vaults", difficulty=3, blurb="Overgrown domes. Burn the hearts or drown.",
                  unlock=("MINES", 900), rules=dict(firstHeart=120, purgeAt=1260, bloomAt=1440), copy_of="FLATS", lighting=dict(ambient=0.71, radius=240),
                  mods=dict(EnemyHealthMultiplier=1.3, EnemySpeed=1.0, GoldMultiplier=1.2, XPBonus=1.1)),
    "REFINERY": dict(name="Refinery District", difficulty=4, blurb="Belts, furnaces, heat. Never stand still.",
                     unlock=("HYDRO", 1200), rules={}, copy_of="FLATS", lighting=dict(ambient=0.55, radius=240),
                     tune="refinery_tune",   # HP and speed relative to Flats x1.0 for now (spec: x1.4 / x1.05): with those the mid bot dies at 4.5 min on average, 0 of 12 pass 8 min
                     mods=dict(EnemyHealthMultiplier=1.0, EnemySpeed=1.0, GoldMultiplier=1.35, XPBonus=1.1)),
    "WRECK": dict(name="Meridian Wreck", difficulty=5, blurb="A falling ship. Wells, pads, no air.",
                  unlock=("REFINERY", 1500), rules={}, copy_of="FLATS", lighting=dict(ambient=0.77, radius=230),
                  mods=dict(EnemyHealthMultiplier=1.5, EnemySpeed=1.05, GoldMultiplier=1.5, XPBonus=1.15)),
}


# ---------------------------------------------------------------------------------------------------------------------------
# v1.4 chunk C: REFINERY DISTRICT ("Stay moving", spec 1.2). The roster additions, the rows, the bosses, the events and the
# unique of the finale; the mechanics live in game/stage/RefineryRules and game/boss/IronheartAi.
# ---------------------------------------------------------------------------------------------------------------------------
REFINERY_ENEMIES = {
    # welder variant: after every dash a fire patch (fireR/fireT/fireDps), immune to Burn (RefineryRules)
    "STOKER": dict(name="Stoker", archetype="STOKER", maxHp=3.5, speed=110, power=20, xp=5, sprite="welder",
                   params=dict(radius=16, runSpeed=300, minDist=80, maxDist=160, gapMin=2, gapMax=6, windup=0.6, dash=0.6,
                               recover=1.0, fireR=40, fireT=3, fireDps=6, tintRGB=0xFF5A1F, drawScale=1.15)),
    # cargo loader variant: -50% from hits, +50% from Burn / Electrify / Decay, no knockback, at most 8 alive
    "IRONCLAD": dict(name="Ironclad", archetype="IRONCLAD", maxHp=5.0, speed=75, power=24, xp=9, knockback=0, sprite="cargo_loader",
                     params=dict(radius=14, tintRGB=0x8A97A6, drawScale=0.8)),
    # timed bosses (BossAi pattern 12, game/boss/IronheartAi; `variant` picks the pattern)
    "FOREMAN_BRASS": dict(name="Foreman Brass", archetype="BOSS", boss=True, maxHp=300, speed=65, power=30, xp=100,
                          sprite="smelter", scale=2.4,
                          description="Carpets the floor with slag and stomps: every stomp turns all the belts around.",
                          params=dict(pattern=12, variant=1, hpRamp=20, radius=10, tintRGB=0xFFC080, gearSlot=3, xpPileLevels=1.5,
                                      castEvery=6, castTime=0.9, pools=5, poolR=60, poolT=3, poolDps=7, stompR=110, stompDmg=20)),
    "STOKER_CAPTAIN": dict(name="Stoker Captain", archetype="BOSS", boss=True, maxHp=420, speed=70, power=24, xp=100,
                           sprite="welder", scale=2.2,
                           description="Three chained dashes, each ending in a ring of fire and a burning pool.",
                           params=dict(pattern=12, variant=2, hpRamp=20, radius=10, tintRGB=0xFF5A1F, gearSlot=1, xpPileLevels=1.5,
                                       pre=0.9, dashLen=200, dash=0.45, gap=0.25, lineW=46, dashDmg=16, recover=2.0, gapMin=2.0,
                                       gapMax=3.5, range=380, dashes=3, ringR=120, ringWarn=0.55, ringDmg=14, poolR=44, poolT=3,
                                       poolDps=7)),
    # the finale at 28:00: Slagborn x3.2, white-hot
    "IRONHEART": dict(name="The Ironheart", archetype="BOSS", boss=True, maxHp=1800, speed=60, power=40, xp=250,
                      sprite="slagborn", scale=3.2,
                      description="Belts drag you in, heat waves cross from both sides, the gates open. Make its slam smash a coolant valve.",
                      params=dict(pattern=12, variant=0, hpRamp=28, radius=10, tintRGB=0xFFA050, gearSlot=4, xpPileLevels=2.0,
                                  phase1Hp=0.66, phase2Hp=0.33, slamR=150, slamWarn=1.0, slamDmg=28, slamEvery=3.5, waveEvery=12,
                                  trail=45, trailT=4, trailDps=6, ventR=230, ventWarn=1.2, ventDmg=14, ventHeat=30)),
}
ENEMIES.update(REFINERY_ENEMIES)
# the Ironheart Core: Ring, dropped by the finale. Overheat limit 100 -> 130 and +15% might while the heat is above 50 (RefineryRules);
# the hero can run hotter and hit harder for it, but the burn from 50 heat (1.5/s) and Overheat (5/s) are still there.
UNIQUES["IRONHEART_CORE"] = ("RING", "Ironheart Core", "unique_ironheart_core", {"heatCore": 1}, "IRONHEART")

KR = dict(K, STK="STOKER", IRC="IRONCLAD")
# composition per minute (the numbers per row come from the Flats rows): Colonists and Drones from 0, Welders from 3, Smelters from 6,
# Stokers from 9, Ironclads from 11; about 15% fewer fodder and 15% more armour than Flats.
REFINERY_COMP = {
    0: "COL", 1: "COL", 2: "COL DRN", 3: "COL DRN WLD", 4: "COL DRN WLD", 5: "DRN WLD SGT", 6: "COL SML SGT", 7: "DRN WLD SML",
    8: "COL DRN SML SGT", 9: "WLD STK DRN", 10: "SML STK COL", 11: "IRC SGT WLD", 12: "DRN STK SML IRC", 13: "COL IRC SGT",
    14: "WLD STK SML", 15: "IRC SML SGT STK", 16: "DRN STK IRC", 17: "COL SML IRC STK", 18: "WLD DRN IRC SPC", 19: "SML IRC VIR",
    20: "STK SGT VIR IRC", 21: "DRN SML IRC", 22: "WLD STK SPC IRC", 23: "COL SML IRC VIR", 24: "DRN STK WLD IRC",
    25: "SML IRC VIR STK", 26: "SGT DRN WLD IRC", 27: "COL SML IRC VIR", 28: "STK WLD SML IRC", 29: "COL DRN WLD SML STK IRC VIR",
}
REFINERY_BOSSES = {5: ["CARGO_LOADER"], 10: ["SMELTER_FOREMAN"], 15: ["SLAGBORN"], 20: ["VIRE_AWAKENED"],
                   25: ["CARGO_LOADER", "CARGO_LOADER"], 29: ["SMELTER_FOREMAN"]}
# 7:30 Foreman Brass and 12:30 Stoker Captain replace the Brood Mother and the Frozen Welder; Hale, the Overseer and Kade stay;
# the finale comes at 28:00 where the Tide would
REFINERY_TIMED = [(450, "FOREMAN_BRASS"), (750, "STOKER_CAPTAIN"), (1050, "COMMANDANT_HALE"), (1350, "OVERSEER_UNIT_7"),
                  (1650, "GOVERNOR_KADE"), (1680, "IRONHEART")]
# 21:00 Cooling Tower (Signal Tower), 23:00 Slag Rain, 24:00 Overdrive (Lumen Surge), 26:00 Defend the Boiler (Beacon)
REFINERY_EVENTS = [("SIGNAL_TOWER", 1260), ("SLAG_RAIN", 1380), ("LUMEN_SURGE", 1440), ("BEACON", 1560)]


def refinery_tune(st, data):
    """Rows (the Flats numbers, the Refinery roster), bosses and events of the stage; everything else stays the copy of Flats."""
    flats = {r["minute"]: r for r in FLATS_ROWS}
    rows = []
    for m in range(30):
        f = flats[m] if m in flats else None
        rows.append(row(m, 0, 0, "COL", ()) if f is None else dict(f))
    for r in rows:
        m = r["minute"]
        r["enemies"] = [KR[k] for k in REFINERY_COMP[m].split()]
        r["bosses"] = list(REFINERY_BOSSES.get(m, []))
    old = {r["minute"]: r for r in st["rows"]}
    out = []
    for r in apply_difficulty(rows, tune_of("REFINERY")):
        r = dict(r)
        if r["minute"] in old and "treasure" in old[r["minute"]]:
            r["treasure"] = old[r["minute"]]["treasure"]
        out.append(r)
    st["rows"] = out
    st["timedBosses"] = [dict(t=t, boss=b) for t, b in REFINERY_TIMED]
    st["events"] = [dict(id=i, at=t) for i, t in REFINERY_EVENTS]


# ---------------------------------------------------------------------------------------------------------------------------
# v1.4 chunk B: Hydroponic Vaults (game/stage/HydroRules, game/layout/DomesLayout, game/boss/RootmindAi). Enemies, the stage's rows,
# timed bosses, events, hordes and the finale's unique. Applied by add_v14_stages through hydro_stage().
# ---------------------------------------------------------------------------------------------------------------------------
HYDRO_ENEMIES = {
    # ARCH_HEALER: heals every enemy within 110 u by 6% every 2 s (HydroRules.updateArch), at most 6 alive
    "GARDENER": dict(name="Gardener", archetype="HEALER", maxHp=3.0, speed=100, power=12, xp=3, sprite="infected_colonist",
                     description="Pulses a heal over every enemy near it. Kill it first.",
                     params=dict(radius=12, tintRGB=0x6FD36F, drawScale=1.1)),
    # a third of a hound's health; comes in swarms of 8 from the edge of a growth patch
    "MYCELID": dict(name="Mycelid", archetype="FAST_PACK", maxHp=round(0.5 / 3, 4), speed=160, power=7, xp=1, sprite="spore_hound",
                    params=dict(radius=7, group=8, weave=0.8, phase=1.0, tintRGB=0xBFE8C8, drawScale=0.7)),
    # Frozen Welder (pattern 7) without frost: its dashes leave sprouts instead (HydroRules.sprouts)
    "VERDANT_WELDER": dict(name="Verdant Welder", archetype="BOSS", boss=True, maxHp=420, speed=70, power=24, xp=100,
                           sprite="welder", scale=2.0,
                           description="Three chained dashes; every dash sows growth patches.",
                           params=dict(pattern=7, hpRamp=20, radius=12, tintRGB=0x6FD060, gearSlot=4, xpPileLevels=1.5,
                                       pre=0.9, dashLen=190, dash=0.45, gap=0.25, lineW=46, dashDmg=16, recover=2.0,
                                       gapMin=2.0, gapMax=3.5, range=380, patchEvery=1000000, patchR=38, patchT=0.02,
                                       ringR=140, ringExtra=0.3, ringDmg=14, ringStun=0.4)),
    # the finale (BossAi pattern 11 -> game/boss/RootmindAi): stationary, shielded by three hearts
    "ROOTMIND": dict(name="The Rootmind", archetype="BOSS", boss=True, maxHp=1800, speed=0, power=30, xp=250,
                     sprite="spore_carrier", scale=3.5,
                     description="Stands in a dome behind three shield hearts. Burn the hearts, then hit it.",
                     params=dict(pattern=11, hpRamp=28, radius=20, tintRGB=0xC8E070, xpPileLevels=1.5, phase1Hp=0.66,
                                 phase2Hp=0.33, ring=12, ringSpeed=200, ringDmg=12, ringEvery=3.5, ringEvery3=5,
                                 lanes=2, laneLen=560, laneW=46, laneDps=22, laneWarn=1.0, laneLife=3.2, laneSpin=0.85,
                                 laneGap=2.0, hounds=6, houndEvery=9, sporeEvery=10)),
    # a shield heart of the Rootmind: pinned like a tentacle, stays (the Rootmind sets its health to 120)
    "ROOT_HEART": dict(name="Root Heart", archetype="TENTACLE", maxHp=9.2, speed=0, power=0, xp=2, knockback=0,
                       sprite="lumen_tentacle",
                       params=dict(radius=18, warn=1.0, up=99999, hitR=34, drawScale=1.4, tintRGB=0xC8E070)),
}
ENEMIES.update(HYDRO_ENEMIES)
HYDRO_UNIQUES = {
    "ROOTMINDS_HEARTWOOD": ("NECK", "Rootmind's Heartwood", "unique_ashen_heartwood",
                            {"healMul": 0.25, "heartVacuum": 0.25}, "ROOTMIND"),
}
UNIQUES.update(HYDRO_UNIQUES)

KH = dict(K, GDN="GARDENER")


def hrow(minute, minimum, freq, enemies, bosses=()):
    return dict(minute=minute, minimum=minimum, frequency=freq, enemies=[KH[e] for e in enemies.split()], bosses=list(bosses))


# Minutes 0-5 colonists and hounds, carriers every minute from 6, Gardeners from 8, Vire from 15; the density is ~10% under Flats
# (minutes 0-4 by hand, from minute 5 apply_difficulty x 0.9). The 29:00 bosses are gone: the finale is the closing fight.
HYDRO_ROWS = [
    hrow(0, 9, 1400, "COL"), hrow(1, 13, 1200, "COL HND"), hrow(2, 16, 1100, "COL HND"), hrow(3, 20, 1000, "COL HND"),
    hrow(4, 23, 900, "COL HND"), hrow(5, 20, 1100, "COL HND", ["CARGO_LOADER"]), hrow(6, 24, 1000, "COL HND SPC"),
    hrow(7, 30, 900, "COL HND SPC SGT"), hrow(8, 32, 850, "HND SPC SGT GDN"), hrow(9, 36, 800, "COL SPC GDN WLD"),
    hrow(10, 40, 800, "HND SPC GDN WLD", ["SMELTER_FOREMAN"]), hrow(11, 44, 750, "COL SPC GDN SGT"),
    hrow(12, 48, 750, "HND SPC GDN WLD"), hrow(13, 52, 700, "COL HND SPC GDN SGT"), hrow(14, 56, 700, "HND SPC GDN SML"),
    hrow(15, 60, 650, "COL SPC GDN VIR", ["SLAGBORN"]), hrow(16, 64, 650, "HND SPC GDN WLD"),
    hrow(17, 68, 600, "COL SPC GDN SML"), hrow(18, 72, 600, "HND SPC GDN VIR"), hrow(19, 76, 600, "COL SPC GDN SML VIR"),
    hrow(20, 80, 550, "HND SPC GDN SGT VIR", ["VIRE_AWAKENED"]), hrow(21, 84, 550, "COL SPC GDN SML"),
    hrow(22, 88, 500, "HND SPC GDN WLD"), hrow(23, 92, 500, "COL SPC GDN SML VIR"), hrow(24, 96, 500, "HND SPC GDN WLD"),
    hrow(25, 100, 450, "COL SPC GDN SML VIR", ["CARGO_LOADER", "CARGO_LOADER"]), hrow(26, 110, 450, "HND SPC GDN SGT WLD"),
    hrow(27, 120, 400, "COL SPC GDN SML VIR"), hrow(28, 130, 400, "HND SPC GDN WLD VIR"),
    hrow(29, 140, 350, "COL HND SPC GDN SML VIR"),
]
HYDRO_DENSITY = 0.9
HYDRO_TIMED = [(450, "BROOD_MOTHER"), (750, "VERDANT_WELDER"), (1050, "COMMANDANT_HALE"), (1350, "OVERSEER_UNIT_7"),
               (1680, "ROOTMIND")]
# Purge Terminal / Sporefall / Bloom run from HydroRules (rules purgeAt, bloomAt; Sporefall 4:00 every 4 min and 23:00); the data
# keeps the Beacon (26:00). The Tide is replaced by the finale at 28:00.
HYDRO_EVENTS = [("BEACON", 1560)]


def hydro_stage(st):
    """Replaces the Flats copy by the Hydroponic Vaults' rows, timed bosses, events and hordes."""
    old = {r["minute"]: r for r in st["rows"]}
    rows = []
    tn = tune_of("HYDRO")
    for r in apply_difficulty(HYDRO_ROWS, tn):
        r = dict(r)
        r["minimum"] = int(round(r["minimum"] * HYDRO_DENSITY))
        if "treasure" in old.get(r["minute"], {}):
            r["treasure"] = old[r["minute"]]["treasure"]
        rows.append(r)
    st["rows"] = rows
    st["timedBosses"] = [dict(t=t, boss=b) for t, b in HYDRO_TIMED]
    st["events"] = [dict(id=i, at=t) for i, t in HYDRO_EVENTS]
    st["hordes"] = dict(types=tn["horde_types"], bursts=[dict(at=a, length=tn["horde_len"], minimum=mn, frequency=tn["horde_freq"]) for a, mn in tn["hordes"]])
    st["destructibleType"] = "MIXED_FLATS"


def add_v14_stages(data):
    """Adds the registry's stages to data['stages'] / data['lighting'] and the card fields to every menu stage."""
    import copy
    stages, lighting = data["stages"], data.setdefault("lighting", {})
    for key, reg in STAGE_REGISTRY.items():
        if "copy_of" in reg:
            st = copy.deepcopy(stages[reg["copy_of"]])
            st["name"] = reg["name"]
            for mod, mul in reg["mods"].items():
                st["mods"][mod] = round(st["mods"][mod] * mul, 4)
            stages[key] = st
            lighting[key] = reg["lighting"]
        st = stages[key]
        st["difficulty"] = reg["difficulty"]
        st["blurb"] = reg["blurb"]
        if reg["unlock"]:
            st["unlock"] = dict(key=reg["unlock"][0], seconds=reg["unlock"][1])
        st["rules"] = reg["rules"]
        if reg.get("tune"):   # a stage's own rows, bosses and events (the section of its chunk below)
            globals()[reg["tune"]](st, data)
        if key == "HYDRO":
            hydro_stage(st)


# ---------------------------------------------------------------------------------------------------------------------------
# Meridian Wreck (v1.4 chunk D): roster additions, rows, timed bosses, events, hordes and the Echo of the Pillar. Applied by add_wreck()
# after the registry copied Cinder Flats into WRECK; nothing here touches another stage.
# ---------------------------------------------------------------------------------------------------------------------------
WRECK_ENEMIES = {
    # spore_hound in violet: blinks to a flank every 6 s (game/stage/WreckRules.updateArch), packs of 4 (WreckRules.initArch)
    "RIFT_HOUND": dict(name="Rift Hound", archetype="RIFT", maxHp=0.5, speed=165, power=7, xp=1, sprite="spore_hound",
                       params=dict(radius=9, drawScale=1.05, tintRGB=0x8A5CFF, group=4, weave=0.8, blinkEvery=6, tele=0.3,
                                   blinkMin=150, blinkMax=250)),
    # security_drone in hazard yellow: a thin warning line for 1.0 s, then a dive that bursts (r 60, 20 damage); at most 8 alive
    "BREACHER": dict(name="Breacher", archetype="BREACHER", maxHp=0.4, speed=150, power=3, xp=2, sprite="security_drone",
                     params=dict(radius=8, drawScale=0.85, tintRGB=0xE8C23A, warn=1.0, diveSpeed=380, diveLen=340, lineW=14,
                                 burstR=60, burstDmg=20, cap=8, range=300, gapMin=2.5, gapMax=5.5)),
    # remixes of the timed bosses (BossAi pattern 13 is the Wreck family: mode 1 Alpha, 2 Warden, 0 The Pillar; game/boss/PillarAi)
    "HULL_BREACHER_ALPHA": dict(name="Hull Breacher Alpha", archetype="BOSS", boss=True, maxHp=300, speed=70, power=24, xp=100,
                                sprite="cargo_loader", scale=1.8,
                                description="Stomps the deck; every stomp tears open a short-lived gravity well on you.",
                                params=dict(pattern=13, mode=1, hpRamp=20, radius=20, tintRGB=0x9A7A1E, gearSlot=3, xpPileLevels=1.5,
                                            stompEvery=4.2, stompR=130, stompWarn=0.9, stompDmg=22, wellR=160, wellT=6,
                                            crates=3, crateR=44, crateWarn=1.0, crateDmg=16, stopDist=60)),
    "SALVAGE_WARDEN": dict(name="Salvage Warden", archetype="BOSS", boss=True, maxHp=280, speed=80, power=22, xp=100,
                           sprite="security_drone", scale=3.0,
                           description="Hovers and fires drone barrages; blinks away and leaves decoys that burst.",
                           params=dict(pattern=13, mode=2, hpRamp=20, radius=9, tintRGB=0x9AA0AA, gearSlot=4, xpPileLevels=1.5,
                                       hover=230, barrageEvery=3.2, fan=7, fanSpread=0.55, boltSpeed=210, boltDmg=9,
                                       blinkEvery=9, blinkTele=0.6, blinkMin=200, blinkMax=300, decoys=2)),
    "WARDEN_DECOY": dict(name="Warden's Decoy", archetype="TENTACLE", maxHp=0.1, speed=0, power=0, xp=1, knockback=0,
                         sprite="security_drone",
                         params=dict(radius=10, warn=0.9, up=8.0, hitR=60, ring=1, blast=12, drawScale=3.0, tintRGB=0x9AA0AA)),
    # the finale: a stationary prop-sized boss (its picture is prop.the_pillar drawn by WreckFx, the model is nearly invisible)
    "THE_PILLAR": dict(name="The Pillar", archetype="BOSS", boss=True, maxHp=1800, speed=0, power=30, xp=250, knockback=0,
                       sprite="the_first", alpha=0.02,
                       description="A dead ship's spine still pulling the wreck down: beams, orbiting wells, a failing bubble.",
                       params=dict(pattern=13, mode=0, hpRamp=28, radius=34, gearSlot=5, xpPileLevels=2.0, phase1Hp=0.66,
                                   phase2Hp=0.33, beams=4, beamLen=520, beamW=46, beamDps=22, beamWarn=1.0, beamLife=5.0,
                                   beamSpin=0.85, beamEvery=8, orbitDist=260, orbitSpeed=0.6, echoEvery=8, echoN=16, echoSpeed=190,
                                   echoDmg=14, bubbleR=170, bubbleDist=140, pulseEvery=6, pulseWarn=1.5, pulseDmg=28)),
}
WRECK_PORTRAITS_NOTE = "boss portraits are mapped in tools/build_ashfall_assets.py PORTRAITS"

# minute -> enemy keys (the spec's pacing: drones and colonists 0-4, Rift Hounds from 4, Sergeants from 6, Vire from 8, Breachers from 9, Carriers from 14)
def wreck_enemies(m):
    if m < 4:
        return ["SECURITY_DRONE", "COLONIST"]
    if m < 6:
        return ["SECURITY_DRONE", "COLONIST", "RIFT_HOUND"]
    if m < 8:
        return ["SERGEANT", "SECURITY_DRONE", "COLONIST", "RIFT_HOUND"]
    if m < 9:
        return ["SERGEANT", "SECURITY_DRONE", "RIFT_HOUND", "VIRE"]
    if m < 11:
        return ["SERGEANT", "SECURITY_DRONE", "RIFT_HOUND", "VIRE", "BREACHER"]
    if m < 14:
        return ["SERGEANT", "SECURITY_DRONE", "RIFT_HOUND", "BREACHER", "WELDER"]
    return ["COLONIST", "SERGEANT", "RIFT_HOUND", "BREACHER", "SPORE_CARRIER", "VIRE" if m % 2 == 0 else "WELDER"]


WRECK_BOSS_ROWS = {5: ["CARGO_LOADER"], 10: ["SMELTER_FOREMAN"], 15: ["SLAGBORN"], 20: ["VIRE_AWAKENED"], 25: ["CARGO_LOADER", "CARGO_LOADER"]}
# 27:30 Kade is dropped for the Pillar Gate (WreckRules); The Pillar itself is the fifth timed boss at 28:00 (its AI moves it to its spire)
WRECK_TIMED = [(450, "HULL_BREACHER_ALPHA"), (750, "SALVAGE_WARDEN"), (1050, "COMMANDANT_HALE"), (1350, "OVERSEER_UNIT_7"), (1680, "THE_PILLAR")]
# Distress Beacon (the Signal Tower), Collapse, Power Surge (the Lumen Surge, 24:40) and the Beacon; the Tide is replaced by the gate and the finale.
# The Vacuum Breaches (4:00, 9:00, 14:00, 19:00, 24:00, 25 s each) are WreckRules; nothing here overlaps them.
WRECK_EVENTS = [("SIGNAL_TOWER", 1260), ("COLLAPSE", 1380), ("LUMEN_SURGE", 1480), ("BEACON", 1560)]
# the Flats bursts, moved off the breaches (14:00-14:25 and 19:00-19:25 each with their 2 s warning)
WRECK_HORDES = [[675, 75], [875, 95], [975, 130], [1170, 180], [1245, 240], [1420, 290], [1545, 290]]
WRECK_DENSITY = 1.10     # "the highest density of the five stages": +10% over Flats at equal minutes (the wave cap of 300 still holds)
# stage rules parameters (StageDef.rules, read by WreckRules with defaults of the same value)
WRECK_RULES = dict(padR=70, padChannel=0.5, padCooldown=12, padShockR=140, padShockDmg=30, wellR=220, wellOn=10, wellOff=6,
                   wellFlicker=1, wellPullHero=40, wellPullEnemy=70, wellPullPickup=120, coreR=24, coreEnemyDps=40,
                   coreEliteDps=15, coreHeroDps=15, breachLen=25, breachTele=2, breachR0=260, breachR1=140, breachDrift=40,
                   breachHeroDps=4, breachRamp=0.5, breachEnemyPct=0.03, gateAt=1650, gateUntil=1770)
ECHO_OF_THE_PILLAR = dict(name="Echo of the Pillar", slot="NECK", icon="unique_heart_of_lumen",
                          values={"padRecharge": 6, "padShock": 0.5}, unique=True, source="THE_PILLAR")


def add_wreck(data):
    """Meridian Wreck on top of the Flats copy: roster, rows, timed bosses, events, hordes, rules, lighting, the Echo."""
    for key, e in WRECK_ENEMIES.items():
        data["enemies"][key] = dict(e)
    st = data["stages"]["WRECK"]
    for r in st["rows"]:
        m = r["minute"]
        r["enemies"] = wreck_enemies(m)
        r["bosses"] = list(WRECK_BOSS_ROWS.get(m, []))
        r["minimum"] = min(300, int(round(r["minimum"] * WRECK_DENSITY)))
    st["timedBosses"] = [dict(t=t, boss=b) for t, b in WRECK_TIMED]
    st["events"] = [dict(id=i, at=t) for i, t in WRECK_EVENTS]
    # hordes: inherited from the Flats copy (TUNE_DEFAULT, v1.4 harder); the old WRECK_HORDES numbers are not applied any more
    st["rules"] = dict(WRECK_RULES)
    data["lighting"]["WRECK"] = dict(ambient=0.62, radius=230)
    data["equipment"]["ECHO_OF_THE_PILLAR"] = dict(ECHO_OF_THE_PILLAR)


def main():
    base = json.load(open(os.path.join(ASSETS, "game_data.json")))
    path = os.path.join(ASSETS, "ashfall_data.json")
    data = json.load(open(path))
    out_path = os.environ.get("OUT_JSON", path)   # experiments write a variant next to the real file

    def fix(params):  # "raise" is a Python keyword in the table above
        return {k.rstrip("_"): v for k, v in params.items()}

    enemies = {}
    for key, e in ENEMIES.items():
        e = dict(e)
        e["params"] = fix(e["params"])
        enemies[key] = e
    data["enemies"] = enemies

    def stage(key, name, vs_key, rows, treasure_map, destructible, mods_fn, spawn_type=None, starting=10):
        vs = base["stages"][vs_key]
        mods = {k: vs[0]["mods"][k] for k in ("TimeLimit", "PlayerPxSpeed", "EnemySpeed", "EnemyHealthMultiplier",
                                              "GoldMultiplier", "XPBonus")}
        mods_fn(mods)
        tre = treasure_rows(vs, treasure_map)
        out_rows = []
        for r in apply_difficulty(rows, tune_of(key)):
            r = dict(r)
            if r["minute"] in tre:
                t = tre[r["minute"]]
                r["treasure"] = {k: t[k] for k in ("chances", "level", "prizeTypes")}
                # design v1.3 1.4: FUSION before EVOLUTION in every boss chest (the first slot), a mini-boss chest included
                r["treasure"]["prizeTypes"] = ["FUSION", "EVOLUTION"] + list(t["prizeTypes"][1:])
            out_rows.append(r)
        o = dict(name=name, mods=mods, startingSpawns=starting, destructibleType=destructible, rows=out_rows)
        if spawn_type:
            o["spawnType"] = spawn_type
        return o

    def flats_mods(m):
        m["EnemyHealthMultiplier"] = FLATS_HEALTH

    def mines_mods(m):
        m["EnemyHealthMultiplier"] = MINES_HEALTH
        m["EnemySpeed"] = round(m["EnemySpeed"] * 1.05, 4)

    stages = data.setdefault("stages", {})
    stages["FLATS"] = stage("FLATS", "Cinder Flats", "FOREST", FLATS_ROWS, FLATS_TREASURE, "MIXED_FLATS", flats_mods,
                            starting=10)
    stages["MINES"] = stage("MINES", "Deep Mines", "LIBRARY", MINES_ROWS, MINES_TREASURE, "MIXED_MINES", mines_mods,
                            spawn_type="HORIZONTAL", starting=12)
    for key, fade in XP_FADE.items():
        stages[key]["xpFade"] = fade
    for key in ("FLATS", "MINES"):
        tn = tune_of(key)
        stages[key]["hpCurve"] = tn["hp_curve"]
        stages[key]["dmgCurve"] = tn["dmg_curve"]
        stages[key]["speedCurve"] = tn["speed_curve"]
        stages[key]["hordes"] = dict(types=tn["horde_types"], bursts=[dict(at=a, length=tn["horde_len"], minimum=mn, frequency=tn["horde_freq"])
                                                                    for a, mn in tn["hordes"]])
        if tn["elites"]:
            stages[key]["elites"] = tn["elites"]
        if tn["ranged_cap"]:
            stages[key]["rangedCap"] = tn["ranged_cap"]
    stages["MINES"]["tentacles"] = dict(minutes=[8, 13, 18, 23, 27], length=20, every=4, count=3, radius=400)
    for key, lst in TIMED_BOSSES.items():
        stages[key]["timedBosses"] = [dict(t=t, boss=b) for t, b in lst]
    for key, lst in EVENTS.items():
        stages[key]["events"] = [dict(id=i, at=t) for i, t in lst]
    add_v14_stages(data)   # v1.4: the three new stages (copies of Flats until their chunks tune them) and the card fields
    data["elites"] = ELITES
    data["powerups"] = {k: dict(price=p, exp=e) for k, (p, e) in POWERUP_PRICES.items()}
    eq = {}
    for key, (slot, name, icon, values) in EQUIPMENT.items():
        eq[key] = dict(name=name, slot=slot, icon=icon, values=values)
    for key, (slot, name, icon, values, source) in UNIQUES.items():
        eq[key] = dict(name=name, slot=slot, icon=icon, values=values, unique=True, source=source)
    data["equipment"] = eq
    data["equipmentRules"] = EQUIPMENT_RULES
    add_wreck(data)   # v1.4 chunk D: Meridian Wreck roster, rows, timed bosses, events, rules, the Echo of the Pillar
    data["fusions"] = {k: dict(name=FUSIONS[k]["name"], description=FUSIONS[k]["description"],
                               parents=FUSIONS[k]["parents"], rarity=1, rows=fusion_rows(FUSIONS[k])) for k in FUSION_KEYS}
    ov = json.loads(os.environ.get("TUNE_JSON") or "{}").get("*", {})   # experiments: hero_tune / main_tune over the tables above
    HERO_TUNE.update(ov.get("hero_tune", {}))
    MAIN_TUNE.update(ov.get("main_tune", {}))
    for table, root in ((HERO_TUNE, data["heroes"]), (MAIN_TUNE, data["mainWeapons"])):
        for dotted, value in table.items():
            keys = dotted.split(".")
            node = root
            for k in keys[:-1]:
                node = node[k]
            node[keys[-1]] = value
    for hero, text in HERO_TEXT.items():
        data["heroes"][hero]["description"] = text
    lighting = data.setdefault("lighting", {})
    lighting["FLATS"] = dict(ambient=0.65, radius=240)
    lighting["MINES"] = dict(ambient=0.78, radius=200)
    with open(out_path, "w") as f:
        json.dump(data, f, indent=1, ensure_ascii=False)
        f.write("\n")
    print("wrote", out_path)


if __name__ == "__main__":
    main()
