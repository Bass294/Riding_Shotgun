# Research Station Gemini

A relic station in the style of Random Assortment of Things' Relics of the Past: one per sector, defended by
Remnants, salvaged at the end. The interaction is done in rules.csv, not an interaction dialog script.

## Decisions (asked 2026-10-22)

- **Text:** drafted by Claude in RAT's relic station style. Rows still drafted are marked `INVENTED` in rules.csv's
  notes column for review. The author's own text (2026-10-23): the Explore text (`rsg_gemini_continue`) and the
  post-battle records (`rsg_gemini_beatDefenders`, highlighting "hull modification" and "two officers"). The
  station description is vanilla's `station_research_remnant`.
- **Name:** Research Station Gemini.
- **Defenders:** a large Remnant fleet that scales to the player's fleet, always led by a Radiant with 3 s-mods.
  Its captain is the "Gemini Core", with all 14 vanilla combat skills elite and a custom portrait.
- **Placement:** one station per sector, in RAT-style systems.
- **Salvage:** a salvage at the end; unique items are to be added later.

## How it works

| Piece | Where |
|---|---|
| Entity | `data/config/custom_entities.json` (`rsg_gemini_station`), description: vanilla's `station_research_remnant` (2026-10-23) |
| Placement | `data/scripts/world/rsg_GeminiStationGenerator.java`, run from `rsg_modPlugin.onGameLoad` once (`$rsg_gemini_generated`); the station is stored in `$global.rsg_gemini_station` |
| Defenders | `data/scripts/rulecmd/rsg_GeminiStationCMD.java` (`prepareDefenders`), built on arrival so it scales; fought through vanilla `SalvageDefenderInteraction` |
| Dialog | `data/campaign/rules.csv`, rows `rsg_gemini_*` |
| Loot | `data/campaign/procgen/salvage_entity_gen_data.csv`, row `rsg_gemini_station` |

Flow: open (`rsg_gemini_open`) -> Continue -> defenders -> win -> `BeatDefendersContinue` (`rsg_gemini_beatDefenders`)
-> "Loot the station's storage" (`rsg_gemini_loot`, vanilla `SalvageEntity performSalvage`) -> closing text
(`rsg_gemini_postSalvage`, trigger `PostSalvagePerform`) -> the station fades out. Returning after the fight without
looting shows `rsg_gemini_openDefeated`.

Placement rules (from RAT): a system tagged theme_ruins (weight 70), theme_derelict (15), theme_remnant (15) or
theme_misc (5); not abyssal, no pulsar, no RAT relic (`rat_has_relic`). Orbit picked by vanilla
`BaseThemeGenerator.getLocations` with planet orbit 5, asteroid belt 1, star orbit 1.

## Unique loot (decided 2026-10-22)

- The station always drops one Dual Command Interface schematic and one **Gemini Core** item
  (`rsg_gemini_core`, `data/campaign/special_items.csv`, icon `graphics/icons/cargo/duelcore_cargo.png`), via
  `rsg_GeminiStationCMD addStationLoot` (vanilla extra salvage) in `rsg_gemini_loot`.
- The Gemini Core item is not an AI core. The Dual Command Interface requires it (`getRequiredItem`), using vanilla's
  `HullModItemManager`: taken when installed, returned when the hullmod is removed or the ship scuttled, dropped as
  loot if the ship is destroyed.
- Hullmod sprite: `graphics/hullmods/duelcore_hullmod.png`.

## Bar lead (decided 2026-10-22)

Built in rules.csv with RAT's Relics of the Past structure, pointing to the station if the player has not found it.

- **Giver:** Cecilia Corabelle, archivist (rank `rsg_archivist` in `data/world/factions/default_ranks.json`, post
  scientist, faction independent), vanilla portrait `graphics/portraits/portrait_league07.png`. Important person
  `rsg_archivist`, created on load by `rsg_GeminiLead`. Female (`Gender.FEMALE`, set 2026-10-22).
- **Frequency:** RAT-like random. `rsg_GeminiStationCMD barCanShow` passes at markets that are not hidden or pirate,
  while the station is undiscovered and no lead was taken, and only at markets that win a roll; each market re-rolls
  every 35 days.
- **Rows:** `rsg_bar_geminiAdd` (prompt), `rsg_bar_geminiTalk` (opening), `rsg_bar_geminiAccept`,
  `rsg_bar_geminiDecline`. RAT's familiar-archivist "Known" variants were removed (2026-10-23): the archivist has no
  further leads planned. RAT's unfinished "Show specification" option was left out.
- **Accepting:** map marker on the station, then `acceptLead` adds `rsg_GeminiLeadIntel` (data/scripts/intel), which
  confirms the station once discovered (+10 reputation with Cecilia) and is removed when the station is gone or with
  its Remove Intel button.
- Tokens `$global.rsg_geminiStationName` / `$global.rsg_geminiSystemName` are set on every load.

## Values chosen by Claude (INVENTED, change freely)

- Fleet size: Remnant combat points = player fleet points x 1.0, clamped to 150-450, plus the Radiant.
- Fleet name "Gemini Defense Network"; fleet type task force, quality 1.
- Radiant variant `radiant_Standard`; its s-mods are its own hullmods `targetingunit`, `hardenedshieldemitter`,
  `stabilizedshieldemitter`. The Gemini Core starts from an alpha core person, level 14, and the Radiant does not
  drop an ordinary alpha core.
- Bar lead: show chance 25% per market per 35-day window; intel title "Research Station Gemini"; vanilla intel icon
  `discovered_entity.png`; all bar text.
- Loot: the vanilla Remnant research station's drops (`basic:8000`, AI cores, Remnant weapons, hullmods,
  blueprints, rare tech).
- Sprite, icon and interaction image borrowed from the vanilla research station.

## Assets

- Gemini Core portrait: `graphics/portraits/portrait_dualcore.png`, registered in `data/config/settings.json`
  as `graphics > characters > rsg_gemini_core`. (2026-10-22)
- Post-fight illustration: `graphics/illustrations/drift_display_rsg.png`, registered as
  `graphics > illustrations > rsg_gemini_display`; shown after the win and on return before looting. (2026-10-22)

- Music: vanilla's salvage music (`music_survey_and_scavenge`), from the `salvage_music` tag on the entity.
  (2026-10-22)

## Open

- Further unique salvage items: add them in `rsg_GeminiStationCMD.addStationLoot` (guaranteed) or as a drop group
  on the `rsg_gemini_station` row in `salvage_entity_gen_data.csv` (random).
- Review the drafted Gemini Core item description in `data/campaign/special_items.csv` (INVENTED).
- Review and replace the drafted text.
