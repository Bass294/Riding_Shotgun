# Riding Shotgun

Starsector mod (0.98a-RC8). Id `rsg_riding_shotgun`, prefix `rsg_`. Java in `data/scripts` (package `data.scripts`),
compiled into `jars/RidingShotgun.jar` by `build.sh`; rules commands live in `data.scripts.rulecmd`, registered in
`data/config/settings.json`.

## Documents

- `docs/LORE.md`: established lore (author-approved facts only)
- `docs/quests/rsg_gemini_station.md`: Research Station Gemini, decisions and open items

## Notes for rules checks

`rules_check.py` reports `data/scripts/rulecmd/*.java` as loose scripts. They are compiled into the jar, so the
"rules command class as a loose script" error does not apply to this mod.
