---
name: thor-survivors-patch-notes
description: Write the Swedish player-facing "Nyheter" notes for the owner of Thor Survivors from the git log and pull requests of this repo. Use when a new APK is handed over, when the owner asks what changed, or for a release summary.
---

# Patch notes - "Nyheter" (thor-survivors)

Adapted from Claude-Code-Game-Studios (MIT), see .claude/CCGS-LICENSE.txt.

The reader is the owner, playing on the AYN Thor. Write in Swedish, plain and short, from the player's point of view.
No class names, file names, PR numbers or build jargon.

## Steps

1. Provenance: confirm the history is this game's. `git log --oneline` should show Thor Survivors commits
   (this repo holds only Thor Survivors; `.claude/` changes are not game changes: drop them). Commits from
   before the move are in felixarvid2/test under `thor-survivors/`; the owner's older history is there.
2. Range: since the last handed-over APK. Find it with `git log --oneline -- dist/thor-survivors.apk`
   (the newest commit that touched the APK is the last hand-over), or take the range the owner names. Then
   `git log --format='%h %s%n%b' <last>..HEAD`. For merged PRs read their descriptions with
   `gh pr view <n>` (or the github MCP tools) when the commit message is thin. If the range is empty, say so.
3. Read the diff only for what the message does not say: `git diff <last>..HEAD --stat`, and
   `game_data.json` changes for before -> after values. Verify a value before quoting it.
4. Sort into: **Nytt** (new weapon, enemy, stage, screen, sound), **Ändrat** (balance and feel, with before -> after
   numbers the player can understand: "Whip: 20 % mer skada på nivå 3"), **Fixat** (what the player saw go wrong, not the
   technical cause: "Fiender blinkar inte längre när de blir träffade"), **Prestanda**, **Känt** (known issues, docs/TEKNIK.md's
   "Ej testat på riktig hårdvara" if still true and relevant).
5. Merge many small commits about one thing into one line. Skip refactors, tests, tooling and docs. Do not invent
   features: every line must trace to a commit or diff you read.
6. Mention how to install only if it changed: copy `dist/thor-survivors.apk` to the device; the APK is signed with
   the project key, so it installs over the old one.

## Output

```
# Nyheter - <datum eller version>
**Nytt**
- ...
**Ändrat**
- ... (före -> efter)
**Fixat**
- ...
**Känt**
- ...
```
Omit empty sections. Aim for 5-15 lines. Reply with the text; if the owner wants a file, put it in
`NYHETER.md` (repo root) and say so. Delegating the wording to `scribe` is fine; give it the verified facts.
