# Riding Shotgun

Starsector mod (0.98a-RC8). Id `rsg_riding_shotgun`, prefix `rsg_`. Java in `data/scripts` (package `data.scripts`),
compiled into `jars/RidingShotgun.jar` by `build.sh`; rules commands live in `data.scripts.rulecmd`, registered in
`data/config/settings.json`.

## Documents

- `docs/LORE.md`: established lore (author-approved facts only)
- `docs/quests/rsg_gemini_station.md`: Research Station Gemini, decisions and open items

## Co-pilot skill whitelist

Co-pilots apply vanilla skills (ids from the game's own `skill_data.csv`) and only those modded skills listed in
`data/config/rsgConfig/rsg_skill_whitelist.csv`. Skills in `data/config/rsgConfig/rsg_skill_blacklist.csv` (confirmed
not working) never apply, overriding the whitelist and vanilla. Both files: `id`, `notes`, merged across mods. The
LunaLib setting `rsg_useSkillWhitelist` ("Use skill whitelist", default on, `data/config/LunaSettings.csv`) turns
the whitelist off; the blacklist still applies. Unsupported skills are skipped and shown under "Unsupported:" in the
co-pilot tooltip. Logic: `data/scripts/officers/rsg_SkillWhitelist.java`.

## Notes for rules checks

`rules_check.py` reports `data/scripts/rulecmd/*.java` as loose scripts. They are compiled into the jar, so the
"rules command class as a loose script" error does not apply to this mod.
