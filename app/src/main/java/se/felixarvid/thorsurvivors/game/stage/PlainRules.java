package se.felixarvid.thorsurvivors.game.stage;

/** The legacy Vampire Survivors stages (FOREST, LIBRARY): no hooks, no finds. */
public final class PlainRules extends StageRules {
    @Override
    public boolean hasFinds() {
        return false;
    }
}
