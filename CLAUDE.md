# Working in this repository

Thor Survivors ("ASHFALL: Lumen Tide") is a plain-Java Android game for the AYN Thor dual-screen handheld:
a Vampire Survivors + Halls of Torment style survivor in the Ashfall world. It moved here from the
`thor-survivors/` folder of felixarvid2/test; older history is there. Start with `README.md` and
`docs/ASHFALL_DESIGN.md`. The owner writes Swedish; answer in Swedish.

The build tools read source art and audio from the shared asset library `assets/` in felixarvid2/test:
clone that repo next to this one (`../test`) or set `ASSET_LIBRARY` to its `assets/` folder. The game
itself builds without it.

## Who does what

Work is split by model; the subagents live in `.claude/agents/`.

| Work | Who | Model |
|---|---|---|
| Finding files and symbols, reading code, answering "where/how" | `Explore` | Haiku |
| Simple mechanical edits that are already fully specified (renames, strings, data values, the same small change in many places) | `mechanic` | Haiku |
| Regular coding: implementing a specified feature or fix, tests, planned refactors | `implementer` | Sonnet |
| Running the project's checks (tests, build) and reporting only the verdict and failures | `verifier` | Haiku |
| Docs from a short summary: README, notes, commit and PR bodies | `scribe` | Haiku |
| Shipping: commit, push, PR (merge only after the user approves) | `shipper` | Haiku |
| Architecture, trade-offs, hard bugs with an unknown cause, risk review | the main session, or `architect` when the main session runs a smaller model | Opus |
| Game design: core loop, weapons, passives, evolutions, pacing; specs (VS as reference) | `game-designer` | Sonnet |
| Formulas, curves, economy; balance modelling with headless Sim runs | `systems-designer` | Sonnet |
| Frame time, allocation and memory analysis (headless measurements, device commands) | `performance-analyst` | Sonnet |
| Test cases, bug reports, regression checklists | `qa-tester` | Haiku |

- Delegate search and reading to `Explore` instead of reading many files yourself, and pass it the
  question, not a file list to dump.
- Hand `implementer` a clear spec (what, where, how to verify); hand `mechanic` the exact change.
  Run independent pieces in parallel.
- Keep decisions here: choosing an approach, reviewing what subagents return, and anything that
  spans several systems. Check their results (the diff and the verifier's verdict) before committing.
- Hand `verifier` the project to check; hand `scribe` the facts. `shipper` merges a pull request
  only when told that the user approved that merge.
- Standing approval from the user: after each change, once the checks pass, merge its pull request
  into `main` (merge commit) without asking first.
- Project skills in `.claude/skills/` cover shipping (`thor-survivors-release`), headless screenshots,
  sprites, balance, soak, performance, playtest reports, patch notes and quick design specs.
